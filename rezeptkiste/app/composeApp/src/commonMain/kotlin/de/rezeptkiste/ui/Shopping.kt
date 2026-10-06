package de.rezeptkiste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.rezeptkiste.AppController
import de.rezeptkiste.Screen
import de.rezeptkiste.data.Quantity
import de.rezeptkiste.db.Recipe
import de.rezeptkiste.db.Shopping_item

/** Einkaufsliste: nach Rezept gruppiert, Erledigtes unten. */
@Composable
fun ShoppingScreen(controller: AppController, narrow: Boolean) {
    val items by controller.shopping.collectAsState()
    var newText by remember { mutableStateOf("") }
    var byRecipe by remember { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
    val open = items.filter { it.checked == 0L }
    val done = items.filter { it.checked == 1L }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PageTitle("Einkaufsliste (${open.size})", Modifier.weight(1f))
            CommandButton(Icons.Filled.Share, if (narrow) null else "Teilen", {
                controller.share("Einkaufsliste", shoppingAsText(open))
            }, enabled = open.isNotEmpty())
            CommandButton(Icons.Filled.Done, if (narrow) null else "Erledigte löschen", { controller.deleteShopping(done) }, enabled = done.isNotEmpty())
            CommandButton(Icons.Filled.Delete, if (narrow) null else "Alle löschen", { confirmClear = true }, enabled = items.isNotEmpty())
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.widthIn(max = 640.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val add = { controller.addShoppingText(newText); newText = "" }
                RkField(newText, { newText = it }, Modifier.weight(1f), placeholder = "Artikel hinzufügen, z. B. 2 Zitronen", onDone = add)
                CommandButton(Icons.Filled.Add, null, add)
            }
            Row(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                DropdownLabel(
                    if (byRecipe) "Nach Rezept gruppiert" else "Eine Liste",
                    listOf("Nach Rezept gruppiert", "Eine Liste"),
                    { byRecipe = it == 0 },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
                    EmptyState(
                        Icons.Outlined.ShoppingCart,
                        "Die Einkaufsliste ist leer. Öffnen Sie ein Rezept und wählen Sie \"Zur Einkaufsliste hinzufügen\".",
                    )
                }
            }
            if (byRecipe) {
                open.groupBy { it.recipe_title ?: "" }.toList().sortedBy { (t, _) -> if (t.isEmpty()) "￿" else t.lowercase() }.forEach { (title, group) ->
                    val recipeId = group.firstNotNullOfOrNull { it.recipe_id }
                    Text(
                        title.ifEmpty { "Weitere Artikel" }, color = accent, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp).then(
                            if (recipeId != null && controller.recipe(recipeId) != null) {
                                Modifier.clip(RoundedCornerShape(3.dp)).clickable { controller.go(Screen.Detail(recipeId, listOf(recipeId))) }
                            } else {
                                Modifier
                            },
                        ),
                    )
                    group.forEach { ShoppingRow(controller, it) }
                }
            } else {
                VSpace(6.dp)
                open.forEach { ShoppingRow(controller, it) }
            }
            if (done.isNotEmpty()) {
                Text("Erledigt (${done.size})", color = RkColors.TextSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 2.dp))
                done.forEach { ShoppingRow(controller, it) }
            }
            VSpace(80.dp)
        }
    }
    if (confirmClear) {
        ConfirmDialog("Einkaufsliste leeren", "Alle Artikel werden von der Einkaufsliste entfernt, auf allen Geräten.", "Alle löschen", { confirmClear = false }) {
            confirmClear = false
            controller.deleteShopping(items)
        }
    }
}

@Composable
private fun ShoppingRow(controller: AppController, item: Shopping_item) {
    val checked = item.checked == 1L
    Row(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().clip(RoundedCornerShape(3.dp)).clickable { controller.toggleShopping(item) }.heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked, { controller.toggleShopping(item) },
            colors = CheckboxDefaults.colors(checkedColor = accentFill, checkmarkColor = Color.White, uncheckedColor = RkColors.TextSecondary),
        )
        val t = item.text
        val n = Quantity.leadingLength(t)
        Text(
            buildAnnotatedString {
                if (n > 0 && !checked) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.substring(0, n)) }
                    append(t.substring(n))
                } else {
                    append(t)
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) RkColors.TextSecondary else RkColors.Text,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).clickable { controller.deleteShopping(listOf(item)) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Entfernen", tint = RkColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

private fun shoppingAsText(items: List<Shopping_item>): String = buildString {
    appendLine("Einkaufsliste")
    items.groupBy { it.recipe_title }.forEach { (title, group) ->
        appendLine()
        appendLine(title ?: "Weitere Artikel")
        group.forEach { appendLine("- ${it.text}") }
    }
}.trimEnd()

/** "Zur Einkaufsliste hinzufügen": Zutaten auswählen, Zwischenüberschriften sind schon entfernt. */
@Composable
fun AddToShoppingDialog(controller: AppController, recipe: Recipe, items: List<String>, onDismiss: () -> Unit) {
    val chosen = remember(items) { mutableStateListOf<Int>().apply { addAll(items.indices) } }
    val a = accentFill
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = RkColors.SurfaceHigh,
        title = { Text("Zur Einkaufsliste hinzufügen", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.widthIn(min = 260.dp, max = 460.dp)) {
                Text(
                    "Artikel auswählen (${chosen.size} ausgewählt)", color = RkColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    items.forEachIndexed { i, text ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(RkColors.Field.copy(alpha = 0.5f))
                                .clickable { if (i in chosen) chosen.remove(i) else chosen.add(i) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(i in chosen, { if (it) chosen.add(i) else chosen.remove(i) }, colors = CheckboxDefaults.colors(checkedColor = a, checkmarkColor = Color.White))
                            Text(text, style = MaterialTheme.typography.bodyLarge)
                        }
                        VSpace(3.dp)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentButton(if (chosen.size == items.size) "Alle abwählen" else "Alle auswählen", {
                    if (chosen.size == items.size) chosen.clear() else { chosen.clear(); chosen.addAll(items.indices) }
                })
                AccentButton("Ausgewählte hinzufügen", {
                    controller.addToShopping(recipe, chosen.sorted().map { items[it] })
                    onDismiss()
                }, enabled = chosen.isNotEmpty())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = RkColors.Text) } },
    )
}
