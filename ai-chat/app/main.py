import logging
import traceback
from fastapi import FastAPI

from app.api.routes import chat, history, diseases
from app.core.config import settings
from app.core.database import engine, Base
from app.models import Disease
from huggingface_hub import login
from app.scripts.init_diseases import init_diseases

# ================= LOGGING =================
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


# ================= LIFESPAN =================
async def lifespan(app: FastAPI):
    logger.info("=== APP STARTING ===")

    # ===== STEP 1: DATABASE =====
    try:
        logger.info("STEP 1: Creating database tables...")
        Base.metadata.create_all(bind=engine)
        logger.info("Database ready")
    except Exception:
        logger.error("Database initialization FAILED")
        traceback.print_exc()
        raise

    # ===== STEP 2: INIT DATA =====
    try:
        logger.info("STEP 2: Initializing disease data...")
        init_diseases()
        logger.info("Disease data initialized")
    except Exception:
        logger.error("Init diseases FAILED")
        traceback.print_exc()

    # ===== STEP 3: HUGGING FACE =====
    try:
        logger.info("STEP 3: HuggingFace login...")

        if not settings.hf_token:
            logger.warning("HF_TOKEN missing → skip login")
        else:
            login(token=settings.hf_token)
            logger.info("HuggingFace login SUCCESS")

    except Exception:
        logger.error("HuggingFace login FAILED")
        traceback.print_exc()

    logger.info("=== STARTUP COMPLETE ===")

    yield

    # ===== SHUTDOWN =====
    logger.info("=== APP SHUTDOWN ===")


# ================= APP =================
app = FastAPI(
    title="Durian Disease API",
    description="CNN + RAG pipeline for durian leaf disease detection",
    version="1.0.0",
    lifespan=lifespan,
)

# ================= ROUTES =================
app.include_router(chat.router)
app.include_router(history.router)
app.include_router(diseases.router)


# ================= ENDPOINTS =================
@app.get("/")
def root():
    return {"message": "Durian Disease API is running"}


@app.get("/health")
def health():
    return {"status": "ok"}


# ================= LOCAL RUN =================
if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.port,
        workers=1,
        log_level="debug",
        reload=True,
    )
