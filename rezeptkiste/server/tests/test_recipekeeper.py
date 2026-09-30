import io
import zipfile

from app.recipekeeper import build_name_map, iso_minutes, servings_count

from .conftest import jpeg_bytes


def rk_recipe(rid, name, course="", categories=(), collections=(), yield_="", prep="PT0S", cook="PT0S",
              ingredients=(), directions=(), notes=(), rating=0, fav="False", photos=(), nutrition=None, source=""):
    cats = "".join(f'<meta content="{c}" itemprop="recipeCategory">' for c in categories)
    cols = "".join(f'<meta content="{c}" itemprop="recipeCollection">' for c in collections)
    nut = "".join(f'<meta content="{v}" itemprop="{k}">' for k, v in (nutrition or {}).items())
    ps = lambda lines: "".join(f"<p>{x}</p>" for x in lines)  # noqa: E731
    main = f'<img src="{photos[0]}" class="recipe-photo">' if photos else ""
    gallery = "".join(f'<img src="{p}" class="recipe-photos" itemprop="photo{i}">' for i, p in enumerate(photos))
    return f"""<div class="recipe-details">
<meta content="{rid}" itemprop="recipeId"><meta content="" itemprop="recipeShareId">
<meta content="{fav}" itemprop="recipeIsFavourite"><meta content="{rating}" itemprop="recipeRating">
<table><tr><td><h2 itemprop="name">{name}</h2>
<div>Rezeptarten: <span itemprop="recipeCourse">{course}</span></div>
<div>Kategorien: <span>{', '.join(categories)}</span>{cats}</div>
<div>Sammlungen: <span>{', '.join(collections)}</span>{cols}</div>
<div>Quelle: <span itemprop="recipeSource">{source}</span></div>
<div>Portionsgröße: <span itemprop="recipeYield">{yield_}</span></div>
<div>Arbeitszeit: <span></span><meta content="{prep}" itemprop="prepTime"></div>
<div>Kochzeit: <span></span><meta content="{cook}" itemprop="cookTime"></div>{nut}
{main}</td></tr><tr><td><div class="recipe-ingredients" itemprop="recipeIngredients">{ps(ingredients)}</div></td>
<td><div itemprop="recipeDirections">{ps(directions)}</div></td></tr></table>
<div class="recipe-notes" itemprop="recipeNotes">{ps(notes)}</div>
<h3>Fotos</h3><div class="recipe-photos-div">{gallery}</div><hr></div>"""


def make_zip() -> bytes:
    body = "".join([
        rk_recipe("rk-1", "Limetten-Sorbet", course="Dessert", categories=["Sorbet", "Eis"], collections=["Patisserie"],
                  yield_="4 PORTIONEN", prep="PT15M", cook="PT1H30M", rating=5, fav="True",
                  ingredients=["Sud:", "250 g Zucker", "", "", "Olivenöl 75 g"],
                  directions=["Zucker kochen", "", "Abkühlen"], notes=["Hält 2 Wochen"],
                  photos=["images/rk-1_0.jpg", "images/rk-1_1.jpg"],
                  nutrition={"recipeNutServingSize": "1 Portion", "recipeNutCalories": "210", "recipeNutSodium": "5"},
                  source="https://example.org/sorbet"),
        rk_recipe("rk-2", "Schokoeis", categories=["eis", "Dessert"], yield_="1 Kastenform 25 cm", source="Ein Kochbuch"),
        rk_recipe("rk-3", "Mousse", course="dessert", categories=["Desser"], photos=["images/fehlt.jpg"]),
    ])
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("recipes.html", f"<html><body>{body}</body></html>")
        z.writestr("images/rk-1_0.jpg", jpeg_bytes((10, 10, 10)))
        z.writestr("images/rk-1_1.jpg", jpeg_bytes((20, 20, 20)))
    return buf.getvalue()


def upload(client, auth, commit):
    r = client.post("/import/recipekeeper", params={"commit": commit}, files={"file": ("rk.zip", make_zip())}, headers=auth)
    assert r.status_code == 200, r.text
    return r.json()


def test_helpers():
    assert iso_minutes("PT1H30M") == 90 and iso_minutes("PT0S") is None and iso_minutes("") is None
    assert servings_count("4 PORTIONEN") == 4 and servings_count("14") == 14
    assert servings_count("1 Kastenform 25 cm") is None


def test_name_merging():
    from collections import Counter

    mapping, merges = build_name_map(Counter({"Dessert": 3, "dessert": 1, "Desser": 1, "Dessert Deko": 1, "Eis": 2}), {})
    assert mapping["dessert"] == mapping["Desser"] == "Dessert"
    assert mapping["Dessert Deko"] == "Dessert Deko" and mapping["Eis"] == "Eis"
    assert merges == [{"into": "Dessert", "merged": ["Desser", "dessert"]}]

    # Fall aus dem echten Export: nur Kleinschreibung und Tippfehler, je einmal
    mapping, merges = build_name_map(Counter({"Desser": 1, "dessert": 1}), {})
    assert mapping["Desser"] == mapping["dessert"] == "Dessert"
    assert merges == [{"into": "Dessert", "merged": ["Desser", "dessert"]}]


def test_dry_run_writes_nothing(app_client, auth):
    res = upload(app_client, auth, False)
    rep = res["report"]
    assert res["dry_run"] and rep["recipes_new"] == 3 and rep["photos"] == 2
    assert rep["new_collections"] == ["Patisserie"]
    assert "Dessert" not in rep["new_courses"]  # vorbelegter Gang wird wiederverwendet
    assert sorted(rep["new_categories"]) == ["Dessert", "Eis", "Sorbet"]
    assert any("fehlt" in w["message"] for w in rep["warnings"])
    records = app_client.get("/sync/pull", headers=auth).json()["records"]
    assert all(r["type"] == "course" for r in records)


def test_commit_and_reimport(app_client, auth):
    res = upload(app_client, auth, True)
    assert res["result"] == {"recipes_imported": 3, "photos_imported": 2}
    records = app_client.get("/sync/pull", headers=auth).json()["records"]
    by_type = {}
    for r in records:
        by_type.setdefault(r["type"], []).append(r)
    names = lambda t: {r["id"]: r["data"]["name"] for r in by_type[t]}  # noqa: E731
    cats, courses, cols = names("category"), names("course"), names("collection")

    sorbet = next(r["data"] for r in by_type["recipe"] if r["data"]["title"] == "Limetten-Sorbet")
    assert sorbet["ingredients_text"] == "Sud:\n250 g Zucker\n\nOlivenöl 75 g"
    assert sorbet["directions_text"] == "Zucker kochen\nAbkühlen"
    assert sorbet["notes"] == "Hält 2 Wochen"
    assert (sorbet["prep_min"], sorbet["cook_min"], sorbet["total_min"]) == (15, 90, 105)
    assert sorbet["servings_count"] == 4 and sorbet["rating"] == 5 and sorbet["is_favourite"] is True
    assert sorbet["source_url"] == "https://example.org/sorbet" and sorbet["source_name"] is None
    assert sorbet["nutrition"] == {"serving_size": "1 Portion", "kcal": "210", "sodium": "5"}
    assert [cols[i] for i in sorbet["collection_ids"]] == ["Patisserie"]
    assert [courses[i] for i in sorbet["course_ids"]] == ["Dessert"]

    schoko = next(r["data"] for r in by_type["recipe"] if r["data"]["title"] == "Schokoeis")
    assert sorted(cats[i] for i in schoko["category_ids"]) == ["Dessert", "Eis"]
    assert schoko["source_name"] == "Ein Kochbuch" and schoko["servings_count"] is None

    mousse = next(r["data"] for r in by_type["recipe"] if r["data"]["title"] == "Mousse")
    assert [cats[i] for i in mousse["category_ids"]] == ["Dessert"]
    assert [courses[i] for i in mousse["course_ids"]] == ["Dessert"]

    photos = sorted(by_type["photo"], key=lambda p: p["data"]["sort_order"])
    assert len(photos) == 2 and photos[0]["data"]["width"] == 900
    sha = photos[0]["data"]["sha256"]
    assert app_client.head(f"/files/{sha}", headers=auth).status_code == 200

    again = upload(app_client, auth, True)
    assert again["report"]["recipes_already_imported"] == 3 and again["result"]["recipes_imported"] == 0


def test_not_a_recipekeeper_zip(app_client, auth):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("foo.txt", "x")
    r = app_client.post("/import/recipekeeper", files={"file": ("x.zip", buf.getvalue())}, headers=auth)
    assert r.status_code == 422
