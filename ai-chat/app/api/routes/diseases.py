import asyncio
from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import datetime
import logging

from app.core.database import get_db
from app.models.disease import Disease, Medicine
from app.models.schemas import (
    DiseaseMobileResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/diseases", tags=["diseases"])


# ═════════════════════ MOBILE SYNC ENDPOINT ══════════════════════════════════════


@router.get("/mobile/sync", response_model=DiseaseMobileResponse)
async def mobile_sync(
    x_correlation_id: Optional[str] = Header(None, alias="X-Correlation-Id"),
    db: Session = Depends(get_db),
):
    """Get all diseases with medicines for mobile offline sync"""

    def _query():
        diseases = db.query(Disease).order_by(Disease.order).all()
        now = datetime.now()
        version = diseases[0].version if diseases else "1.0.0"
        return DiseaseMobileResponse(
            version=version, last_updated=now, diseases=diseases
        )

    return await asyncio.to_thread(_query)
