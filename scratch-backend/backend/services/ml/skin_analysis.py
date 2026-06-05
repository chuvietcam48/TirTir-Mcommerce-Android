import sys
import json
import cv2
import numpy as np
import os
import mediapipe as mp

# Suppress MediaPipe logs
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

def analyze_skin(image_path):
    if not os.path.exists(image_path):
        return {"error": "Image file not found"}

    # Read image
    image = cv2.imread(image_path)
    if image is None:
        return {"error": "Failed to read image"}

    # Initialize MediaPipe Face Mesh
    mp_face_mesh = mp.solutions.face_mesh
    face_mesh = mp_face_mesh.FaceMesh(
        static_image_mode=True,
        max_num_faces=1,
        refine_landmarks=True,
        min_detection_confidence=0.5
    )

    # Convert to RGB for MediaPipe
    rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    results = face_mesh.process(rgb_image)

    if not results.multi_face_landmarks:
        return {"error": "No face detected"}

    face_landmarks = results.multi_face_landmarks[0]
    h, w, _ = image.shape

    # Helper to get coordinates
    def get_coords(landmark_idx):
        pt = face_landmarks.landmark[landmark_idx]
        return int(pt.x * w), int(pt.y * h)

    # Define Regions of Interest (ROI) using specific landmarks
    # Forehead: 10 (top center), 338 (right), 109 (left), 151 (top)
    # Cheeks: 234 (right), 454 (left)
    # Nose: 4
    # Chin: 152
    
    # Simple ROI Extraction using polygon mask
    def extract_roi(indices):
        mask = np.zeros(image.shape[:2], dtype=np.uint8)
        points = np.array([get_coords(idx) for idx in indices], dtype=np.int32)
        cv2.fillPoly(mask, [points], 255)
        mean_color = cv2.mean(image, mask=mask)[:3]
        return mean_color, mask

    # Landmark Indices for ROIs (Approximate)
    forehead_indices = [10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109] 
    # Let's use simpler logic: 
    # Forehead Center: 10, 109, 338
    # Left Cheek: 234, 93, 132, 58, 172, 136, 150, 149, 176, 148
    # Right Cheek: 454, 323, 361, 288, 397, 365, 379, 378, 400, 377
    
    # Better: Rectangular crops around key points for color analysis
    rois = {
        "forehead": [10, 109, 338, 9], # Top, Left, Right, Bottom of forehead
        "left_cheek": [116, 117, 118, 100], # Approximate center of left cheek
        "right_cheek": [345, 346, 347, 329], # Approximate center of right cheek
        "nose": [4, 1, 2, 5],
        "chin": [152, 175, 171, 148]
    }

    extracted_colors = []
    
    for name, indices in rois.items():
        # Get centroid of indices
        coords = [get_coords(idx) for idx in indices]
        cx = int(sum(c[0] for c in coords) / len(coords))
        cy = int(sum(c[1] for c in coords) / len(coords))
        
        # Extract 10x10 patch around centroid
        patch_size = 10
        y1, y2 = max(0, cy - patch_size), min(h, cy + patch_size)
        x1, x2 = max(0, cx - patch_size), min(w, cx + patch_size)
        
        patch = image[y1:y2, x1:x2]
        if patch.size > 0:
            avg = np.mean(patch, axis=(0, 1))
            extracted_colors.append(avg)

    if not extracted_colors:
        return {"error": "Failed to extract skin regions"}

    avg_skin_bgr = np.mean(extracted_colors, axis=0)
    
    # Convert to Lab for brightness (L)
    lab_pixel = cv2.cvtColor(np.uint8([[avg_skin_bgr]]), cv2.COLOR_BGR2Lab)[0][0]
    L, a, b = lab_pixel
    
    # Determine Skin Tone based on L (Lightness)
    if L > 190:
        skin_tone = "Fair"
    elif L > 160:
        skin_tone = "Light"
    elif L > 130:
        skin_tone = "Medium"
    elif L > 90:
        skin_tone = "Tan"
    else:
        skin_tone = "Deep"
        
    # Determine Undertone based on a and b
    # Neutral is around 128 in OpenCV Lab (0-255 range)
    a_norm = float(a) - 128
    b_norm = float(b) - 128
    
    if b_norm > 5 and b_norm > a_norm:
        undertone = "Warm" # Yellow dominant
    elif a_norm > 5 and a_norm > b_norm:
        undertone = "Cool" # Red/Pink dominant
    else:
        undertone = "Neutral"

    # Detect Concerns (Advanced Heuristics)
    concerns = []
    
    # 1. ACNE / REDNESS / BLEMISHES DETECTION
    # Strategy: Look for local red spots in the cheek area using adaptive thresholding
    # Combine Left & Right Cheek indices for a larger sample
    cheek_indices = rois["left_cheek"] + rois["right_cheek"]
    cheek_coords = [get_coords(idx) for idx in cheek_indices]
    
    # Create a mask for cheeks to inspect texture/color more closely
    mask_cheeks = np.zeros(image.shape[:2], dtype=np.uint8)
    # Define a simple polygon for cheeks based on landmarks
    # Left Cheek Polygon (approximate)
    lc_poly = np.array([get_coords(i) for i in [116, 117, 118, 100, 123, 147, 213, 192]], dtype=np.int32)
    cv2.fillPoly(mask_cheeks, [lc_poly], 255)
    # Right Cheek Polygon
    rc_poly = np.array([get_coords(i) for i in [345, 346, 347, 329, 352, 376, 433, 416]], dtype=np.int32)
    cv2.fillPoly(mask_cheeks, [rc_poly], 255)
    
    # Extract cheek regions
    cheeks_bgr = cv2.bitwise_and(image, image, mask=mask_cheeks)
    cheeks_lab = cv2.cvtColor(cheeks_bgr, cv2.COLOR_BGR2Lab)
    l_c, a_c, b_c = cv2.split(cheeks_lab)
    
    # Calculate local redness (a-channel) stats only where mask is > 0
    valid_pixels = a_c[mask_cheeks > 0]
    if len(valid_pixels) > 0:
        mean_a = np.mean(valid_pixels)
        std_a = np.std(valid_pixels)
        
        # If 'a' channel variance is high, it suggests blotchiness/acne
        if std_a > 10: 
            concerns.append("Acne/Blemishes")
        elif mean_a > 145: # High average redness (neutral is ~128)
            concerns.append("Sensitive/Redness")

    # 2. DARK CIRCLES DETECTION
    # Compare under-eye brightness vs cheek brightness
    # Under Eye Landmarks: Left(230, 231), Right(450, 451)
    ue_l_poly = np.array([get_coords(i) for i in [226, 31, 228, 229, 230, 231, 232, 233, 244]], dtype=np.int32)
    ue_r_poly = np.array([get_coords(i) for i in [446, 261, 448, 449, 450, 451, 452, 453, 464]], dtype=np.int32)
    
    mask_eyes = np.zeros(image.shape[:2], dtype=np.uint8)
    cv2.fillPoly(mask_eyes, [ue_l_poly, ue_r_poly], 255)
    
    mean_eye_L = cv2.mean(cv2.cvtColor(image, cv2.COLOR_BGR2Lab)[:,:,0], mask=mask_eyes)[0]
    
    # Compare with cheek L (already calculated implicitly via 'L' variable which is global average, but let's use cheek specifically)
    mean_cheek_L = cv2.mean(l_c, mask=mask_cheeks)[0]
    
    if mean_cheek_L - mean_eye_L > 15: # Eyes significantly darker than cheeks
        concerns.append("Dark Circles")

    # 3. TEXTURE / PORES (High Frequency Noise)
    # Convert cheek area to grayscale and apply Laplacian
    cheeks_gray = cv2.cvtColor(cheeks_bgr, cv2.COLOR_BGR2GRAY)
    laplacian_var = cv2.Laplacian(cheeks_gray, cv2.CV_64F).var()
    
    # Higher variance = more texture (pores, wrinkles, etc)
    # This threshold depends heavily on resolution/blur, but let's estimate
    if laplacian_var > 1500: # Heuristic threshold
        concerns.append("Visible Pores")

    # 4. OILY SKIN (Specular Reflection)
    # Existing logic reused but refined
    fh_indices = rois["forehead"]
    fh_coords = [get_coords(idx) for idx in fh_indices]
    cx, cy = int(sum(c[0] for c in fh_coords) / len(fh_coords)), int(sum(c[1] for c in fh_coords) / len(fh_coords))
    y1, y2 = max(0, cy - 30), min(h, cy + 30)
    x1, x2 = max(0, cx - 30), min(w, cx + 30)
    fh_patch = image[y1:y2, x1:x2]
    
    if fh_patch.size > 0:
        fh_hsv = cv2.cvtColor(fh_patch, cv2.COLOR_BGR2HSV)
        _, _, v_fh = cv2.split(fh_hsv)
        # Check for very bright spots (glare)
        bright_pixels = np.count_nonzero(v_fh > 200)
        total_pixels = v_fh.size
        if (bright_pixels / total_pixels) > 0.1: # >10% of forehead is shiny
            concerns.append("Oily Skin")
    
    if not concerns:
        concerns.append("None")

    # Confidence Score (MediaPipe is generally high confidence if face detected)
    confidence = 0.95

    result = {
        "skinTone": skin_tone,
        "undertone": undertone,
        "skinType": "Combination" if "Oily Skin" in concerns else "Normal",
        "concerns": concerns,
        "confidence": confidence,
        "debug_values": {
            "L": float(L),
            "a": float(a),
            "b": float(b)
        }
    }
    
    return result

if __name__ == "__main__":
    try:
        if len(sys.argv) < 2:
            print(json.dumps({"error": "No image path provided"}))
            sys.exit(1)
            
        image_path = sys.argv[1]
        
        # Perform analysis
        analysis_result = analyze_skin(image_path)
        
        # Print JSON to stdout
        print(json.dumps(analysis_result))
        
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)
