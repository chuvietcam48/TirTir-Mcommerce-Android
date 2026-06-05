"""
Unit Tests for ChatbotEngine
Run: python -m pytest test_chatbot.py -v
"""

import os
import pytest

# ─── ChatbotEngine Tests ──────────────────────────────────────────────────────

CSV_PATH = os.path.join(os.path.dirname(__file__), "chatbot_products.csv")


@pytest.fixture(scope="module")
def engine():
    """Load ChatbotEngine once for all tests in this module."""
    from chatbot_engine import ChatbotEngine

    if not os.path.exists(CSV_PATH):
        pytest.skip("chatbot_products.csv not found — skipping chatbot tests")
    return ChatbotEngine(CSV_PATH)


class TestIntentClassification:
    """Test that the NLP model classifies intents correctly."""

    def test_greeting_intent(self, engine):
        result = engine.process("xin chào shop")
        assert result["intent"] == "greeting"

    def test_greeting_hi(self, engine):
        result = engine.process("hi")
        assert result["intent"] == "greeting"

    def test_shipping_intent(self, engine):
        result = engine.process("ship bao lâu vậy")
        assert result["intent"] == "shipping"

    def test_return_intent(self, engine):
        result = engine.process("đổi trả thế nào")
        assert result["intent"] == "return"

    def test_price_intent(self, engine):
        result = engine.process("giá bao nhiêu")
        assert result["intent"] == "price"

    def test_consultation_intent(self, engine):
        result = engine.process("da dầu dùng gì tốt")
        assert result["intent"] == "consultation"

    def test_info_intent(self, engine):
        result = engine.process("thành phần là gì")
        assert result["intent"] == "info"


class TestResponseStructure:
    """Test that responses have the correct structure."""

    def test_response_has_required_keys(self, engine):
        result = engine.process("hello")
        assert "intent" in result
        assert "message" in result
        assert "data" in result
        assert "type" in result

    def test_greeting_response_is_text(self, engine):
        result = engine.process("chào shop")
        assert result["type"] == "text"
        assert len(result["message"]) > 0

    def test_consultation_with_keyword_returns_product(self, engine):
        result = engine.process("da dầu dùng cushion gì")
        # Should either return a product or ask for more info
        assert result["type"] in ("text", "product")
        assert len(result["message"]) > 0


class TestSmartRecommend:
    """Test keyword-based product recommendation."""

    def test_oily_skin_keyword(self, engine):
        product, tags = engine._smart_recommend("da dầu")
        assert "dầu" in tags or "nhờn" in tags or product is None

    def test_dry_skin_keyword(self, engine):
        product, tags = engine._smart_recommend("da khô")
        if product is not None:
            assert "khô" in tags

    def test_no_keyword_returns_none(self, engine):
        product, tags = engine._smart_recommend("asdfghjkl random gibberish")
        assert product is None
        assert tags == []

    def test_empty_query(self, engine):
        product, tags = engine._smart_recommend("")
        assert product is None
        assert tags == []


class TestEdgeCases:
    """Test edge cases and robustness."""

    def test_single_character_input(self, engine):
        result = engine.process("a")
        assert "intent" in result
        assert "message" in result

    def test_very_long_input(self, engine):
        long_text = "tư vấn giúp mình " * 100
        result = engine.process(long_text)
        assert "intent" in result

    def test_special_characters(self, engine):
        result = engine.process("@#$%^&*()")
        assert "intent" in result
        assert "message" in result

    def test_mixed_language(self, engine):
        result = engine.process("I want cushion cho da dầu")
        assert "intent" in result
