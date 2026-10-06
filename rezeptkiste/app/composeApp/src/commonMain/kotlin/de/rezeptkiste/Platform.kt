package de.rezeptkiste

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.cash.sqldelight.db.SqlDriver

/** Sichere Ablage des Geräte-Tokens (Android Keystore bzw. Windows DPAPI). */
interface SecureStore {
    fun load(): String?
    fun save(token: String)
    fun clear()
}

/** Vom Nutzer gewählte Datei. */
class PickedFile(val name: String, val bytes: ByteArray)

enum class FileKind { IMAGE, ZIP, TEXT }

/** Was jede Plattform bereitstellt. */
interface PlatformServices {
    val sqlDriver: SqlDriver
    val secureStore: SecureStore
    val defaultDeviceName: String

    /** true auf Windows/Desktop: breites Layout mit fester Navigationsleiste ist Standard. */
    val isDesktop: Boolean

    /** Standard-Anzeigegröße (Windows: etwas größer, damit Schrift und Abstände wie in Recipe Keeper wirken). */
    val defaultZoom: Float get() = if (isDesktop) 1.25f else 1f

    /**
     * Timer der Uhr-App starten (Android). Rückgabe false, wenn es keinen gibt;
     * dann übernimmt der eingebaute Timer.
     */
    fun startSystemTimer(seconds: Long, label: String): Boolean = false

    /** Timer-App öffnen (Android); false, wenn nicht möglich. */
    fun openSystemTimers(): Boolean = false

    /** Hinweis, wenn ein eingebauter Timer abgelaufen ist (Windows: Benachrichtigung und Ton). */
    fun timerFinished(label: String) {}

    fun nowMillis(): Long

    /** Dateicache für Bilder; name ist ein relativer Pfad wie "thumb/<sha>". */
    fun readCache(name: String): ByteArray?
    fun writeCache(name: String, bytes: ByteArray)
    fun clearCache()

    fun decodeImage(bytes: ByteArray): ImageBitmap?

    fun sha256(bytes: ByteArray): String

    /** Datei auswählen (Foto, ZIP, Text); null bei Abbruch. */
    suspend fun pickFile(kind: FileKind): PickedFile?

    /** Text in die Zwischenablage legen bzw. teilen (Android: System-Teilen-Menü). */
    fun shareText(title: String, text: String)

    /** Text aus der Zwischenablage. */
    fun clipboardText(): String?

    /** Zurück-Taste (Android); auf dem Desktop ohne Wirkung. */
    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)

    /** Bildschirm eingeschaltet lassen, solange ein Rezept offen ist. */
    @Composable
    fun KeepScreenOn(enabled: Boolean)
}
