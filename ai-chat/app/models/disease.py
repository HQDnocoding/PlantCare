from sqlalchemy import Column, Integer, String, Text, ForeignKey, DateTime, JSON, Table
from sqlalchemy.orm import relationship
from datetime import datetime
from app.core.database import Base

# Bảng trung gian (association table)
disease_medicine = Table(
    "disease_medicines",
    Base.metadata,
    Column("disease_id", Integer, ForeignKey("diseases.id"), primary_key=True),
    Column("medicine_id", Integer, ForeignKey("medicines.id"), primary_key=True),
)


class Disease(Base):
    __tablename__ = "diseases"

    id = Column(Integer, primary_key=True, index=True)
    order = Column(Integer, unique=True, nullable=False)
    class_name = Column(String(50), unique=True, nullable=False)
    name = Column(String(255), nullable=False)
    description = Column(Text, nullable=False)
    symptoms = Column(JSON, nullable=False)
    cause = Column(Text, nullable=False)
    favorable_conditions = Column(Text, nullable=False)
    treatment = Column(Text, nullable=False)
    prevention = Column(Text, nullable=False)

    # Many-to-many
    medicines = relationship(
        "Medicine",
        secondary=disease_medicine,
        back_populates="diseases",
    )

    version = Column(String(20), default="1.0.0")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class Medicine(Base):
    __tablename__ = "medicines"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False)
    active_ingredient = Column(Text, nullable=False)
    formulation = Column(String(100), nullable=False)
    usage = Column(Text, nullable=False)
    dosage = Column(Text, nullable=False)
    weather_condition = Column(Text, nullable=False)
    toxicity = Column(String(100), nullable=False)
    safety_warnings = Column(JSON, nullable=False)
    pre_harvest_interval = Column(String(50), nullable=False)

    # Many-to-many
    diseases = relationship(
        "Disease",
        secondary=disease_medicine,
        back_populates="medicines",
    )

    created_at = Column(DateTime, default=datetime.now())
    updated_at = Column(DateTime, default=datetime.now(), onupdate=datetime.now())
