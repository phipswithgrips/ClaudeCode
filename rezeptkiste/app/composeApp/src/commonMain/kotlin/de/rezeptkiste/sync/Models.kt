package de.rezeptkiste.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Gemeinsame JSON-Konfiguration für API und lokale Ablage. */
val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
    isLenient = true
}

object EntityType {
    const val COURSE = "course"
    const val CATEGORY = "category"
    const val COLLECTION = "collection"
    const val RECIPE = "recipe"
    const val PHOTO = "photo"
    val LABELS = setOf(COURSE, CATEGORY, COLLECTION)
}

// --- Anmeldung -------------------------------------------------------------------

@Serializable
data class DeviceLogin(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class DeviceToken(
    @SerialName("device_id") val deviceId: String,
    val token: String,
)

// --- Sync ------------------------------------------------------------------------

@Serializable
data class SyncRecord(
    val type: String,
    val id: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean = false,
    val data: JsonObject,
)

@Serializable
data class ServerRecord(
    val type: String,
    val id: String,
    @SerialName("updated_at") val updatedAt: String,
    val deleted: Boolean,
    @SerialName("server_rev") val serverRev: Long,
    val data: JsonObject,
)

@Serializable
data class PushRequest(val records: List<SyncRecord>)

@Serializable
data class Accepted(val type: String, val id: String, @SerialName("server_rev") val serverRev: Long)

@Serializable
data class RecordError(val type: String, val id: String, val message: String)

@Serializable
data class PushResponse(
    val accepted: List<Accepted> = emptyList(),
    val rejected: List<ServerRecord> = emptyList(),
    val errors: List<RecordError> = emptyList(),
)

@Serializable
data class PullResponse(
    val records: List<ServerRecord>,
    val cursor: Long,
    @SerialName("has_more") val hasMore: Boolean,
)

// --- Nutzdaten je Typ --------------------------------------------------------------

@Serializable
data class LabelData(
    val name: String = "",
    @SerialName("sort_order") val sortOrder: Long = 0,
)

@Serializable
data class RecipeData(
    val title: String = "",
    val description: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("servings_text") val servingsText: String? = null,
    @SerialName("servings_count") val servingsCount: Long? = null,
    @SerialName("prep_min") val prepMin: Long? = null,
    @SerialName("cook_min") val cookMin: Long? = null,
    @SerialName("total_min") val totalMin: Long? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null,
    @SerialName("directions_text") val directionsText: String? = null,
    val notes: String? = null,
    val nutrition: JsonObject? = null,
    val rating: Long = 0,
    @SerialName("is_favourite") val isFavourite: Boolean = false,
    @SerialName("course_ids") val courseIds: List<String> = emptyList(),
    @SerialName("category_ids") val categoryIds: List<String> = emptyList(),
    @SerialName("collection_ids") val collectionIds: List<String> = emptyList(),
    @SerialName("import_ref") val importRef: String? = null,
)

@Serializable
data class PhotoData(
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("sort_order") val sortOrder: Long = 0,
    val sha256: String,
    val mime: String = "image/jpeg",
    val width: Long? = null,
    val height: Long? = null,
)
