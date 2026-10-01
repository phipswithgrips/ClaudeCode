"""HTTP-Endpunkte."""

import hashlib
import re
import uuid

from fastapi import APIRouter, Depends, File, HTTPException, Query, Request, Response, UploadFile, status
from fastapi.responses import FileResponse
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from . import files, recipekeeper
from . import sync as sync_service
from .auth import client_ip, current_device, login_limiter, new_token, token_hash, verify_password
from .config import get_settings
from .db import get_db
from .models import Device, RecipeHistory, UserAccount
from .schemas import DeviceInfo, DeviceLogin, DeviceToken, HistoryEntry, PullResponse, PushRequest, PushResponse

router = APIRouter()
_SHA = re.compile(r"^[0-9a-f]{64}$")


# --- Zustand -----------------------------------------------------------------

_STATUS_PAGE = """<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Cookfolio</title>
<style>body{{margin:0;background:#121212;color:#EDEDED;font:16px/1.5 system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}}
main{{background:#1E1E1E;border-radius:12px;padding:32px 40px;max-width:520px}}h1{{color:#F26B2A;margin:0 0 8px}}
p{{color:#A0A0A0;margin:8px 0}}b{{color:#EDEDED}}a{{color:#F26B2A}}</style></head>
<body><main><h1>Cookfolio</h1><p>Der Server läuft. Datenbank: <b>{db}</b></p>
<p>Diese Adresse ist für die Apps gedacht: In der Android- und der Windows-App als Server-Adresse eintragen.</p>
<p><a href="/docs">API-Dokumentation</a></p></main></body></html>"""


@router.get("/", include_in_schema=False)
def status_page(db: Session = Depends(get_db)) -> Response:
    try:
        db.execute(text("SELECT 1"))
        state = "verbunden"
    except Exception:  # noqa: BLE001
        state = "nicht erreichbar"
    return Response(_STATUS_PAGE.format(db=state), media_type="text/html; charset=utf-8")

@router.get("/health", tags=["system"])
def health(db: Session = Depends(get_db)) -> dict:
    db.execute(text("SELECT 1"))
    return {"status": "ok"}


# --- Anmeldung ---------------------------------------------------------------

@router.post("/auth/device", response_model=DeviceToken, tags=["auth"])
def login_device(body: DeviceLogin, request: Request, db: Session = Depends(get_db)) -> DeviceToken:
    login_limiter.check(client_ip(request))
    user = db.execute(select(UserAccount).where(UserAccount.username == body.username)).scalar_one_or_none()
    if user is None or not verify_password(user.password_hash, body.password):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Benutzername oder Passwort falsch.")
    token = new_token()
    device = Device(id=str(uuid.uuid4()), user_id=user.id, name=body.device_name.strip(), token_hash=token_hash(token))
    db.add(device)
    db.commit()
    return DeviceToken(device_id=device.id, token=token)


@router.get("/auth/devices", response_model=list[DeviceInfo], tags=["auth"])
def list_devices(me: Device = Depends(current_device), db: Session = Depends(get_db)) -> list[DeviceInfo]:
    rows = db.execute(select(Device).where(Device.revoked.is_(False)).order_by(Device.created_at)).scalars()
    return [DeviceInfo(id=d.id, name=d.name, created_at=d.created_at, last_seen_at=d.last_seen_at, current=d.id == me.id) for d in rows]


@router.delete("/auth/devices/{device_id}", status_code=204, tags=["auth"])
def revoke_device(device_id: uuid.UUID, me: Device = Depends(current_device), db: Session = Depends(get_db)) -> Response:
    device = db.get(Device, str(device_id))
    if device is None or device.revoked:
        raise HTTPException(404, "Gerät nicht gefunden.")
    device.revoked = True
    db.commit()
    return Response(status_code=204)


# --- Sync --------------------------------------------------------------------

@router.post("/sync/push", response_model=PushResponse, tags=["sync"])
def sync_push(body: PushRequest, me: Device = Depends(current_device), db: Session = Depends(get_db)) -> PushResponse:
    return sync_service.push(db, body.records, me.name)


@router.get("/sync/pull", response_model=PullResponse, tags=["sync"])
def sync_pull(
    since: int = Query(0, ge=0),
    limit: int = Query(500, ge=1, le=500),
    me: Device = Depends(current_device),
    db: Session = Depends(get_db),
) -> PullResponse:
    records, cursor, has_more = sync_service.pull(db, since, min(limit, get_settings().sync_page_size))
    return PullResponse(records=records, cursor=cursor, has_more=has_more)


@router.get("/recipes/{recipe_id}/history", response_model=list[HistoryEntry], tags=["sync"])
def recipe_history(recipe_id: uuid.UUID, me: Device = Depends(current_device), db: Session = Depends(get_db)) -> list[HistoryEntry]:
    rows = db.execute(
        select(RecipeHistory).where(RecipeHistory.recipe_id == str(recipe_id)).order_by(RecipeHistory.saved_at.desc(), RecipeHistory.id.desc())
    ).scalars()
    return [HistoryEntry(updated_at=h.updated_at, saved_at=h.saved_at, replaced_by_device=h.replaced_by_device, data=h.data) for h in rows]


# --- Dateien -----------------------------------------------------------------

def _check_sha(sha: str) -> str:
    sha = sha.lower()
    if not _SHA.match(sha):
        raise HTTPException(400, "Ungültiger SHA-256.")
    return sha


async def _read_limited(request: Request) -> bytes:
    limit = get_settings().max_upload_mb * 1024 * 1024
    chunks, size = [], 0
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit:
            raise HTTPException(413, f"Datei größer als {get_settings().max_upload_mb} MB.")
        chunks.append(chunk)
    return b"".join(chunks)


@router.head("/files/{sha}", tags=["files"])
def file_exists(sha: str, me: Device = Depends(current_device)) -> Response:
    return Response(status_code=200 if files.exists(_check_sha(sha)) else 404)


@router.put("/files/{sha}", tags=["files"])
async def upload_file(sha: str, request: Request, me: Device = Depends(current_device)) -> dict:
    sha = _check_sha(sha)
    if files.exists(sha):
        return {"sha256": sha, "stored": False}
    data = await _read_limited(request)
    try:
        img = files.store(data, expected_sha=sha)
    except files.InvalidImage as e:
        raise HTTPException(422, str(e)) from e
    return {"sha256": img.sha256, "stored": True, "mime": img.mime, "width": img.width, "height": img.height}


@router.get("/files/{sha}", tags=["files"])
def get_file(
    sha: str,
    size: str = Query("thumb", pattern="^(thumb|medium|original)$"),
    me: Device = Depends(current_device),
) -> FileResponse:
    path = files.path_for(_check_sha(sha), size)
    if path is None:
        raise HTTPException(404, "Datei nicht vorhanden.")
    media = "image/jpeg" if size != "original" else None
    return FileResponse(path, media_type=media, headers={"Cache-Control": "private, max-age=31536000, immutable"})


# --- Import ------------------------------------------------------------------

@router.post("/import/recipekeeper", tags=["import"])
async def import_recipekeeper(
    file: UploadFile = File(...),
    commit: bool = Query(False, description="false = Probelauf mit Bericht, true = importieren"),
    me: Device = Depends(current_device),
    db: Session = Depends(get_db),
) -> dict:
    data = await file.read()
    try:
        plan = recipekeeper.plan(db, data)
    except recipekeeper.ImportError_ as e:
        raise HTTPException(422, str(e)) from e
    result = {"dry_run": not commit, "sha256": hashlib.sha256(data).hexdigest(), "report": plan.report}
    if commit:
        result["result"] = recipekeeper.commit(db, plan)
    return result
