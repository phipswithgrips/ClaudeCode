package de.rezeptkiste

import de.rezeptkiste.sync.Api
import de.rezeptkiste.sync.Hlc
import de.rezeptkiste.ui.formatMinutes
import de.rezeptkiste.data.RecipeText
import de.rezeptkiste.ui.stars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HlcAndFormatTest {
    @Test
    fun formatMatchesServer() {
        assertEquals("001759216800000-00003-pixel", Hlc.format(1_759_216_800_000, 3, "pixel"))
        assertEquals("Pixel9Pro", Hlc.sanitize("Pixel 9 Pro!"))
    }

    @Test
    fun strictlyMonotonicWithFrozenClock() {
        val hlc = Hlc("a") { 1000 }
        val a = hlc.now()
        val b = hlc.now()
        assertTrue(b > a)
    }

    @Test
    fun observeMovesClockPastRemote() {
        val hlc = Hlc("a") { 1000 }
        val remote = Hlc.format(5000, 7, "b")
        hlc.observe(remote)
        assertTrue(hlc.now() > remote)
    }

    @Test
    fun urlNormalisation() {
        assertEquals("https://rezepte.example.de", Api.normalizeUrl(" rezepte.example.de/ "))
        assertEquals("http://192.168.1.5:8000", Api.normalizeUrl("http://192.168.1.5:8000/"))
    }

    @Test
    fun formatting() {
        assertEquals("1 Std. 30 Min.", formatMinutes(90))
        assertEquals("45 Min.", formatMinutes(45))
        assertEquals(null, formatMinutes(0))
        assertEquals("★★★☆☆", stars(3))
        assertTrue(RecipeText.isDirectionHeading("Füllung:"))
        assertTrue(RecipeText.isDirectionHeading("ZUM ANRICHTEN"))
        assertFalse(RecipeText.isDirectionHeading("250 g Mehl"))
    }
}
