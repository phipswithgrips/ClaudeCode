"""Passwort-Hashing, Geräte-Token und Anmeldebegrenzung."""

import hashlib
import secrets
import threading
import time
from collections import defaultdict, deque
from datetime import datetime, timezone

from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from .config import get_settings
from .db import get_db
from .models import Device

_hasher = PasswordHasher()
_bearer = HTTPBearer(auto_error=False)


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password_hash: str, password: str) -> bool:
    try:
        return _hasher.verify(password_hash, password)
    except VerifyMismatchError:
        return False
    except Exception:
        return False


def new_token() -> str:
    return secrets.token_urlsafe(32)  # 256 Bit


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


class LoginLimiter:
    """Höchstens N Anmeldeversuche pro Minute und IP (im Speicher)."""

    def __init__(self):
        self._hits: dict[str, deque] = defaultdict(deque)
        self._lock = threading.Lock()

    def check(self, ip: str) -> None:
        limit = get_settings().login_attempts_per_minute
        now = time.monotonic()
        with self._lock:
            q = self._hits[ip]
            while q and now - q[0] > 60:
                q.popleft()
            if len(q) >= limit:
                raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Zu viele Anmeldeversuche, bitte eine Minute warten.")
            q.append(now)

    def reset(self) -> None:
        with self._lock:
            self._hits.clear()


login_limiter = LoginLimiter()


def client_ip(request: Request) -> str:
    # Nginx Proxy Manager setzt X-Real-IP bzw. X-Forwarded-For
    fwd = request.headers.get("x-forwarded-for")
    if fwd:
        return fwd.split(",")[0].strip()
    return request.headers.get("x-real-ip") or (request.client.host if request.client else "unknown")


def current_device(
    creds: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: Session = Depends(get_db),
) -> Device:
    if creds is None or creds.scheme.lower() != "bearer":
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token fehlt.", headers={"WWW-Authenticate": "Bearer"})
    device = db.execute(select(Device).where(Device.token_hash == token_hash(creds.credentials))).scalar_one_or_none()
    if device is None or device.revoked:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token ungültig.", headers={"WWW-Authenticate": "Bearer"})
    now = datetime.now(timezone.utc)
    last = device.last_seen_at
    if last is None or (now - (last if last.tzinfo else last.replace(tzinfo=timezone.utc))).total_seconds() > 60:
        device.last_seen_at = now
        db.commit()
    return device
