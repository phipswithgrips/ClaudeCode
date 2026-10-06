package de.rezeptkiste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.rezeptkiste.AppController
import de.rezeptkiste.FileKind
import de.rezeptkiste.Screen
import de.rezeptkiste.categoryIds
import de.rezeptkiste.collectionIds
import de.rezeptkiste.courseIds
import de.rezeptkiste.data.RecipeDraft
import de.rezeptkiste.data.TextSplitter
import de.rezeptkiste.data.toDraft
import de.rezeptkiste.sync.EntityType
import kotlinx.coroutines.launch

private val timeLimits = listOf(null to "Beliebig", 15L to "Bis 15 Min.", 30L to "Bis 30 Min.", 45L to "Bis 45 Min.", 60L to "Bis 1 Std.", 90L to "Bis 1,5 Std.", 120L to "Bis 2 Std.")

@Composable
fun AdvancedSearchScreen(controller: AppController, narrow: Boolean) {
    val all by controller.recipes.collectAsState()
    val labels by controller.labels.collectAsState()
    val photos by controller.photos.collectAsState()
    val size by controller.cardSize.collectAsState()
    val cover = remember(photos) { coverMap(photos) }

    var allWords by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var anyWords by remember { mutableStateOf("") }
    var noneWords by remember { mutableStateOf("") }
    var courses by remember { mutableStateOf(listOf<String>()) }
    var cats by remember { mutableStateOf(listOf<String>()) }
    var cols by remember { mutableStateOf(listOf<String>()) }
    var prep by remember { mutableStateOf<Long?>(null) }
    var cook by remember { mutableStateOf<Long?>(null) }
    var total by remember { mutableStateOf<Long?>(null) }
    var minRating by remember { mutableStateOf(0L) }
    var favOnly by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }

    fun words(s: String) = s.split(Regex("\\s+")).filter { it.isNotBlank() }
    val active = listOf(allWords, phrase, anyWords, noneWords).any { it.isNotBlank() } || courses.isNotEmpty() || cats.isNotEmpty() ||
        cols.isNotEmpty() || prep != null || cook != null || total != null || minRating > 0 || favOnly
    val results = remember(all, allWords, phrase, anyWords, noneWords, courses, cats, cols, prep, cook, total, minRating, favOnly) {
        if (!active) emptyList() else all.filter { r ->
            words(allWords).all { r.containsWord(it) } &&
                (phrase.isBlank() || r.containsWord(phrase.trim())) &&
                (words(anyWords).isEmpty() || words(anyWords).any { r.containsWord(it) }) &&
                words(noneWords).none { r.containsWord(it) } &&
                (courses.isEmpty() || courses.any { it in r.courseIds() }) &&
                (cats.isEmpty() || cats.any { it in r.categoryIds() }) &&
                (cols.isEmpty() || cols.any { it in r.collectionIds() }) &&
                (prep == null || (r.prep_min ?: 0L) <= prep!!) &&
                (cook == null || (r.cook_min ?: 0L) <= cook!!) &&
                (total == null || ((r.total_min ?: ((r.prep_min ?: 0L) + (r.cook_min ?: 0L)))) <= total!!) &&
                r.rating >= minRating && (!favOnly || r.is_favourite == 1L)
        }.sortedBy { it.title.lowercase() }
    }
    fun names(ids: List<String>) = ids.mapNotNull { id -> labels.firstOrNull { it.id == id }?.name }.joinToString(", ")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
        PageTitle("Finde Rezepte, die haben")
        val cells = listOf<@Composable () -> Unit>(
            { FieldLabel("All diese Wörter"); RkField(allWords, { allWords = it }, Modifier.fillMaxWidth()) },
            { FieldLabel("Dieser genaue Ausdruck"); RkField(phrase, { phrase = it }, Modifier.fillMaxWidth()) },
            { FieldLabel("Irgendeines dieser Wörter"); RkField(anyWords, { anyWords = it }, Modifier.fillMaxWidth()) },
            { FieldLabel("Keines dieser Wörter"); RkField(noneWords, { noneWords = it }, Modifier.fillMaxWidth()) },
            { FieldLabel("Rezeptarten"); SelectField(names(courses), { dialog = EntityType.COURSE }, Modifier.fillMaxWidth()) },
            { FieldLabel("Kategorien"); SelectField(names(cats), { dialog = EntityType.CATEGORY }, Modifier.fillMaxWidth()) },
            { FieldLabel("Sammlungen"); SelectField(names(cols), { dialog = EntityType.COLLECTION }, Modifier.fillMaxWidth()) },
            {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("Arbeitszeit", prep) { v: Long? -> prep = v }, Triple("Kochzeit", cook) { v: Long? -> cook = v }, Triple("Gesamtzeit", total) { v: Long? -> total = v })
                        .forEach { (label, value, set) ->
                            Column(Modifier.weight(1f)) {
                                FieldLabel(label)
                                Box(Modifier.fillMaxWidth()) {
                                    DropdownLabel(timeLimits.first { it.first == value }.second, timeLimits.map { it.second }, { set(timeLimits[it].first) }, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                }
            },
            {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column { FieldLabel("Bewertung (mindestens)"); Stars(minRating, { minRating = it }) }
                    Column {
                        FieldLabel("Nur Favoriten")
                        Switch(favOnly, { favOnly = it }, colors = SwitchDefaults.colors(checkedTrackColor = accentFill, checkedThumbColor = Color.White))
                    }
                }
            },
        )
        if (narrow) {
            cells.forEach { it() }
        } else {
            cells.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { cell -> Column(Modifier.weight(1f)) { cell() } }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        VSpace(16.dp)
        if (active) {
            Text("${results.size} Rezepte", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            CardGrid(results, cover, controller, size)
        }
    }

    dialog?.let { kind ->
        val selected = when (kind) { EntityType.COURSE -> courses; EntityType.CATEGORY -> cats; else -> cols }
        MultiSelectDialog(
            title = when (kind) { EntityType.COURSE -> "Rezeptarten"; EntityType.CATEGORY -> "Kategorien"; else -> "Sammlungen" },
            options = labels.filter { it.kind == kind }.sortedBy { it.name.lowercase() }.map { it.id to it.name },
            selected = selected, onAdd = { null }, onDismiss = { dialog = null },
            onConfirm = { ids ->
                dialog = null
                when (kind) { EntityType.COURSE -> courses = ids; EntityType.CATEGORY -> cats = ids; else -> cols = ids }
            },
        )
    }
}
