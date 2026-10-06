package de.rezeptkiste

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import de.rezeptkiste.data.DraftPhoto
import de.rezeptkiste.data.RecipeDraft
import de.rezeptkiste.data.Repository
import de.rezeptkiste.data.decodeIds
import de.rezeptkiste.data.encodeIds
import de.rezeptkiste.db.Label
import de.rezeptkiste.db.Photo
import de.rezeptkiste.db.Recipe
import de.rezeptkiste.db.RezeptDatabase
import de.rezeptkiste.sync.Api
import de.rezeptkiste.sync.ApiException
import de.rezeptkiste.sync.AppJson
import de.rezeptkiste.sync.EntityType
import de.rezeptkiste.sync.Hlc
import de.rezeptkiste.sync.SyncEngine
import de.rezeptkiste.sync.UnauthorizedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Done(val atMillis: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/** Eingebauter Timer (Windows; auf Android nur, wenn keine Uhr-App da ist). */
data class RunningTimer(
    val id: Long,
    val label: String,
    val totalSec: Long,
    /** Ende in Millisekunden; bei pausierten Timern unbenutzt. */
    val endsAt: Long,
    /** Restzeit in Sekunden, wenn pausiert. */
    val pausedLeft: Long? = null,
    val done: Boolean = false,
) {
    fun leftSec(now: Long): Long = pausedLeft ?: ((endsAt - now + 999) / 1000).coerceAtLeast(0)
}

data class LoginState(val busy: Boolean = false, val error: String? = null)

/** Markiert "ohne Rezeptart/Kategorie/Sammlung" in Filtern. */
const val NONE = "~none"

/** Welche Rezepte eine Liste zeigt. null = kein Filter. */
data class RecipeQuery(
    val courseId: String? = null,
    val categoryId: String? = null,
    val collectionId: String? = null,
    val favourites: Boolean = false,
)

/** Bildschirme mit Rücksprung-Stapel wie in Recipe Keeper. */
sealed interface Screen {
    data object Start : Screen
    data class Course(val courseId: String, val collectionId: String?) : Screen
    data class Recipes(val title: String, val query: RecipeQuery) : Screen
    data class Search(val text: String) : Screen
    data object AdvancedSearch : Screen
    data class Detail(val recipeId: String, val context: List<String>) : Screen
    data class Edit(val recipeId: String?, val prefill: RecipeDraft? = null) : Screen
    data object TextImport : Screen
    data class Settings(val tab: Int = 0) : Screen
    data class Placeholder(val title: String, val text: String) : Screen
    data object Help : Screen
}

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

/** Zustand und Aktionen der App, unabhängig von der Plattform. */
class AppController(val platform: PlatformServices, private val scope: CoroutineScope) {

    private val db = RezeptDatabase(platform.sqlDriver)
    private val repo = Repository(db)

    private val hlc = Hlc(nodeName(), platform::nowMillis)
    private var api: Api? = null
    private var engine: SyncEngine? = null
    private var token: String? = null

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _login = MutableStateFlow(LoginState())
    val login: StateFlow<LoginState> = _login.asStateFlow()

    private val _sync = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val sync: StateFlow<SyncStatus> = _sync.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }
    fun toast(text: String) { _message.value = text }

    /** Teilen: Android öffnet das Teilen-Menü, Windows kopiert in die Zwischenablage. */
    fun share(title: String, text: String) {
        platform.shareText(title, text)
        if (platform.isDesktop) toast("Rezept als Text in die Zwischenablage kopiert.")
    }

    val recipes: StateFlow<List<Recipe>> = repo.recipes().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val labels: StateFlow<List<Label>> = repo.labels().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val photos: StateFlow<List<Photo>> = repo.photos().stateIn(scope, SharingStarted.Eagerly, emptyList())

    // --- Einstellungen und Ansicht --------------------------------------------------

    private val _accent = MutableStateFlow(repo.setting(KEY_ACCENT)?.toLongOrNull() ?: DEFAULT_ACCENT)
    val accent: StateFlow<Long> = _accent.asStateFlow()
    fun setAccent(argb: Long) { _accent.value = argb; repo.setSetting(KEY_ACCENT, argb.toString()) }

    private val _textScale = MutableStateFlow(repo.setting(KEY_TEXT_SCALE)?.toFloatOrNull() ?: 1f)
    val textScale: StateFlow<Float> = _textScale.asStateFlow()
    fun setTextScale(v: Float) { _textScale.value = v; repo.setSetting(KEY_TEXT_SCALE, v.toString()) }

    private val _zoom = MutableStateFlow(repo.setting(KEY_ZOOM)?.toFloatOrNull() ?: platform.defaultZoom)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()
    fun setZoom(v: Float) { _zoom.value = v; repo.setSetting(KEY_ZOOM, v.toString()) }

    private val _keepScreenOn = MutableStateFlow(repo.setting(KEY_SCREEN_ON) != "0")
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()
    fun setKeepScreenOn(v: Boolean) { _keepScreenOn.value = v; repo.setSetting(KEY_SCREEN_ON, if (v) "1" else "0") }

    private val _cardSize = MutableStateFlow(repo.setting(KEY_CARD_SIZE)?.toIntOrNull() ?: 1)
    val cardSize: StateFlow<Int> = _cardSize.asStateFlow()
    fun setCardSize(v: Int) { _cardSize.value = v; repo.setSetting(KEY_CARD_SIZE, v.toString()) }

    private val _recent = MutableStateFlow(decodeIds(repo.setting(KEY_RECENT)))
    val recent: StateFlow<List<String>> = _recent.asStateFlow()
    fun markViewed(id: String) {
        val list = (listOf(id) + _recent.value.filter { it != id }).take(12)
        _recent.value = list
        repo.setSetting(KEY_RECENT, encodeIds(list))
    }

    /** Sammlung, auf die die Startseite eingeschränkt ist (null = alle, NONE = ohne Sammlung). */
    private val _collectionScope = MutableStateFlow<String?>(null)
    val collectionScope: StateFlow<String?> = _collectionScope.asStateFlow()
    fun setCollectionScope(id: String?) { _collectionScope.value = id }

    // --- Navigation -------------------------------------------------------------------

    val stack = mutableStateListOf<Screen>(Screen.Start)
    val current: Screen get() = stack.last()
    fun go(screen: Screen) { stack.add(screen) }
    fun replace(screen: Screen) { stack[stack.lastIndex] = screen }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun root(screen: Screen) { stack.clear(); stack.add(screen) }

    val defaultDeviceName: String get() = repo.setting(Repository.KEY_DEVICE_NAME) ?: platform.defaultDeviceName
    val serverUrl: String? get() = repo.setting(Repository.KEY_SERVER)
    val lastSyncMillis: Long? get() = repo.setting(Repository.KEY_LAST_SYNC)?.toLongOrNull()

    private var debounceJob: Job? = null

    init {
        val url = repo.setting(Repository.KEY_SERVER)
        val saved = platform.secureStore.load()
        if (url != null && saved != null) {
            connect(url, saved)
            _loggedIn.value = true
            syncNow()
        }
    }

    private fun nodeName(): String =
        repo.setting(Repository.KEY_NODE) ?: ("n" + Random.nextLong().toULong().toString(16).take(10)).also {
            repo.setSetting(Repository.KEY_NODE, it)
        }

    private fun connect(url: String, newToken: String) {
        api?.close()
        token = newToken
        val client = Api(url, { token })
        api = client
        engine = SyncEngine(client, repo, hlc, localFile = { sha -> platform.readCache("original/$sha") })
    }

    // --- Anmeldung ------------------------------------------------------------------

    fun login(url: String, username: String, password: String, deviceName: String) {
        if (_login.value.busy) return
        _login.value = LoginState(busy = true)
        scope.launch {
            val probe = Api(url, { null })
            try {
                val result = withContext(Dispatchers.Default) { probe.login(username.trim(), password, deviceName.trim()) }
                platform.secureStore.save(result.token)
                repo.setSetting(Repository.KEY_SERVER, probe.baseUrl)
                repo.setSetting(Repository.KEY_DEVICE_NAME, deviceName.trim())
                connect(probe.baseUrl, result.token)
                _login.value = LoginState()
                _loggedIn.value = true
                syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _login.value = LoginState(error = e.message)
            } catch (e: Exception) {
                _login.value = LoginState(error = "Server nicht erreichbar: ${e.message ?: e::class.simpleName}")
            } finally {
                probe.close()
            }
        }
    }

    /** Abmelden: Token und alle lokalen Daten dieses Geräts löschen. */
    fun logout() {
        scope.launch(Dispatchers.Default) {
            platform.secureStore.clear()
            repo.clearAll()
            platform.clearCache()
            imageMutex.withLock { imageCache.clear() }
            api?.close()
            api = null
            engine = null
            token = null
            _recent.value = emptyList()
            _sync.value = SyncStatus.Idle
            withContext(Dispatchers.Main) { root(Screen.Start) }
            _loggedIn.value = false
        }
    }

    // --- Sync ------------------------------------------------------------------------

    fun syncNow() {
        val e = engine ?: return
        if (_sync.value == SyncStatus.Running) return
        _sync.value = SyncStatus.Running
        scope.launch {
            _sync.value = try {
                withContext(Dispatchers.Default) { e.sync() }
                val now = platform.nowMillis()
                repo.setSetting(Repository.KEY_LAST_SYNC, now.toString())
                SyncStatus.Done(now)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: UnauthorizedException) {
                SyncStatus.Failed(ex.message ?: "Nicht angemeldet")
            } catch (ex: ApiException) {
                SyncStatus.Failed(ex.message ?: "Serverfehler")
            } catch (ex: Exception) {
                SyncStatus.Failed("Offline")
            }
        }
    }

    /** Sync 5 Sekunden nach der letzten lokalen Änderung. */
    private fun syncSoon() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(5_000)
            syncNow()
        }
    }

    // --- Timer ------------------------------------------------------------------------

    private val _timers = MutableStateFlow<List<RunningTimer>>(emptyList())
    val timers: StateFlow<List<RunningTimer>> = _timers.asStateFlow()
    private var timerSeq = 0L
    private var ticker: Job? = null

    /** Zeitangabe aus einem Rezept antippen: Android-Uhr, sonst eingebauter Timer. */
    fun startTimer(seconds: Long, label: String) {
        if (seconds <= 0) return
        if (platform.startSystemTimer(seconds, label)) return
        startInAppTimer(seconds, label)
    }

    fun startInAppTimer(seconds: Long, label: String) {
        val t = RunningTimer(++timerSeq, label, seconds, platform.nowMillis() + seconds * 1000)
        _timers.value = _timers.value + t
        toast("Timer gestartet: ${de.rezeptkiste.data.RecipeText.formatSeconds(seconds)}")
        ensureTicker()
    }

    private fun ensureTicker() {
        if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (_timers.value.any { !it.done }) {
                    delay(250)
                    val now = platform.nowMillis()
                    val finished = _timers.value.filter { !it.done && it.pausedLeft == null && it.endsAt <= now }
                    if (finished.isNotEmpty()) {
                        _timers.value = _timers.value.map { if (it in finished) it.copy(done = true) else it }
                        finished.forEach { platform.timerFinished(it.label) }
                    }
                }
            }
        }
    }

    fun pauseTimer(id: Long) {
        val now = platform.nowMillis()
        _timers.value = _timers.value.map { if (it.id == id && it.pausedLeft == null && !it.done) it.copy(pausedLeft = it.leftSec(now)) else it }
    }

    fun resumeTimer(id: Long) {
        val now = platform.nowMillis()
        _timers.value = _timers.value.map { t ->
            if (t.id == id && t.pausedLeft != null) t.copy(endsAt = now + t.pausedLeft * 1000, pausedLeft = null) else t
        }
        ensureTicker()
    }

    fun addMinute(id: Long) {
        val now = platform.nowMillis()
        _timers.value = _timers.value.map { t ->
            when {
                t.id != id -> t
                t.pausedLeft != null -> t.copy(pausedLeft = t.pausedLeft + 60)
                t.done -> t.copy(done = false, endsAt = now + 60_000)
                else -> t.copy(endsAt = t.endsAt + 60_000)
            }
        }
        ensureTicker()
    }

    fun removeTimer(id: Long) {
        _timers.value = _timers.value.filter { it.id != id }
    }

    // --- Rezepte ändern ------------------------------------------------------------------

    fun recipe(id: String): Recipe? = recipes.value.firstOrNull { it.id == id } ?: repo.recipe(id)

    fun toggleFavourite(recipe: Recipe) {
        repo.setFavourite(recipe.id, recipe.is_favourite != 1L, hlc.now())
        syncSoon()
    }

    fun draftOf(id: String): RecipeDraft? {
        val r = recipe(id) ?: return null
        val nutrition = r.nutrition?.let { runCatching { AppJson.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
        return RecipeDraft(
            id = r.id, title = r.title,
            courseIds = decodeIds(r.course_ids), categoryIds = decodeIds(r.category_ids), collectionIds = decodeIds(r.collection_ids),
            source = r.source_name ?: r.source_url.orEmpty(),
            servingsText = r.servings_text ?: r.servings_count?.toString().orEmpty(),
            prepMin = r.prep_min, cookMin = r.cook_min, rating = r.rating, isFavourite = r.is_favourite == 1L,
            ingredients = r.ingredients_text.orEmpty(), directions = r.directions_text.orEmpty(), notes = r.notes.orEmpty(),
            nutrition = nutrition,
            photos = photos.value.filter { it.recipe_id == r.id }.sortedBy { it.sort_order }
                .map { DraftPhoto(it.id, it.sha256, it.mime, it.width, it.height) },
        )
    }

    /** Speichert ein Rezept und liefert seine ID. */
    fun save(d: RecipeDraft): String {
        val id = d.id ?: newId()
        val now = hlc.now()
        val old = d.id?.let { repo.recipe(it) }
        val src = d.source.trim()
        val isUrl = src.startsWith("http://") || src.startsWith("https://")
        val nutrition = d.nutrition.filterValues { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.let { m -> AppJson.encodeToString(JsonObject.serializer(), JsonObject(m.mapValues { JsonPrimitive(it.value.trim()) })) }
        val servings = d.servingsText.trim()
        val recipe = Recipe(
            id = id, updated_at = now, deleted = 0L, server_rev = old?.server_rev ?: 0L, dirty = 1L,
            title = d.title.trim(), description = old?.description,
            source_name = if (isUrl) null else src.ifEmpty { null },
            source_url = if (isUrl) src else null,
            servings_text = servings.ifEmpty { null },
            servings_count = Regex("^\\d{1,3}").find(servings)?.value?.toLongOrNull(),
            prep_min = d.prepMin?.takeIf { it > 0 }, cook_min = d.cookMin?.takeIf { it > 0 },
            total_min = ((d.prepMin ?: 0L) + (d.cookMin ?: 0L)).takeIf { it > 0L },
            ingredients_text = d.ingredients.trimEnd().ifEmpty { null },
            directions_text = d.directions.trimEnd().ifEmpty { null },
            notes = d.notes.trimEnd().ifEmpty { null },
            nutrition = nutrition, rating = d.rating, is_favourite = if (d.isFavourite) 1L else 0L,
            course_ids = encodeIds(d.courseIds), category_ids = encodeIds(d.categoryIds), collection_ids = encodeIds(d.collectionIds),
            import_ref = old?.import_ref,
        )
        val existing = photos.value.filter { it.recipe_id == id }
        repo.transaction {
            repo.saveRecipe(recipe)
            val keep = d.photos.mapNotNull { it.id }.toSet()
            existing.filter { it.id !in keep }.forEach { repo.savePhoto(it.copy(deleted = 1L, updated_at = hlc.now())) }
            d.photos.forEachIndexed { i, p ->
                val prev = p.id?.let { pid -> existing.firstOrNull { it.id == pid } }
                if (prev == null || prev.sort_order != i.toLong()) {
                    repo.savePhoto(
                        Photo(
                            id = p.id ?: newId(), updated_at = hlc.now(), deleted = 0L, server_rev = prev?.server_rev ?: 0L, dirty = 1L,
                            recipe_id = id, sort_order = i.toLong(), sha256 = p.sha256, mime = p.mime, width = p.width, height = p.height,
                        ),
                    )
                }
            }
        }
        syncSoon()
        return id
    }

    fun delete(id: String) {
        val r = repo.recipe(id) ?: return
        repo.transaction {
            repo.saveRecipe(r.copy(deleted = 1L, updated_at = hlc.now()))
            photos.value.filter { it.recipe_id == id }.forEach { repo.savePhoto(it.copy(deleted = 1L, updated_at = hlc.now())) }
        }
        _recent.value = _recent.value.filter { it != id }
        syncSoon()
    }

    fun duplicate(id: String): String? {
        val d = draftOf(id) ?: return null
        return save(d.copy(id = null, title = d.title + " (Kopie)", photos = d.photos.map { it.copy(id = null) }))
    }

    /** Portionen dauerhaft ändern: Mengen und Portionsangabe werden umgeschrieben. */
    fun scalePermanently(id: String, factor: Double, newServings: Long) {
        val d = draftOf(id) ?: return
        save(
            d.copy(
                ingredients = de.rezeptkiste.data.Quantity.scaleText(d.ingredients, factor).orEmpty(),
                servingsText = newServings.toString(),
            ),
        )
    }

    // --- Rezeptarten, Kategorien, Sammlungen --------------------------------------------

    fun addLabel(kind: String, name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        labels.value.firstOrNull { it.kind == kind && it.name.equals(n, ignoreCase = true) }?.let { return it.id }
        val id = newId()
        val order = (labels.value.filter { it.kind == kind }.maxOfOrNull { it.sort_order } ?: -1) + 1
        repo.saveLabel(Label(id, kind, hlc.now(), 0L, 0L, 1L, n, order))
        syncSoon()
        return id
    }

    fun renameLabel(id: String, name: String) {
        val l = repo.label(id) ?: return
        if (name.isBlank() || name.trim() == l.name) return
        repo.saveLabel(l.copy(name = name.trim(), updated_at = hlc.now()))
        syncSoon()
    }

    /** Löscht eine Rezeptart/Kategorie/Sammlung und entfernt sie aus allen Rezepten. */
    fun deleteLabel(id: String) {
        val l = repo.label(id) ?: return
        repo.transaction {
            repo.saveLabel(l.copy(deleted = 1L, updated_at = hlc.now()))
            recipes.value.forEach { r ->
                val c = decodeIds(r.course_ids); val k = decodeIds(r.category_ids); val s = decodeIds(r.collection_ids)
                if (id in c || id in k || id in s) {
                    repo.saveRecipe(
                        r.copy(
                            course_ids = encodeIds(c - id), category_ids = encodeIds(k - id), collection_ids = encodeIds(s - id),
                            updated_at = hlc.now(),
                        ),
                    )
                }
            }
        }
        syncSoon()
    }

    // --- Fotos und Dateien ----------------------------------------------------------------

    /** Foto wählen und lokal ablegen; wird beim Speichern des Rezepts angehängt. */
    suspend fun pickPhoto(): DraftPhoto? {
        val f = platform.pickFile(FileKind.IMAGE) ?: return null
        return withContext(Dispatchers.Default) {
            val bmp = platform.decodeImage(f.bytes) ?: run { toast("Bilddatei konnte nicht gelesen werden."); return@withContext null }
            val sha = platform.sha256(f.bytes)
            platform.writeCache("original/$sha", f.bytes)
            val mime = when {
                f.name.endsWith(".png", true) -> "image/png"
                f.name.endsWith(".webp", true) -> "image/webp"
                else -> "image/jpeg"
            }
            DraftPhoto(null, sha, mime, bmp.width.toLong(), bmp.height.toLong())
        }
    }

    fun importRecipeKeeper() {
        val client = api ?: return
        scope.launch {
            val f = platform.pickFile(FileKind.ZIP) ?: return@launch
            toast("Import läuft …")
            try {
                val res = withContext(Dispatchers.Default) { client.importRecipeKeeper(f.name, f.bytes) }
                val n = Regex("\"recipes_imported\":\\s*(\\d+)").find(res)?.groupValues?.get(1) ?: "?"
                toast("$n Rezepte importiert.")
                syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Import fehlgeschlagen: ${e.message}")
            }
        }
    }

    // --- Bilder ------------------------------------------------------------------------

    private val imageCache = LinkedHashMap<String, ImageBitmap>()
    private val imageMutex = Mutex()

    suspend fun image(sha256: String, size: String): ImageBitmap? {
        val key = "$size/$sha256"
        imageMutex.withLock { imageCache[key] }?.let { return it }
        val bitmap = withContext(Dispatchers.Default) {
            val bytes = platform.readCache(key)
                ?: runCatching { api?.file(sha256, size) }.getOrNull()?.also { platform.writeCache(key, it) }
                ?: platform.readCache("original/$sha256")
            bytes?.let { platform.decodeImage(it) }
        } ?: return null
        imageMutex.withLock {
            imageCache[key] = bitmap
            if (imageCache.size > 200) imageCache.remove(imageCache.keys.first())
        }
        return bitmap
    }

    companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_SCREEN_ON = "screen_on"
        const val KEY_CARD_SIZE = "card_size"
        const val KEY_ZOOM = "zoom"
        const val KEY_RECENT = "recent"
        const val DEFAULT_ACCENT = 0xFFD9622BL
    }
}

fun Recipe.courseIds() = decodeIds(course_ids)
fun Recipe.categoryIds() = decodeIds(category_ids)
fun Recipe.collectionIds() = decodeIds(collection_ids)

fun RecipeQuery.matches(r: Recipe): Boolean {
    if (favourites && r.is_favourite != 1L) return false
    courseId?.let { c -> if (c == NONE) { if (r.courseIds().isNotEmpty()) return false } else if (c !in r.courseIds()) return false }
    categoryId?.let { c -> if (c == NONE) { if (r.categoryIds().isNotEmpty()) return false } else if (c !in r.categoryIds()) return false }
    collectionId?.let { c -> if (c == NONE) { if (r.collectionIds().isNotEmpty()) return false } else if (c !in r.collectionIds()) return false }
    return true
}

val EntityKinds = listOf(EntityType.COURSE, EntityType.CATEGORY, EntityType.COLLECTION)
