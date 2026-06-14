import os
import cv2
import numpy as np
import math
from flask import Flask, request, jsonify

app = Flask(__name__)

# Load Haar Cascade for face detection
face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

def lab_to_hex(L, a, b):
    """Convert LAB to Hex (approximated via RGB)"""
    # This is a very simplified conversion. For production, use proper color profile math like colormath library.
    # We will use a quick approximation or OpenCV's native conversion
    lab_pixel = np.uint8([[[np.clip(L * 255/100, 0, 255), np.clip(a + 128, 0, 255), np.clip(b + 128, 0, 255)]]])
    bgr_pixel = cv2.cvtColor(lab_pixel, cv2.COLOR_LAB2BGR)[0][0]
    return "#{:02x}{:02x}{:02x}".format(bgr_pixel[2], bgr_pixel[1], bgr_pixel[0]).upper()

def get_ita_classification(ita_angle):
    if ita_angle > 55: return "Very Light"
    elif 41 < ita_angle <= 55: return "Light"
    elif 28 < ita_angle <= 41: return "Intermediate"
    elif 10 < ita_angle <= 28: return "Tan"
    elif -30 <= ita_angle <= 10: return "Brown"
    else: return "Dark"

def analyze_skin_roi(image, roi_rect):
    x, y, w, h = roi_rect
    # Extract ROI (just a simple center crop of the face for cheek/forehead approximation)
    # Forehead
    fh_roi = image[y + int(h*0.1): y + int(h*0.3), x + int(w*0.3): x + int(w*0.7)]
    # Cheeks
    lc_roi = image[y + int(h*0.5): y + int(h*0.8), x + int(w*0.1): x + int(w*0.4)]
    rc_roi = image[y + int(h*0.5): y + int(h*0.8), x + int(w*0.6): x + int(w*0.9)]
    
    # Combine ROIs
    if fh_roi.size == 0 or lc_roi.size == 0 or rc_roi.size == 0:
        raise ValueError("Invalid ROI")
        
    combined_roi = np.vstack((fh_roi.reshape(-1, 3), lc_roi.reshape(-1, 3), rc_roi.reshape(-1, 3)))
    combined_img = np.expand_dims(combined_roi, axis=0) # 1 x N x 3

    # 1. LAB Color & Skin Tone
    lab_roi = cv2.cvtColor(combined_img, cv2.COLOR_BGR2LAB)
    
    # OpenCV LAB ranges: L [0, 255], a [0, 255], b [0, 255]
    # We need to map to standard LAB: L [0, 100], a [-127, 127], b [-127, 127]
    L_mean, a_mean, b_mean = cv2.mean(lab_roi)[:3]
    L_std = L_mean * 100 / 255.0
    a_std = a_mean - 128
    b_std = b_mean - 128
    
    skin_tone_hex = lab_to_hex(L_std, a_std, b_std)
    
    # ITA Angle
    if b_std == 0: b_std = 0.001
    ita_angle = math.atan((L_std - 50) / b_std) * (180 / math.pi)
    skin_class = get_ita_classification(ita_angle)

    # 2. Texture Score (Laplacian Variance)
    gray_roi = cv2.cvtColor(combined_img, cv2.COLOR_BGR2GRAY)
    texture_score = cv2.Laplacian(gray_roi, cv2.CV_64F).var()
    # Normalize roughly to 0-100
    texture_normalized = min(100, max(0, int(texture_score / 10)))

    # 3. Pores Score (Canny Edge count)
    edges = cv2.Canny(gray_roi, 100, 200)
    pores_score = np.sum(edges > 0) / edges.size * 100
    pores_normalized = min(100, max(0, int(100 - pores_score * 5))) # Higher edges = lower pore score (worse)

    # 4. Hydration Score (HSV Brightness / V channel)
    hsv_roi = cv2.cvtColor(combined_img, cv2.COLOR_BGR2HSV)
    v_mean = cv2.mean(hsv_roi)[2]
    hydration_score = min(100, max(0, int(v_mean / 255 * 100)))

    return {
        "skin_tone_hex": skin_tone_hex,
        "ita_angle": round(ita_angle, 2),
        "skin_class": skin_class,
        "texture_score": texture_normalized,
        "pores_score": pores_normalized,
        "hydration_score": hydration_score,
        "lab": {
            "L": round(L_std, 2),
            "a": round(a_std, 2),
            "b": round(b_std, 2)
        }
    }

def get_mock_fallback():
    return {
        "success": True,
        "is_mock": True,
        "data": {
            "skin_tone_hex": "#D4A47C",
            "ita_angle": 35.5,
            "skin_class": "Intermediate",
            "texture_score": 85,
            "pores_score": 78,
            "hydration_score": 60,
            "lab": { "L": 65.2, "a": 12.5, "b": 18.3 }
        }
    }

@app.route('/analyze', methods=['POST'])
def analyze():
    if 'image' not in request.files:
        return jsonify({"success": False, "error": "No image part in the request"}), 400

    file = request.files['image']
    if file.filename == '':
        return jsonify({"success": False, "error": "No selected file"}), 400

    try:
        # Read image to numpy array
        npimg = np.frombuffer(file.read(), np.uint8)
        img = cv2.imdecode(npimg, cv2.IMREAD_COLOR)
        
        if img is None:
             return jsonify({"success": False, "error": "Invalid image format"}), 400

        # Face detection
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(100, 100))

        if len(faces) == 0:
            print("No face detected, using fallback")
            return jsonify(get_mock_fallback()), 200

        # Assume the largest face is the main subject
        faces = sorted(faces, key=lambda f: f[2]*f[3], reverse=True)
        main_face = faces[0]

        analysis_result = analyze_skin_roi(img, main_face)
        return jsonify({
            "success": True,
            "is_mock": False,
            "data": analysis_result
        }), 200

    except Exception as e:
        print(f"Analysis Error: {str(e)}")
        # Fallback for demo
        return jsonify(get_mock_fallback()), 200

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "ok", "service": "Flask Skin Analysis AI"}), 200

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port)
