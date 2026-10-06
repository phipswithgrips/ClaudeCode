package de.rezeptkiste.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.rezeptkiste.db.Label
import de.rezeptkiste.db.Photo
import de.rezeptkiste.db.Recipe
import de.rezeptkiste.db.RezeptDatabase
import de.rezeptkiste.db.Shopping_item
import de.rezeptkiste.sync.AppJson
import de.rezeptkiste.sync.EntityType
import de.rezeptkiste.sync.LabelData
import de.rezeptkiste.sync.PhotoData
import de.rezeptkiste.sync.RecipeData
import de.rezeptkiste.sync.ServerRecord
import de.rezeptkiste.sync.ShoppingItemData
import de.rezeptkiste.sync.SyncRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val idList = ListSerializer(String.serializer())

fun encodeIds(ids: List<String>): String = AppJson.encodeToString(idList, ids)

fun decodeIds(text: String?): List<String> =
    if (text.isNullOrBlank()) emptyList() else runCatching { AppJson.decodeFromString(idList, text) }.getOrDefault(emptyList())

private fun Boolean.toLong() = if (this) 1L else 0L

/** Lokale Datenbank: Lesen für die Oberfläche, Schreiben für den Sync. */
class Repository(private val db: RezeptDatabase) {

    // --- Lesen --------------------------------------------------------------------

    fun recipes(): Flow<List<Recipe>> = db.recipeQueries.selectVisible().asFlow().mapToList(Dispatchers.Default)

    fun labels(): Flow<List<Label>> = db.labelQueries.selectVisible().asFlow().mapToList(Dispatchers.Default)

    fun photos(): Flow<List<Photo>> = db.photoQueries.selectVisible().asFlow().mapToList(Dispatchers.Default)

    fun shoppingItems(): Flow<List<Shopping_item>> = db.shoppingItemQueries.selectVisible().asFlow().mapToList(Dispatchers.Default)

    fun shoppingItem(id: String): Shopping_item? = db.shoppingItemQueries.selectById(id).executeAsOneOrNull()

    fun saveShoppingItem(item: Shopping_item) {
        db.shoppingItemQueries.upsert(item.copy(dirty = 1))
    }

    fun recipe(id: String): Recipe? = db.recipeQueries.selectById(id).executeAsOneOrNull()

    // --- Einstellungen --------------------------------------------------------------

    fun setting(key: String): String? = db.kvQueries.get(key).executeAsOneOrNull()

    fun setSetting(key: String, value: String) {
        db.kvQueries.put(key, value)
    }

    fun removeSetting(key: String) {
        db.kvQueries.remove(key)
    }

    var cursor: Long
        get() = setting(KEY_CURSOR)?.toLongOrNull() ?: 0L
        set(value) {
            setSetting(KEY_CURSOR, value.toString())
        }

    /** Alles Lokale löschen, z. B. beim Abmelden. */
    fun clearAll() = db.transaction {
        db.recipeQueries.deleteAll()
        db.labelQueries.deleteAll()
        db.photoQueries.deleteAll()
        db.shoppingItemQueries.deleteAll()
        db.kvQueries.deleteAll()
    }

    // --- Lokale Änderungen -------------------------------------------------------------

    fun setFavourite(id: String, favourite: Boolean, updatedAt: String) {
        db.recipeQueries.setFavourite(favourite.toLong(), updatedAt, id)
    }

    /** Rezept lokal speichern; geht beim nächsten Sync an den Server. */
    fun saveRecipe(recipe: Recipe) {
        db.recipeQueries.upsert(recipe.copy(dirty = 1))
    }

    fun saveLabel(label: Label) {
        db.labelQueries.upsert(label.copy(dirty = 1))
    }

    fun label(id: String): Label? = db.labelQueries.selectById(id).executeAsOneOrNull()

    fun savePhoto(photo: Photo) {
        db.photoQueries.upsert(photo.copy(dirty = 1))
    }

    fun photo(id: String): Photo? = db.photoQueries.selectById(id).executeAsOneOrNull()

    fun transaction(block: () -> Unit) = db.transaction { block() }

    // --- Sync: Push --------------------------------------------------------------

    fun dirtyRecords(): List<SyncRecord> = buildList {
        db.labelQueries.selectDirty().executeAsList().forEach { add(it.toRecord()) }
        db.recipeQueries.selectDirty().executeAsList().forEach { add(it.toRecord()) }
        db.photoQueries.selectDirty().executeAsList().forEach { add(it.toRecord()) }
        db.shoppingItemQueries.selectDirty().executeAsList().forEach { add(it.toRecord()) }
    }

    /** Übernommen: dirty zurücksetzen, außer der Datensatz wurde inzwischen erneut geändert. */
    fun markAccepted(type: String, id: String, serverRev: Long, sentUpdatedAt: String) {
        when (type) {
            EntityType.RECIPE -> db.recipeQueries.markClean(serverRev, id, sentUpdatedAt)
            EntityType.PHOTO -> db.photoQueries.markClean(serverRev, id, sentUpdatedAt)
            EntityType.SHOPPING_ITEM -> db.shoppingItemQueries.markClean(serverRev, id, sentUpdatedAt)
            in EntityType.LABELS -> db.labelQueries.markClean(serverRev, id, sentUpdatedAt)
        }
    }

    // --- Sync: Pull --------------------------------------------------------------

    /**
     * Server-Datensätze einspielen. Eine lokal geänderte, noch nicht hochgeladene
     * und jüngere Fassung bleibt erhalten; sie geht beim nächsten Push hoch.
     * Rückgabe: Anzahl übernommener Datensätze.
     */
    fun applyServerRecords(records: List<ServerRecord>, force: Boolean = false): Int {
        var applied = 0
        db.transaction {
            for (r in records) {
                if (!force && keepLocal(r)) continue
                when (r.type) {
                    EntityType.RECIPE -> db.recipeQueries.upsert(r.toRecipe())
                    EntityType.PHOTO -> db.photoQueries.upsert(r.toPhoto())
                    EntityType.SHOPPING_ITEM -> db.shoppingItemQueries.upsert(r.toShoppingItem())
                    in EntityType.LABELS -> db.labelQueries.upsert(r.toLabel())
                    else -> continue
                }
                applied++
            }
        }
        return applied
    }

    private fun keepLocal(r: ServerRecord): Boolean {
        val (dirty, updatedAt) = when (r.type) {
            EntityType.RECIPE -> db.recipeQueries.selectById(r.id).executeAsOneOrNull()?.let { it.dirty to it.updated_at }
            EntityType.PHOTO -> db.photoQueries.selectById(r.id).executeAsOneOrNull()?.let { it.dirty to it.updated_at }
            EntityType.SHOPPING_ITEM -> db.shoppingItemQueries.selectById(r.id).executeAsOneOrNull()?.let { it.dirty to it.updated_at }
            in EntityType.LABELS -> db.labelQueries.selectById(r.id).executeAsOneOrNull()?.let { it.dirty to it.updated_at }
            else -> null
        } ?: return false
        return dirty == 1L && updatedAt > r.updatedAt
    }

    companion object {
        const val KEY_CURSOR = "sync_cursor"
        const val KEY_SERVER = "server_url"
        const val KEY_NODE = "hlc_node"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_LAST_SYNC = "last_sync_ms"
    }
}

// --- Umwandlung Datenbank <-> Sync-Datensatz ---------------------------------------------

internal fun ServerRecord.toRecipe(): Recipe {
    val d = AppJson.decodeFromJsonElement(RecipeData.serializer(), data)
    return Recipe(
        id = id, updated_at = updatedAt, deleted = deleted.toLong(), server_rev = serverRev, dirty = 0,
        title = d.title, description = d.description, source_name = d.sourceName, source_url = d.sourceUrl,
        servings_text = d.servingsText, servings_count = d.servingsCount, prep_min = d.prepMin, cook_min = d.cookMin,
        total_min = d.totalMin, ingredients_text = d.ingredientsText, directions_text = d.directionsText, notes = d.notes,
        nutrition = d.nutrition?.let { AppJson.encodeToString(JsonObject.serializer(), it) },
        rating = d.rating, is_favourite = d.isFavourite.toLong(),
        course_ids = encodeIds(d.courseIds), category_ids = encodeIds(d.categoryIds), collection_ids = encodeIds(d.collectionIds),
        import_ref = d.importRef,
    )
}

internal fun ServerRecord.toLabel(): Label {
    val d = AppJson.decodeFromJsonElement(LabelData.serializer(), data)
    return Label(
        id = id, kind = type, updated_at = updatedAt, deleted = deleted.toLong(), server_rev = serverRev, dirty = 0,
        name = d.name, sort_order = d.sortOrder,
    )
}

internal fun ServerRecord.toPhoto(): Photo {
    val d = AppJson.decodeFromJsonElement(PhotoData.serializer(), data)
    return Photo(
        id = id, updated_at = updatedAt, deleted = deleted.toLong(), server_rev = serverRev, dirty = 0,
        recipe_id = d.recipeId, sort_order = d.sortOrder, sha256 = d.sha256, mime = d.mime, width = d.width, height = d.height,
    )
}

internal fun Recipe.toRecord(): SyncRecord {
    val d = RecipeData(
        title = title, description = description, sourceName = source_name, sourceUrl = source_url,
        servingsText = servings_text, servingsCount = servings_count, prepMin = prep_min, cookMin = cook_min,
        totalMin = total_min, ingredientsText = ingredients_text, directionsText = directions_text, notes = notes,
        nutrition = nutrition?.let { runCatching { AppJson.parseToJsonElement(it).jsonObject }.getOrNull() },
        rating = rating, isFavourite = is_favourite == 1L,
        courseIds = decodeIds(course_ids), categoryIds = decodeIds(category_ids), collectionIds = decodeIds(collection_ids),
        importRef = import_ref,
    )
    return SyncRecord(EntityType.RECIPE, id, updated_at, deleted == 1L, AppJson.encodeToJsonElement(RecipeData.serializer(), d).jsonObject)
}

internal fun Label.toRecord(): SyncRecord =
    SyncRecord(kind, id, updated_at, deleted == 1L, AppJson.encodeToJsonElement(LabelData.serializer(), LabelData(name, sort_order)).jsonObject)

internal fun Photo.toRecord(): SyncRecord {
    val d = PhotoData(recipe_id, sort_order, sha256, mime, width, height)
    return SyncRecord(EntityType.PHOTO, id, updated_at, deleted == 1L, AppJson.encodeToJsonElement(PhotoData.serializer(), d).jsonObject)
}

internal fun ServerRecord.toShoppingItem(): Shopping_item {
    val d = AppJson.decodeFromJsonElement(ShoppingItemData.serializer(), data)
    return Shopping_item(
        id = id, updated_at = updatedAt, deleted = deleted.toLong(), server_rev = serverRev, dirty = 0,
        text = d.text, checked = d.checked.toLong(), recipe_id = d.recipeId, recipe_title = d.recipeTitle, sort_order = d.sortOrder,
    )
}

internal fun Shopping_item.toRecord(): SyncRecord {
    val d = ShoppingItemData(text, checked == 1L, recipe_id, recipe_title, sort_order)
    return SyncRecord(EntityType.SHOPPING_ITEM, id, updated_at, deleted == 1L, AppJson.encodeToJsonElement(ShoppingItemData.serializer(), d).jsonObject)
}
