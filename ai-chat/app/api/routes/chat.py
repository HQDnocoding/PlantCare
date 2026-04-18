import os
import asyncio
import tempfile
import shutil
from fastapi import (
    APIRouter,
    UploadFile,
    File,
    Form,
    HTTPException,
    Request,
)
from typing import Optional
from app.services.chat import chat_service
from app.models.schemas import ChatResponse

router = APIRouter(prefix="/api/v1/chat", tags=["Chat"])


@router.post("", response_model=ChatResponse)
async def chat(
    request: Request,
    message: str = Form(...),
    conv_id: Optional[str] = Form(default=None),
    file: Optional[UploadFile] = File(default=None),
):
    user_id = request.headers.get("X-User-Id")
    if not user_id:
        raise HTTPException(status_code=401, detail="Unauthorized")

    image_path = None

    try:
        if file:
            ext = file.filename.split(".")[-1]
            with tempfile.NamedTemporaryFile(delete=False, suffix=f".{ext}") as tmp:
                shutil.copyfileobj(file.file, tmp)
                image_path = tmp.name

        result = await chat_service.chat_async(
            user_id,
            message,
            conv_id,
            image_path,
        )

        return ChatResponse(**result)

    except HTTPException:
        raise

    except Exception as e:
        raise HTTPException(status_code=500, detail="Internal server error")

    finally:
        if image_path and os.path.exists(image_path):
            os.unlink(image_path)


@router.get("/health")
async def health_check():
    return {"status": "healthy", "service": "chat"}
