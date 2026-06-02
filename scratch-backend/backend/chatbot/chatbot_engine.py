"""
Chatbot Engine — RAG-lite + rules + catalog filtering.

Design goals for MVP:
- No model training.
- Ask for missing required slots (skin type, concern, budget, category).
- Hard-filter products by those slots.
- Score with: 0.5 * concern + 0.3 * skin + 0.2 * budget.
- Return top 3 + 1 cheaper alternative.
- Let LLM only rephrase from selected products (not choose products).
"""

import json
import logging
import os
import re
import unicodedata
from collections.abc import Generator
from dataclasses import dataclass

import google.generativeai as genai
import pandas as pd
import redis

logger = logging.getLogger(__name__)


@dataclass
class SessionState:
    skin_type: str | None = None
    concern: str | None = None
    budget_min: float | None = None
    budget_max: float | None = None
    category: str | None = None


class ChatbotEngine:
    def __init__(self, csv_path: str):
        logger.info("🤖 Loading RAG-lite chatbot engine...")

        api_key = os.environ.get("GEMINI_API_KEY", "").strip()
        self.model = None
        if api_key:
            genai.configure(api_key=api_key)
            try:
                self.model = genai.GenerativeModel("gemini-2.0-flash")
                logger.info("✅ Gemini-2.0-Flash model configured for natural-language phrasing.")
            except Exception:
                try:
                    self.model = genai.GenerativeModel("gemini-pro")
                    logger.info("✅ Gemini-Pro model configured (fallback).")
                except Exception:
                    logger.warning("⚠️ Could not initialize Gemini model. Will use template responses.")
        else:
            logger.warning("⚠️ GEMINI_API_KEY not set. Falling back to template responses.")

        self.df = pd.read_csv(csv_path)
        self._ensure_columns()
        self._normalize_catalog()

        self.redis_url = os.environ.get("REDIS_URL", "redis://redis:6379")
        self.session_ttl_seconds = 30 * 60
        self.redis_client: redis.Redis | None = None
        try:
            self.redis_client = redis.Redis.from_url(self.redis_url, decode_responses=True, socket_connect_timeout=2, socket_keepalive=True)
            self.redis_client.ping()
            logger.info(f"✅ Redis session store connected at {self.redis_url}")
        except Exception as e:
            self.redis_client = None
            logger.warning(f"⚠️  Redis unavailable at {self.redis_url}. Conversation memory will NOT persist. Error: {str(e)}")
            logger.warning("➡️  For persistence, ensure Redis is running and REDIS_URL is correct.")

        self.skin_keywords = {
            "oily": ["da dầu", "dầu", "nhờn", "oily", "acne-prone", "mụn"],
            "dry": ["da khô", "khô", "dry", "very dry"],
            "combination": ["da hỗn hợp", "hỗn hợp", "combination"],
            "sensitive": ["da nhạy cảm", "nhạy cảm", "sensitive"],
            "normal": ["da thường", "normal"],
            "mature": ["lão hóa", "da lão hóa", "mature", "wrinkle"],
            "all": ["mọi loại da", "all skin", "all types", "all"],
        }

        self.concern_keywords = {
            "acne": ["mụn", "acne", "breakout"],
            "hydration": ["cấp ẩm", "dưỡng ẩm", "khô", "hydration", "moisture"],
            "soothing": ["làm dịu", "soothing", "đỏ da", "kích ứng", "calming"],
            "brightening": ["sáng da", "đều màu", "brightening", "dull"],
            "coverage": ["che phủ", "coverage", "khuyết điểm"],
            "matte": ["lì", "matte", "kiềm dầu"],
            "glow": ["căng bóng", "glow", "dewy", "glass skin"],
            "uv": ["chống nắng", "uv", "spf", "sun"],
            "wrinkles": ["nếp nhăn", "wrinkle", "chống lão hóa", "lifting"],
        }

        self.category_keywords = {
            "cushion": ["cushion", "phấn nước"],
            "toner": ["toner", "nước cân bằng"],
            "serum": ["serum", "tinh chất", "ampoule"],
            "cream": ["kem dưỡng", "cream"],
            "cleanser": ["sữa rửa mặt", "cleanser", "cleansing"],
            "sunscreen": ["chống nắng", "sunscreen", "sun"],
            "tint": ["tint", "son tint"],
            "balm": ["balm", "son dưỡng"],
            "eye-cream": ["kem mắt", "eye cream"],
            "setting-spray": ["xịt khóa nền", "setting spray", "fixer"],
            "gift-set": ["set", "combo", "gift set", "duo"],
            "primer": ["primer", "lót"],
            "mask": ["mask", "mặt nạ"],
            "facial-oil": ["oil", "dầu dưỡng"],
        }

        self.required_slots = ["skin_type", "concern", "budget", "category"]
        logger.info("✅ RAG-lite chatbot ready.")

    def _session_key(self, session_id: str | None) -> str:
        return (session_id or "anonymous").strip()[:120]

    def _redis_state_key(self, session_key: str) -> str:
        return f"chatbot:session:{session_key}"

    def _load_session_state(self, session_key: str) -> SessionState:
        if not self.redis_client:
            return SessionState()

        try:
            raw = self.redis_client.get(self._redis_state_key(session_key))
            if not raw:
                return SessionState()

            parsed = json.loads(raw)
            return SessionState(
                skin_type=parsed.get("skin_type"),
                concern=parsed.get("concern"),
                budget_min=parsed.get("budget_min"),
                budget_max=parsed.get("budget_max"),
                category=parsed.get("category"),
            )
        except Exception:
            logger.exception("Failed to load state from Redis for session=%s", session_key)
            return SessionState()

    def _save_session_state(self, session_key: str, state: SessionState) -> None:
        if not self.redis_client:
            return

        try:
            payload = json.dumps(
                {
                    "skin_type": state.skin_type,
                    "concern": state.concern,
                    "budget_min": state.budget_min,
                    "budget_max": state.budget_max,
                    "category": state.category,
                },
                ensure_ascii=False,
            )
            self.redis_client.setex(self._redis_state_key(session_key), self.session_ttl_seconds, payload)
        except Exception:
            logger.exception("Failed to save state to Redis for session=%s", session_key)

    def _ensure_columns(self):
        required_columns = [
            "Product_ID",
            "Name",
            "Price",
            "Sale_Price",
            "Skin_Type_Target",
            "Main_Concern",
            "Category",
            "Category_Slug",
            "Description_Short",
            "Product_Slug",
            "Thumbnail_Images",
            "Status",
            "Stock_Quantity",
        ]
        for col in required_columns:
            if col not in self.df.columns:
                self.df[col] = ""
        self.df["Price"] = pd.to_numeric(self.df["Price"], errors="coerce").fillna(0.0)
        self.df["Sale_Price"] = pd.to_numeric(self.df["Sale_Price"], errors="coerce").fillna(0.0)
        self.df["Stock_Quantity"] = pd.to_numeric(self.df["Stock_Quantity"], errors="coerce").fillna(0)

    def _normalize_catalog(self):
        text_cols = [
            "Name",
            "Category",
            "Category_Slug",
            "Skin_Type_Target",
            "Main_Concern",
            "Description_Short",
            "Product_Slug",
        ]
        for col in text_cols:
            self.df[col] = self.df[col].fillna("").astype(str)

        self.df["_norm_text"] = (
            self.df["Name"]
            + " "
            + self.df["Category"]
            + " "
            + self.df["Category_Slug"]
            + " "
            + self.df["Skin_Type_Target"]
            + " "
            + self.df["Main_Concern"]
            + " "
            + self.df["Description_Short"]
        ).str.lower()

        self.df["_active"] = self.df["Status"].astype(str).str.lower().isin(["in stock", "instock", "active", "true"]) | (
            self.df["Stock_Quantity"] > 0
        )

    def _strip_accents(self, text: str) -> str:
        normalized = unicodedata.normalize("NFD", text)
        plain = "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn")
        return plain.replace("đ", "d").replace("Đ", "D")

    def _slot_status(self, state: SessionState) -> list[str]:
        missing = []
        if not state.skin_type:
            missing.append("skin_type")
        if not state.concern:
            missing.append("concern")
        if state.budget_min is None and state.budget_max is None:
            missing.append("budget")
        if not state.category:
            missing.append("category")
        return missing

    def _ask_missing(self, missing: list[str]) -> str:
        prompts = {
            "skin_type": "loại da (da dầu/khô/hỗn hợp/nhạy cảm)",
            "concern": "vấn đề chính (mụn/thâm/đỏ da/kiềm dầu/căng bóng/chống nắng)",
            "budget": "ngân sách (ví dụ: dưới 700k hoặc 20-30)",
            "category": "loại sản phẩm (cushion/toner/serum/kem dưỡng/chống nắng)",
        }
        ask = ", ".join(prompts[k] for k in missing)
        return f"Để tư vấn chuẩn hơn, bạn cho mình thêm: {ask}. Mình sẽ không đoán bừa đâu nhé."

    def _has_any_filter(self, state: SessionState) -> bool:
        """Return True if at least one filter criterion is set."""
        return bool(
            state.skin_type
            or state.concern
            or state.category
            or state.budget_min is not None
            or state.budget_max is not None
        )

    def _ask_for_filter(self) -> str:
        return "Bạn cho mình một thông tin để recommend nhé: loại da, vấn đề chính, ngân sách, hoặc loại sản phẩm."

    def _filter_products(self, state: SessionState) -> pd.DataFrame:
        candidates = self.df[self.df["_active"]].copy()

        if state.category:
            cat_words = self.category_keywords.get(state.category, [state.category])
            mask = candidates["_norm_text"].apply(lambda txt: any(word in txt for word in cat_words))
            candidates = candidates[mask]

        if state.skin_type:
            skin_words = self.skin_keywords.get(state.skin_type, [state.skin_type])
            skin_mask = candidates["Skin_Type_Target"].str.lower().apply(lambda txt: any(word in txt for word in skin_words))
            if state.skin_type == "all":
                skin_mask = skin_mask | candidates["Skin_Type_Target"].str.lower().str.contains("all", na=False)
            candidates = candidates[skin_mask]

        if state.concern:
            concern_words = self.concern_keywords.get(state.concern, [state.concern])
            concern_mask = candidates["_norm_text"].apply(lambda txt: any(word in txt for word in concern_words))
            candidates = candidates[concern_mask]

        if state.budget_min is not None:
            candidates = candidates[candidates["Price"] >= state.budget_min]
        if state.budget_max is not None:
            candidates = candidates[candidates["Price"] <= state.budget_max]

        return candidates

    def _score_products(self, products: pd.DataFrame, state: SessionState) -> pd.DataFrame:
        scored = products.copy()

        concern_words = self.concern_keywords.get(state.concern or "", [state.concern or ""])
        skin_words = self.skin_keywords.get(state.skin_type or "", [state.skin_type or ""])

        scored["concern_match"] = scored["_norm_text"].apply(lambda txt: 1.0 if any(w and w in txt for w in concern_words) else 0.0)
        scored["skin_match"] = scored["Skin_Type_Target"].str.lower().apply(lambda txt: 1.0 if any(w and w in txt for w in skin_words) else 0.0)

        def budget_fit(price: float) -> float:
            if state.budget_min is None and state.budget_max is None:
                return 1.0
            if state.budget_min is not None and state.budget_max is not None:
                target = (state.budget_min + state.budget_max) / 2
                span = max((state.budget_max - state.budget_min) / 2, 1.0)
                return max(0.0, 1.0 - abs(price - target) / span)
            if state.budget_max is not None:
                return max(0.0, min(1.0, (state.budget_max - price + 1) / max(state.budget_max, 1.0)))
            return 1.0

        scored["budget_fit"] = scored["Price"].apply(budget_fit)
        # Renormalize weights based on available criteria
        has_concern = bool(state.concern)
        has_skin = bool(state.skin_type)
        has_budget = state.budget_min is not None or state.budget_max is not None

        if has_concern and has_skin and has_budget:
            w_concern, w_skin, w_budget = 0.5, 0.3, 0.2
        elif has_concern and has_skin:
            w_concern, w_skin, w_budget = 0.6, 0.4, 0.0
        elif has_concern and has_budget:
            w_concern, w_skin, w_budget = 0.7, 0.0, 0.3
        elif has_skin and has_budget:
            w_concern, w_skin, w_budget = 0.0, 0.6, 0.4
        elif has_concern:
            w_concern, w_skin, w_budget = 1.0, 0.0, 0.0
        elif has_skin:
            w_concern, w_skin, w_budget = 0.0, 1.0, 0.0
        else:  # only budget
            w_concern, w_skin, w_budget = 0.0, 0.0, 1.0
        
        scored["score"] = w_concern * scored["concern_match"] + w_skin * scored["skin_match"] + w_budget * scored["budget_fit"]
        scored = scored.sort_values(by=["score", "Price"], ascending=[False, True])
        return scored

    def _to_product_payload(self, row: pd.Series, score: float | None = None) -> dict:
        payload = {
            "id": str(row.get("Product_ID", "")),
            "name": row.get("Name", ""),
            "price": float(row.get("Price", 0.0)),
            "image": row.get("Thumbnail_Images", ""),
            "desc": row.get("Description_Short", ""),
            "slug": row.get("Product_Slug", ""),
        }
        if score is not None:
            payload["score"] = round(float(score), 4)
        return payload

    def _build_llm_prompt(
        self,
        user_message: str,
        state: SessionState,
        recommendations: list[dict],
        cheaper_alternative: dict | None,
        conversation_history: list[dict] | None = None,
        dynamic_context: dict | None = None,
    ) -> str:
        prompt = {
            "instruction": "Bạn là trợ lý mỹ phẩm TirTir. Chỉ được mô tả sản phẩm có trong recommendations và cheaper_alternative. Không được thêm sản phẩm ngoài danh sách. Luôn đọc conversation_history để hiểu ngữ cảnh tham chiếu (ví dụ: 'cái thứ hai', 'loại trước đó') và trả lời nối tiếp đúng sản phẩm đã nhắc trước đó. Dưới đây là thông tin nội bộ của hệ thống: dynamic_context. Hãy dùng chính xác thông tin này để tư vấn mã giảm giá hoặc tình trạng đơn hàng cho khách. Tuyệt đối không được yêu cầu khách tự đi tra cứu.",
            "user_message": user_message,
            "conversation_history": conversation_history or [],
            "dynamic_context": dynamic_context or {},
            "profile": {
                "skin_type": state.skin_type,
                "concern": state.concern,
                "budget_min": state.budget_min,
                "budget_max": state.budget_max,
                "category": state.category,
            },
            "recommendations": recommendations,
            "cheaper_alternative": cheaper_alternative,
            "output_style": "Tiếng Việt, thân thiện, 4-6 câu, nêu lý do chọn theo concern/skin/budget và kết thúc bằng câu hỏi follow-up.",
        }
        return json.dumps(prompt, ensure_ascii=False)

    def _render_with_llm(
        self,
        user_message: str,
        state: SessionState,
        recommendations: list[dict],
        cheaper_alternative: dict | None,
        conversation_history: list[dict] | None = None,
        dynamic_context: dict | None = None,
    ) -> str:
        if not self.model:
            return self._template_message(state, recommendations, cheaper_alternative)

        prompt_json = self._build_llm_prompt(
            user_message=user_message,
            state=state,
            recommendations=recommendations,
            cheaper_alternative=cheaper_alternative,
            conversation_history=conversation_history,
            dynamic_context=dynamic_context,
        )

        try:
            response = self.model.generate_content(
                prompt_json,
                generation_config=genai.types.GenerationConfig(
                    temperature=0.2,
                ),
            )
            text = (response.text or "").strip()
            if text:
                return text
        except Exception:
            logger.exception("LLM phrasing failed, fallback to template.")

        return self._template_message(state, recommendations, cheaper_alternative)

    def _render_with_llm_stream(
        self,
        user_message: str,
        state: SessionState,
        recommendations: list[dict],
        cheaper_alternative: dict | None,
        conversation_history: list[dict] | None = None,
        dynamic_context: dict | None = None,
    ) -> Generator[str, None, None]:
        if not self.model:
            yield self._template_message(state, recommendations, cheaper_alternative)
            return

        prompt_json = self._build_llm_prompt(
            user_message=user_message,
            state=state,
            recommendations=recommendations,
            cheaper_alternative=cheaper_alternative,
            conversation_history=conversation_history,
            dynamic_context=dynamic_context,
        )

        had_chunk = False
        try:
            response_stream = self.model.generate_content(
                prompt_json,
                generation_config=genai.types.GenerationConfig(
                    temperature=0.2,
                ),
                stream=True,
            )
            for chunk in response_stream:
                chunk_text = getattr(chunk, "text", "") or ""
                if chunk_text:
                    had_chunk = True
                    yield chunk_text
        except Exception:
            logger.exception("LLM streaming failed, fallback to template.")

        if not had_chunk:
            yield self._template_message(state, recommendations, cheaper_alternative)

    def _build_coupon_message_from_context(self, dynamic_context: dict | None) -> str:
        coupons = (dynamic_context or {}).get("active_coupons") or []
        if not coupons:
            return "Hiện tại chưa có mã giảm giá hợp lệ đang active trong hệ thống. Mình sẽ báo ngay khi có mã mới phù hợp cho bạn nhé."

        lines = ["Hiện tại hệ thống đang có các mã giảm giá hợp lệ:"]
        for idx, coupon in enumerate(coupons[:5], start=1):
            code = coupon.get("code", "")
            discount_type = coupon.get("discount_type")
            discount_value = coupon.get("discount_value")
            min_order = coupon.get("min_order_value")
            max_discount = coupon.get("max_discount")
            remains = coupon.get("remaining_uses")

            if discount_type == "percentage":
                discount_text = f"giảm {discount_value}%"
                if max_discount:
                    discount_text += f" (tối đa {max_discount})"
            else:
                discount_text = f"giảm trực tiếp {discount_value}"

            line = f"{idx}) {code}: {discount_text}, đơn tối thiểu {min_order}"
            if remains is not None:
                line += f", còn {remains} lượt"
            lines.append(line + ".")

        lines.append("Bạn muốn mình gợi ý mã có lợi nhất theo giá trị đơn hàng của bạn không?")
        return "\n".join(lines)

    def _build_order_status_message_from_context(self, dynamic_context: dict | None) -> str | None:
        order_ctx = (dynamic_context or {}).get("order_status")
        if not order_ctx:
            return None

        order_code = order_ctx.get("order_code")
        if not order_ctx.get("found"):
            return f"Mình chưa tìm thấy đơn hàng với mã {order_code} trong hệ thống của bạn. Bạn kiểm tra lại mã đơn giúp mình nhé."

        status = order_ctx.get("status") or "N/A"
        shipping_status = order_ctx.get("shipping_status") or "N/A"
        tracking = order_ctx.get("tracking_number") or order_ctx.get("ghn_order_code") or "chưa có"
        return (
            f"Mình đã kiểm tra đơn {order_code}: trạng thái đơn là {status}, trạng thái vận chuyển là {shipping_status}. "
            f"Mã vận đơn/đối soát: {tracking}."
        )

    def _build_product_result(
        self,
        state: SessionState,
        recommendations: list[dict],
        cheaper_alternative: dict | None,
        phrased_message: str,
    ) -> dict:
        best = recommendations[0]
        return {
            "intent": "consultation",
            "message": phrased_message,
            "data": {
                **best,
                "recommendations": recommendations,
                "cheaper_alternative": cheaper_alternative,
                "filters": {
                    "skin_type": state.skin_type,
                    "concern": state.concern,
                    "budget_min": state.budget_min,
                    "budget_max": state.budget_max,
                    "category": state.category,
                },
                "scoring_formula": "0.5*concern_match + 0.3*skin_match + 0.2*budget_fit",
            },
            "type": "product",
        }

    def _template_message(self, state: SessionState, recommendations: list[dict], cheaper_alternative: dict | None) -> str:
        lines = [
            f"Mình đã lọc theo {state.skin_type}, {state.concern}, nhóm {state.category}" + (
                f", ngân sách {round(state.budget_min, 1)}-{round(state.budget_max, 1)}." if state.budget_min is not None and state.budget_max is not None
                else (f", ngân sách tối đa {round(state.budget_max, 1)}." if state.budget_max is not None else ".")
            )
        ]
        for idx, product in enumerate(recommendations, start=1):
            lines.append(f"{idx}) {product['name']} ({product['price']}) - {product['desc']}")
        if cheaper_alternative:
            lines.append(f"Gợi ý rẻ hơn: {cheaper_alternative['name']} ({cheaper_alternative['price']}).")
        lines.append("Bạn muốn mình chốt 1 lựa chọn an toàn nhất cho da của bạn không?")
        return "\n".join(lines)

    def _intent(self, message_lower: str) -> str:
        plain = self._strip_accents(message_lower)
        if re.search(r"\b(xin\s*chao|chao|hello|hi)\b", plain):
            return "greeting"
        if any(k in plain for k in ["ma giam gia", "voucher", "coupon", "khuyen mai", "uu dai"]):
            return "coupon"
        if any(k in plain for k in ["kiem tra don", "don hang", "tracking", "theo doi don"]):
            return "order_status"
        if re.search(r"\b(ship|shipping|giao\s*hang|freeship)\b", plain):
            return "shipping"
        if re.search(r"\b(doi\s*tra|hoan\s*tien|return)\b", plain):
            return "return"
        if re.search(r"\b(thanh\s*phan|cong\s*dung|huong\s*dan|cach\s*dung)\b", plain):
            return "info"
        return "consultation"

    def _resolve_contextual_intent(
        self,
        message_lower: str,
        conversation_history: list[dict] | None,
        dynamic_context: dict | None,
    ) -> str | None:
        plain = self._strip_accents(message_lower)
        context = dynamic_context or {}

        order_ctx = context.get("order_status")
        if order_ctx:
            order_follow_up_terms = [
                "don do",
                "don nay",
                "tinh trang don",
                "trang thai don",
                "dang shipping",
                "dang ship",
                "van chuyen",
                "ma van don",
                "tracking",
                "khi nao giao",
                "da giao chua",
            ]
            if any(term in plain for term in order_follow_up_terms):
                return "order_status"

        coupons = context.get("active_coupons") or []
        if coupons:
            coupon_follow_up_terms = [
                "ma do",
                "ma nao",
                "voucher nao",
                "coupon nao",
                "ap ma",
                "giam gia",
                "uu dai",
            ]
            if any(term in plain for term in coupon_follow_up_terms):
                return "coupon"

        if conversation_history:
            recent_text = " ".join((turn.get("content", "") for turn in conversation_history[-3:]))
            recent_plain = self._strip_accents(recent_text.lower())

            if any(token in plain for token in ["don do", "don nay", "ma van don", "shipping", "ship"]):
                if any(token in recent_plain for token in ["don", "tracking", "van don", "shipped"]):
                    return "order_status"

            if any(token in plain for token in ["ma do", "ma nao", "voucher do", "coupon do"]):
                if any(token in recent_plain for token in ["ma giam gia", "voucher", "coupon", "uu dai"]):
                    return "coupon"

        return None

    def _map_to_canonical(self, raw_value: str | None, aliases: dict[str, list[str]]) -> str | None:
        if not raw_value:
            return None

        plain_value = self._strip_accents(str(raw_value).lower()).strip()
        if not plain_value:
            return None

        for canonical, words in aliases.items():
            canonical_plain = self._strip_accents(canonical.lower())
            if plain_value == canonical_plain:
                return canonical

            candidate_words = [canonical, *words]
            for word in candidate_words:
                word_plain = self._strip_accents(str(word).lower())
                if plain_value == word_plain or word_plain in plain_value or plain_value in word_plain:
                    return canonical

        return None

    def _sanitize_budget_max(self, budget_max: float | int | str | None) -> float | None:
        if budget_max is None:
            return None
        try:
            value = float(budget_max)
            if value <= 0:
                return None
            return value
        except (TypeError, ValueError):
            return None

    def _extract_intent_slots_with_llm(self, user_message: str, history: list[dict] | None) -> dict | None:
        if not self.model:
            return None

        history_tail = (history or [])[-6:]
        schema = {
            "type": "object",
            "properties": {
                "skin_type": {"type": ["string", "null"]},
                "concern": {"type": ["string", "null"]},
                "budget_max": {"type": ["number", "null"]},
                "category": {"type": ["string", "null"]},
            },
            "required": ["skin_type", "concern", "budget_max", "category"],
        }

        extraction_prompt = {
            "instruction": (
                "Bạn là bộ trích xuất slot cho chatbot mỹ phẩm. "
                "Phân tích user_message và history để trích xuất slot. "
                "Hiểu từ lóng tiếng Việt, ví dụ 'viêm màng túi' => budget thấp, 'sần sùi' có thể map về acne/texture concern. "
                "Trả JSON đúng schema, không thêm text ngoài JSON. "
                "Nếu không chắc thì trả null. "
                "Giá trị skin_type ưu tiên một trong: oily,dry,combination,sensitive,normal,mature,all. "
                "Giá trị concern ưu tiên một trong: acne,hydration,soothing,brightening,coverage,matte,glow,uv,wrinkles. "
                "Giá trị category ưu tiên một trong: cushion,toner,serum,cream,cleanser,sunscreen,tint,balm,eye-cream,setting-spray,gift-set,primer,mask,facial-oil. "
                "budget_max là số dương theo cùng hệ đơn vị giá catalog hiện tại."
            ),
            "history": history_tail,
            "user_message": user_message,
            "output_schema": {
                "skin_type": "string|null",
                "concern": "string|null",
                "budget_max": "number|null",
                "category": "string|null",
            },
        }

        try:
            response = self.model.generate_content(
                json.dumps(extraction_prompt, ensure_ascii=False),
                generation_config=genai.types.GenerationConfig(
                    temperature=0.0,
                    response_mime_type="application/json",
                    response_schema=schema,
                ),
            )

            parsed = json.loads((response.text or "{}").strip())
            return {
                "skin_type": self._map_to_canonical(parsed.get("skin_type"), self.skin_keywords),
                "concern": self._map_to_canonical(parsed.get("concern"), self.concern_keywords),
                "budget_max": self._sanitize_budget_max(parsed.get("budget_max")),
                "category": self._map_to_canonical(parsed.get("category"), self.category_keywords),
            }
        except Exception:
            logger.exception("LLM slot extraction failed. Keeping previous state.")
            return None

    def process(
        self,
        message: str,
        session_id: str | None = None,
        conversation_history: list[dict] | None = None,
        dynamic_context: dict | None = None,
    ) -> dict:
        session_key = self._session_key(session_id)
        state = self._load_session_state(session_key)
        self._save_session_state(session_key, state)

        message_text = message.strip()
        message_lower = message_text.lower()

        if any(k in message_lower for k in ["reset", "làm mới", "bắt đầu lại", "xóa tư vấn"]):
            state = SessionState()
            self._save_session_state(session_key, state)
            return {
                "intent": "reset",
                "message": "Mình đã reset phiên tư vấn. Bạn cho mình loại da, vấn đề da, ngân sách và loại sản phẩm nhé.",
                "data": None,
                "type": "text",
            }

        intent = self._intent(message_lower)
        contextual_intent = self._resolve_contextual_intent(
            message_lower=message_lower,
            conversation_history=conversation_history,
            dynamic_context=dynamic_context,
        )
        if contextual_intent:
            intent = contextual_intent

        llm_slots = self._extract_intent_slots_with_llm(message_text, conversation_history)
        if llm_slots:
            if llm_slots.get("skin_type"):
                state.skin_type = llm_slots["skin_type"]
            if llm_slots.get("concern"):
                state.concern = llm_slots["concern"]
            if llm_slots.get("category"):
                state.category = llm_slots["category"]
            if llm_slots.get("budget_max") is not None:
                state.budget_max = llm_slots["budget_max"]
                state.budget_min = None
        self._save_session_state(session_key, state)

        if intent == "greeting":
            return {
                "intent": "greeting",
                "message": "Xin chào! Mình tư vấn theo catalog thật của TirTir. Bạn cho mình 4 thông tin: loại da, vấn đề da, ngân sách, và loại sản phẩm cần mua nhé.",
                "data": None,
                "type": "text",
            }
        if intent == "shipping":
            return {
                "intent": "shipping",
                "message": "Bên mình hỗ trợ kiểm tra giao hàng tại trang đơn hàng. Nếu cần, mình vẫn có thể tư vấn sản phẩm trước theo loại da và ngân sách của bạn.",
                "data": None,
                "type": "text",
            }
        if intent == "coupon":
            coupon_message = self._build_coupon_message_from_context(dynamic_context)
            return {
                "intent": "coupon",
                "message": coupon_message,
                "data": {
                    "active_coupons": (dynamic_context or {}).get("active_coupons") or [],
                },
                "type": "text",
            }
        if intent == "order_status":
            order_message = self._build_order_status_message_from_context(dynamic_context)
            return {
                "intent": "order_status",
                "message": order_message or "Mình chưa nhận được mã đơn hợp lệ trong tin nhắn. Bạn gửi giúp mình mã đơn để mình kiểm tra ngay cho bạn nhé.",
                "data": {
                    "order_status": (dynamic_context or {}).get("order_status"),
                },
                "type": "text",
            }
        if intent == "return":
            return {
                "intent": "return",
                "message": "Chính sách đổi trả áp dụng theo tình trạng sản phẩm khi nhận hàng. Nếu bạn muốn, mình tiếp tục tư vấn sản phẩm phù hợp để giảm rủi ro chọn sai.",
                "data": None,
                "type": "text",
            }
        if intent == "info" and not any(k in message_lower for k in ["tư vấn", "gợi ý", "recommend", "chọn", "mua"]):
            return {
                "intent": "info",
                "message": "Mình có thể giải thích thành phần/công dụng chi tiết sau khi bạn chọn được sản phẩm phù hợp. Bạn cho mình loại da, vấn đề da, ngân sách và loại sản phẩm nhé.",
                "data": None,
                "type": "text",
            }

        if not self._has_any_filter(state):
            return {
                "intent": "consultation",
                "message": self._ask_for_filter(),
                "data": {
                    "collected": {
                        "skin_type": state.skin_type,
                        "concern": state.concern,
                        "budget_min": state.budget_min,
                        "budget_max": state.budget_max,
                        "category": state.category,
                    }
                },
                "type": "text",
            }

        filtered = self._filter_products(state)
        if filtered.empty:
            return {
                "intent": "consultation",
                "message": "Mình chưa tìm thấy sản phẩm khớp tiêu chí bạn nêu. Bạn có thể nới ngân sách, thay đổi tiêu chí khác hoặc cho mình thêm thông tin để mình lọc lại nhé.",
                "data": {
                    "filters": {
                        "skin_type": state.skin_type,
                        "concern": state.concern,
                        "budget_min": state.budget_min,
                        "budget_max": state.budget_max,
                        "category": state.category,
                    }
                },
                "type": "text",
            }

        scored = self._score_products(filtered, state)
        top_n = scored.head(3)

        recommendations = [
            self._to_product_payload(row, score=row["score"])
            for _, row in top_n.iterrows()
        ]

        cheaper_rows = scored[scored["Price"] < top_n.iloc[0]["Price"]]
        cheaper_alternative = None
        if not cheaper_rows.empty:
            cheaper_alternative = self._to_product_payload(
                cheaper_rows.iloc[0], score=cheaper_rows.iloc[0]["score"]
            )

        phrased_message = self._render_with_llm(
            user_message=message_text,
            state=state,
            recommendations=recommendations,
            cheaper_alternative=cheaper_alternative,
            conversation_history=conversation_history,
            dynamic_context=dynamic_context,
        )

        return self._build_product_result(
            state=state,
            recommendations=recommendations,
            cheaper_alternative=cheaper_alternative,
            phrased_message=phrased_message,
        )

    def process_stream(
        self,
        message: str,
        session_id: str | None = None,
        conversation_history: list[dict] | None = None,
        dynamic_context: dict | None = None,
    ) -> Generator[dict, None, None]:
        session_key = self._session_key(session_id)
        state = self._load_session_state(session_key)
        self._save_session_state(session_key, state)

        message_text = message.strip()
        message_lower = message_text.lower()

        if any(k in message_lower for k in ["reset", "làm mới", "bắt đầu lại", "xóa tư vấn"]):
            state = SessionState()
            self._save_session_state(session_key, state)
            yield {
                "type": "final",
                "result": {
                    "intent": "reset",
                    "message": "Mình đã reset phiên tư vấn. Bạn cho mình loại da, vấn đề da, ngân sách và loại sản phẩm nhé.",
                    "data": None,
                    "type": "text",
                },
            }
            return

        intent = self._intent(message_lower)
        contextual_intent = self._resolve_contextual_intent(
            message_lower=message_lower,
            conversation_history=conversation_history,
            dynamic_context=dynamic_context,
        )
        if contextual_intent:
            intent = contextual_intent

        llm_slots = self._extract_intent_slots_with_llm(message_text, conversation_history)
        if llm_slots:
            if llm_slots.get("skin_type"):
                state.skin_type = llm_slots["skin_type"]
            if llm_slots.get("concern"):
                state.concern = llm_slots["concern"]
            if llm_slots.get("category"):
                state.category = llm_slots["category"]
            if llm_slots.get("budget_max") is not None:
                state.budget_max = llm_slots["budget_max"]
                state.budget_min = None
        self._save_session_state(session_key, state)

        if intent == "greeting":
            yield {
                "type": "final",
                "result": {
                    "intent": "greeting",
                    "message": "Xin chào! Mình tư vấn theo catalog thật của TirTir. Bạn cho mình 4 thông tin: loại da, vấn đề da, ngân sách, và loại sản phẩm cần mua nhé.",
                    "data": None,
                    "type": "text",
                },
            }
            return
        if intent == "shipping":
            yield {
                "type": "final",
                "result": {
                    "intent": "shipping",
                    "message": "Bên mình hỗ trợ kiểm tra giao hàng tại trang đơn hàng. Nếu cần, mình vẫn có thể tư vấn sản phẩm trước theo loại da và ngân sách của bạn.",
                    "data": None,
                    "type": "text",
                },
            }
            return
        if intent == "coupon":
            coupon_message = self._build_coupon_message_from_context(dynamic_context)
            yield {
                "type": "final",
                "result": {
                    "intent": "coupon",
                    "message": coupon_message,
                    "data": {
                        "active_coupons": (dynamic_context or {}).get("active_coupons") or [],
                    },
                    "type": "text",
                },
            }
            return
        if intent == "order_status":
            order_message = self._build_order_status_message_from_context(dynamic_context)
            yield {
                "type": "final",
                "result": {
                    "intent": "order_status",
                    "message": order_message or "Mình chưa nhận được mã đơn hợp lệ trong tin nhắn. Bạn gửi giúp mình mã đơn để mình kiểm tra ngay cho bạn nhé.",
                    "data": {
                        "order_status": (dynamic_context or {}).get("order_status"),
                    },
                    "type": "text",
                },
            }
            return
        if intent == "return":
            yield {
                "type": "final",
                "result": {
                    "intent": "return",
                    "message": "Chính sách đổi trả áp dụng theo tình trạng sản phẩm khi nhận hàng. Nếu bạn muốn, mình tiếp tục tư vấn sản phẩm phù hợp để giảm rủi ro chọn sai.",
                    "data": None,
                    "type": "text",
                },
            }
            return
        if intent == "info" and not any(k in message_lower for k in ["tư vấn", "gợi ý", "recommend", "chọn", "mua"]):
            yield {
                "type": "final",
                "result": {
                    "intent": "info",
                    "message": "Mình có thể giải thích thành phần/công dụng chi tiết sau khi bạn chọn được sản phẩm phù hợp. Bạn cho mình loại da, vấn đề da, ngân sách và loại sản phẩm nhé.",
                    "data": None,
                    "type": "text",
                },
            }
            return

        if not self._has_any_filter(state):
            yield {
                "type": "final",
                "result": {
                    "intent": "consultation",
                    "message": self._ask_for_filter(),
                    "data": {
                        "collected": {
                            "skin_type": state.skin_type,
                            "concern": state.concern,
                            "budget_min": state.budget_min,
                            "budget_max": state.budget_max,
                            "category": state.category,
                        }
                    },
                    "type": "text",
                },
            }
            return

        filtered = self._filter_products(state)
        if filtered.empty:
            yield {
                "type": "final",
                "result": {
                    "intent": "consultation",
                    "message": "Mình chưa tìm thấy sản phẩm khớp tiêu chí bạn nêu. Bạn có thể nới ngân sách, thay đổi tiêu chí khác hoặc cho mình thêm thông tin để mình lọc lại nhé.",
                    "data": {
                        "filters": {
                            "skin_type": state.skin_type,
                            "concern": state.concern,
                            "budget_min": state.budget_min,
                            "budget_max": state.budget_max,
                            "category": state.category,
                        }
                    },
                    "type": "text",
                },
            }
            return

        scored = self._score_products(filtered, state)
        top_n = scored.head(3)

        recommendations = [
            self._to_product_payload(row, score=row["score"])
            for _, row in top_n.iterrows()
        ]

        cheaper_rows = scored[scored["Price"] < top_n.iloc[0]["Price"]]
        cheaper_alternative = None
        if not cheaper_rows.empty:
            cheaper_alternative = self._to_product_payload(
                cheaper_rows.iloc[0], score=cheaper_rows.iloc[0]["score"]
            )

        chunks: list[str] = []
        for chunk_text in self._render_with_llm_stream(
            user_message=message_text,
            state=state,
            recommendations=recommendations,
            cheaper_alternative=cheaper_alternative,
            conversation_history=conversation_history,
            dynamic_context=dynamic_context,
        ):
            chunks.append(chunk_text)
            yield {"type": "chunk", "text": chunk_text}

        final_message = "".join(chunks).strip()
        if not final_message:
            final_message = self._template_message(state, recommendations, cheaper_alternative)

        yield {
            "type": "final",
            "result": self._build_product_result(
                state=state,
                recommendations=recommendations,
                cheaper_alternative=cheaper_alternative,
                phrased_message=final_message,
            ),
        }