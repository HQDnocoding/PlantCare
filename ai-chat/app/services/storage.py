import uuid
import logging
from datetime import datetime, timedelta, timezone
from firebase_admin import firestore, storage as fb_storage
from app.core.firebase import db, bucket

logger = logging.getLogger(__name__)


class StorageService:

    # ─── IMAGE ────────────────────────────────────
    def upload_image(self, image_path: str, user_id: str) -> str:
        """Upload image to Firebase Storage, return public URL."""
        ext = image_path.split(".")[-1]
        filename = f"images/{user_id}/{uuid.uuid4()}.{ext}"
        blob = bucket.blob(filename)
        blob.upload_from_filename(image_path)
        blob.make_public()
        return blob.public_url

    # ─── CONVERSATION ─────────────────────────────
    def create_conversation(self, user_id: str) -> str:
        """Create new conversation, return conv_id."""
        conv_id = str(uuid.uuid4())
        db.collection("conversations").document(user_id).collection("chats").document(
            conv_id
        ).set(
            {
                "created_at": datetime.now(),
                "expires_at": datetime.now() + timedelta(days=14),
                "summary": "",
            }
        )
        return conv_id

    def get_conversations(self, user_id: str) -> list:
        """Get all active conversations of a user."""
        try:
            logger.info(f" Fetching conversations for user: {user_id}")
            now = datetime.now(timezone.utc)

            # Fix: Remove .where() + .order_by() together (requires composite index)
            # Instead: fetch all, filter in memory
            docs = (
                db.collection("conversations")
                .document(user_id)
                .collection("chats")
                .order_by("created_at", direction=firestore.Query.DESCENDING)
                .stream()
            )

            conversations = []
            for doc in docs:
                data = doc.to_dict()
                expires_at = data.get("expires_at")

                # Filter: skip expired only
                if expires_at and expires_at > now:
                    conversations.append(
                        {
                            "conv_id": doc.id,
                            "created_at": data.get("created_at"),
                            "summary": data.get("summary", ""),
                            "expires_at": data.get("expires_at"),
                        }
                    )

            logger.info(
                f"Found {len(conversations)} active conversations for user {user_id}"
            )
            return conversations

        except Exception as e:
            logger.error(
                f"ERROR fetching conversations for user {user_id}: {type(e).__name__}: {str(e)}",
                exc_info=True,
            )
            raise

    # ─── MESSAGES ─────────────────────────────────
    def save_message(
        self, user_id: str, conv_id: str, role: str, content: str, image_url: str = None
    ):
        """Save a single message to Firestore."""
        db.collection("conversations").document(user_id).collection("chats").document(
            conv_id
        ).collection("messages").add(
            {
                "role": role,
                "content": content,
                "image_url": image_url,
                "timestamp": datetime.now(),
            }
        )

    def get_messages(self, user_id: str, conv_id: str) -> list:
        """Get all messages of a conversation (for history display)."""
        try:
            logger.info(f"Fetching messages for user {user_id}, conv {conv_id}")
            docs = (
                db.collection("conversations")
                .document(user_id)
                .collection("chats")
                .document(conv_id)
                .collection("messages")
                .order_by("timestamp")
                .stream()
            )
            messages = [doc.to_dict() for doc in docs]
            logger.info(f"Found {len(messages)} messages for conv {conv_id}")
            return messages
        except Exception as e:
            logger.error(
                f"ERROR fetching messages for user {user_id} conv {conv_id}: {type(e).__name__}: {str(e)}",
                exc_info=True,
            )
            raise

    def get_recent_messages(self, user_id: str, conv_id: str, n: int = 4) -> list:
        """Get N most recent messages to inject into prompt."""
        docs = (
            db.collection("conversations")
            .document(user_id)
            .collection("chats")
            .document(conv_id)
            .collection("messages")
            .order_by("timestamp", direction=firestore.Query.DESCENDING)
            .limit(n)
            .stream()
        )
        return list(reversed([doc.to_dict() for doc in docs]))

    # ─── SUMMARY ──────────────────────────────────
    def get_summary(self, user_id: str, conv_id: str) -> str:
        """Get current summary of a conversation."""
        try:
            logger.info(f"Fetching summary for user {user_id}, conv {conv_id}")
            doc = (
                db.collection("conversations")
                .document(user_id)
                .collection("chats")
                .document(conv_id)
                .get()
            )

            if not doc.exists:
                logger.warn(f" Conversation {conv_id} not found for user {user_id}")
                return ""

            summary = doc.to_dict().get("summary", "")
            logger.info(f"Got summary (length: {len(summary)}) for conv {conv_id}")
            return summary
        except Exception as e:
            logger.error(
                f" ERROR fetching summary for user {user_id} conv {conv_id}: {type(e).__name__}: {str(e)}",
                exc_info=True,
            )
            raise

    def update_summary(self, user_id: str, conv_id: str, summary: str):
        """Update conversation summary."""
        db.collection("conversations").document(user_id).collection("chats").document(
            conv_id
        ).update({"summary": summary})


# Singleton
storage_service = StorageService()
