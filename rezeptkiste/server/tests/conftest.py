import io
import os

import pytest

os.environ.setdefault("RK_DATABASE_URL", "postgresql+psycopg://postgres@/rezeptkiste_test?host=/tmp/pg&port=5433")
os.environ["RK_ADMIN_USER"] = "admin"
os.environ["RK_ADMIN_PASSWORD"] = "geheim-123"

from fastapi.testclient import TestClient  # noqa: E402
from PIL import Image  # noqa: E402

from app import db as dbmod  # noqa: E402
from app.auth import login_limiter  # noqa: E402
from app.bootstrap import bootstrap  # noqa: E402
from app.config import get_settings  # noqa: E402
from app.main import create_app  # noqa: E402
from app.models import Base  # noqa: E402


@pytest.fixture()
def app_client(tmp_path, monkeypatch):
    monkeypatch.setenv("RK_FILES_DIR", str(tmp_path / "files"))
    get_settings.cache_clear()
    engine = dbmod.init_engine(os.environ["RK_DATABASE_URL"])
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    with dbmod.session() as s:
        bootstrap(s)
    login_limiter.reset()
    client = TestClient(create_app(run_bootstrap=False))
    yield client
    engine.dispose()


def login(client, name="Pixel 9 Pro") -> dict:
    r = client.post("/auth/device", json={"username": "admin", "password": "geheim-123", "device_name": name})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['token']}"}


@pytest.fixture()
def auth(app_client):
    return login(app_client)


def jpeg_bytes(color=(200, 80, 20), size=(900, 600)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, color).save(buf, "JPEG")
    return buf.getvalue()
