"""
TirTir Chatbot Microservice — FastAPI
Runs on port 8001
"""

import os
import json
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Request, Security, Depends
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import APIKeyHeader
from pydantic import BaseModel
from starlette.responses import JSONResponse, StreamingResponse
import logging
import time
from typing import Literal
from typing import Any

from slowapi import Limiter
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

from chatbot_engine import ChatbotEngine

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ─── Rate Limiter ──────────────────────────────────────────────────────────────
limiter = Limiter(key_func=get_remote_address)

# ─── API Key Auth ──────────────────────────────────────────────────────────────
AI_API_KEY = os.environ.get("AI_SERVICE_API_KEY", "")
ENVIRONMENT = os.environ.get("NODE_ENV", "development")
api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


async def verify_api_key(api_key: str = Security(api_key_header)):
    """
    Validate API key from X-API-Key header.
    - Production: API key required
    - Development: API key optional (for local testing)
    """
    if not AI_API_KEY:
        if ENVIRONMENT == "production":
            raise HTTPException(
                status_code=500,
                detail="AI_SERVICE_API_KEY not configured. Set this environment variable in production."
            )
        logger.warning("DEV MODE: API key auth disabled. Not suitable for production.")
        return True
    
    if not api_key or api_key != AI_API_KEY:
        raise HTTPException(
            status_code=403,
            detail="Invalid or missing API key. Provide X-API-Key header."
        )
    return True


# ─── Global references — set during lifespan startup ──────────────────────────
chatbot: ChatbotEngine | None = None

# Path to the product CSV 
_CHATBOT_CSV = os.path.join(os.path.dirname(__file__), "chatbot_products.csv")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load model once at startup, clean up on shutdown."""
    global chatbot

    logger.info("🚀 Starting Chatbot Microservice — Loading model...")
    start = time.time()

    if os.path.exists(_CHATBOT_CSV):
        chatbot = ChatbotEngine(_CHATBOT_CSV)
        logger.info("✅ ChatbotEngine loaded.")
    else:
        logger.warning(f"⚠️  Chatbot CSV not found at {_CHATBOT_CSV}. /chat endpoint will be unavailable.")

    if AI_API_KEY:
        logger.info("🔑 API Key authentication is ENABLED.")
    else:
        logger.warning("⚠️  AI_SERVICE_API_KEY not set — authentication disabled (dev mode).")

    elapsed = time.time() - start
    logger.info(f"✅ Chatbot model loaded in {elapsed:.2f}s. Ready to serve.")
    yield

    # Cleanup
    logger.info("🛑 Shutting down Chatbot Microservice.")
    chatbot = None


app = FastAPI(
    title="TirTir Chatbot Service",
    version="1.0.0",
    lifespan=lifespan
)

# Attach rate limiter to app
app.state.limiter = limiter


@app.exception_handler(RateLimitExceeded)
async def rate_limit_handler(request: Request, exc: RateLimitExceeded):
    return JSONResponse(
        status_code=429,
        content={
            "success": False,
            "error": f"Rate limit exceeded: {exc.detail}. Please try again later."
        }
    )


# CORS — allow Node.js backend to call this service
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5001", "http://127.0.0.1:5001", "http://backend:5001"],
    allow_methods=["POST", "GET"],
    allow_headers=["*"],
)


# ─── Request / Response Models ─────────────────────────────────────────────────

class ConversationTurn(BaseModel):
    role: Literal["user", "bot"]
    content: str


class ChatRequest(BaseModel):
    message: str
    session_id: str | None = None
    conversation_history: list[ConversationTurn] | None = None
    dynamic_context: dict[str, Any] | None = None


class ChatResponse(BaseModel):
    success: bool
    data: dict | None = None   # contains: intent, message, type, product data
    error: str | None = None
    processing_time_ms: float | None = None


# ─── Endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health")
async def health_check():
    """Health check — no auth, no rate limit."""
    return {
        "status": "ok",
        "chatbot_loaded": chatbot is not None,
        "service": "TirTir Chatbot Service v1.0",
        "auth_enabled": bool(AI_API_KEY),
    }

@app.post("/chat", dependencies=[Depends(verify_api_key)])
@limiter.limit("30/minute")
async def chat(body: ChatRequest, request: Request):
    """
    Process a Vietnamese beauty query and stream response via SSE.
    Model is loaded at startup — sub-millisecond inference, no spawn overhead.
    Rate limit: 30 requests/minute per IP.
    NOTE: slowapi requires the Starlette Request param to be named `request`.
    """
    if chatbot is None:
        raise HTTPException(
            status_code=503,
            detail="Chatbot model not available. Ensure chatbot_products.csv exists in the backend/chatbot directory."
        )

    if not body.message or not body.message.strip():
        raise HTTPException(status_code=400, detail="message field is required and cannot be empty")

    start = time.time()
    fallback_session = request.client.host if request.client else None
    message = body.message.strip()
    session_id = body.session_id or fallback_session
    conversation_history = [turn.model_dump() for turn in (body.conversation_history or [])]
    dynamic_context = body.dynamic_context or {}

    def sse_event(event_name: str, payload: dict) -> str:
        return f"event: {event_name}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"

    def event_stream():
        try:
            for event in chatbot.process_stream(
                message,
                session_id=session_id,
                conversation_history=conversation_history,
                dynamic_context=dynamic_context,
            ):
                if event.get("type") == "chunk":
                    yield sse_event("chunk", {"text": event.get("text", "")})
                    continue

                if event.get("type") == "final":
                    elapsed_ms = (time.time() - start) * 1000
                    yield sse_event(
                        "done",
                        {
                            "success": True,
                            "data": event.get("result"),
                            "processing_time_ms": round(elapsed_ms, 2),
                        },
                    )
                    return

            raise RuntimeError("Streaming finished without final result")
        except Exception as e:
            logger.exception("Chatbot streaming failed")
            yield sse_event("error", {"success": False, "error": str(e)})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
