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
import de.rezeptkiste.AppController
import de.rezeptkiste.Screen
import de.rezeptkiste.data.NUTRITION_FIELDS
import de.rezeptkiste.data.RecipeDraft
import de.rezeptkiste.sync.EntityType
import kotlinx.coroutines.launch

@Composable
fun RecipeEditScreen(controller: AppController, screen: Screen.Edit, narrow: Boolean) {
    val initial = remember(screen) { screen.prefill ?: screen.recipeId?.let { controller.draftOf(it) } ?: RecipeDraft() }
    var d by remember(screen) { mutableStateOf(initial) }
    var confirmCancel by remember { mutableStateOf(false) }

    fun save() {
        if (d.title.isBlank()) { controller.toast("Bitte einen Titel eingeben."); return }
        val id = controller.save(d)
        if (screen.recipeId == null) controller.replace(Screen.Detail(id, listOf(id))) else controller.back()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PageTitle(if (screen.recipeId == null) "Neues Rezept" else "Rezept bearbeiten", Modifier.weight(1f))
            CommandButton(Icons.Filled.Done, "Speichern", ::save)
            CommandButton(Icons.Filled.Close, "Abbrechen", { if (d != initial) confirmCancel = true else controller.back() })
        }
        EditForm(controller, d, { d = it }, narrow)
    }
    if (confirmCancel) {
        ConfirmDialog("Änderungen verwerfen", "Die Änderungen an diesem Rezept gehen verloren.", "Verwerfen", { confirmCancel = false }) {
            confirmCancel = false
            controller.back()
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
            TimeFields(d.prepMin) { onChange(d.copy(prepMin = it)) }
        }
        Column(Modifier.weight(1f)) {
            FieldLabel("Kochzeit")
            TimeFields(d.cookMin) { onChange(d.copy(cookMin = it)) }
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

@Composable
private fun TimeFields(minutes: Long?, onChange: (Long?) -> Unit) {
    val h = (minutes ?: 0L) / 60L
    val m = (minutes ?: 0L) % 60L
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Std.", style = MaterialTheme.typography.bodyMedium)
            NumberDropdown(h, 0..24) { onChange((it * 60L + m).takeIf { v -> v > 0L }) }
        }
        Column(Modifier.weight(1f)) {
            Text("Min.", style = MaterialTheme.typography.bodyMedium)
            NumberDropdown(m, 0..59) { onChange((h * 60L + it).takeIf { v -> v > 0L }) }
        }
    }
}

@Composable
private fun NumberDropdown(value: Long, range: IntRange, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 34.dp).clip(RoundedCornerShape(3.dp)).background(RkColors.Field).clickable { open = true }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value.toString(), modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null, tint = RkColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = RkColors.SurfaceHigh, modifier = Modifier.heightIn(max = 320.dp)) {
            range.forEach { v ->
                DropdownMenuItem(text = { Text(v.toString(), color = if (v.toLong() == value) accent else RkColors.Text) }, onClick = { open = false; onSelect(v.toLong()) })
            }
        }
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
