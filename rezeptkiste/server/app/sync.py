"""Sync-Logik: Last-Writer-Wins pro Datensatz, Revisionen als Cursor."""

from datetime import datetime, timezone

from pydantic import ValidationError
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from .config import get_settings
from .models import SYNC_MODELS, Recipe, RecipeHistory, data_fields, to_record
from .revs import RevAllocator
from .schemas import DATA_SCHEMAS, Accepted, PushResponse, RecordError, ServerRecord, SyncRecord


def _save_history(db: Session, recipe: Recipe, device_name: str | None) -> None:
    db.add(
        RecipeHistory(
            recipe_id=recipe.id,
            updated_at=recipe.updated_at,
            data=to_record("recipe", recipe)["data"] | {"deleted": recipe.deleted},
            replaced_by_device=device_name,
        )
    )
    db.flush()
    keep = get_settings().history_per_recipe
    old_ids = db.execute(
        select(RecipeHistory.id)
        .where(RecipeHistory.recipe_id == recipe.id)
        .order_by(RecipeHistory.saved_at.desc(), RecipeHistory.id.desc())
        .offset(keep)
    ).scalars().all()
    if old_ids:
        db.execute(delete(RecipeHistory).where(RecipeHistory.id.in_(old_ids)))


def apply_record(
    db: Session,
    revs: RevAllocator,
    rec: SyncRecord,
    device_name: str | None = None,
) -> tuple[str, object]:
    """Wendet einen Datensatz an.

    Rückgabe: ("accepted", server_rev) oder ("rejected", ServerRecord).
    Wirft ValueError bei ungültigen Daten.
    """
    model = SYNC_MODELS[rec.type]
    try:
        data = DATA_SCHEMAS[rec.type].model_validate(rec.data).model_dump()
    except ValidationError as e:
        raise ValueError(e.errors(include_url=False)[0]["msg"]) from e

    existing = db.get(model, rec.id)
    if existing is not None:
        if rec.updated_at == existing.updated_at:
            # Gleiche Fassung erneut gesendet (z. B. nach Verbindungsabbruch)
            return "accepted", existing.server_rev
        if rec.updated_at < existing.updated_at:
            return "rejected", ServerRecord(**to_record(rec.type, existing))
        if rec.type == "recipe":
            _save_history(db, existing, device_name)
        obj = existing
    else:
        obj = model(id=rec.id)
        db.add(obj)

    for field in data_fields(model):
        setattr(obj, field, data.get(field))
    if not rec.deleted:
        obj.deleted_at = None
    elif not obj.deleted:
        obj.deleted_at = datetime.now(timezone.utc)
    obj.deleted = rec.deleted
    obj.updated_at = rec.updated_at
    obj.server_rev = revs.next()
    db.flush()
    return "accepted", obj.server_rev


def push(db: Session, records: list[SyncRecord], device_name: str | None) -> PushResponse:
    revs = RevAllocator(db)
    accepted: list[Accepted] = []
    rejected: list[ServerRecord] = []
    errors: list[RecordError] = []
    revs.lock()
    for rec in records:
        try:
            with db.begin_nested():
                outcome, value = apply_record(db, revs, rec, device_name)
        except ValueError as e:
            errors.append(RecordError(type=rec.type, id=rec.id, message=str(e)))
            continue
        if outcome == "accepted":
            accepted.append(Accepted(type=rec.type, id=rec.id, server_rev=value))
        else:
            rejected.append(value)
    db.commit()
    return PushResponse(accepted=accepted, rejected=rejected, errors=errors)


def pull(db: Session, since: int, limit: int) -> tuple[list[ServerRecord], int, bool]:
    rows: list[tuple[str, object]] = []
    for name, model in SYNC_MODELS.items():
        objs = db.execute(
            select(model).where(model.server_rev > since).order_by(model.server_rev).limit(limit + 1)
        ).scalars().all()
        rows.extend((name, o) for o in objs)
    rows.sort(key=lambda r: r[1].server_rev)
    has_more = len(rows) > limit
    rows = rows[:limit]
    records = [ServerRecord(**to_record(name, obj)) for name, obj in rows]
    cursor = records[-1].server_rev if records else since
    return records, cursor, has_more
