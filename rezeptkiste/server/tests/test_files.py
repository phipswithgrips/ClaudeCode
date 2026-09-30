import hashlib

from .conftest import jpeg_bytes


def test_upload_head_get(app_client, auth):
    data = jpeg_bytes(size=(2000, 1000))
    sha = hashlib.sha256(data).hexdigest()
    assert app_client.head(f"/files/{sha}", headers=auth).status_code == 404
    r = app_client.put(f"/files/{sha}", content=data, headers=auth)
    assert r.status_code == 200 and r.json()["width"] == 2000
    assert app_client.head(f"/files/{sha}", headers=auth).status_code == 200
    assert app_client.put(f"/files/{sha}", content=data, headers=auth).json()["stored"] is False

    from io import BytesIO

    from PIL import Image

    thumb = app_client.get(f"/files/{sha}", params={"size": "thumb"}, headers=auth)
    assert Image.open(BytesIO(thumb.content)).size == (400, 200)
    orig = app_client.get(f"/files/{sha}", params={"size": "original"}, headers=auth)
    assert orig.content == data


def test_hash_mismatch(app_client, auth):
    r = app_client.put(f"/files/{'0' * 64}", content=jpeg_bytes(), headers=auth)
    assert r.status_code == 422


def test_not_an_image(app_client, auth):
    data = b"kein bild"
    r = app_client.put(f"/files/{hashlib.sha256(data).hexdigest()}", content=data, headers=auth)
    assert r.status_code == 422
