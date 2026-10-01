package de.rezeptkiste.data

/** Ergebnis der Textaufteilung, Grundlage für den Prüfdialog. */
data class SplitRecipe(
    val title: String = "",
    val servingsText: String? = null,
    val servingsCount: Long? = null,
    val prepMin: Long? = null,
    val cookMin: Long? = null,
    val ingredients: String = "",
    val directions: String = "",
    val notes: String = "",
)

/**
 * Teilt frei eingefügten Rezepttext in Felder auf (regelbasiert, ohne Sprachmodell).
 *
 * - Erste nicht leere Zeile wird Titel.
 * - Überschriften wie "Zutaten" oder "Zubereitung" trennen die Blöcke.
 * - Ohne Überschriften: Zeilen mit Menge am Anfang gelten als Zutaten, längere Sätze als Schritte.
 * - Muster wie "4 Portionen", "für 6 Personen", "30 Min." füllen Portionen und Zeiten.
 */
object TextSplitter {
    private val ingredientHeads = Regex("^(zutaten|ingredients|für den teig|einkaufsliste)\\s*:?$", RegexOption.IGNORE_CASE)
    private val directionHeads = Regex("^(zubereitung|anleitung|so geht'?s|directions|instructions|method|arbeitsschritte)\\s*:?$", RegexOption.IGNORE_CASE)
    private val noteHeads = Regex("^(notizen|tipps?|hinweise?|notes)\\s*:?$", RegexOption.IGNORE_CASE)
    private val servings = Regex("(?:für\\s+)?(\\d{1,3})\\s*(portionen|portion|personen|person|stück|servings)", RegexOption.IGNORE_CASE)
    private val prep = Regex("(arbeitszeit|vorbereitung|zubereitungszeit|prep)[^0-9]{0,15}(\\d{1,3})\\s*(std|stunden|h|min)", RegexOption.IGNORE_CASE)
    private val cook = Regex("(kochzeit|backzeit|garzeit|cook)[^0-9]{0,15}(\\d{1,3})\\s*(std|stunden|h|min)", RegexOption.IGNORE_CASE)
    private val stepNumber = Regex("^(\\d{1,2})[.)]\\s+")

    private fun minutes(m: MatchResult?): Long? {
        m ?: return null
        val v = m.groupValues[2].toLongOrNull() ?: return null
        return if (m.groupValues[3].lowercase().startsWith("min")) v else v * 60
    }

    fun split(raw: String): SplitRecipe {
        val lines = raw.replace("\r", "").lines().map { it.trim() }
        val nonEmpty = lines.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return SplitRecipe()
        val title = nonEmpty.first().trimEnd(':')
        val body = lines.drop(lines.indexOfFirst { it.isNotEmpty() } + 1)

        val ing = mutableListOf<String>()
        val dir = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val meta = mutableListOf<String>()
        var mode = 0 // 0 = unbekannt, 1 = Zutaten, 2 = Zubereitung, 3 = Notizen
        val hasHeads = body.any { ingredientHeads.matches(it) || directionHeads.matches(it) }

        for (line in body) {
            when {
                ingredientHeads.matches(line) -> { mode = 1; continue }
                directionHeads.matches(line) -> { mode = 2; continue }
                noteHeads.matches(line) -> { mode = 3; continue }
            }
            if (line.isEmpty()) {
                if (mode == 1 && ing.isNotEmpty() && ing.last().isNotEmpty()) ing.add("")
                continue
            }
            val isMeta = servings.containsMatchIn(line) && line.length < 40 || prep.containsMatchIn(line) || cook.containsMatchIn(line)
            if (isMeta && mode == 0) { meta.add(line); continue }
            val target = when {
                mode == 1 -> 1
                mode == 2 -> 2
                mode == 3 -> 3
                hasHeads -> if (dir.isEmpty() && ing.isEmpty()) 0 else 2
                Quantity.leadingLength(line) > 0 && line.length < 60 && !stepNumber.containsMatchIn(line) -> 1
                line.length < 35 && dir.isEmpty() -> 1
                else -> 2
            }
            when (target) {
                0 -> meta.add(line)
                1 -> ing.add(line)
                2 -> dir.add(line.replace(stepNumber, ""))
                3 -> notes.add(line)
            }
        }
        while (ing.lastOrNull() == "") ing.removeAt(ing.size - 1)

        val all = (listOf(raw)).joinToString("\n")
        val sv = servings.find(all)
        return SplitRecipe(
            title = title,
            servingsText = sv?.value?.trim(),
            servingsCount = sv?.groupValues?.get(1)?.toLongOrNull(),
            prepMin = minutes(prep.find(all)),
            cookMin = minutes(cook.find(all)),
            ingredients = ing.joinToString("\n"),
            directions = dir.joinToString("\n"),
            notes = (meta.filterNot { servings.containsMatchIn(it) || prep.containsMatchIn(it) || cook.containsMatchIn(it) } + notes).joinToString("\n"),
        )
    }
}
