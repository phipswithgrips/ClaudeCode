package de.rezeptkiste.data

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Mengenangaben in Zutatenzeilen erkennen und umrechnen.
 *
 * Erkannt werden am Zeilenanfang: "4", "1,5", "1.5", "1/2", "1 1/2", "½", "1½", "2-3", "2–3",
 * auch direkt vor der Einheit ("1lb", "250g"). Mengen am Zeilenende ("Olivenöl 75 g")
 * werden ebenfalls umgerechnet.
 */
object Quantity {
    private const val FRACTIONS = "¼½¾⅓⅔⅛⅜⅝⅞"
    private val unicode = mapOf('¼' to 0.25, '½' to 0.5, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875)

    private const val NUM = "(?:\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?(?:\\s*[$FRACTIONS])?|[$FRACTIONS])"
    private val leading = Regex("^\\s*($NUM(?:\\s*[-–]\\s*$NUM)?)")
    private val trailing = Regex("(\\s)($NUM)(\\s*(?:g|kg|ml|l|cl|dl|EL|TL|Stk\\.?|Stück))\\s*$")

    /** Länge des Mengen-Präfixes einer Zeile (0 = keine Menge). Für die fette Darstellung. */
    fun leadingLength(line: String): Int = leading.find(line)?.groups?.get(1)?.let { it.range.last + 1 } ?: 0

    fun parse(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        // "1 1/2"
        Regex("^(\\d+)\\s+(\\d+)/(\\d+)$").matchEntire(t)?.let { m ->
            val (w, n, d) = m.destructured
            return w.toDouble() + n.toDouble() / d.toDouble().coerceAtLeast(1.0)
        }
        Regex("^(\\d+)/(\\d+)$").matchEntire(t)?.let { m ->
            val (n, d) = m.destructured
            return n.toDouble() / d.toDouble().coerceAtLeast(1.0)
        }
        // "1½" oder "1 ½" oder "½"
        val last = t.last()
        if (last in unicode) {
            val whole = t.dropLast(1).trim()
            return (if (whole.isEmpty()) 0.0 else whole.toDoubleOrNull() ?: return null) + unicode.getValue(last)
        }
        return t.toDoubleOrNull()
    }

    fun format(value: Double, unitHint: String = ""): String {
        if (value <= 0) return "0"
        val unit = unitHint.trim().lowercase()
        val metric = unit.startsWith("g") || unit.startsWith("kg") || unit.startsWith("ml") || unit == "l"
        if (metric && value >= 10) return value.roundToLong().toString()
        val whole = value.toLong()
        val frac = value - whole
        if (frac < 0.02) return whole.toString()
        if (frac > 0.98) return (whole + 1).toString()
        if (!metric) {
            val candidates = listOf(0.25 to "1/4", 1.0 / 3 to "1/3", 0.5 to "1/2", 2.0 / 3 to "2/3", 0.75 to "3/4", 0.125 to "1/8")
            candidates.firstOrNull { abs(it.first - frac) < 0.03 }?.let { (_, s) ->
                return if (whole == 0L) s else "$whole $s"
            }
        }
        val rounded = (value * 10).roundToLong() / 10.0
        val txt = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
        return txt.replace('.', ',')
    }

    private fun scaleToken(token: String, factor: Double, unitHint: String): String {
        val parts = token.split(Regex("\\s*[-–]\\s*"))
        if (parts.size == 2) {
            val a = parse(parts[0]) ?: return token
            val b = parse(parts[1]) ?: return token
            return "${format(a * factor, unitHint)}-${format(b * factor, unitHint)}"
        }
        val v = parse(token) ?: return token
        return format(v * factor, unitHint)
    }

    /** Eine Zutatenzeile mit Faktor umrechnen. Zeilen ohne Menge bleiben unverändert. */
    fun scaleLine(line: String, factor: Double): String {
        if (factor == 1.0 || line.isBlank()) return line
        leading.find(line)?.let { m ->
            val g = m.groups[1]!!
            val rest = line.substring(g.range.last + 1)
            return line.substring(0, g.range.first) + scaleToken(g.value, factor, rest.trimStart().take(3)) + rest
        }
        trailing.find(line)?.let { m ->
            val num = m.groups[2]!!
            return line.substring(0, num.range.first) + scaleToken(num.value, factor, m.groups[3]!!.value) + line.substring(num.range.last + 1)
        }
        return line
    }

    fun scaleText(text: String?, factor: Double): String? = text?.lines()?.joinToString("\n") { scaleLine(it, factor) }
}

