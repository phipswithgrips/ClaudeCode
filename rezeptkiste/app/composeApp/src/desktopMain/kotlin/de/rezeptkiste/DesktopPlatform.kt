package de.rezeptkiste

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sun.jna.platform.win32.Crypt32Util
import de.rezeptkiste.db.RezeptDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest

/** Datenordner: %APPDATA%\Cookfolio unter Windows, sonst ~/.cookfolio. Übernimmt den alten Ordner "Rezeptkiste". */
fun appDataDir(): File {
    val appData = System.getenv("APPDATA")
    val base = if (!appData.isNullOrBlank()) File(appData) else File(System.getProperty("user.home"))
    val dir = File(base, if (appData.isNullOrBlank()) ".cookfolio" else "Cookfolio")
    val old = File(base, if (appData.isNullOrBlank()) ".rezeptkiste" else "Rezeptkiste")
    if (!dir.exists() && old.isDirectory) old.renameTo(dir)
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
    } else if (version < RezeptDatabase.Schema.version) {
        // Vorhandene Datenbank auf den neuen Stand bringen (z. B. Tabelle für die Einkaufsliste)
        RezeptDatabase.Schema.migrate(driver, version, RezeptDatabase.Schema.version)
        driver.execute(null, "PRAGMA user_version = ${RezeptDatabase.Schema.version}", 0)
    }
    return driver
}

class DesktopPlatform(private val dataDir: File = appDataDir()) : PlatformServices {

    override val sqlDriver: SqlDriver = createDesktopDriver("jdbc:sqlite:${File(dataDir, "rezeptkiste.db").absolutePath}")

    override val secureStore: SecureStore = FileSecureStore(File(dataDir, "token.bin"))

    override val defaultDeviceName: String =
        runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: if (isWindows) "Windows-PC" else "Desktop"

    override val isDesktop: Boolean = true

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Wird von main() gesetzt, damit der Dialog vor dem App-Fenster erscheint. */
    var window: Frame? = null

    override suspend fun pickFile(kind: FileKind): PickedFile? = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(window, "Datei auswählen", FileDialog.LOAD)
        val exts = when (kind) {
            FileKind.IMAGE -> listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")
            FileKind.ZIP -> listOf(".zip")
            FileKind.TEXT -> listOf(".txt", ".md", ".text")
        }
        dialog.file = exts.joinToString(";") { "*$it" }
        dialog.setFilenameFilter { _, name -> exts.any { name.lowercase().endsWith(it) } }
        dialog.isVisible = true
        val name = dialog.file ?: return@withContext null
        val f = File(dialog.directory, name)
        withContext(Dispatchers.IO) { PickedFile(f.name, f.readBytes()) }
    }

    override fun shareText(title: String, text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        onShared?.invoke()
    }

    /** Hinweis an die Oberfläche nach dem Kopieren (gesetzt von der App). */
    var onShared: (() -> Unit)? = null

    override fun clipboardText(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

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

    private var tray: java.awt.TrayIcon? = null

    /** Abgelaufener Timer: Windows-Benachrichtigung, Ton und Fenster nach vorn. */
    override fun timerFinished(label: String) {
        java.awt.EventQueue.invokeLater {
            runCatching {
                if (java.awt.SystemTray.isSupported()) {
                    val icon = tray ?: java.awt.TrayIcon(
                        Thread.currentThread().contextClassLoader.getResource("icon.png")?.let { Toolkit.getDefaultToolkit().getImage(it) }
                            ?: java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                        "Cookfolio",
                    ).also { it.isImageAutoSize = true; java.awt.SystemTray.getSystemTray().add(it); tray = it }
                    icon.displayMessage("Timer abgelaufen", label, java.awt.TrayIcon.MessageType.INFO)
                }
            }
            window?.let { w -> if (w.state == Frame.ICONIFIED) w.state = Frame.NORMAL; w.toFront() }
        }
        Thread {
            repeat(3) { Toolkit.getDefaultToolkit().beep(); Thread.sleep(600) }
        }.apply { isDaemon = true }.start()
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

    @Composable
    override fun KeepScreenOn(enabled: Boolean) = Unit
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
