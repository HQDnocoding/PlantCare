import asyncio
import logging
from fastapi import APIRouter, HTTPException, Request
from app.services.storage import storage_service
from app.models.schemas import HistoryResponse, ConversationResponse

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/history", tags=["History"])


def _get_user_id(request: Request) -> str:
    """
    Extract user_id from X-User-Id header set by the API Gateway.
    Raises 401 if the header is missing (request bypassed the gateway).
    """
    user_id = request.headers.get("X-User-Id")
    if not user_id:
        raise HTTPException(status_code=401, detail="Unauthorized")
    return user_id


@router.get("", response_model=list[ConversationResponse])
async def get_conversations(request: Request):
    user_id = _get_user_id(request)
    try:
        result = await asyncio.to_thread(storage_service.get_conversations, user_id)
        return result
    except Exception as e:
        logger.error(
            f"❌ Get conversations FAILED for user {user_id}: {type(e).__name__}: {str(e)}",
            exc_info=True,
        )
        raise HTTPException(
            status_code=500, detail=f"Error fetching conversations: {str(e)}"
        )


@router.get("/{conv_id}", response_model=HistoryResponse)
async def get_conversation_history(conv_id: str, request: Request):
    user_id = _get_user_id(request)
    try:
        messages = await asyncio.to_thread(
            storage_service.get_messages, user_id, conv_id
        )
        summary = await asyncio.to_thread(storage_service.get_summary, user_id, conv_id)
        return HistoryResponse(conv_id=conv_id, summary=summary, messages=messages)
    except Exception as e:
        logger.error(
            f"❌ Get history FAILED for user {user_id} conv {conv_id}: {type(e).__name__}: {str(e)}",
            exc_info=True,
        )
        raise HTTPException(status_code=500, detail=f"Error fetching history: {str(e)}")


@router.delete("/{conv_id}")
async def delete_conversation(conv_id: str, request: Request):
    """
    Delete a specific conversation and all its messages.
    Only the owner can delete their own conversation.
    """
    user_id = _get_user_id(request)
    try:
        logger.info(
            f"🗑️  DELETE /api/v1/history/{{conv_id}} → Deleting conv {conv_id} for user {user_id}"
        )

        messages_ref = (
            storage_service.db.collection("conversations")
            .document(user_id)
            .collection("chats")
            .document(conv_id)
            .collection("messages")
        )
        msg_count = 0
        for doc in messages_ref.stream():
            doc.reference.delete()
            msg_count += 1

        storage_service.db.collection("conversations").document(user_id).collection(
            "chats"
        ).document(conv_id).delete()

        logger.info(
            f"✅ Deleted conv {conv_id} with {msg_count} messages for user {user_id}"
        )
        return {"message": f"Conversation {conv_id} deleted successfully"}

    except Exception as e:
        logger.error(
            f"❌ Delete FAILED for user {user_id} conv {conv_id}: {type(e).__name__}: {str(e)}",
            exc_info=True,
        )
        raise HTTPException(
            status_code=500, detail=f"Error deleting conversation: {str(e)}"
        )
