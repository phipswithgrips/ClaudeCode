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

/** Was jede Plattform bereitstellt. */
interface PlatformServices {
    val sqlDriver: SqlDriver
    val secureStore: SecureStore
    val defaultDeviceName: String

    fun nowMillis(): Long

    /** Dateicache für Bilder; name ist ein relativer Pfad wie "thumb/<sha>". */
    fun readCache(name: String): ByteArray?
    fun writeCache(name: String, bytes: ByteArray)
    fun clearCache()

    fun decodeImage(bytes: ByteArray): ImageBitmap?

    /** Zurück-Taste (Android); auf dem Desktop ohne Wirkung. */
    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)
}
