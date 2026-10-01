package de.rezeptkiste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import de.rezeptkiste.AppController
import de.rezeptkiste.PlatformServices
import de.rezeptkiste.Screen
import de.rezeptkiste.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun App(platform: PlatformServices) {
    val scope = rememberCoroutineScope()
    val controller = remember { AppController(platform, scope) }
    val loggedIn by controller.loggedIn.collectAsState()
    val accentArgb by controller.accent.collectAsState()
    val textScale by controller.textScale.collectAsState()

    RezeptTheme(accentArgb, textScale) {
        Surface(modifier = Modifier.fillMaxSize(), color = RkColors.Background) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (loggedIn) Shell(controller) else LoginScreen(controller)
                MessageBar(controller)
            }
        }
    }
}

@Composable
private fun MessageBar(controller: AppController) {
    val msg by controller.message.collectAsState()
    LaunchedEffect(msg) {
        if (msg != null) {
            delay(3500)
            controller.clearMessage()
        }
    }
    msg?.let {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(color = RkColors.SurfaceHigh, shape = RoundedCornerShape(4.dp)) {
                Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

/** Bildschirme ohne Navigationsleiste (wie "Rezept bearbeiten" in Recipe Keeper). */
private fun Screen.isFullScreen() = this is Screen.Edit || this is Screen.TextImport

@Composable
fun Shell(controller: AppController) {
    val screen = controller.stack.last()
    val platform = controller.platform
    platform.BackHandler(enabled = controller.stack.size > 1) { controller.back() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val narrowContent = maxWidth < 600.dp
        if (screen.isFullScreen()) {
            ScreenContent(controller, screen, narrowContent)
        } else if (wide) {
            var expanded by rememberSaveable { mutableStateOf(true) }
            Row(Modifier.fillMaxSize()) {
                NavPane(controller, expanded, onToggle = { expanded = !expanded }, onPicked = {})
                Box(Modifier.weight(1f).fillMaxHeight().background(RkColors.Background)) {
                    ScreenContent(controller, screen, narrowContent)
                }
            }
        } else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = RkColors.Pane, modifier = Modifier.width(280.dp)) {
                        NavPane(controller, expanded = true, onToggle = { scope.launch { drawer.close() } }, onPicked = { scope.launch { drawer.close() } })
                    }
                },
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().background(RkColors.Pane).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PaneIcon(Icons.Filled.Menu, "Menü") { scope.launch { drawer.open() } }
                        if (controller.stack.size > 1) PaneIcon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") { controller.back() }
                        Spacer(Modifier.weight(1f))
                        SyncIcon(controller)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) { ScreenContent(controller, screen, narrowContent) }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(controller: AppController, screen: Screen, narrow: Boolean) {
    when (screen) {
        Screen.Start -> StartScreen(controller, narrow)
        is Screen.Course -> CourseScreen(controller, screen, narrow)
        is Screen.Recipes -> RecipesScreen(controller, screen.title, screen.query, narrow)
        is Screen.Search -> SearchResultsScreen(controller, screen.text, narrow)
        Screen.AdvancedSearch -> AdvancedSearchScreen(controller, narrow)
        is Screen.Detail -> RecipeDetailScreen(controller, screen, narrow)
        is Screen.Edit -> RecipeEditScreen(controller, screen, narrow)
        Screen.TextImport -> TextImportScreen(controller, narrow)
        is Screen.Settings -> SettingsScreen(controller, screen.tab, narrow)
        is Screen.Placeholder -> PlaceholderScreen(screen)
        Screen.Help -> HelpScreen()
    }
}

@Composable
private fun PaneIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = RkColors.Text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SyncIcon(controller: AppController) {
    val sync by controller.sync.collectAsState()
    val tint = when (sync) {
        SyncStatus.Running -> RkColors.TextSecondary
        is SyncStatus.Failed -> RkColors.Error
        else -> RkColors.Text
    }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).clickable { controller.syncNow() }, contentAlignment = Alignment.Center) {
        Icon(if (sync is SyncStatus.Failed) Icons.Filled.Warning else Icons.Filled.Refresh, "Sync", tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Linke Navigationsleiste wie in Recipe Keeper; eingeklappt nur Symbole. */
@Composable
private fun NavPane(controller: AppController, expanded: Boolean, onToggle: () -> Unit, onPicked: () -> Unit) {
    val screen = controller.stack.last()
    val sync by controller.sync.collectAsState()
    val width = if (expanded) 236.dp else 48.dp
    var newMenu by remember { mutableStateOf(false) }

    fun open(s: Screen) { controller.root(s); onPicked() }

    Column(Modifier.width(width).fillMaxHeight().background(RkColors.Pane).padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PaneIcon(Icons.Filled.Menu, "Menü", onToggle)
            if (expanded && controller.stack.size > 1) PaneIcon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") { controller.back() }
        }
        if (expanded) {
            SearchBox(controller, onPicked)
            Row(Modifier.fillMaxWidth().padding(end = 10.dp, top = 2.dp, bottom = 4.dp)) {
                Spacer(Modifier.weight(1f))
                LinkText("Erweiterte Suche", { controller.go(Screen.AdvancedSearch); onPicked() })
            }
        } else {
            NavItem(Icons.Filled.Search, "Suchen", false, expanded) { onToggle() }
        }
        Box {
            NavItem(Icons.Filled.Add, "Neues Rezept", false, expanded) { newMenu = true }
            DropdownMenu(newMenu, onDismissRequest = { newMenu = false }, containerColor = RkColors.SurfaceHigh) {
                DropdownMenuItem(text = { Text("Neues Rezept hinzufügen") }, onClick = { newMenu = false; controller.go(Screen.Edit(null)); onPicked() })
                DropdownMenuItem(text = { Text("Rezept von der Website importieren") }, onClick = {
                    newMenu = false; controller.go(Screen.Placeholder("Rezept importieren", "Der Import von Webseiten folgt im nächsten Schritt. Bis dahin: Text der Seite kopieren und über \"Rezept aus Text hinzufügen\" einfügen.")); onPicked()
                })
                DropdownMenuItem(text = { Text("Rezept vom Foto scannen") }, onClick = {
                    newMenu = false; controller.go(Screen.Placeholder("Rezept scannen", "Der Foto-Scan mit Texterkennung folgt im nächsten Schritt.")); onPicked()
                })
                DropdownMenuItem(text = { Text("Rezept aus PDF scannen") }, onClick = {
                    newMenu = false; controller.go(Screen.Placeholder("Rezept aus PDF", "Der PDF-Scan folgt im nächsten Schritt.")); onPicked()
                })
                DropdownMenuItem(text = { Text("Rezept aus Text hinzufügen") }, onClick = { newMenu = false; controller.go(Screen.TextImport); onPicked() })
            }
        }
        NavItem(Icons.Filled.Home, "Start", screen is Screen.Start || screen is Screen.Course || screen is Screen.Recipes || screen is Screen.Detail, expanded) { open(Screen.Start) }
        NavItem(Icons.AutoMirrored.Filled.List, "Einkaufsliste", (screen as? Screen.Placeholder)?.title == "Einkaufsliste", expanded) {
            open(Screen.Placeholder("Einkaufsliste", "Die Einkaufsliste mit Sortierung nach Gängen im Markt kommt in Version 2."))
        }
        NavItem(Icons.Filled.DateRange, "Speiseplan", (screen as? Screen.Placeholder)?.title == "Speiseplan", expanded) {
            open(Screen.Placeholder("Speiseplan", "Der Wochen- und Monatsplaner kommt in Version 2."))
        }
        NavItem(Icons.Outlined.Create, "Kochbücher", (screen as? Screen.Placeholder)?.title == "Kochbücher", expanded) {
            open(Screen.Placeholder("Kochbücher", "Kochbücher als PDF mit Deckblatt und Inhaltsverzeichnis kommen in Version 2."))
        }
        NavItem(Icons.Filled.Notifications, "Neuer Timer", (screen as? Screen.Placeholder)?.title == "Timer", expanded) {
            open(Screen.Placeholder("Timer", "Timer kommen in Version 2."))
        }
        Spacer(Modifier.weight(1f))
        NavItem(if (sync is SyncStatus.Failed) Icons.Filled.Warning else Icons.Filled.Refresh, syncText(sync), false, expanded, tint = if (sync is SyncStatus.Failed) RkColors.Error else RkColors.Text) {
            controller.syncNow()
        }
        NavItem(Icons.Filled.Info, "Hilfe", screen is Screen.Help, expanded) { open(Screen.Help) }
        NavItem(Icons.Filled.Settings, "Einstellungen", screen is Screen.Settings, expanded) { open(Screen.Settings()) }
    }
}

private fun syncText(s: SyncStatus) = when (s) {
    SyncStatus.Running -> "Synchronisiere …"
    is SyncStatus.Failed -> "Sync: ${s.message}"
    else -> "Sync"
}

@Composable
private fun NavItem(icon: ImageVector, text: String, selected: Boolean, expanded: Boolean, tint: Color = RkColors.Text, onClick: () -> Unit) {
    val a = accent
    Row(Modifier.fillMaxWidth().height(38.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(24.dp).background(if (selected) a else Color.Transparent))
        Spacer(Modifier.width(9.dp))
        Icon(icon, text, tint = if (selected) a else tint, modifier = Modifier.size(20.dp))
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                text, color = if (selected) a else RkColors.Text, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Suchfeld mit Vorschlägen beim Tippen; Enter öffnet die Ergebnisseite. */
@Composable
private fun SearchBox(controller: AppController, onPicked: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val recipes by controller.recipes.collectAsState()
    val photos by controller.photos.collectAsState()
    val suggestions = remember(query, recipes) {
        if (query.length < 2) emptyList() else recipes.filter { it.title.contains(query.trim(), ignoreCase = true) }.take(8)
    }
    val cover = remember(photos) { photos.groupBy { it.recipe_id }.mapValues { (_, p) -> p.minBy { it.sort_order }.sha256 } }
    fun submit() {
        if (query.isNotBlank()) { controller.go(Screen.Search(query.trim())); onPicked() }
    }
    Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(RkColors.Field), verticalAlignment = Alignment.CenterVertically) {
            RkField(
                query, { query = it }, Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                placeholder = "Rezepte suchen",
                keyboard = KeyboardOptions(imeAction = ImeAction.Search), onDone = ::submit,
            )
            Icon(Icons.Filled.Search, "Suchen", tint = RkColors.TextSecondary, modifier = Modifier.padding(end = 8.dp).size(18.dp).clickable { submit() })
        }
        DropdownMenu(
            expanded = focused && suggestions.isNotEmpty(),
            onDismissRequest = { focused = false },
            properties = PopupProperties(focusable = false),
            containerColor = RkColors.SurfaceHigh,
        ) {
            suggestions.forEach { r ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RemoteImage(cover[r.id], "thumb", controller, Modifier.size(34.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Normal)
                        }
                    },
                    onClick = {
                        controller.markViewed(r.id)
                        controller.go(Screen.Detail(r.id, suggestions.map { it.id }))
                        query = ""
                        onPicked()
                    },
                )
            }
        }
    }
}
