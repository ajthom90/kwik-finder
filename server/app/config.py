from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    data_dir: str = "./data"
    list_refresh_seconds: int = 900
    details_refresh_seconds: int = 1200
    pdf_refresh_seconds: int = 86400
    cors_origins: str = "*"
    log_level: str = "INFO"
    host: str = "0.0.0.0"
    port: int = 8080


@lru_cache
def get_settings() -> Settings:
    return Settings()
