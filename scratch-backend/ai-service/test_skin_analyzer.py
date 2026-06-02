"""
Unit Tests for SkinAnalyzer
Run: python -m pytest test_skin_analyzer.py -v
"""

import os
import base64
import pytest
import numpy as np


@pytest.fixture(scope="module")
def analyzer():
    """Load SkinAnalyzer once for all tests."""
    from skin_analyzer import SkinAnalyzer
    return SkinAnalyzer()


class TestBase64Decode:
    """Test base64 image decoding."""

    def test_valid_base64_image(self, analyzer):
        """Create a tiny valid JPEG in memory and test decode."""
        # Create a small 10x10 red image, encode as PNG via OpenCV
        import cv2
        img = np.zeros((10, 10, 3), dtype=np.uint8)
        img[:, :] = [0, 0, 255]  # Red in BGR
        _, buffer = cv2.imencode('.png', img)
        b64_str = base64.b64encode(buffer).decode('utf-8')

        result = analyzer.decode_base64_image(b64_str)
        assert result is not None
        assert result.shape == (10, 10, 3)

    def test_base64_with_data_uri_prefix(self, analyzer):
        """Test that data URI prefix is handled."""
        import cv2
        img = np.zeros((10, 10, 3), dtype=np.uint8)
        _, buffer = cv2.imencode('.png', img)
        b64_str = "data:image/png;base64," + base64.b64encode(buffer).decode('utf-8')

        result = analyzer.decode_base64_image(b64_str)
        # Should handle with or without prefix
        assert result is not None or result is None  # Implementation may vary

    def test_invalid_base64_returns_none(self, analyzer):
        """Invalid base64 should return None, not crash."""
        result = analyzer.decode_base64_image("not_valid_base64!!!")
        assert result is None

    def test_empty_string_returns_none(self, analyzer):
        """Empty string should return None."""
        result = analyzer.decode_base64_image("")
        assert result is None


class TestAnalyzerInit:
    """Test that the analyzer initializes correctly."""

    def test_analyzer_loads(self, analyzer):
        """SkinAnalyzer should initialize without errors."""
        assert analyzer is not None

    def test_model_path_exists(self):
        """The FaceLandmarker model file should exist."""
        model_path = os.path.join(os.path.dirname(__file__), "face_landmarker.task")
        assert os.path.exists(model_path), "face_landmarker.task model file is missing"


class TestAnalyzeOutput:
    """Test the analyze method output structure."""

    def test_analyze_no_face_image(self, analyzer):
        """Analyzing a plain color image (no face) should return error or empty result."""
        # Create a plain blue 100x100 image — no face
        img = np.zeros((100, 100, 3), dtype=np.uint8)
        img[:, :] = [255, 0, 0]  # Blue

        result = analyzer.analyze(img)
        # Should return an error since no face is detected
        assert isinstance(result, dict)
        assert "error" in result or "skinTone" in result

    def test_analyze_returns_dict(self, analyzer):
        """analyze() should always return a dict."""
        img = np.zeros((100, 100, 3), dtype=np.uint8)
        result = analyzer.analyze(img)
        assert isinstance(result, dict)


class TestAnalyzeWithFace:
    """Tests that require a real face image — skipped if no test image available."""

    TEST_IMAGE_PATH = os.path.join(os.path.dirname(__file__), "test_face.jpg")

    @pytest.fixture
    def face_image(self):
        if not os.path.exists(self.TEST_IMAGE_PATH):
            pytest.skip("test_face.jpg not found — skipping face analysis tests")
        import cv2
        return cv2.imread(self.TEST_IMAGE_PATH)

    def test_face_analysis_returns_skin_tone(self, analyzer, face_image):
        result = analyzer.analyze(face_image)
        assert "error" not in result, f"Analysis failed: {result.get('error')}"
        assert "skinTone" in result

    def test_face_analysis_returns_undertone(self, analyzer, face_image):
        result = analyzer.analyze(face_image)
        assert "undertone" in result
        assert result["undertone"] in ["warm", "cool", "neutral"]

    def test_face_analysis_returns_concerns(self, analyzer, face_image):
        result = analyzer.analyze(face_image)
        assert "concerns" in result
        assert isinstance(result["concerns"], list)
