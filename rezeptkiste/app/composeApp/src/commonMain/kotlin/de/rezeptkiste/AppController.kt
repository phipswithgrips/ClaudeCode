package de.rezeptkiste

import androidx.compose.ui.graphics.ImageBitmap
import de.rezeptkiste.data.Repository
import de.rezeptkiste.db.Label
import de.rezeptkiste.db.Photo
import de.rezeptkiste.db.Recipe
import de.rezeptkiste.db.RezeptDatabase
import de.rezeptkiste.sync.Api
import de.rezeptkiste.sync.ApiException
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
import kotlin.random.Random

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Done(val atMillis: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

data class LoginState(val busy: Boolean = false, val error: String? = null)

/** Zustand und Aktionen der App, unabhängig von der Plattform. */
class AppController(private val platform: PlatformServices, private val scope: CoroutineScope) {

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

    val recipes: StateFlow<List<Recipe>> = repo.recipes().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val labels: StateFlow<List<Label>> = repo.labels().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val photos: StateFlow<List<Photo>> = repo.photos().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val defaultDeviceName: String get() = repo.setting(Repository.KEY_DEVICE_NAME) ?: platform.defaultDeviceName
    val serverUrl: String? get() = repo.setting(Repository.KEY_SERVER)

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
        engine = SyncEngine(client, repo, hlc)
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
            imageCache.clear()
            api?.close()
            api = null
            engine = null
            token = null
            _sync.value = SyncStatus.Idle
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

    // --- Lokale Änderungen -------------------------------------------------------------

    fun toggleFavourite(recipe: Recipe) {
        repo.setFavourite(recipe.id, recipe.is_favourite != 1L, hlc.now())
        syncSoon()
    }

    // --- Bilder ------------------------------------------------------------------------

    private val imageCache = LinkedHashMap<String, ImageBitmap>()
    private val imageMutex = Mutex()

    suspend fun image(sha256: String, size: String): ImageBitmap? {
        val key = "$size/$sha256"
        imageMutex.withLock { imageCache[key] }?.let { return it }
        val bitmap = withContext(Dispatchers.Default) {
            val bytes = platform.readCache(key) ?: runCatching { api?.file(sha256, size) }.getOrNull()?.also {
                platform.writeCache(key, it)
            }
            bytes?.let { platform.decodeImage(it) }
        } ?: return null
        imageMutex.withLock {
            imageCache[key] = bitmap
            if (imageCache.size > 150) imageCache.remove(imageCache.keys.first())
        }
        return bitmap
    }
}
