package de.rezeptkiste

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.rezeptkiste.data.RecipeDraft
import de.rezeptkiste.data.Repository
import de.rezeptkiste.db.RezeptDatabase
import de.rezeptkiste.ui.AppContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test

/** Plattform ohne Netz und Dateien, nur für Bildschirmfotos. */
private class ShotPlatform(override val isDesktop: Boolean) : PlatformServices {
    override val sqlDriver = createDesktopDriver(JdbcSqliteDriver.IN_MEMORY)
    override val secureStore = object : SecureStore {
        override fun load() = "token"
        override fun save(token: String) {}
        override fun clear() {}
    }
    override val defaultDeviceName = "Test"
    override fun nowMillis() = 1_790_000_000_000L
    override fun readCache(name: String): ByteArray? = null
    override fun writeCache(name: String, bytes: ByteArray) {}
    override fun clearCache() {}
    override fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    override fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    override suspend fun pickFile(kind: FileKind): PickedFile? = null
    override fun shareText(title: String, text: String) {}
    override fun clipboardText(): String? = null

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

    @Composable
    override fun KeepScreenOn(enabled: Boolean) = Unit
}

private val ZWETSCHGEN = RecipeDraft(
    title = "Zwetschgen-Streuselkuchen", servingsText = "12", prepMin = 30, cookMin = 45, source = "Oma Erna",
    ingredients = """
        500g Zwetschgen, entsteint und halbiert

        Teig
        125g Butter, weich
        100g Zucker
        1 TL Vanillezucker
        1 Prise Salz
        2 Eier
        180g Mehl
        1 ½ TL Backpulver

        Butter für die Form

        Streusel
        50g Butter, weich
        50g brauner Zucker
        1/4 TL Zimt
        90g Mehl
        Puderzucker zum Bestäuben
    """.trimIndent(),
    directions = """
        Teig
        Butter, Zucker, Vanillezucker und Salz 5 Minuten schaumig schlagen. Die Eier langsam einrühren.
        Das Backpulver mit dem Mehl mischen und unterrühren.
        Den Teig in die gefettete Form streichen und die Zwetschgen fächerförmig darauf legen.
        Streusel
        Butter, Zucker, Zimt und Mehl verkneten und 30 Min. kalt stellen.
        Den Kuchen bei 170 Grad Heißluft 40-45 Minuten backen.
        Den ausgekühlten Kuchen mit Puderzucker bestäuben
    """.trimIndent(),
)

class ScreenshotTest {
    private val out = File("build/screens").apply { mkdirs() }

    private fun shoot(name: String, widthDp: Int, heightDp: Int, desktop: Boolean, setup: (AppController) -> Unit) {
        val platform = ShotPlatform(desktop)
        val repo = Repository(RezeptDatabase(platform.sqlDriver))
        repo.setSetting(Repository.KEY_SERVER, "http://127.0.0.1:9")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = AppController(platform, scope)
        val dessert = controller.addLabel("course", "Dessert")!!
        controller.addLabel("course", "Hauptgericht")
        controller.addLabel("course", "Gebäck")
        val id = controller.save(ZWETSCHGEN.copy(courseIds = listOf(dessert)))
        controller.save(RecipeDraft(title = "Pasta Puttanesca", servingsText = "4", ingredients = "400 g Spaghetti\n2 EL Kapern", directions = "Nudeln 9 Minuten kochen."))
        controller.save(RecipeDraft(title = "Limettensorbet", servingsText = "6", ingredients = "250 g Zucker", directions = "Aufkochen."))
        Thread.sleep(400)
        setup(controller)
        val density = 2f
        val scene = ImageComposeScene(widthDp * 2, heightDp * 2, Density(density)) { AppContent(controller) }
        var t = 0L
        repeat(12) { scene.render(t); t += 100_000_000L; Thread.sleep(60) }
        val png = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(out, "$name.png").writeBytes(png)
        scene.close()
        scope.cancel()
        if (id.isEmpty()) error("kein Rezept")
    }

    private fun recipeId(c: AppController) = c.recipes.value.first { it.title.startsWith("Zwetschgen") }.id

    @Test
    fun screens() {
        for ((suffix, w, h, desktop) in listOf(Quad("handy", 400, 860, false), Quad("tablet", 900, 1200, false), Quad("windows", 1400, 860, true))) {
            shoot("start-$suffix", w, h, desktop) {}
            shoot("rezept-$suffix", w, h, desktop) { c -> recipeId(c).let { c.go(Screen.Detail(it, listOf(it))) } }
            shoot("freitext-$suffix", w, h, desktop) { c -> c.go(Screen.Edit(null, prefill = ZWETSCHGEN, freeText = true)) }
            shoot("formular-$suffix", w, h, desktop) { c -> c.go(Screen.Edit(recipeId(c))) }
            shoot("einkauf-$suffix", w, h, desktop) { c ->
                val r = c.recipes.value.first { it.title.startsWith("Zwetschgen") }
                c.addToShopping(r, de.rezeptkiste.data.RecipeText.shoppingItems(r.ingredients_text))
                c.addShoppingText("Milch\n2 Zitronen")
                Thread.sleep(300)
                c.toggleShopping(c.shopping.value.first())
                Thread.sleep(300)
                c.go(Screen.Shopping)
            }
            shoot("timer-$suffix", w, h, desktop) { c ->
                recipeId(c).let { c.go(Screen.Detail(it, listOf(it))) }
                c.startInAppTimer(600, "Zwetschgen-Streuselkuchen: Teig 10 Minuten schlagen")
            }
        }
    }
}

private data class Quad(val a: String, val b: Int, val c: Int, val d: Boolean)
