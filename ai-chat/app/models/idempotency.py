from sqlalchemy import Column, String, Text, DateTime, Index
from datetime import datetime, timedelta
from app.core.database import Base


class IdempotencyCache(Base):
    """
    Cache for idempotent API requests.
    Prevents duplicate LLM calls when same request is retried.

    Key: (user_id, request_hash) determines uniqueness
    Value: cached response body + status code
    TTL: 24 hours (configurable)
    """

    __tablename__ = "idempotency_cache"

    # Primary key: idempotency_key from request header OR generated
    idempotency_key = Column(String(255), primary_key=True, index=True)

    # Request metadata
    user_id = Column(String(255), nullable=False, index=True)
    request_hash = Column(String(64), nullable=False)  # SHA-256
    method = Column(String(10), nullable=False)  # POST, PUT, DELETE
    endpoint = Column(String(255), nullable=False)

    # Response cache
    response_body = Column(Text, nullable=False)  # JSON string
    response_status = Column(String(3), nullable=False, default="200")

    # Timestamps
    created_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    expires_at = Column(DateTime, nullable=False)  # For TTL cleanup

    # Index for query optimization
    __table_args__ = (
        Index("idx_user_request", "user_id", "request_hash"),
        Index("idx_expires", "expires_at"),
    )


def create_idempotency_record(
    idempotency_key: str,
    user_id: str,
    request_hash: str,
    method: str,
    endpoint: str,
    response_body: str,
    response_status: str = "200",
    ttl_hours: int = 24,
) -> IdempotencyCache:
    """Create idempotency cache record"""
    now = datetime.utcnow()
    return IdempotencyCache(
        idempotency_key=idempotency_key,
        user_id=user_id,
        request_hash=request_hash,
        method=method,
        endpoint=endpoint,
        response_body=response_body,
        response_status=response_status,
        created_at=now,
        expires_at=now + timedelta(hours=ttl_hours),
    )
