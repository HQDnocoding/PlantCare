import os
import asyncio
import tempfile
from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Request
from typing import Optional
from app.services.chat import chat_service
from app.models.schemas import ChatResponse
import logging

router = APIRouter(prefix="/api/v1/chat", tags=["Chat"])
semaphore = asyncio.Semaphore(20)
logger = logging.getLogger(__name__)


@router.post("", response_model=ChatResponse)
async def chat(
    request: Request,
    message: str = Form(...),
    conv_id: Optional[str] = Form(default=None),
    file: Optional[UploadFile] = File(default=None),
):
    x_correlation_id = request.headers.get("X-Correlation-Id", "N/A")
    logger.info(
        f"[{x_correlation_id}] POST /api/v1/chat - conv_id={conv_id} has_file={file is not None}"
    )

    async with semaphore:
        user_id = request.headers.get("X-User-Id")
        if not user_id:
            logger.warning(f"[{x_correlation_id}] Unauthorized - missing X-User-Id")
            raise HTTPException(status_code=401, detail="Unauthorized")

        image_path = None

        try:
            if file:
                ext = (
                    file.filename.rsplit(".", 1)[-1]
                    if file.filename and "." in file.filename
                    else "jpg"
                )
                max_size = 10 * 1024 * 1024
                size = 0
                logger.info(
                    f"[{x_correlation_id}] Receiving file: {file.filename} content_type={file.content_type}"
                )

                with tempfile.NamedTemporaryFile(delete=False, suffix=f".{ext}") as tmp:
                    while chunk := await file.read(1024 * 1024):
                        size += len(chunk)
                        if size > max_size:
                            logger.warning(
                                f"[{x_correlation_id}] File too large: {size} bytes"
                            )
                            raise HTTPException(400, "File too large")
                        tmp.write(chunk)
                    image_path = tmp.name

                logger.info(
                    f"[{x_correlation_id}] File saved to {image_path} size={size} bytes"
                )

            logger.info(f"[{x_correlation_id}] Calling chat_service for user={user_id}")
            result = await chat_service.chat_async(
                user_id, message, conv_id, image_path
            )
            logger.info(f"[{x_correlation_id}] chat_service completed successfully")

            return ChatResponse(**result)

        except HTTPException:
            raise

        except Exception as e:
            logger.error(
                f"[{x_correlation_id}] Unhandled error for user={user_id}: {type(e).__name__}: {e}",
                exc_info=True,
            )
            raise HTTPException(status_code=500, detail="Internal server error")

        finally:
            if image_path and os.path.exists(image_path):
                os.unlink(image_path)
                logger.info(f"[{x_correlation_id}] Temp file deleted: {image_path}")


@router.get("/health")
async def health_check():
    return {"status": "healthy", "service": "chat"}
