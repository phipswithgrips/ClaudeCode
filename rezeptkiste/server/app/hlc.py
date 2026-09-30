"""Hybrid Logical Clock als sortierbarer String.

Format: ``<millis:15>-<zähler:05>-<knoten>``, z. B. ``001759216800000-00003-pixel``.
Zeichenkettenvergleich entspricht zeitlicher Reihenfolge. Clients erzeugen
dasselbe Format; der Server vergleicht nur, er rechnet nicht damit.
"""

import re
import threading
import time

PATTERN = re.compile(r"^\d{15}-\d{5}-[A-Za-z0-9_.]{1,32}$")

_lock = threading.Lock()
_last = (0, 0)


def is_valid(value: str) -> bool:
    return bool(PATTERN.match(value))


def now(node: str = "server") -> str:
    """Nächster Zeitstempel dieses Knotens, streng monoton."""
    global _last
    with _lock:
        ms = int(time.time() * 1000)
        last_ms, last_c = _last
        if ms > last_ms:
            _last = (ms, 0)
        else:
            _last = (last_ms, last_c + 1)
        ms, c = _last
    return f"{ms:015d}-{c:05d}-{node}"
