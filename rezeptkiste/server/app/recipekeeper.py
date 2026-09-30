"""Import des Recipe-Keeper-Exports (ZIP mit recipes.html und images/).

Ablauf: parse_zip() -> plan() liefert Bericht und Importplan -> commit() schreibt.
"""

from __future__ import annotations

import difflib
import io
import re
import uuid
import zipfile
from collections import Counter
from dataclasses import dataclass, field
from posixpath import normpath

from bs4 import BeautifulSoup, Tag
from sqlalchemy import select
from sqlalchemy.orm import Session

from . import files, hlc
from .models import Category, Collection, Course, Photo, Recipe
from .revs import RevAllocator

IMPORT_NAMESPACE = uuid.UUID("0b8f4f7e-2a6c-4d5e-8f10-7c9d3e2b1a64")

# recipeNut* -> Schlüssel in recipe.nutrition
NUTRITION_KEYS = {
    "recipeNutServingSize": "serving_size",
    "recipeNutCalories": "kcal",
    "recipeNutTotalFat": "fat",
    "recipeNutSaturatedFat": "saturated_fat",
    "recipeNutCholesterol": "cholesterol",
    "recipeNutSodium": "sodium",
    "recipeNutTotalCarbohydrate": "carbohydrates",
    "recipeNutDietaryFiber": "fiber",
    "recipeNutSugars": "sugar",
    "recipeNutProtein": "protein",
}

_DURATION = re.compile(r"^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$")
_SERVINGS = re.compile(r"^(?:ca\.\s*)?(\d{1,3})\s*(portionen|portion|personen|person|stück|stk\.?)?$", re.I)
_URL = re.compile(r"^https?://\S+$", re.I)


class ImportError_(ValueError):
    pass


# --- Parsen ------------------------------------------------------------------

@dataclass
class RkRecipe:
    rk_id: str
    title: str
    courses: list[str]
    categories: list[str]
    collections: list[str]
    source: str | None
    servings_text: str | None
    prep_min: int | None
    cook_min: int | None
    ingredients: str | None
    directions: str | None
    notes: str | None
    rating: int
    favourite: bool
    nutrition: dict | None
    photo_paths: list[str] = field(default_factory=list)


def iso_minutes(value: str | None) -> int | None:
    if not value:
        return None
    m = _DURATION.match(value.strip())
    if not m:
        return None
    d, h, mi, s = (float(x) if x else 0 for x in m.groups())
    total = int(round(d * 1440 + h * 60 + mi + s / 60))
    return total or None


def servings_count(text: str | None) -> int | None:
    if not text:
        return None
    m = _SERVINGS.match(text.strip())
    return int(m.group(1)) if m else None


def _clean(text: str) -> str:
    return " ".join(text.replace("\xa0", " ").split())


def _lines(container: Tag | None) -> list[str]:
    if container is None:
        return []
    paras = container.find_all("p")
    if not paras:
        text = container.get_text("\n")
        return [_clean(x) for x in text.split("\n")]
    return [_clean(p.get_text(" ")) for p in paras]


def _ingredients_text(lines: list[str]) -> str | None:
    """Leerzeilen bleiben als Gruppentrenner, mehrere werden zu einer."""
    out: list[str] = []
    for line in lines:
        if not line:
            if out and out[-1] != "":
                out.append("")
        else:
            out.append(line)
    while out and out[-1] == "":
        out.pop()
    return "\n".join(out) or None


def _directions_text(lines: list[str]) -> str | None:
    """Recipe Keeper trennt Schritte mit leeren Absätzen; jeder Schritt wird eine Zeile."""
    return "\n".join(line for line in lines if line) or None


def _meta(tag: Tag, prop: str) -> str | None:
    el = tag.find("meta", attrs={"itemprop": prop})
    return el.get("content") if el else None


def _metas(tag: Tag, prop: str) -> list[str]:
    return [m.get("content", "").strip() for m in tag.find_all("meta", attrs={"itemprop": prop}) if m.get("content", "").strip()]


def _span(tag: Tag, prop: str) -> str | None:
    el = tag.find(attrs={"itemprop": prop})
    if el is None:
        return None
    return _clean(el.get_text(" ")) or None


def parse_recipe(div: Tag) -> RkRecipe:
    rk_id = _meta(div, "recipeId")
    name = div.find(attrs={"itemprop": "name"})
    if not rk_id or name is None:
        raise ImportError_("Rezept ohne ID oder Titel")

    course_el = div.find(attrs={"itemprop": "recipeCourse"})
    courses = []
    if course_el is not None:
        courses = [c for c in (_clean(x) for x in course_el.get_text().split(",")) if c]

    nutrition = {}
    for prop, key in NUTRITION_KEYS.items():
        v = _meta(div, prop)
        if v not in (None, ""):
            nutrition[key] = v
    for m in div.find_all("meta", attrs={"itemprop": re.compile(r"^recipeNut")}):
        prop = m["itemprop"]
        if prop not in NUTRITION_KEYS and m.get("content"):
            nutrition[prop[len("recipeNut"):].lower()] = m["content"]

    # Fotos: photo0, photo1 ... in Reihenfolge; Titelbild (recipe-photo) zuerst
    numbered = []
    for img in div.find_all("img", attrs={"itemprop": re.compile(r"^photo\d+$")}):
        numbered.append((int(img["itemprop"][5:]), img.get("src", "")))
    paths = [src for _, src in sorted(numbered) if src]
    main = div.find("img", class_="recipe-photo")
    if main is not None and main.get("src") and main["src"] not in paths:
        paths.insert(0, main["src"])

    try:
        rating = max(0, min(5, int(float(_meta(div, "recipeRating") or 0))))
    except ValueError:
        rating = 0

    return RkRecipe(
        rk_id=rk_id.strip(),
        title=_clean(name.get_text(" ")),
        courses=courses,
        categories=_metas(div, "recipeCategory"),
        collections=_metas(div, "recipeCollection"),
        source=_span(div, "recipeSource"),
        servings_text=_span(div, "recipeYield"),
        prep_min=iso_minutes(_meta(div, "prepTime")),
        cook_min=iso_minutes(_meta(div, "cookTime")),
        ingredients=_ingredients_text(_lines(div.find(attrs={"itemprop": "recipeIngredients"}))),
        directions=_directions_text(_lines(div.find(attrs={"itemprop": "recipeDirections"}))),
        notes=_ingredients_text(_lines(div.find(attrs={"itemprop": "recipeNotes"}))),
        rating=rating,
        favourite=(_meta(div, "recipeIsFavourite") or "").strip().lower() == "true",
        nutrition=nutrition or None,
        photo_paths=paths,
    )


def parse_zip(data: bytes) -> tuple[list[RkRecipe], dict[str, bytes]]:
    try:
        zf = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile as e:
        raise ImportError_("Die Datei ist kein ZIP-Archiv.") from e
    with zf:
        names = {normpath(n.replace("\\", "/")).lstrip("/"): n for n in zf.namelist() if not n.endswith("/")}
        html_name = next((n for n in names if n.rsplit("/", 1)[-1].lower() == "recipes.html"), None)
        if html_name is None:
            raise ImportError_("recipes.html fehlt; ist das ein Export aus Recipe Keeper?")
        base = html_name.rsplit("/", 1)[0] + "/" if "/" in html_name else ""
        soup = BeautifulSoup(zf.read(names[html_name]).decode("utf-8", errors="replace"), "html.parser")
        recipes = [parse_recipe(div) for div in soup.find_all("div", class_="recipe-details")]
        wanted = {normpath(base + p) for r in recipes for p in r.photo_paths}
        images = {p: zf.read(names[p]) for p in wanted if p in names}
        # Pfade in den Rezepten auf ZIP-Pfade umstellen
        for r in recipes:
            r.photo_paths = [normpath(base + p) for p in r.photo_paths]
    return recipes, images


# --- Zusammenführen ähnlicher Namen -------------------------------------------

def _similar(a: str, b: str) -> bool:
    a, b = a.casefold(), b.casefold()
    if a == b:
        return True
    if min(len(a), len(b)) < 5:
        return False
    return difflib.SequenceMatcher(None, a, b).ratio() >= 0.9


def build_name_map(counts: Counter, existing: dict[str, str]) -> tuple[dict[str, str], list[dict]]:
    """Ordnet jeden Namen aus dem Import einem kanonischen Namen zu.

    existing: kanonischer Name -> ID bereits vorhandener Einträge.
    Rückgabe: Name -> kanonischer Name, Liste der Zusammenführungen.
    """
    clusters: list[list[str]] = []
    for name in list(existing) + [n for n in counts if n not in existing]:
        for cl in clusters:
            if any(_similar(name, other) for other in cl):
                cl.append(name)
                break
        else:
            clusters.append([name])

    mapping: dict[str, str] = {}
    merges: list[dict] = []
    for cl in clusters:
        in_db = [n for n in cl if n in existing]
        if in_db:
            canon = in_db[0]
        else:
            # Häufigste Schreibweise; bei Gleichstand die längere (Tippfehler
            # verkürzen eher: "Desser" gegenüber "dessert"), dann Großschreibung.
            canon = sorted(cl, key=lambda n: (-counts[n], -len(n), not n[:1].isupper(), n))[0]
            if len(cl) > 1 and canon[:1].islower():
                canon = canon[:1].upper() + canon[1:]
        for n in cl:
            mapping[n] = canon
        variants = sorted(n for n in cl if n != canon and n in counts)
        if variants:
            merges.append({"into": canon, "merged": variants})
    return mapping, merges


# --- Plan und Import ----------------------------------------------------------

@dataclass
class Plan:
    recipes: list[RkRecipe]
    images: dict[str, bytes]
    skip_ids: set[str]
    maps: dict[str, dict[str, str]]
    existing: dict[str, dict[str, str]]
    report: dict


_LIST_MODELS = {"courses": Course, "categories": Category, "collections": Collection}


def _existing_names(db: Session, model) -> dict[str, str]:
    rows = db.execute(select(model.name, model.id).where(model.deleted.is_(False)).order_by(model.sort_order)).all()
    out: dict[str, str] = {}
    for name, id_ in rows:
        out.setdefault(name, id_)
    return out


def plan(db: Session, data: bytes) -> Plan:
    recipes, images = parse_zip(data)

    refs = {r.rk_id for r in recipes}
    already = set(db.execute(select(Recipe.import_ref).where(Recipe.import_ref.in_(refs))).scalars()) if refs else set()
    new = [r for r in recipes if r.rk_id not in already]

    maps, existing, new_names, merges = {}, {}, {}, {}
    for key, model in _LIST_MODELS.items():
        counts = Counter(n for r in new for n in getattr(r, key))
        existing[key] = _existing_names(db, model)
        mapping, merged = build_name_map(counts, existing[key])
        maps[key] = mapping
        merges[key] = merged
        new_names[key] = sorted({mapping[n] for n in counts} - set(existing[key]))

    warnings = []
    photos = 0
    for r in new:
        for p in r.photo_paths:
            if p in images:
                photos += 1
            else:
                warnings.append({"recipe": r.title, "message": f"Foto fehlt im ZIP: {p}"})
        if not r.ingredients and not r.directions:
            warnings.append({"recipe": r.title, "message": "Weder Zutaten noch Zubereitung"})

    report = {
        "recipes_in_file": len(recipes),
        "recipes_new": len(new),
        "recipes_already_imported": len(recipes) - len(new),
        "photos": photos,
        "new_courses": new_names["courses"],
        "new_categories": new_names["categories"],
        "new_collections": new_names["collections"],
        "merged": merges,
        "with_rating": sum(1 for r in new if r.rating),
        "favourites": sum(1 for r in new if r.favourite),
        "with_nutrition": sum(1 for r in new if r.nutrition),
        "warnings": warnings,
    }
    return Plan(recipes, images, already, maps, existing, report)


def recipe_uuid(rk_id: str) -> str:
    return str(uuid.uuid5(IMPORT_NAMESPACE, f"recipe:{rk_id}"))


def commit(db: Session, p: Plan) -> dict:
    revs = RevAllocator(db)
    revs.lock()

    # Gänge, Kategorien, Sammlungen anlegen
    ids: dict[str, dict[str, str]] = {}
    for key, model in _LIST_MODELS.items():
        ids[key] = dict(p.existing[key])
        order = len(ids[key])
        for canon in sorted(set(p.maps[key].values())):
            if canon not in ids[key]:
                new_id = str(uuid.uuid4())
                db.add(model(id=new_id, name=canon, sort_order=order, updated_at=hlc.now(), deleted=False, server_rev=revs.next()))
                ids[key][canon] = new_id
                order += 1

    def resolve(key: str, names: list[str]) -> list[str]:
        out: list[str] = []
        for n in names:
            i = ids[key][p.maps[key][n]]
            if i not in out:
                out.append(i)
        return out

    imported = photos = 0
    for r in p.recipes:
        if r.rk_id in p.skip_ids:
            continue
        rid = recipe_uuid(r.rk_id)
        if db.get(Recipe, rid) is not None:
            continue
        source_url = r.source if r.source and _URL.match(r.source) else None
        total = (r.prep_min or 0) + (r.cook_min or 0) or None
        db.add(Recipe(
            id=rid, updated_at=hlc.now(), deleted=False, server_rev=revs.next(),
            title=r.title, source_name=None if source_url else r.source, source_url=source_url,
            servings_text=r.servings_text, servings_count=servings_count(r.servings_text),
            prep_min=r.prep_min, cook_min=r.cook_min, total_min=total,
            ingredients_text=r.ingredients, directions_text=r.directions, notes=r.notes,
            nutrition=r.nutrition, rating=r.rating, is_favourite=r.favourite,
            course_ids=resolve("courses", r.courses), category_ids=resolve("categories", r.categories),
            collection_ids=resolve("collections", r.collections), import_ref=r.rk_id,
        ))
        order = 0
        for path in r.photo_paths:
            blob = p.images.get(path)
            if blob is None:
                continue
            try:
                img = files.store(blob)
            except files.InvalidImage:
                continue
            db.add(Photo(
                id=str(uuid.uuid5(IMPORT_NAMESPACE, f"photo:{r.rk_id}:{path}")),
                updated_at=hlc.now(), deleted=False, server_rev=revs.next(),
                recipe_id=rid, sort_order=order, sha256=img.sha256, mime=img.mime, width=img.width, height=img.height,
            ))
            order += 1
            photos += 1
        imported += 1
    db.commit()
    return {"recipes_imported": imported, "photos_imported": photos}
