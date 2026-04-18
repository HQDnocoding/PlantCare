import hashlib
import json
import logging
import uuid
from datetime import datetime
from sqlalchemy.orm import Session
from app.models.idempotency import IdempotencyCache, create_idempotency_record
from typing import Optional, Dict, Any

logger = logging.getLogger(__name__)


class IdempotencyService:
    """Service for managing request idempotency and response caching"""

    TTL_HOURS = 24  # Cache for 24 hours to save token cost

    @staticmethod
    def compute_request_hash(request_body: str) -> str:
        """
        Compute SHA-256 hash of request body for conflict detection

        Args:
            request_body: Request payload as JSON string

        Returns:
            SHA-256 hash in hex format
        """
        try:
            hash_obj = hashlib.sha256(request_body.encode("utf-8"))
            return hash_obj.hexdigest()
        except Exception as e:
            logger.error(f"Failed to compute request hash: {e}")
            raise

    @staticmethod
    def get_or_generate_idempotency_key(provided_key: Optional[str]) -> str:
        """
        Get idempotency key from header or generate new one

        Args:
            provided_key: X-Idempotency-Key from request header

        Returns:
            Valid idempotency key
        """
        if provided_key:
            return provided_key
        # Generate deterministic-ish key based on timestamp hour
        hour_epoch = int(datetime.now().timestamp()) // 3600
        return f"gen-{hour_epoch}-{uuid.uuid4().hex[:8]}"

    @staticmethod
    def get_cached_response(
        db: Session, user_id: str, request_hash: str
    ) -> Optional[Dict[str, Any]]:
        """
        Check if request was already processed and return cached response

        Args:
            db: Database session
            user_id: User ID
            request_hash: SHA-256 hash of request body

        Returns:
            Cached response dict with keys: response_body, response_status
            OR None if not found or expired
        """
        try:
            now = datetime.utcnow()

            # Query: (user_id, request_hash) and not expired
            record = (
                db.query(IdempotencyCache)
                .filter(
                    IdempotencyCache.user_id == user_id,
                    IdempotencyCache.request_hash == request_hash,
                    IdempotencyCache.expires_at > now,
                )
                .first()
            )

            if record:
                logger.info(
                    f"Cache hit for user {user_id} with hash {request_hash[:8]}..."
                )
                return {
                    "response_body": record.response_body,
                    "response_status": record.response_status,
                    "cached": True,
                    "created_at": record.created_at.isoformat(),
                }

            logger.debug(
                f"Cache miss for user {user_id} with hash {request_hash[:8]}..."
            )
            return None

        except Exception as e:
            logger.error(f"Failed to get cached response: {e}")
            return None

    @staticmethod
    def cache_response(
        db: Session,
        idempotency_key: str,
        user_id: str,
        request_hash: str,
        method: str,
        endpoint: str,
        response_body: str,
        response_status: str = "200",
    ) -> bool:
        """
        Cache API response for future idempotent retries

        Args:
            db: Database session
            idempotency_key: X-Idempotency-Key value
            user_id: User ID
            request_hash: SHA-256 hash of request body
            method: HTTP method (POST, PUT, DELETE)
            endpoint: API endpoint path
            response_body: Response as JSON string
            response_status: HTTP status code

        Returns:
            True if cached successfully, False otherwise
        """
        try:
            record = create_idempotency_record(
                idempotency_key=idempotency_key,
                user_id=user_id,
                request_hash=request_hash,
                method=method,
                endpoint=endpoint,
                response_body=response_body,
                response_status=response_status,
                ttl_hours=IdempotencyService.TTL_HOURS,
            )

            db.add(record)
            db.commit()

            logger.info(
                f"Cached response for user {user_id} (key={idempotency_key[:16]}...)"
            )
            return True

        except Exception as e:
            logger.error(f"Failed to cache response: {e}")
            db.rollback()
            return False

    @staticmethod
    def cleanup_expired_records(db: Session) -> int:
        """
        Delete expired cache records (for scheduled task)

        Args:
            db: Database session

        Returns:
            Number of records deleted
        """
        try:
            now = datetime.utcnow()
            deleted = (
                db.query(IdempotencyCache)
                .filter(IdempotencyCache.expires_at <= now)
                .delete(synchronize_session=False)
            )

            db.commit()
            logger.info(f"Cleaned up {deleted} expired idempotency records")
            return deleted

        except Exception as e:
            logger.error(f"Failed to cleanup expired records: {e}")
            db.rollback()
            return 0
