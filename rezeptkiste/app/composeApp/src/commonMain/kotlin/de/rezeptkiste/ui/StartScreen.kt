package de.rezeptkiste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Info
import de.rezeptkiste.AppController
import de.rezeptkiste.NONE
import de.rezeptkiste.RecipeQuery
import de.rezeptkiste.Screen
import de.rezeptkiste.categoryIds
import de.rezeptkiste.courseIds
import de.rezeptkiste.db.Label
import de.rezeptkiste.db.Photo
import de.rezeptkiste.db.Recipe
import de.rezeptkiste.matches
import de.rezeptkiste.sync.EntityType

/** Ansichtszustand, der Bildschirmwechsel übersteht (Reiter, Sortierung). */
object UiMemory {
    val startTab = mutableIntStateOf(0)
    val sort = mutableIntStateOf(0)
}

fun coverMap(photos: List<Photo>): Map<String, String> =
    photos.groupBy { it.recipe_id }.mapValues { (_, p) -> p.minBy { it.sort_order }.sha256 }

/** Kachel-Gruppe: Name, Anzahl, Titelbild, Ziel. */
private data class Group(val id: String, val name: String, val recipes: List<Recipe>)

private fun groups(recipes: List<Recipe>, labels: List<Label>, kind: String, ids: (Recipe) -> List<String>): List<Group> {
    val named = labels.filter { it.kind == kind }
        .map { l -> Group(l.id, l.name, recipes.filter { l.id in ids(it) }) }
        .filter { it.recipes.isNotEmpty() }
        .sortedBy { it.name.lowercase() }
    val none = recipes.filter { r -> ids(r).none { id -> labels.any { it.id == id } } }
    return if (none.isNotEmpty()) named + Group(NONE, "Keiner", none) else named
}

private fun coverOf(recipes: List<Recipe>, cover: Map<String, String>): String? =
    recipes.sortedBy { it.title.lowercase() }.firstNotNullOfOrNull { cover[it.id] }

@Composable
fun StartScreen(controller: AppController, narrow: Boolean) {
    val all by controller.recipes.collectAsState()
    val labels by controller.labels.collectAsState()
    val photos by controller.photos.collectAsState()
    val recent by controller.recent.collectAsState()
    val scope by controller.collectionScope.collectAsState()
    val cardSize by controller.cardSize.collectAsState()
    var tab by UiMemory.startTab

    val cover = remember(photos) { coverMap(photos) }
    val collections = remember(labels) { labels.filter { it.kind == EntityType.COLLECTION }.sortedBy { it.name.lowercase() } }
    val recipes = remember(all, scope) { all.filter { RecipeQuery(collectionId = scope).matches(it) } }
    val scopeName = when (scope) {
        null -> "Alle Rezepte"
        NONE -> "Keine Sammlung"
        else -> collections.firstOrNull { it.id == scope }?.name ?: "Alle Rezepte"
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
            val options = listOf("Alle Rezepte (${all.size})") + collections.map { c -> "${c.name} (${all.count { c.id in it.collectionIdsSafe() }})" } +
                listOf("(Keine Sammlungen)")
            DropdownLabel(
                "$scopeName (${recipes.size})", options,
                onSelect = { i ->
                    controller.setCollectionScope(
                        when {
                            i == 0 -> null
                            i <= collections.size -> collections[i - 1].id
                            else -> NONE
                        },
                    )
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            TextTabs(listOf("Rezeptarten", "Kategorien", "Favoriten"), tab, { tab = it }, Modifier.padding(top = 8.dp, bottom = 10.dp))
            when (tab) {
                0, 1 -> {
                    val kind = if (tab == 0) EntityType.COURSE else EntityType.CATEGORY
                    val gs = groups(recipes, labels, kind) { if (tab == 0) it.courseIds() else it.categoryIds() }
                    TileGrid {
                        gs.forEach { g ->
                            Tile(g.name, g.recipes.size, coverOf(g.recipes, cover), controller) {
                                if (tab == 0) {
                                    controller.go(Screen.Course(g.id, scope))
                                } else {
                                    controller.go(Screen.Recipes(g.name, RecipeQuery(categoryId = g.id, collectionId = scope)))
                                }
                            }
                        }
                        Tile("Alle", recipes.size, null, controller) {
                            controller.go(Screen.Recipes("$scopeName • Alle", RecipeQuery(collectionId = scope)))
                        }
                    }
                }
                else -> {
                    val favs = recipes.filter { it.is_favourite == 1L }.sortedBy { it.title.lowercase() }
                    if (favs.isEmpty()) {
                        Text("Noch keine Favoriten. Im Rezept auf \"Zu Favoriten hinzufügen\" klicken.", color = RkColors.TextSecondary)
                    } else {
                        CardGrid(favs, cover, controller, cardSize)
                    }
                }
            }
        }
        if (!narrow) {
            Column(Modifier.width(232.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 58.dp, end = 12.dp)) {
                Text("Zuletzt angesehen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                val list = recent.mapNotNull { id -> all.firstOrNull { it.id == id } }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { r ->
                        RecipeCard(r, cover[r.id], controller, 1) { openRecipe(controller, r, list) }
                    }
                }
            }
        }
    }
}

private fun Recipe.collectionIdsSafe() = de.rezeptkiste.data.decodeIds(collection_ids)

fun openRecipe(controller: AppController, r: Recipe, context: List<Recipe>) {
    controller.markViewed(r.id)
    controller.go(Screen.Detail(r.id, context.map { it.id }))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardGrid(recipes: List<Recipe>, cover: Map<String, String>, controller: AppController, size: Int) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        recipes.forEach { r -> RecipeCard(r, cover[r.id], controller, size) { openRecipe(controller, r, recipes) } }
    }
}

/** Rezeptart geöffnet: deren Kategorien als Kacheln. */
@Composable
fun CourseScreen(controller: AppController, screen: Screen.Course, narrow: Boolean) {
    val all by controller.recipes.collectAsState()
    val labels by controller.labels.collectAsState()
    val photos by controller.photos.collectAsState()
    val cover = remember(photos) { coverMap(photos) }
    val courseName = if (screen.courseId == NONE) "Keiner" else labels.firstOrNull { it.id == screen.courseId }?.name ?: "Rezeptart"
    val base = RecipeQuery(courseId = screen.courseId, collectionId = screen.collectionId)
    val recipes = remember(all, screen) { all.filter { base.matches(it) } }
    val gs = groups(recipes, labels, EntityType.CATEGORY) { it.categoryIds() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
        PageTitle(courseName)
        SectionLabel("Kategorien", Modifier.padding(top = 10.dp))
        TileGrid {
            gs.forEach { g ->
                Tile(g.name, g.recipes.size, coverOf(g.recipes, cover), controller) {
                    controller.go(Screen.Recipes("$courseName • ${g.name}", base.copy(categoryId = g.id)))
                }
            }
            Tile("Alle", recipes.size, null, controller) { controller.go(Screen.Recipes("$courseName • Alle", base)) }
        }
    }
}

private val sortNames = listOf("Sortieren nach Titel", "Sortieren nach Bewertung", "Sortieren nach Neuesten")
private val sizeNames = listOf("Klein", "Mittel", "Groß")

fun sortRecipes(list: List<Recipe>, sort: Int): List<Recipe> = when (sort) {
    1 -> list.sortedWith(compareByDescending<Recipe> { it.rating }.thenBy { it.title.lowercase() })
    2 -> list.sortedByDescending { it.updated_at }
    else -> list.sortedBy { it.title.lowercase() }
}

@Composable
private fun ListHeader(title: String, sortLabel: String, sortOptions: List<String>, sortIndex: Int, onSort: (Int) -> Unit, controller: AppController) {
    val size by controller.cardSize.collectAsState()
    PageTitle(title)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DropdownLabel(sortLabel, sortOptions, onSort, selectedIndex = sortIndex)
        Spacer(Modifier.weight(1f))
        DropdownLabel("Größe: ${sizeNames[size]}", sizeNames, { controller.setCardSize(it) }, selectedIndex = size)
    }
}

/** Rezeptkarten einer Auswahl, mit Sortierung und Kartengröße. */
@Composable
fun RecipesScreen(controller: AppController, title: String, query: RecipeQuery, narrow: Boolean) {
    val all by controller.recipes.collectAsState()
    val photos by controller.photos.collectAsState()
    val size by controller.cardSize.collectAsState()
    var sort by UiMemory.sort
    val cover = remember(photos) { coverMap(photos) }
    val list = remember(all, query, sort) { sortRecipes(all.filter { query.matches(it) }, sort) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
        ListHeader(title, sortNames[sort], sortNames, sort, { sort = it }, controller)
        if (list.isEmpty()) Text("Keine Rezepte.", color = RkColors.TextSecondary) else CardGrid(list, cover, controller, size)
    }
}

/** Relevanz: Treffer im Titel zählen mehr als in Zutaten, Zubereitung und Notizen. */
fun relevance(r: Recipe, words: List<String>): Int = words.sumOf { w ->
    (if (r.title.contains(w, true)) 10 else 0) + (if (r.ingredients_text?.contains(w, true) == true) 3 else 0) +
        (if (r.directions_text?.contains(w, true) == true) 1 else 0) + (if (r.notes?.contains(w, true) == true) 1 else 0)
}

fun Recipe.containsWord(w: String): Boolean =
    listOf(title, ingredients_text, directions_text, notes, source_name).any { it?.contains(w, ignoreCase = true) == true }

@Composable
fun SearchResultsScreen(controller: AppController, text: String, narrow: Boolean) {
    val all by controller.recipes.collectAsState()
    val photos by controller.photos.collectAsState()
    val size by controller.cardSize.collectAsState()
    val sortOptions = listOf("Sortieren nach Relevanz") + sortNames.map { it }
    var sort by remember { mutableIntStateOf(0) }
    val cover = remember(photos) { coverMap(photos) }
    val words = remember(text) { text.split(Regex("\\s+")).filter { it.isNotBlank() } }
    val list = remember(all, words, sort) {
        val hits = all.filter { r -> words.all { r.containsWord(it) } }
        if (sort == 0) hits.sortedByDescending { relevance(it, words) } else sortRecipes(hits, sort - 1)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
        ListHeader("Ergebnisse für \"$text\"", sortOptions[sort], sortOptions, sort, { sort = it }, controller)
        if (list.isEmpty()) Text("Keine Treffer.", color = RkColors.TextSecondary) else CardGrid(list, cover, controller, size)
    }
}

@Composable
fun PlaceholderScreen(screen: Screen.Placeholder) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        PageTitle(screen.title)
        EmptyState(androidx.compose.material.icons.Icons.Filled.Info, screen.text)
    }
}

@Composable
fun HelpScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle("Hilfe")
        listOf(
            "Start" to "Rezepte nach Rezeptarten, Kategorien oder Favoriten durchsuchen. Oben links lässt sich auf eine Sammlung einschränken.",
            "Neues Rezept" to "Rezept von Hand anlegen oder Text einfügen und automatisch in Zutaten und Zubereitung aufteilen lassen.",
            "Portionen" to "Im Rezept auf \"Einstellen +/-\" klicken; die Mengen werden umgerechnet. \"Permanent\" speichert die neuen Mengen.",
            "Sync" to "Änderungen werden 5 Sekunden nach dem Speichern an den Server übertragen. Ohne Netz bleibt alles lokal und wird später abgeglichen.",
            "Rezeptarten und Kategorien" to "Unter Einstellungen > Rezepte anlegen, umbenennen und löschen.",
            "Recipe-Keeper-Export" to "Unter Einstellungen > Importieren/Exportieren die ZIP-Datei auswählen.",
        ).forEach { (t, d) ->
            Column {
                Text(t, style = MaterialTheme.typography.titleMedium, color = accent)
                Text(d, color = RkColors.Text)
            }
        }
    }
}
