package de.rezeptkiste

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sun.jna.platform.win32.Crypt32Util
import de.rezeptkiste.db.RezeptDatabase
import java.io.File
import java.net.InetAddress

/** Datenordner: %APPDATA%\Rezeptkiste unter Windows, sonst ~/.rezeptkiste. */
fun appDataDir(): File {
    val appData = System.getenv("APPDATA")
    val dir = if (!appData.isNullOrBlank()) File(appData, "Rezeptkiste") else File(System.getProperty("user.home"), ".rezeptkiste")
    dir.mkdirs()
    return dir
}

val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/** SQLite-Treiber; legt das Schema beim ersten Start an. */
fun createDesktopDriver(url: String): SqlDriver {
    val driver = JdbcSqliteDriver(url)
    val version = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        parameters = 0,
    ).value
    if (version == 0L) {
        RezeptDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA user_version = ${RezeptDatabase.Schema.version}", 0)
    }
    return driver
}

class DesktopPlatform(private val dataDir: File = appDataDir()) : PlatformServices {

    override val sqlDriver: SqlDriver = createDesktopDriver("jdbc:sqlite:${File(dataDir, "rezeptkiste.db").absolutePath}")

    override val secureStore: SecureStore = FileSecureStore(File(dataDir, "token.bin"))

    override val defaultDeviceName: String =
        runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: if (isWindows) "Windows-PC" else "Desktop"

    override fun nowMillis(): Long = System.currentTimeMillis()

    private val cacheRoot = File(dataDir, "cache")

    override fun readCache(name: String): ByteArray? = File(cacheRoot, name).takeIf { it.isFile }?.readBytes()

    override fun writeCache(name: String, bytes: ByteArray) {
        val f = File(cacheRoot, name)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    override fun clearCache() {
        cacheRoot.deleteRecursively()
    }

    override fun decodeImage(bytes: ByteArray): ImageBitmap? =
        runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
}

/**
 * Token-Ablage. Unter Windows mit DPAPI an das Benutzerkonto gebunden
 * (nur dieser Windows-Benutzer kann es entschlüsseln), sonst als Datei
 * mit Leserecht nur für den Besitzer.
 */
private class FileSecureStore(private val file: File) : SecureStore {
    override fun load(): String? {
        if (!file.isFile) return null
        return runCatching {
            val raw = file.readBytes()
            String(if (isWindows) Crypt32Util.cryptUnprotectData(raw) else raw, Charsets.UTF_8)
        }.getOrNull()
    }

    override fun save(token: String) {
        val raw = token.toByteArray(Charsets.UTF_8)
        file.writeBytes(if (isWindows) Crypt32Util.cryptProtectData(raw) else raw)
        if (!isWindows) {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
    }

    override fun clear() {
        file.delete()
    }
}
