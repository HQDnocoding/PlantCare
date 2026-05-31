"""
Admin CRUD endpoints for diseases and medicines.
Protected by X-User-Role header set by the API Gateway.
"""

import asyncio
import logging
from typing import List, Optional

from fastapi import APIRouter, Body, Depends, Header, HTTPException, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models.disease import Disease, Medicine
from app.models.schemas import (
    DiseaseCreate,
    DiseaseResponse,
    DiseaseUpdate,
    MedicineCreate,
    MedicineResponse,
    MedicineUpdate,
)

logger = logging.getLogger(__name__)

router = APIRouter(tags=["admin"])


# ── Auth dependency ──────────────────────────────────────────────────────────


def require_admin(x_user_role: Optional[str] = Header(None, alias="X-User-Role")):
    if not x_user_role or x_user_role.upper() != "ADMIN":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin role required",
        )


# ═══════════════════════ DISEASE ADMIN ENDPOINTS ═════════════════════════════


@router.get("/api/v1/admin/diseases", response_model=List[DiseaseResponse])
async def list_diseases(
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    def _query():
        return db.query(Disease).order_by(Disease.order).all()

    return await asyncio.to_thread(_query)


@router.post("/api/v1/admin/diseases", response_model=DiseaseResponse, status_code=201)
async def create_disease(
    body: DiseaseCreate,
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    def _create():
        medicines = db.query(Medicine).filter(Medicine.id.in_(body.medicine_ids)).all()
        disease = Disease(
            order=body.order,
            class_name=body.class_name,
            name=body.name,
            description=body.description,
            symptoms=body.symptoms,
            cause=body.cause,
            favorable_conditions=body.favorable_conditions,
            treatment=body.treatment,
            prevention=body.prevention,
            medicines=medicines,
        )
        db.add(disease)
        db.commit()
        db.refresh(disease)
        logger.info("[Admin] Created disease id=%s name=%s", disease.id, disease.name)
        return disease

    return await asyncio.to_thread(_create)


@router.put("/api/v1/admin/diseases/{disease_id}", response_model=DiseaseResponse)
async def update_disease(
    disease_id: int,
    body: DiseaseUpdate,
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    def _update():
        disease = db.query(Disease).filter(Disease.id == disease_id).first()
        if not disease:
            raise HTTPException(status_code=404, detail="Disease not found")

        for field, value in body.model_dump(
            exclude_unset=True, exclude={"medicine_ids"}, by_alias=False
        ).items():
            setattr(disease, field, value)

        if body.medicine_ids is not None:
            disease.medicines = (
                db.query(Medicine).filter(Medicine.id.in_(body.medicine_ids)).all()
            )

        db.commit()
        db.refresh(disease)
        logger.info("[Admin] Updated disease id=%s", disease_id)
        return disease

    return await asyncio.to_thread(_update)


@router.delete("/api/v1/admin/diseases/{disease_id}", status_code=204)
async def delete_disease(
    disease_id: int,
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    def _delete():
        disease = db.query(Disease).filter(Disease.id == disease_id).first()
        if not disease:
            raise HTTPException(status_code=404, detail="Disease not found")
        db.delete(disease)
        db.commit()
        logger.info("[Admin] Deleted disease id=%s", disease_id)

    await asyncio.to_thread(_delete)


@router.put(
    "/api/v1/admin/diseases/{disease_id}/medicines", response_model=DiseaseResponse
)
async def assign_medicines_to_disease(
    disease_id: int,
    body: dict = Body(...),
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    medicine_ids = body.get("medicineIds") or body.get("medicine_ids") or []

    def _assign():
        disease = db.query(Disease).filter(Disease.id == disease_id).first()
        if not disease:
            raise HTTPException(status_code=404, detail="Disease not found")
        disease.medicines = (
            db.query(Medicine).filter(Medicine.id.in_(medicine_ids)).all()
        )
        db.commit()
        db.refresh(disease)
        logger.info(
            "[Admin] Assigned medicines %s to disease %s", medicine_ids, disease_id
        )
        return disease

    return await asyncio.to_thread(_assign)


# ══════════════════════ MEDICINE ADMIN ENDPOINTS ═════════════════════════════


@router.get("/api/v1/admin/medicines", response_model=List[MedicineResponse])
async def list_medicines(
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    def _query():
        return db.query(Medicine).order_by(Medicine.id).all()

    return await asyncio.to_thread(_query)


@router.post(
    "/api/v1/admin/medicines", response_model=MedicineResponse, status_code=201
)
async def create_medicine(
    body: MedicineCreate,
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    def _create():
        data = body.model_dump(by_alias=False, exclude={"disease_ids"})
        medicine = Medicine(**data)
        if body.disease_ids:
            medicine.diseases = (
                db.query(Disease).filter(Disease.id.in_(body.disease_ids)).all()
            )
        db.add(medicine)
        db.commit()
        db.refresh(medicine)
        logger.info(
            "[Admin] Created medicine id=%s name=%s", medicine.id, medicine.name
        )
        return medicine

    return await asyncio.to_thread(_create)


@router.put("/api/v1/admin/medicines/{medicine_id}", response_model=MedicineResponse)
async def update_medicine(
    medicine_id: int,
    body: MedicineUpdate,
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    def _update():
        medicine = db.query(Medicine).filter(Medicine.id == medicine_id).first()
        if not medicine:
            raise HTTPException(status_code=404, detail="Medicine not found")

        for field, value in body.model_dump(exclude_unset=True, by_alias=False).items():
            setattr(medicine, field, value)

        db.commit()
        db.refresh(medicine)
        logger.info("[Admin] Updated medicine id=%s", medicine_id)
        return medicine

    return await asyncio.to_thread(_update)


@router.delete("/api/v1/admin/medicines/{medicine_id}", status_code=204)
async def delete_medicine(
    medicine_id: int,
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    def _delete():
        medicine = db.query(Medicine).filter(Medicine.id == medicine_id).first()
        if not medicine:
            raise HTTPException(status_code=404, detail="Medicine not found")
        db.delete(medicine)
        db.commit()
        logger.info("[Admin] Deleted medicine id=%s", medicine_id)

    await asyncio.to_thread(_delete)


@router.put(
    "/api/v1/admin/medicines/{medicine_id}/diseases", response_model=MedicineResponse
)
async def assign_diseases_to_medicine(
    medicine_id: int,
    body: dict = Body(...),
    db: Session = Depends(get_db),
    _: None = Depends(require_admin),
):
    disease_ids = body.get("diseaseIds") or body.get("disease_ids") or []

    def _assign():
        medicine = db.query(Medicine).filter(Medicine.id == medicine_id).first()
        if not medicine:
            raise HTTPException(status_code=404, detail="Medicine not found")
        medicine.diseases = db.query(Disease).filter(Disease.id.in_(disease_ids)).all()
        db.commit()
        db.refresh(medicine)
        logger.info(
            "[Admin] Assigned diseases %s to medicine %s", disease_ids, medicine_id
        )
        return medicine

    return await asyncio.to_thread(_assign)
