"""Erststart: Zähler, Benutzerkonto und vorbelegte Gänge anlegen."""

import logging
import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from . import hlc
from .auth import hash_password
from .config import get_settings
from .models import Course, RevCounter, UserAccount
from .revs import RevAllocator

log = logging.getLogger(__name__)

# Feste IDs, damit zwei Geräte beim ersten Start nicht doppelte Gänge erzeugen
COURSE_NAMESPACE = uuid.UUID("6f1c2a0e-5d4b-4c1e-9b7a-2f3e4d5c6b7a")
DEFAULT_COURSES = ["Frühstück", "Vorspeise", "Hauptgericht", "Beilage", "Dessert", "Snack", "Getränk"]


def default_course_id(name: str) -> str:
    return str(uuid.uuid5(COURSE_NAMESPACE, name))


def bootstrap(db: Session) -> None:
    settings = get_settings()

    if db.get(RevCounter, 1) is None:
        db.add(RevCounter(id=1, value=0))
        db.flush()

    if db.execute(select(UserAccount)).first() is None:
        if not settings.admin_password:
            raise RuntimeError("RK_ADMIN_PASSWORD ist nicht gesetzt; Erststart nicht möglich.")
        db.add(UserAccount(username=settings.admin_user, password_hash=hash_password(settings.admin_password)))
        log.info("Benutzerkonto %s angelegt", settings.admin_user)

        revs = RevAllocator(db)
        for i, name in enumerate(DEFAULT_COURSES):
            cid = default_course_id(name)
            if db.get(Course, cid) is None:
                db.add(Course(id=cid, name=name, sort_order=i, updated_at=hlc.now(), deleted=False, server_rev=revs.next()))

    db.commit()
