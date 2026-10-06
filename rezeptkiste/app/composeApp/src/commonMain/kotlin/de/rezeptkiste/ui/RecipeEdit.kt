package de.rezeptkiste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rezeptkiste.AppController
import de.rezeptkiste.Screen
import de.rezeptkiste.data.NUTRITION_FIELDS
import de.rezeptkiste.data.RecipeDraft
import de.rezeptkiste.data.RecipeText
import de.rezeptkiste.data.TextSplitter
import de.rezeptkiste.FileKind
import de.rezeptkiste.sync.EntityType
import kotlinx.coroutines.launch

@Composable
fun RecipeEditScreen(controller: AppController, screen: Screen.Edit, narrow: Boolean) {
    val initial = remember(screen) { screen.prefill ?: screen.recipeId?.let { controller.draftOf(it) } ?: RecipeDraft() }
    var d by remember(screen) { mutableStateOf(initial) }
    var confirmCancel by remember { mutableStateOf(false) }
    // Neues Rezept startet im Freitext, Bearbeiten im Formular
    var freeText by remember(screen) { mutableStateOf(screen.freeText || screen.recipeId == null && screen.prefill == null) }
    var text by remember(screen) { mutableStateOf(if (freeText) RecipeText.compose(d).let { if (d.title.isBlank()) "" else it } else "") }

    fun applyText(t: String) {
        text = t
        val p = TextSplitter.split(t)
        d = d.copy(
            title = p.title, servingsText = p.servingsText ?: p.servingsCount?.toString().orEmpty(),
            prepMin = p.prepMin, cookMin = p.cookMin, source = p.source ?: d.source,
            ingredients = p.ingredients, directions = p.directions, notes = p.notes,
        )
    }
    fun switchMode(toText: Boolean) {
        if (toText == freeText) return
        if (toText) text = if (d.title.isBlank() && d.ingredients.isBlank() && d.directions.isBlank()) "" else RecipeText.compose(d)
        freeText = toText
    }
    fun save() {
        if (d.title.isBlank()) { controller.toast("Bitte einen Titel eingeben (erste Zeile im Freitext)."); return }
        val id = controller.save(d)
        if (screen.recipeId == null) controller.replace(Screen.Detail(id, listOf(id))) else controller.back()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PageTitle(if (screen.recipeId == null) "Neues Rezept" else "Rezept bearbeiten", Modifier.weight(1f))
            CommandButton(Icons.Filled.Done, "Speichern", ::save)
            CommandButton(Icons.Filled.Close, if (narrow) null else "Abbrechen", { if (d != initial) confirmCancel = true else controller.back() })
        }
        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 4.dp)) {
            TextTabs(listOf("Freitext", "Formular"), if (freeText) 0 else 1, { switchMode(it == 0) })
        }
        if (freeText) {
            FreeTextEditor(controller, text, ::applyText, d, narrow)
        } else {
            EditForm(controller, d, { d = it }, narrow)
        }
    }
    if (confirmCancel) {
        ConfirmDialog("Änderungen verwerfen", "Die Änderungen an diesem Rezept gehen verloren.", "Verwerfen", { confirmCancel = false }) {
            confirmCancel = false
            controller.back()
        }
    }
}

private const val FREE_TEXT_HELP =
    "Erste Zeile = Titel. Danach z. B. \"4 Portionen\", \"Arbeitszeit: 20 Min.\", \"Quelle: …\". " +
        "\"Zutaten\", \"Zubereitung\" und \"Notizen\" trennen die Teile. Zwischenüberschriften wie \"Teig\" oder \"Streusel:\" " +
        "werden erkannt (erzwingen mit \"#\"). Zeiten wie \"10 Minuten\" werden zu Timern."

/** Freitext links, fertige Ansicht rechts (schmal: umschaltbar). */
@Composable
private fun FreeTextEditor(controller: AppController, text: String, onText: (String) -> Unit, d: RecipeDraft, narrow: Boolean) {
    val scope = rememberCoroutineScope()
    var showPreview by remember { mutableStateOf(false) }

    val editor: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentButton("Text einfügen", {
                    val c = controller.platform.clipboardText()
                    if (c.isNullOrBlank()) controller.toast("Die Zwischenablage enthält keinen Text.")
                    else onText(if (text.isBlank()) c else text.trimEnd() + "\n" + c)
                })
                AccentButton("Textdatei", {
                    scope.launch { controller.platform.pickFile(FileKind.TEXT)?.let { onText(it.bytes.decodeToString()) } }
                })
            }
            VSpace(6.dp)
            RkField(
                text, onText, Modifier.fillMaxWidth().weight(1f), singleLine = false,
                placeholder = "Zwetschgenkuchen\n12 Portionen\nArbeitszeit: 30 Min.\n\nZutaten\nTeig\n250 g Mehl\n2 Eier\n\nZubereitung\nTeig 10 Minuten kneten.\n…",
            )
            Text(FREE_TEXT_HELP, style = MaterialTheme.typography.bodySmall, color = RkColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
    val preview: @Composable (Modifier) -> Unit = { m ->
        Column(m.clip(RoundedCornerShape(3.dp)).background(RkColors.Pane).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Text("Vorschau", style = MaterialTheme.typography.bodyMedium, color = RkColors.TextSecondary)
            VSpace(4.dp)
            if (d.title.isBlank() && d.ingredients.isBlank() && d.directions.isBlank()) {
                Text("Hier erscheint das Rezept, sobald Sie Text eingeben oder einfügen.", color = RkColors.TextSecondary)
            } else {
                Text(d.title.ifBlank { "(ohne Titel)" }, color = accent, fontSize = 23.sp)
                val meta = listOfNotNull(
                    d.servingsText.takeIf { it.isNotBlank() }?.let { "Portionen: $it" },
                    formatMinutes(d.prepMin)?.let { "Arbeitszeit: $it" },
                    formatMinutes(d.cookMin)?.let { "Kochzeit: $it" },
                    d.source.takeIf { it.isNotBlank() }?.let { "Quelle: $it" },
                )
                meta.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = RkColors.TextSecondary) }
                VSpace(12.dp)
                IngredientsBlock(d.ingredients)
                VSpace(12.dp)
                DirectionsBlock(d.directions, d.notes, d.title, controller)
            }
        }
    }
    if (narrow) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
            Row(Modifier.padding(bottom = 6.dp)) {
                TextTabs(listOf("Text", "Vorschau"), if (showPreview) 1 else 0, { showPreview = it == 1 })
            }
            if (showPreview) preview(Modifier.fillMaxWidth().weight(1f)) else editor(Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            editor(Modifier.weight(1f).fillMaxHeight())
            preview(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Formular in Spalten (breit) bzw. untereinander (schmal); auch für den Text-Import. */
@Composable
fun EditForm(controller: AppController, d: RecipeDraft, onChange: (RecipeDraft) -> Unit, narrow: Boolean, compactColumns: Boolean = false) {
    if (narrow) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp)) {
            BasicsColumn(controller, d, onChange)
            TextColumn("Zutaten", d.ingredients, { onChange(d.copy(ingredients = it)) }, fill = false)
            TextColumn("Zubereitung", d.directions, { onChange(d.copy(directions = it)) }, fill = false)
            TextColumn("Notizen", d.notes, { onChange(d.copy(notes = it)) }, fill = false)
            NutritionColumn(d, onChange)
            PhotosColumn(controller, d, onChange)
            VSpace(24.dp)
        }
    } else {
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.width(222.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { BasicsColumn(controller, d, onChange) }
            Column(Modifier.width(276.dp).fillMaxHeight()) { TextColumn("Zutaten", d.ingredients, { onChange(d.copy(ingredients = it)) }) }
            Column(Modifier.width(266.dp).fillMaxHeight()) { TextColumn("Zubereitung", d.directions, { onChange(d.copy(directions = it)) }) }
            if (!compactColumns) {
                Column(Modifier.width(266.dp).fillMaxHeight()) { TextColumn("Notizen", d.notes, { onChange(d.copy(notes = it)) }) }
                Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { NutritionColumn(d, onChange) }
                Column(Modifier.width(200.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { PhotosColumn(controller, d, onChange) }
            }
        }
    }
}

@Composable
private fun BasicsColumn(controller: AppController, d: RecipeDraft, onChange: (RecipeDraft) -> Unit) {
    val labels by controller.labels.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }

    FieldLabel("Titel")
    RkField(d.title, { onChange(d.copy(title = it)) }, Modifier.fillMaxWidth())

    @Composable
    fun labelField(title: String, kind: String, ids: List<String>) {
        FieldLabel(title)
        val names = ids.mapNotNull { id -> labels.firstOrNull { it.id == id }?.name }
        SelectField(names.joinToString(", "), { dialog = kind }, Modifier.fillMaxWidth())
    }
    labelField("Rezeptarten", EntityType.COURSE, d.courseIds)
    labelField("Kategorien", EntityType.CATEGORY, d.categoryIds)
    labelField("Sammlungen", EntityType.COLLECTION, d.collectionIds)

    FieldLabel("Quelle (Buch / Magazin / Website)")
    RkField(d.source, { onChange(d.copy(source = it)) }, Modifier.fillMaxWidth())
    FieldLabel("Portionsgröße")
    RkField(d.servingsText, { onChange(d.copy(servingsText = it)) }, Modifier.fillMaxWidth())

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            FieldLabel("Arbeitszeit")
            TimeField(d.prepMin, { onChange(d.copy(prepMin = it)) })
        }
        Column(Modifier.weight(1f)) {
            FieldLabel("Kochzeit")
            TimeField(d.cookMin, { onChange(d.copy(cookMin = it)) })
        }
    }
    FieldLabel("Bewertung")
    Stars(d.rating, { onChange(d.copy(rating = it)) }, size = 26.dp)
    VSpace(12.dp)

    dialog?.let { kind ->
        val (title, selected) = when (kind) {
            EntityType.COURSE -> "Wählen Sie eine oder mehrere Rezeptarten aus." to d.courseIds
            EntityType.CATEGORY -> "Wählen Sie eine oder mehrere Kategorien aus." to d.categoryIds
            else -> "Wählen Sie eine oder mehrere Sammlungen aus." to d.collectionIds
        }
        MultiSelectDialog(
            title = title,
            options = labels.filter { it.kind == kind }.sortedBy { it.name.lowercase() }.map { it.id to it.name },
            selected = selected,
            onAdd = { controller.addLabel(kind, it) },
            onDismiss = { dialog = null },
            onConfirm = { ids ->
                dialog = null
                onChange(
                    when (kind) {
                        EntityType.COURSE -> d.copy(courseIds = ids)
                        EntityType.CATEGORY -> d.copy(categoryIds = ids)
                        else -> d.copy(collectionIds = ids)
                    },
                )
            },
        )
    }
}

/** Zeit frei eintippen: "45", "1:30" oder "1 Std. 30 Min.". */
@Composable
fun TimeField(minutes: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(RecipeText.minutesField(minutes)) }
    LaunchedEffect(minutes) {
        if (RecipeText.parseMinutes(text) != minutes) text = RecipeText.minutesField(minutes)
    }
    val parsed = RecipeText.parseMinutes(text)
    Column(modifier) {
        RkField(
            text, { t -> text = t; onChange(RecipeText.parseMinutes(t)?.takeIf { it > 0 }) }, Modifier.fillMaxWidth(),
            placeholder = "z. B. 1:30",
        )
        val hint = when {
            text.isBlank() -> "Minuten oder Std:Min"
            parsed == null -> "Nicht erkannt"
            else -> formatMinutes(parsed) ?: ""
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = if (parsed == null && text.isNotBlank()) RkColors.Error else RkColors.TextSecondary)
    }
}

@Composable
private fun ColumnScope.TextColumn(title: String, value: String, onChange: (String) -> Unit, fill: Boolean = true) {
    FieldLabel(title)
    RkField(
        value, onChange,
        if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
        singleLine = false, minHeight = if (fill) 120.dp else 160.dp,
    )
}

@Composable
private fun NutritionColumn(d: RecipeDraft, onChange: (RecipeDraft) -> Unit) {
    FieldLabel("Nährwertangaben")
    Text("Menge pro Portion", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 6.dp))
    NUTRITION_FIELDS.forEach { (key, label) ->
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            RkField(d.nutrition[key].orEmpty(), { v -> onChange(d.copy(nutrition = d.nutrition + (key to v))) }, Modifier.width(90.dp))
        }
    }
}

@Composable
private fun PhotosColumn(controller: AppController, d: RecipeDraft, onChange: (RecipeDraft) -> Unit) {
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("Fotos")
        Spacer(Modifier.weight(1f))
        CommandButton(Icons.Filled.Add, "Foto hinzufügen", {
            scope.launch { controller.pickPhoto()?.let { p -> onChange(d.copy(photos = d.photos + p)) } }
        })
    }
    d.photos.forEachIndexed { i, p ->
        Box(Modifier.padding(vertical = 4.dp)) {
            RemoteImage(p.sha256, "thumb", controller, Modifier.size(width = 150.dp, height = 110.dp).clip(RoundedCornerShape(3.dp)))
            Row(Modifier.width(150.dp), horizontalArrangement = Arrangement.End) {
                if (i > 0) SmallIcon("Als Titelbild") {
                    val list = d.photos.toMutableList(); val x = list.removeAt(i); list.add(0, x); onChange(d.copy(photos = list))
                }
                SmallIcon("Entfernen") { onChange(d.copy(photos = d.photos.filterIndexed { j, _ -> j != i })) }
            }
        }
    }
}

@Composable
private fun SmallIcon(label: String, size: Dp = 22.dp, onClick: () -> Unit) {
    Box(
        Modifier.padding(2.dp).size(size).clip(RoundedCornerShape(11.dp)).background(RkColors.Background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (label == "Entfernen") Icons.Filled.Close else Icons.Filled.Done, label, tint = RkColors.Text, modifier = Modifier.size(14.dp))
    }
}
