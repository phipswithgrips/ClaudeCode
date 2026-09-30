import threading
import uuid

from fastapi.testclient import TestClient

from app.bootstrap import DEFAULT_COURSES

from .conftest import login


def ts(ms: int, node="pixel", c=0) -> str:
    return f"{ms:015d}-{c:05d}-{node}"


def recipe(rid, updated, title="Soba", deleted=False, **data):
    return {"type": "recipe", "id": rid, "updated_at": updated, "deleted": deleted, "data": {"title": title, **data}}


def push(client, auth, *records):
    r = client.post("/sync/push", json={"records": list(records)}, headers=auth)
    assert r.status_code == 200, r.text
    return r.json()


def pull_all(client, auth, since=0):
    out = []
    while True:
        r = client.get("/sync/pull", params={"since": since, "limit": 3}, headers=auth).json()
        out += r["records"]
        since = r["cursor"]
        if not r["has_more"]:
            return out, since


def test_first_pull_contains_default_courses(app_client, auth):
    records, cursor = pull_all(app_client, auth)
    assert [r["data"]["name"] for r in records if r["type"] == "course"] == DEFAULT_COURSES
    assert cursor == len(DEFAULT_COURSES)


def test_push_and_pull(app_client, auth):
    rid = str(uuid.uuid4())
    res = push(app_client, auth, recipe(rid, ts(1000), rating=5, category_ids=["a"]))
    assert len(res["accepted"]) == 1 and not res["rejected"] and not res["errors"]
    _, cursor = pull_all(app_client, auth)
    windows = login(app_client, "Windows")
    got = app_client.get("/sync/pull", params={"since": cursor - 1}, headers=windows).json()["records"]
    assert got[0]["id"] == rid and got[0]["data"]["rating"] == 5 and got[0]["data"]["category_ids"] == ["a"]


def test_newer_wins_older_rejected_and_history(app_client, auth):
    rid = str(uuid.uuid4())
    push(app_client, auth, recipe(rid, ts(2000), "Fassung Pixel"))
    res = push(app_client, auth, recipe(rid, ts(3000, "windows"), "Fassung Windows"))
    assert res["accepted"]
    res = push(app_client, auth, recipe(rid, ts(2500), "zu alt"))
    assert res["rejected"][0]["data"]["title"] == "Fassung Windows"
    hist = app_client.get(f"/recipes/{rid}/history", headers=auth).json()
    assert [h["data"]["title"] for h in hist] == ["Fassung Pixel"]


def test_resend_same_version_is_idempotent(app_client, auth):
    rid = str(uuid.uuid4())
    a = push(app_client, auth, recipe(rid, ts(5000)))["accepted"][0]["server_rev"]
    b = push(app_client, auth, recipe(rid, ts(5000)))["accepted"][0]["server_rev"]
    assert a == b


def test_tombstone(app_client, auth):
    rid = str(uuid.uuid4())
    push(app_client, auth, recipe(rid, ts(1000)))
    push(app_client, auth, recipe(rid, ts(2000), deleted=True))
    records, _ = pull_all(app_client, auth)
    assert next(r for r in records if r["id"] == rid)["deleted"] is True
    # Später auf anderem Gerät bearbeitet: Rezept kommt zurück
    push(app_client, auth, recipe(rid, ts(3000, "windows"), "wieder da"))
    records, _ = pull_all(app_client, auth)
    r = next(r for r in records if r["id"] == rid)
    assert r["deleted"] is False and r["data"]["title"] == "wieder da"


def test_invalid_records_do_not_block_others(app_client, auth):
    good = str(uuid.uuid4())
    res = push(
        app_client, auth,
        recipe(str(uuid.uuid4()), ts(1000), rating=9),
        recipe(good, ts(1000)),
    )
    assert len(res["errors"]) == 1 and res["accepted"][0]["id"] == good


def test_bad_hlc_rejected(app_client, auth):
    r = app_client.post("/sync/push", json={"records": [recipe(str(uuid.uuid4()), "2026-09-30")]}, headers=auth)
    assert r.status_code == 422


def test_pull_pagination_is_complete(app_client, auth):
    ids = {str(uuid.uuid4()) for _ in range(10)}
    push(app_client, auth, *[recipe(i, ts(1000)) for i in ids])
    records, _ = pull_all(app_client, auth)
    assert ids <= {r["id"] for r in records}
    revs = [r["server_rev"] for r in records]
    assert revs == sorted(revs) and len(revs) == len(set(revs))


def test_parallel_pushes_get_unique_revisions(app_client):
    tokens = [login(app_client, f"g{i}") for i in range(4)]
    errors = []

    def worker(headers):
        try:
            c = TestClient(app_client.app)
            for _ in range(5):
                r = c.post("/sync/push", json={"records": [recipe(str(uuid.uuid4()), ts(1000))]}, headers=headers)
                assert r.status_code == 200
        except Exception as e:  # noqa: BLE001
            errors.append(e)

    threads = [threading.Thread(target=worker, args=(t,)) for t in tokens]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert not errors
    records, cursor = pull_all(app_client, tokens[0])
    revs = [r["server_rev"] for r in records]
    assert len(revs) == len(set(revs)) == len(DEFAULT_COURSES) + 20
    assert cursor == max(revs)
