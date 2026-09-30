"""Vergabe der server_rev innerhalb einer Transaktion."""

from sqlalchemy import select
from sqlalchemy.orm import Session

from .models import RevCounter


class RevAllocator:
    """Sperrt den Zähler beim ersten Aufruf und vergibt fortlaufende Nummern.

    Der Zählerstand wird mit der Transaktion geschrieben; die Sperre hält bis
    zum Commit. Parallele Schreibvorgänge warten deshalb aufeinander.
    """

    def __init__(self, db: Session):
        self.db = db
        self._row: RevCounter | None = None

    def lock(self) -> None:
        """Zähler sperren. Außerhalb von Savepoints aufrufen, damit die Sperre
        nicht mit einem zurückgerollten Savepoint verloren geht."""
        if self._row is None:
            self._row = self.db.execute(
                select(RevCounter).where(RevCounter.id == 1).with_for_update()
            ).scalar_one()

    def next(self) -> int:
        self.lock()
        self._row.value += 1
        return self._row.value

    def current(self, db: Session | None = None) -> int:
        return (db or self.db).execute(select(RevCounter.value).where(RevCounter.id == 1)).scalar_one()
