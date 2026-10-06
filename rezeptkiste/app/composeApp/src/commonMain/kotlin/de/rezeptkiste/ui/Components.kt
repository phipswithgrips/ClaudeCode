package de.rezeptkiste.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.rezeptkiste.AppController
import de.rezeptkiste.db.Recipe

/** Großer Seitentitel wie in Recipe Keeper ("Hauptgericht • Alle"). */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = RkColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = RkColors.Text, modifier = modifier.padding(bottom = 6.dp))
}

/** Reiter als reine Textzeile: gewählt weiß, sonst grau. */
@Composable
fun TextTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        items.forEachIndexed { i, t ->
            Text(
                t, style = MaterialTheme.typography.titleLarge,
                color = if (i == selected) RkColors.Text else RkColors.TextSecondary,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onSelect(i) }.padding(vertical = 4.dp),
            )
        }
    }
}

/** Beschriftung mit kleinem Pfeil, öffnet eine Auswahl ("Sortieren nach Titel ˅"). */
@Composable
fun DropdownLabel(
    text: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    selectedIndex: Int = -1,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(4.dp)).clickable { open = true }.padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = style, color = RkColors.Text)
            Icon(Icons.Filled.ArrowDropDown, null, tint = RkColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = RkColors.SurfaceHigh) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(
                    text = { Text(o, color = if (i == selectedIndex) accent else RkColors.Text) },
                    onClick = { open = false; onSelect(i) },
                )
            }
        }
    }
}

/** Bild vom Server (mit Plattenspeicher-Cache); ohne Bild eine Fläche. */
@Composable
fun RemoteImage(sha256: String?, size: String, controller: AppController, modifier: Modifier, placeholder: Color = RkColors.SurfaceHigh) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, sha256, size) {
        value = sha256?.let { controller.image(it, size) }
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(placeholder))
    }
}

/** Startseiten-Kachel: Foto oben, Leiste in Akzentfarbe mit Name und Anzahl. */
@Composable
fun Tile(title: String, count: Int, coverSha: String?, controller: AppController, size: Dp = 168.dp, onClick: () -> Unit) {
    val a = accentFill
    Column(
        Modifier.size(size).clip(RoundedCornerShape(2.dp)).background(a).clickable(onClick = onClick),
    ) {
        if (coverSha != null) {
            RemoteImage(coverSha, "medium", controller, Modifier.fillMaxWidth().weight(1f), placeholder = a)
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().background(a).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.Bottom) {
            Text(title, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(count.toString(), color = Color.White, fontSize = 17.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TileGrid(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

/** Rezeptkarte; size 0 = klein, 1 = mittel, 2 = groß. */
@Composable
fun RecipeCard(recipe: Recipe, coverSha: String?, controller: AppController, size: Int = 1, onClick: () -> Unit) {
    val a = accentFill
    when (size) {
        2 -> Column(Modifier.size(200.dp, 200.dp).clip(RoundedCornerShape(2.dp)).background(a).clickable(onClick = onClick)) {
            RemoteImage(coverSha, "medium", controller, Modifier.fillMaxWidth().weight(1f), placeholder = a)
            Text(
                recipe.title, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        else -> {
            val h = if (size == 0) 34.dp else 46.dp
            val w = if (size == 0) 200.dp else 244.dp
            Row(Modifier.size(w, h).clip(RoundedCornerShape(2.dp)).background(a).clickable(onClick = onClick)) {
                if (coverSha != null) RemoteImage(coverSha, "thumb", controller, Modifier.size(h), placeholder = a)
                Text(
                    recipe.title, color = Color.White, fontSize = if (size == 0) 13.sp else 14.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Befehl in der Leiste oben rechts (Symbol + Text). */
@Composable
fun CommandButton(icon: ImageVector, text: String?, onClick: () -> Unit, tint: Color = RkColors.Text, enabled: Boolean = true) {
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, text, tint = if (enabled) tint else RkColors.TextSecondary, modifier = Modifier.size(18.dp))
        if (text != null) {
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (enabled) RkColors.Text else RkColors.TextSecondary)
        }
    }
}

@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(text, color = RkColors.Link, style = MaterialTheme.typography.bodyLarge, modifier = modifier.clickable(onClick = onClick))
}

@Composable
fun Stars(value: Long, onChange: ((Long) -> Unit)? = null, size: Dp = 22.dp) {
    Row {
        for (i in 1..5) {
            Icon(
                Icons.Filled.Star, null,
                tint = if (i <= value) accent else Color(0xFFDDDDDD),
                modifier = Modifier.size(size).then(
                    if (onChange != null) Modifier.clickable { onChange(if (value == i.toLong()) 0L else i.toLong()) } else Modifier,
                ),
            )
        }
    }
}

/** Eingabefeld im Stil von Recipe Keeper: graue Fläche, keine Umrandung. */
@Composable
fun RkField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Dp = 36.dp,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    onDone: (() -> Unit)? = null,
) {
    val a = accent
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = RkColors.Text),
        cursorBrush = SolidColor(a),
        keyboardOptions = if (onDone != null) keyboard.copy(imeAction = ImeAction.Done) else keyboard,
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onSearch = { onDone?.invoke() }),
        modifier = modifier.heightIn(min = minHeight).clip(RoundedCornerShape(3.dp)).background(RkColors.Field).padding(horizontal = 8.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = RkColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        },
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = RkColors.Text, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

/** Feld, das eine Auswahl anzeigt und beim Klick öffnet (Rezeptarten, Kategorien …). */
@Composable
fun SelectField(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(3.dp)).background(RkColors.Field).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ArrowDropDown, null, tint = RkColors.TextSecondary)
    }
}

/** Mehrfachauswahl mit Häkchen, "Neue hinzufügen" und Bestätigen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSelectDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: List<String>,
    onAdd: (String) -> String?,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val chosen = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    var newName by remember { mutableStateOf("") }
    val a = accent
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RkColors.SurfaceHigh,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.widthIn(min = 260.dp, max = 420.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2) {
                    options.forEach { (id, name) ->
                        Row(Modifier.fillMaxWidth(0.5f).clickable { if (id in chosen) chosen.remove(id) else chosen.add(id) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = id in chosen, onCheckedChange = { if (it) chosen.add(id) else chosen.remove(id) },
                                colors = CheckboxDefaults.colors(checkedColor = a, checkmarkColor = Color.White),
                            )
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val add = { onAdd(newName)?.let { if (it !in chosen) chosen.add(it) }; newName = "" }
                    RkField(newName, { newName = it }, Modifier.weight(1f), placeholder = "Neue hinzufügen", onDone = add)
                    CommandButton(Icons.Filled.Add, null, add)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(chosen.toList()) }) { Text("OK", color = a) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = RkColors.Text) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = RkColors.SurfaceHigh,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = RkColors.Text) } },
    )
}

/** Leerer Zustand mit Symbol, Text und optional einer Schaltfläche (wie "Kochbücher"). */
@Composable
fun EmptyState(icon: ImageVector, text: String, button: String? = null, onButton: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(64.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = RkColors.Text, modifier = Modifier.widthIn(max = 480.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (button != null) AccentButton(button, onButton)
        }
    }
}

@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        color = if (enabled) accentFill else RkColors.SurfaceHigh, shape = RoundedCornerShape(3.dp),
        modifier = modifier.clip(RoundedCornerShape(3.dp)).clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

/** Abschnittsüberschrift im Rezept ("Zutaten", "Zubereitung") in Akzentfarbe. */
@Composable
fun AccentHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, color = accent, fontSize = 19.sp, fontWeight = FontWeight.Normal, modifier = modifier.padding(bottom = 4.dp))
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

/** Platzhalter für Rezepte ohne Foto: Schüssel mit Schneebesen auf der Akzentfarbe. */
@Composable
fun RecipePlaceholder(modifier: Modifier) {
    val fill = accentFill
    Canvas(modifier.background(lerp(fill, Color.White, 0.3f))) {
        val w = size.width
        val h = size.height
        val ink = fill
        // Schneebesen: Griff und drei Drahtschlaufen
        val handleStart = Offset(w * 0.70f, h * 0.22f)
        val handleEnd = Offset(w * 0.58f, h * 0.36f)
        drawLine(ink, handleStart, handleEnd, strokeWidth = w * 0.035f, cap = StrokeCap.Round)
        for (k in -1..1) {
            val path = Path().apply {
                moveTo(handleEnd.x, handleEnd.y)
                quadraticTo(w * (0.47f + k * 0.05f), h * (0.38f + k * 0.03f), w * 0.42f, h * 0.56f)
            }
            drawPath(path, ink, style = Stroke(width = w * 0.012f, cap = StrokeCap.Round))
        }
        // Schüssel
        val bowl = Path().apply {
            moveTo(w * 0.24f, h * 0.52f)
            lineTo(w * 0.76f, h * 0.52f)
            cubicTo(w * 0.76f, h * 0.70f, w * 0.64f, h * 0.78f, w * 0.50f, h * 0.78f)
            cubicTo(w * 0.36f, h * 0.78f, w * 0.24f, h * 0.70f, w * 0.24f, h * 0.52f)
            close()
        }
        drawPath(bowl, ink)
        drawRect(ink, topLeft = Offset(w * 0.42f, h * 0.79f), size = Size(w * 0.16f, h * 0.025f))
    }
}
