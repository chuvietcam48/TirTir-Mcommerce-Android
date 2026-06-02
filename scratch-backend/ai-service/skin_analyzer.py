"""
Skin Analysis Module — Loaded ONCE at startup, reused for all requests.
Uses mediapipe 0.10.x tasks-vision API (mp.solutions removed in 0.10.x).
"""

import cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision
import base64
import os
import urllib.request
import logging

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
logger = logging.getLogger(__name__)

# Face Landmarker model — downloaded once to local cache
MODEL_URL = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
MODEL_PATH = os.path.join(os.path.dirname(__file__), "face_landmarker.task")


def _download_model_if_needed():
    """Download the FaceLandmarker model if not already cached locally."""
    if os.path.exists(MODEL_PATH):
        logger.info(f"✅ Model already cached at {MODEL_PATH}")
        return
    logger.info(f"📥 Downloading FaceLandmarker model from MediaPipe CDN...")
    urllib.request.urlretrieve(MODEL_URL, MODEL_PATH)
    logger.info(f"✅ Model downloaded to {MODEL_PATH}")


class SkinAnalyzer:
    """
    Singleton-style analyzer.
    FaceLandmarker is loaded once in __init__ and reused across all requests.
    """

    def __init__(self):
        logger.info("🔄 Loading MediaPipe FaceLandmarker...")
        _download_model_if_needed()

        base_options = mp_python.BaseOptions(model_asset_path=MODEL_PATH)
        options = mp_vision.FaceLandmarkerOptions(
            base_options=base_options,
            output_face_blendshapes=False,
            output_facial_transformation_matrixes=False,
            num_faces=1,
            min_face_detection_confidence=0.5,
            min_face_presence_confidence=0.5,
            min_tracking_confidence=0.5,
            running_mode=mp_vision.RunningMode.IMAGE,
        )
        self.landmarker = mp_vision.FaceLandmarker.create_from_options(options)
        logger.info("✅ MediaPipe FaceLandmarker loaded successfully.")

        # Pre-define ROI landmark groups (MediaPipe 478-landmark model)
        self.rois = {
            "forehead":    [10, 109, 338, 9],
            "left_cheek":  [116, 117, 118, 100],
            "right_cheek": [345, 346, 347, 329],
            "nose":        [4, 1, 2, 5],
            "chin":        [152, 175, 171, 148],
        }

    def decode_base64_image(self, image_base64: str) -> np.ndarray | None:
        """Decode a base64 string into an OpenCV BGR image."""
        try:
            if ',' in image_base64:
                image_base64 = image_base64.split(',', 1)[1]
            img_bytes = base64.b64decode(image_base64)
            nparr = np.frombuffer(img_bytes, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            return image
        except Exception as e:
            logger.error(f"Failed to decode base64 image: {e}")
            return None

    def _preprocess(self, image: np.ndarray) -> np.ndarray:
        """
        Normalize input image before analysis:
        1. Resize to a fixed width (640px) to keep landmark coordinates consistent.
        2. Apply CLAHE (Contrast Limited Adaptive Histogram Equalization) on the
           L-channel in LAB space to reduce the impact of uneven lighting.
        The returned image is still BGR, same as input.
        """
        # 1. Resize — keep aspect ratio, max width 640
        h, w = image.shape[:2]
        if w > 640:
            scale = 640 / w
            image = cv2.resize(image, (640, int(h * scale)), interpolation=cv2.INTER_AREA)

        # 2. CLAHE on L channel to normalize brightness/contrast
        lab = cv2.cvtColor(image, cv2.COLOR_BGR2Lab)
        l, a, b = cv2.split(lab)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        l = clahe.apply(l)
        lab = cv2.merge([l, a, b])
        return cv2.cvtColor(lab, cv2.COLOR_Lab2BGR)

    def analyze(self, image: np.ndarray) -> dict:
        """
        Run full skin analysis on an OpenCV BGR image.
        Returns dict: { skinTone, undertone, skinType, concerns, confidence, debug_values }
        """
        image = self._preprocess(image)
        h, w, _ = image.shape
        rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

        # Wrap in MediaPipe Image
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_image)
        result = self.landmarker.detect(mp_image)

        if not result.face_landmarks:
            return {"error": "No face detected"}

        landmarks = result.face_landmarks[0]  # First face

        def get_coords(idx: int) -> tuple[int, int]:
            pt = landmarks[idx]
            return int(pt.x * w), int(pt.y * h)

        # --- Extract average skin color from 5 ROIs ---
        extracted_colors = []
        for name, indices in self.rois.items():
            coords = [get_coords(idx) for idx in indices]
            cx = int(sum(c[0] for c in coords) / len(coords))
            cy = int(sum(c[1] for c in coords) / len(coords))

            patch_size = 10
            y1, y2 = max(0, cy - patch_size), min(h, cy + patch_size)
            x1, x2 = max(0, cx - patch_size), min(w, cx + patch_size)
            patch = image[y1:y2, x1:x2]
            if patch.size > 0:
                extracted_colors.append(np.mean(patch, axis=(0, 1)))

        if not extracted_colors:
            return {"error": "Failed to extract skin regions"}

        avg_skin_bgr = np.mean(extracted_colors, axis=0)

        # --- Convert to LAB ---
        lab_pixel = cv2.cvtColor(np.uint8([[avg_skin_bgr]]), cv2.COLOR_BGR2Lab)[0][0]
        L, a, b = float(lab_pixel[0]), float(lab_pixel[1]), float(lab_pixel[2])

        # --- Determine Skin Tone (based on LAB L channel) ---
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

        # --- Determine Undertone ---
        # LAB a* > 128 = red/magenta, b* > 128 = yellow (both centred at 128).
        # Use signed deviations and a dead-zone (±3) to avoid near-neutral flipping.
        a_norm = a - 128  # positive = red/pink
        b_norm = b - 128  # positive = yellow/warm
        UNDERTONE_THRESHOLD = 3.0  # dead-zone: within ±3 = Neutral
        if abs(a_norm) < UNDERTONE_THRESHOLD and abs(b_norm) < UNDERTONE_THRESHOLD:
            undertone = "Neutral"
        elif b_norm > UNDERTONE_THRESHOLD and b_norm > a_norm:
            undertone = "Warm"   # yellow dominates
        elif a_norm > UNDERTONE_THRESHOLD and a_norm > b_norm:
            undertone = "Cool"   # red/pink dominates
        else:
            undertone = "Neutral"

        # --- Detect Concerns ---
        concerns = self._detect_concerns(image, landmarks, h, w)

        # --- C2 FIX: Derive skinType from multiple signals ---
        skin_type = self._determine_skin_type(concerns, image, landmarks, h, w)

        # --- C1 FIX: Compute real confidence ---
        concern_penalty = len([c for c in concerns if c != "None"]) * 0.05
        confidence = round(max(0.3, min(0.98, 0.92 - concern_penalty)), 2)

        return {
            "skinTone": skin_tone,
            "undertone": undertone,
            "skinType": skin_type,
            "concerns": concerns,
            "confidence": confidence,
            "debug_values": {"L": L, "a": a, "b": b}
        }

    def _determine_skin_type(self, concerns: list, image: np.ndarray, landmarks: list, h: int, w: int) -> str:
        """
        Derive skin type from multiple analysis signals.
        Signals used (in priority order):
          1. Oily + Redness         → Combination
          2. Oily only              → Oily
          3. Redness only           → Sensitive
          4. Low HSV saturation     → Dry  (replaces the Dark Circles proxy which had weak correlation)
          5. Low surface variance   → Dry  (dry skin appears very smooth/matte)
          6. Default                → Normal
        """
        has_oily = "Oily Skin" in concerns
        has_redness = "Sensitive/Redness" in concerns

        def get_coords(idx: int) -> tuple[int, int]:
            pt = landmarks[idx]
            return int(pt.x * w), int(pt.y * h)

        # Signal: forehead dryness via low HSV saturation
        fh_coords = [get_coords(idx) for idx in self.rois["forehead"]]
        cx = int(sum(c[0] for c in fh_coords) / len(fh_coords))
        cy = int(sum(c[1] for c in fh_coords) / len(fh_coords))
        pad = 20
        fh_patch = image[max(0, cy - pad):min(h, cy + pad), max(0, cx - pad):min(w, cx + pad)]

        low_saturation = False
        low_variance = False
        if fh_patch.size > 0:
            hsv_fh = cv2.cvtColor(fh_patch, cv2.COLOR_BGR2HSV)
            s_fh = cv2.split(hsv_fh)[1]
            v_fh = cv2.split(hsv_fh)[2]
            mean_s = float(np.mean(s_fh))
            # Low saturation (<35) = desaturated/matte skin → likely dry
            low_saturation = mean_s < 35
            # Low value variance on forehead = very smooth, no shine → likely dry (not oily)
            low_variance = float(np.std(v_fh)) < 12 and not has_oily

        if has_oily and has_redness:
            return "Combination"
        elif has_oily:
            return "Oily"
        elif has_redness:
            return "Sensitive"
        elif low_saturation or low_variance:
            return "Dry"
        else:
            return "Normal"

    def _detect_concerns(self, image: np.ndarray, landmarks: list, h: int, w: int) -> list[str]:
        """Detect: Acne/Blemishes, Sensitive/Redness, Dark Circles, Visible Pores, Oily Skin."""
        concerns = []

        def get_coords(idx: int) -> tuple[int, int]:
            pt = landmarks[idx]
            return int(pt.x * w), int(pt.y * h)

        # 1. ACNE / REDNESS — Cheek a-channel variance
        lc_poly = np.array([get_coords(i) for i in [116, 117, 118, 100, 123, 147, 213, 192]], dtype=np.int32)
        rc_poly = np.array([get_coords(i) for i in [345, 346, 347, 329, 352, 376, 433, 416]], dtype=np.int32)

        mask_cheeks = np.zeros(image.shape[:2], dtype=np.uint8)
        cv2.fillPoly(mask_cheeks, [lc_poly], 255)
        cv2.fillPoly(mask_cheeks, [rc_poly], 255)

        cheeks_bgr = cv2.bitwise_and(image, image, mask=mask_cheeks)
        cheeks_lab = cv2.cvtColor(cheeks_bgr, cv2.COLOR_BGR2Lab)
        l_c, a_c, _ = cv2.split(cheeks_lab)

        valid_a = a_c[mask_cheeks > 0]
        if len(valid_a) > 0:
            if np.std(valid_a) > 10:
                concerns.append("Acne/Blemishes")
            elif np.mean(valid_a) > 145:
                concerns.append("Sensitive/Redness")

        # 2. DARK CIRCLES — Under-eye vs cheek brightness delta
        ue_l = np.array([get_coords(i) for i in [226, 31, 228, 229, 230, 231, 232, 233, 244]], dtype=np.int32)
        ue_r = np.array([get_coords(i) for i in [446, 261, 448, 449, 450, 451, 452, 453, 464]], dtype=np.int32)
        mask_eyes = np.zeros(image.shape[:2], dtype=np.uint8)
        cv2.fillPoly(mask_eyes, [ue_l, ue_r], 255)

        lab_full = cv2.cvtColor(image, cv2.COLOR_BGR2Lab)
        mean_eye_L = cv2.mean(lab_full[:, :, 0], mask=mask_eyes)[0]
        mean_cheek_L = cv2.mean(l_c, mask=mask_cheeks)[0]
        if mean_cheek_L - mean_eye_L > 15:
            concerns.append("Dark Circles")

        # 3. PORES — Laplacian variance on cheeks
        # Threshold calibrated against typical webcam images at 640px width:
        #   < 300  → smooth skin / soft focus
        #   300–700 → normal texture
        #   > 700  → visible pore texture
        cheeks_gray = cv2.cvtColor(cheeks_bgr, cv2.COLOR_BGR2GRAY)
        laplacian_var = cv2.Laplacian(cheeks_gray, cv2.CV_64F).var()
        if laplacian_var > 700:
            concerns.append("Visible Pores")

        # 4. OILY SKIN — Specular highlights on forehead
        fh_coords = [get_coords(idx) for idx in self.rois["forehead"]]
        cx = int(sum(c[0] for c in fh_coords) / len(fh_coords))
        cy = int(sum(c[1] for c in fh_coords) / len(fh_coords))
        fh_patch = image[max(0, cy - 30):min(h, cy + 30), max(0, cx - 30):min(w, cx + 30)]
        if fh_patch.size > 0:
            v_fh = cv2.split(cv2.cvtColor(fh_patch, cv2.COLOR_BGR2HSV))[2]
            if (np.count_nonzero(v_fh > 200) / v_fh.size) > 0.1:
                concerns.append("Oily Skin")

        return concerns if concerns else ["None"]
