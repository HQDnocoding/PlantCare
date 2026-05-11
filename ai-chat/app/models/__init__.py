"""
Import all ORM models to register them with SQLAlchemy Base.
This ensures Base.metadata.create_all() creates all required tables.
"""

from app.models.disease import Disease  # noqa
from app.models.idempotency import IdempotencyCache  # noqa

__all__ = ["Disease", "IdempotencyCache"]
