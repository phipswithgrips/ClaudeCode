package de.rezeptkiste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rezeptkiste.AppController
import de.rezeptkiste.Screen
import de.rezeptkiste.categoryIds
import de.rezeptkiste.courseIds
import de.rezeptkiste.data.NUTRITION_FIELDS
import de.rezeptkiste.data.Quantity
import de.rezeptkiste.db.Label
import de.rezeptkiste.db.Recipe
import de.rezeptkiste.sync.AppJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val nutritionUnits = mapOf(
    "fat" to "g", "saturated_fat" to "g", "carbohydrates" to "g", "fiber" to "g", "sugar" to "g", "protein" to "g",
    "cholesterol" to "mg", "sodium" to "mg",
)

/** Rezept als Klartext für Teilen und Zwischenablage. */
fun recipeAsText(r: Recipe, labelsById: Map<String, Label>): String = buildString {
    appendLine(r.title)
    val meta = (r.courseIds() + r.categoryIds()).mapNotNull { labelsById[it]?.name }
    if (meta.isNotEmpty()) appendLine(meta.joinToString(" • "))
    r.servings_text?.let { appendLine("Portionen: $it") }
    formatMinutes(r.prep_min)?.let { appendLine("Arbeitszeit: $it") }
    formatMinutes(r.cook_min)?.let { appendLine("Kochzeit: $it") }
    r.ingredients_text?.takeIf { it.isNotBlank() }?.let {
        appendLine(); appendLine("Zutaten")
        it.lines().forEach { l -> appendLine(if (l.isBlank() || isHeading(l)) l else "- $l") }
    }
    r.directions_text?.takeIf { it.isNotBlank() }?.let {
        appendLine(); appendLine("Zubereitung")
        it.lines().filter { l -> l.isNotBlank() }.forEach { l -> appendLine(l) }
    }
    r.notes?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("Notizen"); appendLine(it) }
    (r.source_name ?: r.source_url)?.let { appendLine(); appendLine("Quelle: $it") }
}.trimEnd()

@Composable
fun RecipeDetailScreen(controller: AppController, screen: Screen.Detail, narrow: Boolean) {
    val all by controller.recipes.collectAsState()
    val labels by controller.labels.collectAsState()
    val photos by controller.photos.collectAsState()
    val keepOn by controller.keepScreenOn.collectAsState()
    val recipe = all.firstOrNull { it.id == screen.recipeId }
    if (recipe == null) {
        EmptyState(Icons.Filled.Close, "Dieses Rezept gibt es nicht mehr.", "Zurück") { controller.back() }
        return
    }
    controller.platform.KeepScreenOn(keepOn)
    val labelsById = remember(labels) { labels.associateBy { it.id } }
    val recipePhotos = remember(photos, recipe.id) { photos.filter { it.recipe_id == recipe.id }.sortedBy { it.sort_order } }

    // Portionen umrechnen (nur Anzeige, bis "Permanent")
    val base = recipe.servings_count ?: 1L
    var servings by remember(recipe.id) { mutableStateOf(base) }
    LaunchedEffect(recipe.id, base) { servings = base }
    val factor = servings.toDouble() / base.toDouble()
    var portionDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val idx = screen.context.indexOf(recipe.id)
    val prev = if (idx > 0) screen.context[idx - 1] else null
    val next = if (idx >= 0 && idx < screen.context.lastIndex) screen.context[idx + 1] else null
    fun goTo(id: String) { controller.markViewed(id); controller.replace(Screen.Detail(id, screen.context)) }

    Column(Modifier.fillMaxSize()) {
        // Befehlsleiste
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
        ) {
            CommandButton(Icons.Filled.Share, if (narrow) null else "Teilen", { controller.share(recipe.title, recipeAsText(recipe, labelsById)) })
            CommandButton(Icons.Filled.Edit, if (narrow) null else "Bearbeiten", { controller.go(Screen.Edit(recipe.id)) })
            val fav = recipe.is_favourite == 1L
            CommandButton(
                if (fav) Icons.Filled.Star else Icons.Outlined.Star,
                if (narrow) null else if (fav) "Aus Favoriten entfernen" else "Zu Favoriten hinzufügen",
                { controller.toggleFavourite(recipe) }, tint = if (fav) accent else RkColors.Text,
            )
            Box {
                CommandButton(Icons.Filled.MoreVert, null, { menu = true })
                DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = RkColors.SurfaceHigh) {
                    listOf(0.9f to "Textgröße: klein", 1f to "Textgröße: normal", 1.15f to "Textgröße: groß", 1.3f to "Textgröße: sehr groß").forEach { (v, t) ->
                        DropdownMenuItem(text = { Text(t) }, onClick = { menu = false; controller.setTextScale(v) })
                    }
                    DropdownMenuItem(text = { Text("In Zwischenablage kopieren") }, onClick = {
                        menu = false
                        controller.share(recipe.title, recipeAsText(recipe, labelsById))
                    })
                    DropdownMenuItem(text = { Text("Rezept duplizieren") }, onClick = {
                        menu = false
                        controller.duplicate(recipe.id)?.let { controller.go(Screen.Edit(it)) }
                    })
                    DropdownMenuItem(text = { Text("Rezept löschen") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 26.dp, bottom = 24.dp)) {
                Header(recipe, recipePhotos.map { it.sha256 }, labelsById, controller, servings, narrow) { portionDialog = true }
                VSpace(18.dp)
                if (narrow) {
                    Ingredients(recipe, factor)
                    VSpace(16.dp)
                    Directions(recipe)
                    Nutrition(recipe)
                } else {
                    Row {
                        Column(Modifier.width(400.dp).padding(end = 28.dp)) {
                            Ingredients(recipe, factor)
                            Nutrition(recipe)
                        }
                        Column(Modifier.weight(1f)) { Directions(recipe) }
                    }
                }
            }
            if (prev != null) EdgeArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, Alignment.CenterStart) { goTo(prev) }
            if (next != null) EdgeArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, Alignment.CenterEnd) { goTo(next) }
        }
    }

    if (portionDialog) {
        PortionDialog(
            current = servings, base = base,
            onDismiss = { portionDialog = false },
            onApply = { servings = it; portionDialog = false },
            onPermanent = { v ->
                controller.scalePermanently(recipe.id, v.toDouble() / base.toDouble(), v)
                portionDialog = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog("Rezept löschen", "\"${recipe.title}\" wirklich löschen?", "Löschen", { confirmDelete = false }) {
            confirmDelete = false
            controller.delete(recipe.id)
            controller.back()
        }
    }
}

@Composable
private fun EdgeArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, align: Alignment, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = align) {
        Box(
            Modifier.size(18.dp, 40.dp).clip(RoundedCornerShape(3.dp)).background(RkColors.SurfaceHigh).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = RkColors.Text, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun Header(
    recipe: Recipe,
    photoShas: List<String>,
    labelsById: Map<String, Label>,
    controller: AppController,
    servings: Long,
    narrow: Boolean,
    onPortions: () -> Unit,
) {
    var shown by remember(recipe.id) { mutableStateOf(0) }
    val photo: @Composable () -> Unit = {
        if (photoShas.isNotEmpty()) {
            Column {
                RemoteImage(photoShas.getOrElse(shown) { photoShas.first() }, "medium", controller, Modifier.size(if (narrow) 140.dp else 180.dp))
                if (photoShas.size > 1) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        photoShas.forEachIndexed { i, s ->
                            RemoteImage(s, "thumb", controller, Modifier.size(32.dp).clickable { shown = i })
                        }
                    }
                }
            }
        }
    }
    val info: @Composable () -> Unit = {
        Column(Modifier.padding(start = if (photoShas.isEmpty() || narrow) 0.dp else 16.dp)) {
            Text(recipe.title, color = accent, fontSize = 23.sp)
            val courses = recipe.courseIds().mapNotNull { labelsById[it]?.name }
            val cats = recipe.categoryIds().mapNotNull { labelsById[it]?.name }
            val line = listOf(courses.joinToString(", "), cats.joinToString(", ")).filter { it.isNotEmpty() }.joinToString(" • ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
            VSpace(4.dp)
            (recipe.source_name ?: recipe.source_url)?.let { InfoRow("Quelle") { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            InfoRow("Portionsgröße") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val txt = if (recipe.servings_count != null) {
                        val rest = recipe.servings_text?.replaceFirst(Regex("^\\s*\\d{1,3}"), "")?.trim().orEmpty()
                        listOf(servings.toString(), rest).filter { it.isNotEmpty() }.joinToString(" ")
                    } else {
                        recipe.servings_text ?: if (servings != 1L) "×$servings" else ""
                    }
                    if (txt.isNotEmpty()) Text(txt + " ", style = MaterialTheme.typography.bodyLarge)
                    LinkText("Einstellen +/-", onPortions)
                }
            }
            formatMinutes(recipe.prep_min)?.let { InfoRow("Arbeitszeit") { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            formatMinutes(recipe.cook_min)?.let { InfoRow("Kochzeit") { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            if (recipe.rating > 0) InfoRow("Bewertung") { Stars(recipe.rating, size = 16.dp) }
        }
    }
    if (narrow) {
        Column { photo(); VSpace(8.dp); info() }
    } else {
        Row { photo(); info() }
    }
}

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RkColors.TextSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(100.dp))
        value()
    }
}

@Composable
private fun Ingredients(recipe: Recipe, factor: Double) {
    val text = Quantity.scaleText(recipe.ingredients_text, factor)
    val lines = text?.lines().orEmpty()
    if (lines.all { it.isBlank() }) return
    AccentHeading("Zutaten")
    lines.forEach { line ->
        when {
            line.isBlank() -> VSpace(8.dp)
            isHeading(line) -> Text(line.trim(), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            else -> {
                val t = line.trim()
                val n = Quantity.leadingLength(t)
                Text(
                    buildAnnotatedString {
                        if (n > 0) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.substring(0, n)) }
                            append(t.substring(n))
                        } else {
                            append(t)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun Directions(recipe: Recipe) {
    val lines = recipe.directions_text?.lines()?.filter { it.isNotBlank() }.orEmpty()
    if (lines.isNotEmpty()) {
        AccentHeading("Zubereitung")
        lines.forEach { line ->
            if (isHeading(line)) {
                Text(line.trim(), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            } else {
                Text(line.trim(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 14.dp))
            }
        }
    }
    recipe.notes?.takeIf { it.isNotBlank() }?.let {
        AccentHeading("Notizen", Modifier.padding(top = 10.dp))
        Text(it, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Nutrition(recipe: Recipe) {
    val obj = recipe.nutrition?.let { runCatching { AppJson.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return
    val rows = NUTRITION_FIELDS.mapNotNull { (key, label) ->
        (obj[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { v ->
            val unit = nutritionUnits[key]
            label.substringBefore(" (") to (if (unit != null && v.none { it.isLetter() }) "$v $unit" else v)
        }
    }
    if (rows.isEmpty()) return
    AccentHeading("Nährwertangaben", Modifier.padding(top = 18.dp))
    Text("Menge pro Portion", color = RkColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    rows.forEach { (label, value) ->
        Row(Modifier.padding(vertical = 1.dp)) {
            Text(label, color = RkColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(160.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "Einstellen +/-": Portionen ändern, zurücksetzen oder dauerhaft übernehmen. */
@Composable
private fun PortionDialog(current: Long, base: Long, onDismiss: () -> Unit, onApply: (Long) -> Unit, onPermanent: (Long) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toLongOrNull()?.coerceIn(1L, 999L)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = RkColors.SurfaceHigh,
        title = { Text("Portionsgröße", style = MaterialTheme.typography.titleMedium) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RkField(text, { t -> text = t.filter { it.isDigit() }.take(3) }, Modifier.widthIn(min = 80.dp).width(100.dp), keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                CommandButton(androidx.compose.material.icons.Icons.Filled.Close, null, { text = "" })
                Text("−", fontSize = 22.sp, modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { text = ((value ?: 2L) - 1L).coerceAtLeast(1L).toString() }.padding(horizontal = 10.dp))
                CommandButton(Icons.Filled.Add, null, { text = ((value ?: 0L) + 1L).toString() })
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentButton("Zurücksetzen", { onApply(base) })
                AccentButton("Permanent", { value?.let(onPermanent) }, enabled = value != null && value != base)
                AccentButton("OK", { value?.let(onApply) }, enabled = value != null)
            }
        },
    )
}
