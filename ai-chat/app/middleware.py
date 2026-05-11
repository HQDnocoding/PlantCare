"""
Idempotency middleware for FastAPI.
Prevents duplicate LLM calls by caching responses based on request content.
"""

import json
import logging
from io import BytesIO
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import StreamingResponse
from app.core.idempotency import IdempotencyService
from app.core.database import SessionLocal

logger = logging.getLogger(__name__)


class IdempotencyMiddleware(BaseHTTPMiddleware):
    """
    Middleware to cache responses for idempotent requests.

    Only processes:
    - POST/PUT/DELETE/PATCH requests
    - Requests with X-User-Id header (authenticated)
    - Application/json content type

    Flow:
    1. Extract X-Idempotency-Key header
    2. If not provided, use X-Correlation-Id or generate
    3. Compute SHA-256 hash of request body
    4. Check if cached response exists
    5. If cached → return it (avoid LLM call)
    6. If new → let request process, then cache response
    """

    IDEMPOTENT_METHODS = {"POST", "PUT", "DELETE", "PATCH"}
    PROTECTED_ENDPOINTS = {"/api/v1/chat"}  # Endpoints that use tokens

    async def dispatch(self, request: Request, call_next):
        # Skip non-idempotent methods
        if request.method not in self.IDEMPOTENT_METHODS:
            return await call_next(request)

        # Skip non-protected endpoints
        if not any(request.url.path.startswith(ep) for ep in self.PROTECTED_ENDPOINTS):
            return await call_next(request)

        # Get user ID (must be authenticated)
        user_id = request.headers.get("X-User-Id")
        if not user_id:
            # Not authenticated - skip idempotency
            return await call_next(request)

        try:
            # Read request body (needed for hashing)
            body = await request.body()
            if not body:
                return await call_next(request)

            # Extract idempotency key
            idempotency_key = request.headers.get(
                "X-Idempotency-Key", request.headers.get("X-Correlation-Id")
            )
            idempotency_key = IdempotencyService.get_or_generate_idempotency_key(
                idempotency_key
            )

            # Compute request hash
            request_hash = IdempotencyService.compute_request_hash(body.decode("utf-8"))

            # Check cache
            db = SessionLocal()
            try:
                cached = IdempotencyService.get_cached_response(
                    db, user_id, request_hash
                )

                if cached:
                    logger.info(
                        f"Returning cached response for {request.method} {request.url.path}"
                    )
                    # Return cached response as StreamingResponse
                    return StreamingResponse(
                        iter([cached["response_body"].encode("utf-8")]),
                        status_code=int(cached["response_status"]),
                        headers={
                            "X-From-Cache": "true",
                            "X-Cache-Key": idempotency_key,
                        },
                    )
            finally:
                db.close()

            # Not cached - process request normally, capture response
            response = await call_next(request)

            # Capture response body for caching
            try:
                response_body = b""
                async for chunk in response.body_iterator:
                    response_body += chunk

                # Cache the response
                db = SessionLocal()
                try:
                    response_text = response_body.decode("utf-8", errors="ignore")
                    IdempotencyService.cache_response(
                        db=db,
                        idempotency_key=idempotency_key,
                        user_id=user_id,
                        request_hash=request_hash,
                        method=request.method,
                        endpoint=request.url.path,
                        response_body=response_text,
                        response_status=str(response.status_code),
                    )
                finally:
                    db.close()

                # Return response with cache headers using StreamingResponse
                return StreamingResponse(
                    iter([response_body]),
                    status_code=response.status_code,
                    headers={
                        **dict(response.headers),
                        "X-Idempotency-Key": idempotency_key,
                    },
                    media_type=response.media_type,
                )
            except Exception as e:
                logger.error(f"Failed to process response for caching: {e}")
                # Recreate response with captured body
                return StreamingResponse(
                    iter([response_body]),
                    status_code=response.status_code,
                    headers=dict(response.headers),
                    media_type=response.media_type,
                )

        except Exception as e:
            logger.error(f"Idempotency middleware error: {e}", exc_info=True)
            # Pass through on error
            return await call_next(request)
