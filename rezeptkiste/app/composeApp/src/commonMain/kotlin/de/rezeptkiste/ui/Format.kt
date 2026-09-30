package de.rezeptkiste.ui

import de.rezeptkiste.data.decodeIds
import de.rezeptkiste.db.Recipe

fun formatMinutes(min: Long?): String? {
    if (min == null || min <= 0) return null
    val h = min / 60
    val m = min % 60
    return when {
        h == 0L -> "$m Min."
        m == 0L -> "$h Std."
        else -> "$h Std. $m Min."
    }
}

fun stars(rating: Long): String = if (rating <= 0) "" else "★".repeat(rating.toInt().coerceAtMost(5)) + "☆".repeat((5 - rating.toInt()).coerceAtLeast(0))

/** Zwischenüberschrift in Zutaten oder Zubereitung: mit Doppelpunkt oder in Großbuchstaben. */
fun isHeading(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty()) return false
    if (t.endsWith(":")) return true
    val letters = t.filter { it.isLetter() }
    return letters.length >= 3 && letters.all { it.isUpperCase() }
}

fun Recipe.labelIds(): Set<String> = (decodeIds(course_ids) + decodeIds(category_ids) + decodeIds(collection_ids)).toSet()

fun Recipe.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return listOf(title, ingredients_text, directions_text, notes, source_name).any { it?.contains(q, ignoreCase = true) == true }
}
