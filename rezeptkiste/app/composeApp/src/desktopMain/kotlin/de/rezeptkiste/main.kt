package de.rezeptkiste

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sun.jna.platform.win32.Crypt32Util
import de.rezeptkiste.ui.App
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private fun appIcon() = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { ImageIO.read(it) }

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--selftest") selfTest(args.getOrNull(1))

    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        runCatching { File(appDataDir(), "error.log").appendText("${java.time.Instant.now()} ${e.stackTraceToString()}\n") }
    }
    // Nur eine Cookfolio-Instanz: zwei Fenster auf derselben Datenbank würden sich gegenseitig überschreiben
    val lock = runCatching {
        java.io.RandomAccessFile(File(appDataDir(), "app.lock"), "rw").channel.tryLock()
    }.getOrNull()
    if (lock == null) {
        javax.swing.JOptionPane.showMessageDialog(null, "Cookfolio ist bereits geöffnet.", "Cookfolio", javax.swing.JOptionPane.INFORMATION_MESSAGE)
        exitProcess(0)
    }
    val platform = DesktopPlatform()
    application {
        val icon = remember { appIcon()?.let { BitmapPainter(it.toComposeImageBitmap()) } }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Cookfolio",
            icon = icon,
            state = rememberWindowState(size = DpSize(1320.dp, 860.dp)),
        ) {
            LaunchedEffect(Unit) { applyDarkTitleBar(window) }
            window.minimumSize = java.awt.Dimension(900, 600)
            platform.window = window
            App(platform)
        }
    }
}

/**
 * Prüft im fertigen Installationspaket, ob die verkleinerte Java-Laufzeit alles enthält:
 * SQLite (java.sql), HTTPS mit modernen Zertifikaten (jdk.crypto.ec), Bilder, DPAPI.
 * Aufruf im CI: Cookfolio.exe --selftest <Ergebnisdatei>
 */
private fun selfTest(out: String?) {
    val results = mutableListOf<String>()
    fun check(name: String, block: () -> Unit) {
        results += runCatching { block(); "OK   $name" }.getOrElse { "FAIL $name: ${it::class.simpleName}: ${it.message}" }
    }
    check("SQLite") { JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).execute(null, "CREATE TABLE t(x INTEGER)", 0) }
    check("HTTPS") { runBlocking { HttpClient().use { it.get("https://api.github.com") } } }
    check("Bild") { requireNotNull(appIcon()) { "icon.png fehlt" }.toComposeImageBitmap() }
    check("Skia") { org.jetbrains.skia.Surface.makeRasterN32Premul(8, 8).close() }
    if (isWindows) check("DPAPI") { Crypt32Util.cryptUnprotectData(Crypt32Util.cryptProtectData("x".toByteArray())) }
    val text = results.joinToString("\n")
    if (out != null) File(out).writeText(text) else println(text)
    exitProcess(if (results.all { it.startsWith("OK") }) 0 else 1)
}
