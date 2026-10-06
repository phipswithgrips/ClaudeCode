package de.rezeptkiste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rezeptkiste.APP_VERSION
import de.rezeptkiste.AppController
import de.rezeptkiste.SyncStatus
import de.rezeptkiste.db.Label
import de.rezeptkiste.sync.EntityType
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(controller: AppController, initialTab: Int, narrow: Boolean) {
    var tab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("Aussehen", "Synchronisierung", "Rezepte", "Importieren/Exportieren", "Info")
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        PageTitle("Einstellungen")
        Box(Modifier.fillMaxWidth().background(RkColors.Pane).padding(horizontal = 6.dp, vertical = 4.dp).padding(top = 2.dp)) {
            TextTabs(tabs, tab, { tab = it })
        }
        VSpace(10.dp)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> Appearance(controller)
                1 -> SyncTab(controller)
                2 -> LabelsTab(controller, narrow)
                3 -> ImportTab(controller)
                else -> InfoTab(controller)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Appearance(controller: AppController) {
    val current by controller.accent.collectAsState()
    val keepOn by controller.keepScreenOn.collectAsState()
    val scale by controller.textScale.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Wählen Sie ein Farbschema", style = MaterialTheme.typography.titleMedium)
        VSpace(6.dp)
        FlowRow(Modifier.width(240.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RkColors.AccentChoices.forEach { c ->
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(2.dp)).background(Color(c))
                        .then(if (c == current) Modifier.border(2.dp, Color.White, RoundedCornerShape(2.dp)) else Modifier)
                        .clickable { controller.setAccent(c) },
                )
            }
        }
        VSpace(18.dp)
        Text("Halten Sie den Bildschirm eingeschaltet, wenn Sie Rezepte anzeigen", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(keepOn, { controller.setKeepScreenOn(it) }, colors = SwitchDefaults.colors(checkedTrackColor = accentFill, checkedThumbColor = Color.White))
            Text(if (keepOn) "  Ein" else "  Aus")
        }
        VSpace(14.dp)
        Text("Textgröße", style = MaterialTheme.typography.titleMedium)
        val sizes = listOf(0.9f to "Klein", 1f to "Normal", 1.15f to "Groß", 1.3f to "Sehr groß")
        DropdownLabel(sizes.firstOrNull { it.first == scale }?.second ?: "Normal", sizes.map { it.second }, { controller.setTextScale(sizes[it].first) })
        VSpace(14.dp)
        Text("Anzeigegröße (Schrift, Abstände und Bilder)", style = MaterialTheme.typography.titleMedium)
        val zoom by controller.zoom.collectAsState()
        val zooms = listOf(0.9f, 1f, 1.1f, 1.25f, 1.4f, 1.6f)
        DropdownLabel(
            "${(zoom * 100).roundToInt()} %" + if (zoom == controller.platform.defaultZoom) " (Standard)" else "",
            zooms.map { "${(it * 100).roundToInt()} %" + if (it == controller.platform.defaultZoom) " (Standard)" else "" },
            { controller.setZoom(zooms[it]) },
        )
    }
}

@Composable
private fun SyncTab(controller: AppController) {
    val sync by controller.sync.collectAsState()
    val recipes by controller.recipes.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoLine("Server", controller.serverUrl ?: "-")
        InfoLine("Gerät", controller.defaultDeviceName)
        InfoLine("Rezepte auf diesem Gerät", recipes.size.toString())
        InfoLine(
            "Status",
            when (val s = sync) {
                SyncStatus.Idle -> "Noch nicht synchronisiert"
                SyncStatus.Running -> "Synchronisiere …"
                is SyncStatus.Done -> "Synchron (${ago(controller.platform.nowMillis() - s.atMillis)})"
                is SyncStatus.Failed -> "Fehler: ${s.message}"
            },
        )
        VSpace(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentButton("Jetzt synchronisieren", { controller.syncNow() }, enabled = sync != SyncStatus.Running)
            AccentButton("Abmelden", { controller.logout() })
        }
        Text(
            "Abmelden löscht die Rezepte nur auf diesem Gerät. Auf dem Server und den anderen Geräten bleibt alles erhalten.",
            color = RkColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun ago(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "gerade eben"
        m < 60 -> "vor $m Min."
        m < 60 * 24 -> "vor ${m / 60} Std."
        else -> "vor ${m / 1440} Tagen"
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, color = RkColors.TextSecondary, modifier = Modifier.width(200.dp))
        Text(value)
    }
}

@Composable
private fun LabelsTab(controller: AppController, narrow: Boolean) {
    val labels by controller.labels.collectAsState()
    val kinds = listOf(EntityType.COURSE to "Rezeptarten", EntityType.CATEGORY to "Kategorien", EntityType.COLLECTION to "Sammlungen")
    if (narrow) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            kinds.forEach { (k, t) -> LabelColumn(controller, t, k, labels.filter { it.kind == k }); VSpace(16.dp) }
        }
    } else {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            kinds.forEach { (k, t) ->
                Column(Modifier.width(230.dp).verticalScroll(rememberScrollState())) { LabelColumn(controller, t, k, labels.filter { it.kind == k }) }
            }
        }
    }
}

@Composable
private fun LabelColumn(controller: AppController, title: String, kind: String, items: List<Label>) {
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Label?>(null) }
    Text(title, style = MaterialTheme.typography.titleMedium)
    VSpace(6.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val add = { controller.addLabel(kind, newName); newName = "" }
        RkField(newName, { newName = it }, Modifier.weight(1f), placeholder = "Neue hinzufügen", onDone = add)
        CommandButton(Icons.Filled.Add, null, add)
    }
    VSpace(4.dp)
    items.sortedBy { it.name.lowercase() }.forEach { l ->
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editing == l.id) {
                val done = { controller.renameLabel(l.id, editText); editing = null }
                RkField(editText, { editText = it }, Modifier.weight(1f), onDone = done)
                CommandButton(Icons.Filled.Done, null, done)
            } else {
                Text(l.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                CommandButton(Icons.Filled.Edit, null, { editing = l.id; editText = l.name }, tint = RkColors.TextSecondary)
                CommandButton(Icons.Filled.Close, null, { deleting = l }, tint = RkColors.TextSecondary)
            }
        }
    }
    deleting?.let { l ->
        ConfirmDialog(
            "\"${l.name}\" löschen", "Der Eintrag wird aus allen Rezepten entfernt. Die Rezepte selbst bleiben erhalten.", "Löschen",
            { deleting = null },
        ) { controller.deleteLabel(l.id); deleting = null }
    }
}

@Composable
private fun ImportTab(controller: AppController) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rezepte importieren von", style = MaterialTheme.typography.titleMedium)
        AccentButton("Recipe Keeper .zip Datei", { controller.importRecipeKeeper() }, Modifier.width(320.dp))
        Text(
            "Der Export wird auf den Server geladen und dort importiert. Bereits übernommene Rezepte werden übersprungen; " +
                "Kategorien, die sich nur in Schreibweise oder Tippfehler unterscheiden, werden zusammengeführt.",
            color = RkColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(520.dp),
        )
        VSpace(10.dp)
        Text("Rezepte exportieren", style = MaterialTheme.typography.titleMedium)
        Text("Der Export als Datei folgt. Bis dahin sichert der Server alle Rezepte jede Nacht.", color = RkColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoTab(controller: AppController) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Cookfolio", style = MaterialTheme.typography.headlineSmall, color = accent)
        Text("Version $APP_VERSION")
        Text("Private Rezeptverwaltung mit Synchronisierung über den eigenen Server.", color = RkColors.TextSecondary)
        Spacer(Modifier.size(4.dp))
        InfoLine("Server", controller.serverUrl ?: "-")
    }
}
