from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime

# ─── REQUEST ──────────────────────────────────


class ChatRequest(BaseModel):
    user_id: str
    conv_id: Optional[str] = None
    message: str


class HistoryRequest(BaseModel):
    user_id: str
    conv_id: str


# ─── RESPONSE ─────────────────────────────────


class MessageResponse(BaseModel):
    role: str
    content: str
    image_url: Optional[str] = None
    timestamp: Optional[datetime] = None


class ChatResponse(BaseModel):
    conv_id: str
    answer: str
    disease: Optional[str] = None
    confidence: Optional[float] = None


class ConversationResponse(BaseModel):
    conv_id: str
    created_at: datetime
    expires_at: datetime
    summary: str


class HistoryResponse(BaseModel):
    conv_id: str
    summary: str
    messages: list[MessageResponse]


# ─── MEDICINE SCHEMAS ─────────────────────────


class MedicineBase(BaseModel):
    name: str
    active_ingredient: str
    formulation: str
    usage: str
    dosage: str
    weather_condition: str
    toxicity: str
    safety_warnings: List[str]
    pre_harvest_interval: str


class MedicineCreate(MedicineBase):
    pass


class MedicineUpdate(BaseModel):
    name: Optional[str] = None
    active_ingredient: Optional[str] = None
    formulation: Optional[str] = None
    usage: Optional[str] = None
    dosage: Optional[str] = None
    weather_condition: Optional[str] = None
    toxicity: Optional[str] = None
    safety_warnings: Optional[List[str]] = None
    pre_harvest_interval: Optional[str] = None


class MedicineResponse(MedicineBase):
    id: int

    disease_ids: List[int] = []
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


# ─── DISEASE SCHEMAS ──────────────────────────


class DiseaseBase(BaseModel):
    order: int = Field(..., description="CNN model label order (>=0)", ge=0)
    class_name: str = Field(..., min_length=1)
    name: str = Field(..., min_length=1)
    description: str = Field(..., min_length=1)
    symptoms: List[str] = Field(..., min_length=1)
    cause: str = Field(..., min_length=1)
    favorable_conditions: str = Field(..., min_length=1)
    treatment: str = Field(..., min_length=1)
    prevention: str = Field(..., min_length=1)


class DiseaseCreate(DiseaseBase):
    medicine_ids: List[int] = []


class DiseaseUpdate(BaseModel):
    order: Optional[int] = None
    class_name: Optional[str] = None
    name: Optional[str] = None
    description: Optional[str] = None
    symptoms: Optional[List[str]] = None
    cause: Optional[str] = None
    favorable_conditions: Optional[str] = None
    treatment: Optional[str] = None
    prevention: Optional[str] = None
    medicine_ids: Optional[List[int]] = None


class DiseaseResponse(DiseaseBase):
    id: int
    medicines: List[MedicineResponse] = []
    version: str
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class DiseaseMobileResponse(BaseModel):
    version: str
    last_updated: datetime
    diseases: List[DiseaseResponse]
