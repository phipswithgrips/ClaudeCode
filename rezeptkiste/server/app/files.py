"""Bildablage nach SHA-256 mit abgeleiteten Größen."""

import hashlib
import io
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageOps

from .config import get_settings

SIZES = {"thumb": 400, "medium": 1200}
MIME_BY_FORMAT = {"JPEG": "image/jpeg", "PNG": "image/png", "WEBP": "image/webp", "GIF": "image/gif"}


class InvalidImage(ValueError):
    pass


@dataclass
class StoredImage:
    sha256: str
    mime: str
    width: int
    height: int


def _root() -> Path:
    return Path(get_settings().files_dir)


def original_path(sha: str) -> Path:
    return _root() / "original" / sha[:2] / sha[2:4] / sha


def derived_path(sha: str, size: str) -> Path:
    return _root() / size / sha[:2] / sha[2:4] / f"{sha}.jpg"


def exists(sha: str) -> bool:
    return original_path(sha).is_file()


def _atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".tmp-")
    with os.fdopen(fd, "wb") as f:
        f.write(data)
    os.replace(tmp, path)


def inspect(data: bytes) -> tuple[str, int, int]:
    try:
        with Image.open(io.BytesIO(data)) as img:
            img.verify()
        with Image.open(io.BytesIO(data)) as img:
            fmt = img.format or ""
            img = ImageOps.exif_transpose(img)
            w, h = img.size
    except Exception as e:  # noqa: BLE001
        raise InvalidImage("Keine lesbare Bilddatei.") from e
    mime = MIME_BY_FORMAT.get(fmt)
    if mime is None:
        raise InvalidImage(f"Bildformat {fmt or 'unbekannt'} wird nicht unterstützt.")
    return mime, w, h


def store(data: bytes, expected_sha: str | None = None) -> StoredImage:
    sha = hashlib.sha256(data).hexdigest()
    if expected_sha is not None and sha != expected_sha.lower():
        raise InvalidImage("SHA-256 stimmt nicht mit dem Inhalt überein.")
    mime, w, h = inspect(data)
    if not exists(sha):
        _atomic_write(original_path(sha), data)
    for size in SIZES:
        if not derived_path(sha, size).is_file():
            _make_derived(sha, data, size)
    return StoredImage(sha, mime, w, h)


def _make_derived(sha: str, data: bytes, size: str) -> None:
    edge = SIZES[size]
    with Image.open(io.BytesIO(data)) as img:
        img = ImageOps.exif_transpose(img)
        if img.mode not in ("RGB", "L"):
            bg = Image.new("RGB", img.size, (18, 18, 18))  # dunkler Hintergrund für Transparenz
            bg.paste(img.convert("RGBA"), mask=img.convert("RGBA").split()[-1])
            img = bg
        img.thumbnail((edge, edge), Image.Resampling.LANCZOS)
        buf = io.BytesIO()
        img.convert("RGB").save(buf, "JPEG", quality=85, optimize=True, progressive=True)
    _atomic_write(derived_path(sha, size), buf.getvalue())


def path_for(sha: str, size: str) -> Path | None:
    if not exists(sha):
        return None
    if size == "original":
        return original_path(sha)
    p = derived_path(sha, size)
    if not p.is_file():
        _make_derived(sha, original_path(sha).read_bytes(), size)
    return p
