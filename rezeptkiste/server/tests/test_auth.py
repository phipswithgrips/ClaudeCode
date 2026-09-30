from .conftest import login


def test_health(app_client):
    assert app_client.get("/health").json() == {"status": "ok"}


def test_login_and_token(app_client):
    headers = login(app_client)
    devices = app_client.get("/auth/devices", headers=headers).json()
    assert len(devices) == 1 and devices[0]["current"] is True
    assert devices[0]["name"] == "Pixel 9 Pro"


def test_wrong_password(app_client):
    r = app_client.post("/auth/device", json={"username": "admin", "password": "falsch", "device_name": "x"})
    assert r.status_code == 401


def test_rate_limit(app_client):
    for _ in range(5):
        app_client.post("/auth/device", json={"username": "admin", "password": "falsch", "device_name": "x"})
    r = app_client.post("/auth/device", json={"username": "admin", "password": "geheim-123", "device_name": "x"})
    assert r.status_code == 429


def test_endpoints_need_token(app_client):
    assert app_client.get("/sync/pull").status_code == 401
    assert app_client.get("/sync/pull", headers={"Authorization": "Bearer unsinn"}).status_code == 401


def test_revoke_device(app_client):
    pixel = login(app_client, "Pixel")
    windows = login(app_client, "Windows")
    win_id = next(d["id"] for d in app_client.get("/auth/devices", headers=pixel).json() if d["name"] == "Windows")
    assert app_client.delete(f"/auth/devices/{win_id}", headers=pixel).status_code == 204
    assert app_client.get("/sync/pull", headers=windows).status_code == 401
    assert app_client.get("/sync/pull", headers=pixel).status_code == 200


def test_status_page(app_client):
    r = app_client.get("/")
    assert r.status_code == 200 and "Der Server läuft" in r.text and "verbunden" in r.text
