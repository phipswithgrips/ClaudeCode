"""Request- und Response-Modelle."""

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

from . import hlc

EntityType = Literal["course", "category", "collection", "recipe", "photo", "shopping_item"]


# --- Anmeldung ---------------------------------------------------------------

class DeviceLogin(BaseModel):
    username: str
    password: str
    device_name: str = Field(min_length=1, max_length=100)


class DeviceToken(BaseModel):
    device_id: str
    token: str


class DeviceInfo(BaseModel):
    id: str
    name: str
    created_at: Any
    last_seen_at: Any | None
    current: bool


# --- Daten je Typ (für die Prüfung beim Push) ----------------------------------

class _Data(BaseModel):
    model_config = ConfigDict(extra="ignore")


class NamedData(_Data):
    name: str = ""
    sort_order: int = 0


class RecipeData(_Data):
    title: str = ""
    description: str | None = None
    source_name: str | None = None
    source_url: str | None = None
    servings_text: str | None = None
    servings_count: int | None = Field(default=None, ge=0)
    prep_min: int | None = Field(default=None, ge=0)
    cook_min: int | None = Field(default=None, ge=0)
    total_min: int | None = Field(default=None, ge=0)
    ingredients_text: str | None = None
    directions_text: str | None = None
    notes: str | None = None
    nutrition: dict[str, Any] | None = None
    rating: int = Field(default=0, ge=0, le=5)
    is_favourite: bool = False
    course_ids: list[str] = []
    category_ids: list[str] = []
    collection_ids: list[str] = []
    import_ref: str | None = None


class PhotoData(_Data):
    recipe_id: str
    sort_order: int = 0
    sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    mime: str = "image/jpeg"
    width: int | None = None
    height: int | None = None


class ShoppingItemData(_Data):
    text: str = Field(default="", max_length=500)
    checked: bool = False
    recipe_id: str | None = Field(default=None, max_length=64)
    recipe_title: str | None = None
    sort_order: int = 0


DATA_SCHEMAS: dict[str, type[_Data]] = {
    "course": NamedData,
    "category": NamedData,
    "collection": NamedData,
    "recipe": RecipeData,
    "photo": PhotoData,
    "shopping_item": ShoppingItemData,
}


# --- Sync --------------------------------------------------------------------

class SyncRecord(BaseModel):
    type: EntityType
    id: str
    updated_at: str
    deleted: bool = False
    data: dict[str, Any] = {}

    @field_validator("updated_at")
    @classmethod
    def _hlc(cls, v: str) -> str:
        if not hlc.is_valid(v):
            raise ValueError("updated_at ist kein gültiger HLC-Zeitstempel")
        return v


class PushRequest(BaseModel):
    records: list[SyncRecord] = Field(max_length=500)


class ServerRecord(BaseModel):
    type: EntityType
    id: str
    updated_at: str
    deleted: bool
    server_rev: int
    data: dict[str, Any]


class Accepted(BaseModel):
    type: EntityType
    id: str
    server_rev: int


class RecordError(BaseModel):
    type: str
    id: str
    message: str


class PushResponse(BaseModel):
    accepted: list[Accepted]
    rejected: list[ServerRecord]
    errors: list[RecordError]


class PullResponse(BaseModel):
    records: list[ServerRecord]
    cursor: int
    has_more: bool


class HistoryEntry(BaseModel):
    updated_at: str
    saved_at: Any
    replaced_by_device: str | None
    data: dict[str, Any]
