from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Service Configuration
    port: int = 8000
    log_level: str = "INFO"

    # CORS Config (not needed for internal service, but kept for direct calls)
    # In production, only API Gateway (port 8080) handles CORS
    # Remove this or set to empty if service is internal only
    cors_allowed_origins: str = ""

    # Idempotency Cache
    cache_ttl: int = 86400  # 24 hours in seconds
    cleanup_interval_ms: int = 21600000  # 6 hours in milliseconds

    # Gemini
    gemini_api_key: str

    # Firebase
    firebase_credentials_json: str
    firebase_storage_bucket: str

    # CNN
    model_weights_path_2: str = "best_loss.pth"
    confidence_threshold: float = 0.6
    model_name: str = None
    num_classes: int = 6

    # RAG
    chroma_db_path_2: str = "chroma_db"
    model_embedding: str = "BAAI/bge-m3"

    # HF
    hf_token: str

    # Database (use same credentials as other services)
    postgres_user: str = "plant_auth"
    postgres_password: str = "dat112004"
    db_host: str = "localhost"
    db_port: int = 5440
    db_name: str = "plant_chat_db"
    db_pool_size: int = 10
    db_max_overflow: int = 20

    # Admin
    admin_secret_key: str = "your-secret-key-change-in-production"
    internal_secret: str = "dat112004111111111222222222444444444000000aaaa999"

    @property
    def db_url(self) -> str:
        """Build database URL"""
        return (
            f"postgresql://{self.postgres_user}:{self.postgres_password}@"
            f"{self.db_host}:{self.db_port}/{self.db_name}"
        )

    class Config:
        # env_file = "../.env"
        env_file = "F:/khoa_luan/plant_app_backend/ai-chat/.env"


settings = Settings()
