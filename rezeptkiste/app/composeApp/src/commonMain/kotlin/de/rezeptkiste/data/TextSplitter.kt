package de.rezeptkiste.data

/** Ergebnis der Textaufteilung. */
data class SplitRecipe(
    val title: String = "",
    val servingsText: String? = null,
    val servingsCount: Long? = null,
    val prepMin: Long? = null,
    val cookMin: Long? = null,
    val source: String? = null,
    val ingredients: String = "",
    val directions: String = "",
    val notes: String = "",
)

/**
 * Teilt frei eingefügten Rezepttext in Felder auf (regelbasiert, ohne Sprachmodell).
 *
 * - Erste nicht leere Zeile wird Titel.
 * - Überschriften wie "Zutaten" oder "Zubereitung" trennen die Blöcke.
 * - Ohne Überschriften: Zeilen mit Menge am Anfang gelten als Zutaten, Sätze als Schritte.
 * - "4 Portionen", "Portionen: 4", "Arbeitszeit: 1 Std. 30 Min.", "Quelle: …" füllen die Eckdaten.
 * - Zwischenüberschriften ("Teig", "Streusel:") bleiben in Zutaten und Zubereitung erhalten.
 */
object TextSplitter {
    private val ingredientHeads = Regex("^(zutaten|zutatenliste|ingredients|einkaufsliste)(\\s+f(ü|ue)r\\b.*)?\\s*:?$", RegexOption.IGNORE_CASE)
    private val directionHeads = Regex("^(zubereitung|anleitung|so geht'?s|directions|instructions|method|arbeitsschritte|schritte)\\s*:?$", RegexOption.IGNORE_CASE)
    private val noteHeads = Regex("^(notizen|tipps?|hinweise?|notes|anmerkungen?)\\s*:?$", RegexOption.IGNORE_CASE)
    private val servings = Regex("(?:f(?:ü|ue)r\\s+)?(\\d{1,3})\\s*(portionen|portion|personen|person|stück|servings)\\b", RegexOption.IGNORE_CASE)
    private val servingsOnly = Regex("^(?:f(?:ü|ue)r\\s+)?(\\d{1,3})\\s*(portionen|portion|personen|person|servings)\\.?$", RegexOption.IGNORE_CASE)
    private val servingsLabel = Regex("^(portionen|portionsgröße|personen|servings)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val prep = Regex("^(arbeitszeit|vorbereitung(?:szeit)?|zubereitungszeit|prep(?: time)?)\\s*:?\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val cook = Regex("^(kochzeit|backzeit|garzeit|ruhezeit|cook(?: time)?)\\s*:?\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val sourceLine = Regex("^(quelle|source|aus)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val stepNumber = Regex("^(\\d{1,2})[.)]\\s+")

    private fun minutes(text: String): Long? = RecipeText.durations(text).firstOrNull()?.let { it.seconds / 60 }
        ?: text.trim().toLongOrNull()

    fun split(raw: String): SplitRecipe {
        val lines = raw.replace("\r", "").lines().map { it.trim() }
        val first = lines.indexOfFirst { it.isNotEmpty() }
        if (first < 0) return SplitRecipe()
        val title = lines[first].trimEnd(':').trimStart('#').trim()
        val body = lines.drop(first + 1)

        val ing = mutableListOf<String>()
        val dir = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var servingsText: String? = null
        var servingsCount: Long? = null
        var prepMin: Long? = null
        var cookMin: Long? = null
        var source: String? = null
        var mode = 0 // 0 = unbekannt, 1 = Zutaten, 2 = Zubereitung, 3 = Notizen
        val hasHeads = body.any { ingredientHeads.matches(it) || directionHeads.matches(it) }

        fun meta(line: String): Boolean {
            servingsLabel.matchEntire(line)?.let { m ->
                servingsText = m.groupValues[2].trim()
                servingsCount = Regex("\\d{1,3}").find(servingsText!!)?.value?.toLongOrNull()
                return true
            }
            prep.matchEntire(line)?.let { m -> minutes(m.groupValues[2])?.let { prepMin = it; return true } }
            cook.matchEntire(line)?.let { m -> minutes(m.groupValues[2])?.let { cookMin = it; return true } }
            sourceLine.matchEntire(line)?.let { m -> source = m.groupValues[2].trim(); return true }
            servingsOnly.matchEntire(line)?.let { m ->
                if (servingsText == null) {
                    // "4 Portionen" wird zu "4", wie in Recipe Keeper
                    servingsText = m.groupValues[1]
                    servingsCount = m.groupValues[1].toLongOrNull()
                }
                return true
            }
            return false
        }

        for (line in body) {
            if (ingredientHeads.matches(line)) {
                mode = 1
                servings.find(line)?.let { m -> if (servingsText == null) { servingsText = m.groupValues[1]; servingsCount = m.groupValues[1].toLongOrNull() } }
                continue
            }
            if (directionHeads.matches(line)) { mode = 2; continue }
            if (noteHeads.matches(line)) { mode = 3; continue }
            if (line.isEmpty()) {
                if (mode == 1 && ing.isNotEmpty() && ing.last().isNotEmpty()) ing.add("")
                if (mode == 0 && !hasHeads && ing.isNotEmpty() && ing.last().isNotEmpty() && dir.isEmpty()) ing.add("")
                continue
            }
            if (mode == 0 && meta(line)) continue
            val target = when {
                mode != 0 -> mode
                hasHeads -> 3 // Text vor der ersten Überschrift: Beschreibung
                Quantity.leadingLength(line) > 0 && line.length < 70 && !stepNumber.containsMatchIn(line) && dir.isEmpty() -> 1
                stepNumber.containsMatchIn(line) -> 2
                line.endsWith(".") || line.endsWith("!") -> 2
                dir.isEmpty() && line.length < 45 -> 1
                else -> 2
            }
            when (target) {
                1 -> ing.add(line)
                2 -> dir.add(line.replace(stepNumber, ""))
                else -> notes.add(line)
            }
        }
        while (ing.lastOrNull() == "") ing.removeAt(ing.size - 1)

        return SplitRecipe(
            title = title,
            servingsText = servingsText,
            servingsCount = servingsCount,
            prepMin = prepMin,
            cookMin = cookMin,
            source = source,
            ingredients = ing.joinToString("\n"),
            directions = dir.joinToString("\n"),
            notes = notes.joinToString("\n"),
        )
    }
}
