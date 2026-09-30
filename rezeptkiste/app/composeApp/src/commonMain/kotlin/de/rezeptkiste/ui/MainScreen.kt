package de.rezeptkiste.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.rezeptkiste.AppController
import de.rezeptkiste.PlatformServices
import de.rezeptkiste.SyncStatus
import de.rezeptkiste.db.Label
import de.rezeptkiste.db.Photo
import de.rezeptkiste.db.Recipe
import de.rezeptkiste.sync.EntityType
import kotlinx.coroutines.launch

sealed interface Filter {
    data object All : Filter
    data object Favourites : Filter
    data class ByLabel(val id: String, val name: String) : Filter
}

@Composable
fun MainScreen(controller: AppController, platform: PlatformServices) {
    val recipes by controller.recipes.collectAsState()
    val labels by controller.labels.collectAsState()
    val photos by controller.photos.collectAsState()
    val sync by controller.sync.collectAsState()

    var filter by remember { mutableStateOf<Filter>(Filter.All) }
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }

    val coverBySha = remember(photos) { photos.groupBy { it.recipe_id }.mapValues { (_, p) -> p.minBy { it.sort_order }.sha256 } }
    val photosByRecipe = remember(photos) { photos.groupBy { it.recipe_id }.mapValues { (_, p) -> p.sortedBy { it.sort_order } } }
    val labelsById = remember(labels) { labels.associateBy { it.id } }
    val visible = remember(recipes, filter, query) {
        recipes.filter { r ->
            r.matches(query) && when (val f = filter) {
                Filter.All -> true
                Filter.Favourites -> r.is_favourite == 1L
                is Filter.ByLabel -> f.id in r.labelIds()
            }
        }
    }
    val selected = remember(recipes, selectedId) { recipes.firstOrNull { it.id == selectedId } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val nav: @Composable (onPicked: () -> Unit) -> Unit = { onPicked ->
            NavPanel(
                labels = labels, recipes = recipes, current = filter, sync = sync,
                onSelect = { filter = it; selectedId = null; onPicked() },
                onSync = controller::syncNow, onLogout = controller::logout,
            )
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(248.dp).fillMaxHeight().background(RkColors.Surface)) { nav {} }
                VerticalDivider(color = RkColors.Line)
                Column(Modifier.width(380.dp).fillMaxHeight()) {
                    ListHeader(title = filterTitle(filter), query = query, onQuery = { query = it }, sync = sync, onSync = controller::syncNow, onMenu = null)
                    RecipeList(visible, selectedId, coverBySha, labelsById, controller) { selectedId = it.id }
                }
                VerticalDivider(color = RkColors.Line)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (selected != null) {
                        RecipeDetail(selected, photosByRecipe[selected.id].orEmpty(), labelsById, controller, onBack = null)
                    } else {
                        Placeholder(if (recipes.isEmpty()) emptyText(sync) else "Rezept auswählen")
                    }
                }
            }
        } else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            platform.BackHandler(enabled = selected != null || drawer.isOpen) {
                if (drawer.isOpen) scope.launch { drawer.close() } else selectedId = null
            }
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = RkColors.Surface) { nav { scope.launch { drawer.close() } } }
                },
            ) {
                if (selected != null) {
                    RecipeDetail(selected, photosByRecipe[selected.id].orEmpty(), labelsById, controller, onBack = { selectedId = null })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        ListHeader(
                            title = filterTitle(filter), query = query, onQuery = { query = it }, sync = sync,
                            onSync = controller::syncNow, onMenu = { scope.launch { drawer.open() } },
                        )
                        if (recipes.isEmpty()) Placeholder(emptyText(sync))
                        else RecipeList(visible, selectedId, coverBySha, labelsById, controller) { selectedId = it.id }
                    }
                }
            }
        }
    }
}

private fun filterTitle(f: Filter) = when (f) {
    Filter.All -> "Alle Rezepte"
    Filter.Favourites -> "Favoriten"
    is Filter.ByLabel -> f.name
}

private fun emptyText(sync: SyncStatus) = when (sync) {
    SyncStatus.Running -> "Rezepte werden geladen …"
    is SyncStatus.Failed -> "Noch keine Rezepte. Sync fehlgeschlagen: ${sync.message}"
    else -> "Noch keine Rezepte."
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = RkColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ListHeader(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    sync: SyncStatus,
    onSync: () -> Unit,
    onMenu: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onMenu != null) {
                TextButton(onClick = onMenu) { Text("☰", style = MaterialTheme.typography.titleLarge, color = RkColors.Text) }
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onMenu != null) SyncButton(sync, onSync)
        }
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            placeholder = { Text("Suchen in Titel, Zutaten, Zubereitung") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun SyncButton(sync: SyncStatus, onSync: () -> Unit) {
    val (symbol, color) = when (sync) {
        SyncStatus.Running -> "⟳ …" to RkColors.TextSecondary
        is SyncStatus.Failed -> "⟳ !" to RkColors.Error
        else -> "⟳" to RkColors.Accent
    }
    TextButton(onClick = onSync, enabled = sync != SyncStatus.Running) {
        Text(symbol, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NavPanel(
    labels: List<Label>,
    recipes: List<Recipe>,
    current: Filter,
    sync: SyncStatus,
    onSelect: (Filter) -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit,
) {
    val counts = remember(recipes) {
        val m = mutableMapOf<String, Int>()
        recipes.forEach { r -> r.labelIds().forEach { m[it] = (m[it] ?: 0) + 1 } }
        m
    }
    LazyColumn(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        item {
            Text("Rezeptkiste", style = MaterialTheme.typography.titleLarge, color = RkColors.Accent, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        item { NavItem("Alle Rezepte", recipes.size, current == Filter.All) { onSelect(Filter.All) } }
        item { NavItem("Favoriten", recipes.count { it.is_favourite == 1L }, current == Filter.Favourites) { onSelect(Filter.Favourites) } }
        for ((kind, heading) in listOf(EntityType.COURSE to "Gänge", EntityType.CATEGORY to "Kategorien", EntityType.COLLECTION to "Sammlungen")) {
            val group = labels.filter { it.kind == kind && (counts[it.id] ?: 0) > 0 }
            if (group.isEmpty()) continue
            item {
                Text(heading, style = MaterialTheme.typography.labelLarge, color = RkColors.TextSecondary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
            }
            items(group, key = { it.id }) { l ->
                NavItem(l.name, counts[l.id] ?: 0, (current as? Filter.ByLabel)?.id == l.id) { onSelect(Filter.ByLabel(l.id, l.name)) }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = RkColors.Line)
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SyncButton(sync, onSync)
                Text(syncLabel(sync), style = MaterialTheme.typography.bodySmall, color = RkColors.TextSecondary, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onLogout, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Abmelden", color = RkColors.TextSecondary)
            }
        }
    }
}

private fun syncLabel(sync: SyncStatus) = when (sync) {
    SyncStatus.Idle -> "Nicht synchronisiert"
    SyncStatus.Running -> "Synchronisiere …"
    is SyncStatus.Done -> "Synchron"
    is SyncStatus.Failed -> sync.message
}

@Composable
private fun NavItem(text: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) RkColors.SurfaceHigh else RkColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) RkColors.Accent else RkColors.Text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(count.toString(), color = RkColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecipeList(
    recipes: List<Recipe>,
    selectedId: String?,
    cover: Map<String, String>,
    labelsById: Map<String, Label>,
    controller: AppController,
    onOpen: (Recipe) -> Unit,
) {
    if (recipes.isEmpty()) {
        Placeholder("Keine Treffer")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(recipes, key = { it.id }) { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (r.id == selectedId) RkColors.SurfaceHigh else RkColors.Background)
                    .clickable { onOpen(r) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteImage(cover[r.id], "thumb", controller, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = r.labelIds().mapNotNull { labelsById[it]?.name }.joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = RkColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (r.rating > 0) Text(stars(r.rating), color = RkColors.Accent, style = MaterialTheme.typography.bodySmall)
                }
                if (r.is_favourite == 1L) Text("♥", color = RkColors.Accent, modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider(color = RkColors.Line, modifier = Modifier.padding(start = 88.dp))
        }
    }
}

/** Bild vom Server (mit Plattenspeicher-Cache); ohne Bild eine ruhige Fläche. */
@Composable
fun RemoteImage(sha256: String?, size: String, controller: AppController, modifier: Modifier) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, sha256, size) {
        value = sha256?.let { controller.image(it, size) }
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Surface(modifier = modifier, color = RkColors.SurfaceHigh) {}
    }
}
