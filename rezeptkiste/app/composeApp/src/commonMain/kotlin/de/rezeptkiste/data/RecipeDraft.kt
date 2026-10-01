package de.rezeptkiste.data

/** Bearbeitbare Fassung eines Rezepts (Bearbeiten-Bildschirm, Text-Import). */
data class RecipeDraft(
    val id: String? = null,
    val title: String = "",
    val courseIds: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val collectionIds: List<String> = emptyList(),
    val source: String = "",
    val servingsText: String = "",
    val prepMin: Long? = null,
    val cookMin: Long? = null,
    val rating: Long = 0,
    val isFavourite: Boolean = false,
    val ingredients: String = "",
    val directions: String = "",
    val notes: String = "",
    val nutrition: Map<String, String> = emptyMap(),
    val photos: List<DraftPhoto> = emptyList(),
)

data class DraftPhoto(
    val id: String?,
    val sha256: String,
    val mime: String = "image/jpeg",
    val width: Long? = null,
    val height: Long? = null,
)

/** Nährwertfelder in der Reihenfolge von Anzeige und Bearbeiten. */
val NUTRITION_FIELDS = listOf(
    "serving_size" to "Portionsgröße",
    "kcal" to "Kalorien",
    "fat" to "Fette gesamt (g)",
    "saturated_fat" to "Gesättigte Fettsäuren (g)",
    "cholesterol" to "Cholesterin (mg)",
    "sodium" to "Natrium (mg)",
    "carbohydrates" to "Kohlenhydrate gesamt (g)",
    "fiber" to "Ballaststoffe (g)",
    "sugar" to "Zucker (g)",
    "protein" to "Eiweiß (g)",
)

fun SplitRecipe.toDraft(): RecipeDraft = RecipeDraft(
    title = title,
    servingsText = servingsText ?: servingsCount?.toString().orEmpty(),
    prepMin = prepMin,
    cookMin = cookMin,
    ingredients = ingredients,
    directions = directions,
    notes = notes,
)
