"""Datenmodell. Clients (SQLite) führen dieselben synchronisierten Felder."""

from datetime import datetime, timezone

from sqlalchemy import (
    JSON,
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    Uuid,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

JsonType = JSON().with_variant(JSONB(), "postgresql")


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Base(DeclarativeBase):
    pass


class SyncMixin:
    """Felder, die jede synchronisierte Tabelle trägt."""

    id: Mapped[str] = mapped_column(Uuid(as_uuid=False), primary_key=True)
    updated_at: Mapped[str] = mapped_column(String(64), nullable=False)  # HLC
    deleted: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    server_rev: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class Recipe(SyncMixin, Base):
    __tablename__ = "recipe"

    title: Mapped[str] = mapped_column(Text, nullable=False, default="")
    description: Mapped[str | None] = mapped_column(Text)
    source_name: Mapped[str | None] = mapped_column(Text)
    source_url: Mapped[str | None] = mapped_column(Text)
    servings_text: Mapped[str | None] = mapped_column(Text)
    servings_count: Mapped[int | None] = mapped_column(Integer)
    prep_min: Mapped[int | None] = mapped_column(Integer)
    cook_min: Mapped[int | None] = mapped_column(Integer)
    total_min: Mapped[int | None] = mapped_column(Integer)
    ingredients_text: Mapped[str | None] = mapped_column(Text)
    directions_text: Mapped[str | None] = mapped_column(Text)
    notes: Mapped[str | None] = mapped_column(Text)
    nutrition: Mapped[dict | None] = mapped_column(JsonType)
    rating: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    is_favourite: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    course_ids: Mapped[list] = mapped_column(JsonType, nullable=False, default=list)
    category_ids: Mapped[list] = mapped_column(JsonType, nullable=False, default=list)
    collection_ids: Mapped[list] = mapped_column(JsonType, nullable=False, default=list)
    import_ref: Mapped[str | None] = mapped_column(String(64), index=True)


class Course(SyncMixin, Base):
    __tablename__ = "course"

    name: Mapped[str] = mapped_column(Text, nullable=False, default="")
    sort_order: Mapped[int] = mapped_column(Integer, nullable=False, default=0)


class Category(SyncMixin, Base):
    __tablename__ = "category"

    name: Mapped[str] = mapped_column(Text, nullable=False, default="")
    sort_order: Mapped[int] = mapped_column(Integer, nullable=False, default=0)


class Collection(SyncMixin, Base):
    __tablename__ = "collection"

    name: Mapped[str] = mapped_column(Text, nullable=False, default="")
    sort_order: Mapped[int] = mapped_column(Integer, nullable=False, default=0)


class Photo(SyncMixin, Base):
    __tablename__ = "photo"

    # Kein Fremdschlüssel: Foto und Rezept können in beliebiger Reihenfolge ankommen.
    recipe_id: Mapped[str] = mapped_column(Uuid(as_uuid=False), nullable=False, index=True)
    sort_order: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    mime: Mapped[str] = mapped_column(String(64), nullable=False, default="image/jpeg")
    width: Mapped[int | None] = mapped_column(Integer)
    height: Mapped[int | None] = mapped_column(Integer)


class RecipeHistory(Base):
    """Überschriebene Fassungen eines Rezepts (Sicherheitsnetz bei Konflikten)."""

    __tablename__ = "recipe_history"
    __table_args__ = (Index("ix_recipe_history_recipe_saved", "recipe_id", "saved_at"),)

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    recipe_id: Mapped[str] = mapped_column(Uuid(as_uuid=False), nullable=False)
    updated_at: Mapped[str] = mapped_column(String(64), nullable=False)
    data: Mapped[dict] = mapped_column(JsonType, nullable=False)
    replaced_by_device: Mapped[str | None] = mapped_column(Text)
    saved_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)


class UserAccount(Base):
    __tablename__ = "user_account"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    username: Mapped[str] = mapped_column(String(128), unique=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(Text, nullable=False)


class Device(Base):
    __tablename__ = "device"

    id: Mapped[str] = mapped_column(Uuid(as_uuid=False), primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("user_account.id"), nullable=False)
    name: Mapped[str] = mapped_column(Text, nullable=False)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    revoked: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)


class RevCounter(Base):
    """Eine Zeile mit der zuletzt vergebenen server_rev.

    Die Zeile wird pro Schreibtransaktion mit FOR UPDATE gesperrt. Dadurch
    werden Revisionen in derselben Reihenfolge sichtbar, in der sie vergeben
    wurden, und ein Pull mit Cursor verpasst nie einen Datensatz.
    """

    __tablename__ = "rev_counter"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    value: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)


# Synchronisierte Typen, wie sie im Sync-Protokoll heißen
SYNC_MODELS: dict[str, type[SyncMixin]] = {
    "course": Course,
    "category": Category,
    "collection": Collection,
    "recipe": Recipe,
    "photo": Photo,
}

SYNC_META_FIELDS = {"id", "updated_at", "deleted", "server_rev", "deleted_at"}


def data_fields(model: type[SyncMixin]) -> list[str]:
    return [c.name for c in model.__table__.columns if c.name not in SYNC_META_FIELDS]


def to_record(entity_type: str, obj: SyncMixin) -> dict:
    return {
        "type": entity_type,
        "id": obj.id,
        "updated_at": obj.updated_at,
        "deleted": obj.deleted,
        "server_rev": obj.server_rev,
        "data": {f: getattr(obj, f) for f in data_fields(type(obj))},
    }
