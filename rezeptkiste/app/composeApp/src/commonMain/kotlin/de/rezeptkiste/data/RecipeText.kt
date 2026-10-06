package de.rezeptkiste.data

/** Eine im Text gefundene Zeitangabe, z. B. "10 Minuten" in "10 Minuten kneten". */
data class TextDuration(
    /** Position im Text (für die Verlinkung). */
    val start: Int,
    val end: Int,
    /** Dauer in Sekunden; bei Spannen ("40-45 Minuten") der kleinere Wert. */
    val seconds: Long,
    /** Bei Spannen der größere Wert, sonst null. */
    val maxSeconds: Long? = null,
)

/**
 * Regeln für Rezepttexte (ohne Sprachmodell):
 * Zwischenüberschriften wie "Teig" oder "Streusel:", Zeitangaben wie "10 Minuten kneten",
 * Zutaten für die Einkaufsliste.
 */
object RecipeText {

    // --- Zwischenüberschriften -------------------------------------------------------

    /** Wortenden, die eine Zwischenüberschrift anzeigen ("Mürbeteig", "Schokoladenglasur"). */
    private val headingNouns = listOf(
        "teig", "belag", "füllung", "fuellung", "streusel", "dressing", "marinade", "garnitur", "glasur", "guss",
        "topping", "toppings", "deko", "dekoration", "ganache", "crumble", "frosting", "vinaigrette", "boden",
        "buttercreme", "sauce", "soße", "sosse", "mousse", "biskuit", "baiser", "anrichten", "fertigstellen",
        "beilage", "beilagen", "gewürzmischung", "panade", "panierung", "kruste",
    )

    /** Zutaten, die oft ohne Menge allein in einer Zeile stehen und keine Überschrift sind. */
    private val plainIngredients = setOf(
        "salz", "pfeffer", "zucker", "mehl", "butter", "öl", "olivenöl", "wasser", "milch", "petersilie", "schnittlauch",
        "basilikum", "muskat", "muskatnuss", "puderzucker", "zimt", "vanillezucker", "ei", "eier", "sahne", "salz und pfeffer",
        "eigelb", "eiweiß", "honig", "zitronensaft", "pflanzenöl", "rapsöl", "sonnenblumenöl", "backpapier",
    )

    private val leadIn = Regex("^(für|fuer|zum|zur|außerdem|ausserdem|dazu|optional)\\b", RegexOption.IGNORE_CASE)

    private fun words(t: String) = t.split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun cleanWord(w: String) = w.lowercase().trim(':', ',', '.', '(', ')', '*', '-', '–')

    /** Zeile beginnt mit "#": immer Überschrift (zum Erzwingen). */
    fun isForcedHeading(line: String) = line.trimStart().startsWith("#")

    /** Text einer Überschrift ohne "#" und ohne Doppelpunkt am Ende. */
    fun headingText(line: String) = line.trim().trimStart('#').trim()

    private fun formalHeading(t: String): Boolean {
        if (t.isEmpty()) return false
        if (t.startsWith("#")) return true
        if (t.endsWith(":") && t.length <= 60) return true
        val letters = t.filter { it.isLetter() }
        return letters.length >= 3 && letters.all { it.isUpperCase() }
    }

    private fun hasNounHint(t: String): Boolean {
        val ws = words(t)
        if (ws.isEmpty() || ws.size > 4) return false
        if (leadIn.containsMatchIn(t)) return true
        return ws.any { w -> val c = cleanWord(w); headingNouns.any { c.endsWith(it) } }
    }

    /**
     * Ist Zeile [i] einer Zutatenliste eine Zwischenüberschrift?
     * Beispiele: "Teig", "Für die Sauce", "Streusel:", "BELAG", "# Deko".
     */
    fun isIngredientHeading(lines: List<String>, i: Int): Boolean {
        val t = lines[i].trim()
        if (t.isEmpty()) return false
        if (formalHeading(t)) return true
        if (Quantity.leadingLength(t) > 0 || t.any { it.isDigit() }) return false
        if (t.length > 40 || t.endsWith(".")) return false
        val ws = words(t)
        if (cleanWord(t) in plainIngredients || ws.size == 1 && cleanWord(ws[0]) in plainIngredients) return false
        val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() }?.trim() ?: return false
        val nextIsItem = Quantity.leadingLength(next) > 0
        if (hasNounHint(t)) return true
        // Kurze Zeile ohne Menge am Anfang eines Blocks, danach folgen Zutaten mit Mengen
        val startsBlock = i == 0 || lines[i - 1].isBlank()
        return ws.size <= 3 && startsBlock && nextIsItem
    }

    /** Merkt für jede Zeile, ob sie eine Überschrift ist. */
    fun ingredientHeadings(lines: List<String>): List<Boolean> = lines.indices.map { isIngredientHeading(lines, it) }

    /** Zwischenüberschrift in der Zubereitung ("Teig", "Für die Streusel", "Fertigstellen:"). */
    fun isDirectionHeading(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return false
        if (formalHeading(t)) return true
        if (t.any { it.isDigit() } || t.last() in ".!?;," || t.length > 40) return false
        val ws = words(t)
        if (ws.size <= 4 && leadIn.containsMatchIn(t)) return true
        if (ws.size > 3) return false
        // Verben im Infinitiv ("kneten", "verrühren") sprechen für einen Arbeitsschritt
        val verbish = ws.any { w -> val c = cleanWord(w); c.endsWith("en") && c !in articles && headingNouns.none { c.endsWith(it) } }
        if (verbish) return false
        return hasNounHint(t) || ws.size <= 2 && t.first().isUpperCase()
    }

    private val articles = setOf("den", "einen", "dem", "eben")

    /** Zutaten für die Einkaufsliste: ohne Leerzeilen und Zwischenüberschriften. */
    fun shoppingItems(ingredients: String?): List<String> {
        val lines = ingredients?.replace("\r", "")?.lines().orEmpty()
        val heads = ingredientHeadings(lines)
        return lines.indices.filter { lines[it].isNotBlank() && !heads[it] }
            .map { lines[it].trim().removePrefix("- ").removePrefix("• ").removePrefix("* ").trim() }
            .filter { it.isNotEmpty() }
    }

    // --- Zeitangaben ---------------------------------------------------------------------

    private const val NUM = "(\\d+(?:[.,]\\d+)?(?:\\s?½)?|½|\\d+\\s1/2)"
    private const val HOUR = "(?:Stunden|Stunde|Std\\.?|h)"
    private const val MIN = "(?:Minuten|Minute|Min\\.?|min\\.?)"
    private const val SEC = "(?:Sekunden|Sekunde|Sek\\.?|sek\\.?)"
    private const val END = "(?![\\p{L}])"
    private const val RANGE = "\\s*(?:-|–|—|bis)\\s*"

    private val compound = Regex("$NUM\\s*$HOUR\\s*(?:und\\s+)?$NUM\\s*$MIN$END", RegexOption.IGNORE_CASE)
    private val range = Regex("$NUM$RANGE$NUM\\s*($HOUR|$MIN|$SEC)$END", RegexOption.IGNORE_CASE)
    private val single = Regex("$NUM\\s*($HOUR|$MIN|$SEC)$END", RegexOption.IGNORE_CASE)
    private val wordsRx = Regex(
        "(anderthalb\\s+Stunden|eine[rn]?\\s+(?:halben?|viertel)\\s+Stunde|halbe[rn]?\\s+Stunde|Dreiviertelstunde|Viertelstunde|eine[rn]?\\s+Stunde)$END",
        RegexOption.IGNORE_CASE,
    )

    private fun num(s: String): Double? {
        val t = s.trim().replace(',', '.')
        if (t == "½") return 0.5
        if (t.endsWith("1/2")) return t.removeSuffix("1/2").trim().toDoubleOrNull()?.plus(0.5)
        if (t.endsWith("½")) return (t.removeSuffix("½").trim().ifEmpty { "0" }).toDoubleOrNull()?.plus(0.5)
        return t.toDoubleOrNull()
    }

    private fun unitSeconds(u: String): Long {
        val l = u.lowercase()
        return when {
            l.startsWith("sek") -> 1L
            l.startsWith("m") -> 60L
            else -> 3600L
        }
    }

    /** Alle Zeitangaben einer Zeile, ohne Überschneidungen, von links nach rechts. */
    fun durations(line: String): List<TextDuration> {
        val found = mutableListOf<TextDuration>()
        fun free(r: IntRange) = found.none { it.start <= r.last && r.first < it.end }
        for (m in compound.findAll(line)) {
            val h = num(m.groupValues[1]) ?: continue
            val mi = num(m.groupValues[2]) ?: continue
            if (free(m.range)) found += TextDuration(m.range.first, m.range.last + 1, (h * 3600 + mi * 60).toLong())
        }
        for (m in range.findAll(line)) {
            val a = num(m.groupValues[1]) ?: continue
            val b = num(m.groupValues[2]) ?: continue
            val u = unitSeconds(m.groupValues[3])
            if (free(m.range)) found += TextDuration(m.range.first, m.range.last + 1, (minOf(a, b) * u).toLong(), (maxOf(a, b) * u).toLong())
        }
        for (m in single.findAll(line)) {
            val a = num(m.groupValues[1]) ?: continue
            if (free(m.range)) found += TextDuration(m.range.first, m.range.last + 1, (a * unitSeconds(m.groupValues[2])).toLong())
        }
        for (m in wordsRx.findAll(line)) {
            val t = m.value.lowercase()
            val s = when {
                t.startsWith("anderthalb") -> 5400L
                t.startsWith("dreiviertel") -> 2700L
                "viertel" in t -> 900L
                "halb" in t -> 1800L
                else -> 3600L
            }
            if (free(m.range)) found += TextDuration(m.range.first, m.range.last + 1, s)
        }
        return found.filter { it.seconds in 1..(48 * 3600) }.sortedBy { it.start }
    }

    /**
     * Zeit aus einem Eingabefeld in Minuten: "90", "1:30", "1 Std. 30 Min.", "1,5 Std.", "45 Min.".
     * Eine reine Zahl gilt als Minuten. Leer oder unlesbar: null.
     */
    fun parseMinutes(input: String): Long? {
        val t = input.trim()
        if (t.isEmpty()) return null
        Regex("^(\\d{1,2}):(\\d{1,2})$").matchEntire(t)?.let { return it.groupValues[1].toLong() * 60 + it.groupValues[2].toLong() }
        t.replace(',', '.').toDoubleOrNull()?.let { return it.toLong().takeIf { v -> v >= 0 } }
        val d = durations(t).firstOrNull() ?: return null
        return d.seconds / 60
    }

    /** Minuten für ein Eingabefeld: "45" oder "1:30". */
    fun minutesField(min: Long?): String = when {
        min == null || min <= 0 -> ""
        min < 60 -> min.toString()
        else -> "${min / 60}:${(min % 60).toString().padStart(2, '0')}"
    }

    /** Lesbare Dauer für Timer: "10 Min.", "1 Std. 30 Min.", "45 Sek.". */
    fun formatSeconds(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return listOfNotNull(
            if (h > 0) "$h Std." else null,
            if (m > 0) "$m Min." else null,
            if (s > 0 && h == 0L) "$s Sek." else null,
        ).joinToString(" ").ifEmpty { "0 Sek." }
    }

    /** Countdown-Anzeige "1:05:09" bzw. "4:59". */
    fun clock(sec: Long): String {
        val s = sec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        val mm = if (h > 0) m.toString().padStart(2, '0') else m.toString()
        return (if (h > 0) "$h:" else "") + "$mm:" + r.toString().padStart(2, '0')
    }

    // --- Freitext ----------------------------------------------------------------------

    /** Ein Rezept als freier Text, so wie ihn der Freitext-Editor zeigt und wieder einliest. */
    fun compose(d: RecipeDraft): String = buildString {
        appendLine(d.title.trim())
        if (d.servingsText.isNotBlank()) {
            val s = d.servingsText.trim()
            appendLine(if (s.all { it.isDigit() }) "$s Portionen" else "Portionen: $s")
        }
        d.prepMin?.takeIf { it > 0 }?.let { appendLine("Arbeitszeit: ${durationText(it)}") }
        d.cookMin?.takeIf { it > 0 }?.let { appendLine("Kochzeit: ${durationText(it)}") }
        if (d.source.isNotBlank()) appendLine("Quelle: ${d.source.trim()}")
        if (d.ingredients.isNotBlank()) { appendLine(); appendLine("Zutaten"); appendLine(d.ingredients.trimEnd()) }
        if (d.directions.isNotBlank()) { appendLine(); appendLine("Zubereitung"); appendLine(d.directions.trimEnd()) }
        if (d.notes.isNotBlank()) { appendLine(); appendLine("Notizen"); appendLine(d.notes.trimEnd()) }
    }.trimEnd() + "\n"

    private fun durationText(min: Long): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0L -> "$m Min."
            m == 0L -> "$h Std."
            else -> "$h Std. $m Min."
        }
    }
}
