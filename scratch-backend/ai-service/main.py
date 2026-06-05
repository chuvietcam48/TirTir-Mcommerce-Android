"""
TirTir AI Microservice — FastAPI
Models are loaded ONCE at startup via lifespan event:
  - SkinAnalyzer (MediaPipe FaceLandmarker)
"""

import os
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Request, Security, Depends
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import APIKeyHeader
from pydantic import BaseModel
from starlette.responses import JSONResponse
import logging
import time

from slowapi import Limiter
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

from skin_analyzer import SkinAnalyzer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ─── Rate Limiter ──────────────────────────────────────────────────────────────
limiter = Limiter(key_func=get_remote_address)

# ─── API Key Auth ──────────────────────────────────────────────────────────────
AI_API_KEY = os.environ.get("AI_SERVICE_API_KEY", "")
api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


async def verify_api_key(api_key: str = Security(api_key_header)):
    """
    Validate API key from X-API-Key header.
    If AI_SERVICE_API_KEY env var is not set, skip auth (dev mode).
    """
    # No key configured → skip auth (backward compatible for dev)
    if not AI_API_KEY:
        return True
    if api_key and api_key == AI_API_KEY:
        return True
    raise HTTPException(
        status_code=403,
        detail="Invalid or missing API key. Provide X-API-Key header."
    )


# ─── Global references — set during lifespan startup ──────────────────────────
analyzer: SkinAnalyzer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load all AI models once at startup, clean up on shutdown."""
    global analyzer, chatbot

    logger.info("🚀 Starting AI Microservice — Loading models...")
    start = time.time()

    analyzer = SkinAnalyzer()
    logger.info("✅ SkinAnalyzer loaded.")

    if AI_API_KEY:
        logger.info("🔑 API Key authentication is ENABLED.")
    else:
        logger.warning("⚠️  AI_SERVICE_API_KEY not set — authentication disabled (dev mode).")

    elapsed = time.time() - start
    logger.info(f"✅ All models loaded in {elapsed:.2f}s. Ready to serve.")
    yield

    # Cleanup
    logger.info("🛑 Shutting down AI Microservice.")
    analyzer = None


app = FastAPI(
    title="TirTir AI Service",
    version="2.1.0",
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

class AnalyzeRequest(BaseModel):
    image_base64: str


class AnalyzeResponse(BaseModel):
    success: bool
    data: dict | None = None
    error: str | None = None
    processing_time_ms: float | None = None


# ─── Endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health")
async def health_check():
    """Health check — no auth, no rate limit."""
    return {
        "status": "ok",
        "skin_analyzer_loaded": analyzer is not None,
        "service": "TirTir AI Service v2.1",
        "auth_enabled": bool(AI_API_KEY),
    }


@app.post("/analyze", response_model=AnalyzeResponse, dependencies=[Depends(verify_api_key)])
@limiter.limit("10/minute")
async def analyze_skin(request: Request, body: AnalyzeRequest):
    """
    Analyze skin from a base64-encoded image.
    Returns skin tone, undertone, concerns, and confidence.
    Rate limit: 10 requests/minute per IP.
    """
    if analyzer is None:
        raise HTTPException(status_code=503, detail="AI model not loaded yet")

    start = time.time()

    image = analyzer.decode_base64_image(body.image_base64)
    if image is None:
        return AnalyzeResponse(
            success=False,
            error="Failed to decode image. Please send a valid base64 JPEG/PNG."
        )

    try:
        result = analyzer.analyze(image)
    except Exception as e:
        logger.exception("Skin analysis failed")
        return AnalyzeResponse(success=False, error=str(e))

    elapsed_ms = (time.time() - start) * 1000

    if "error" in result:
        return AnalyzeResponse(
            success=False,
            error=result["error"],
            processing_time_ms=round(elapsed_ms, 2)
        )

    return AnalyzeResponse(
        success=True,
        data=result,
        processing_time_ms=round(elapsed_ms, 2)
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
