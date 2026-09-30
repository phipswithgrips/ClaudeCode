"""Konfiguration aus Umgebungsvariablen (Präfix RK_)."""

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="RK_", env_file=".env", extra="ignore")

    database_url: str = "postgresql+psycopg://rezeptkiste:rezeptkiste@db:5432/rezeptkiste"
    files_dir: Path = Path("/data/files")

    # Einziges Benutzerkonto, wird beim ersten Start angelegt
    admin_user: str = "admin"
    admin_password: str = ""

    # Grenzen
    max_upload_mb: int = 50
    login_attempts_per_minute: int = 5
    sync_page_size: int = 500
    history_per_recipe: int = 20


@lru_cache
def get_settings() -> Settings:
    return Settings()
