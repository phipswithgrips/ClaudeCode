package de.rezeptkiste

import de.rezeptkiste.data.RecipeDraft
import de.rezeptkiste.data.RecipeText
import de.rezeptkiste.data.TextSplitter
import de.rezeptkiste.data.toDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeTextTest {

    private val zwetschgen = """
        500g Zwetschgen, entsteint und halbiert

        Teig
        125g Butter, weich
        100g Zucker
        1 Prise Salz
        2 Eier
        180g Mehl

        Butter für die Form

        Streusel
        50g Butter, weich
        1/4 TL Zimt
        90g Mehl
        Puderzucker zum Bestäuben
    """.trimIndent().lines()

    @Test
    fun detectsIngredientHeadings() {
        val heads = zwetschgen.indices.filter { RecipeText.isIngredientHeading(zwetschgen, it) }.map { zwetschgen[it] }
        assertEquals(listOf("Teig", "Streusel"), heads)
    }

    @Test
    fun detectsHeadingsWithoutBlankLines() {
        val lines = listOf("Teig", "250 g Mehl", "1/2 TL Salz", "2 Eier", "Belag", "200 ml Sahne")
        assertEquals(listOf("Teig", "Belag"), lines.indices.filter { RecipeText.isIngredientHeading(lines, it) }.map { lines[it] })
        val other = listOf("Für die Sauce:", "1 Zwiebel", "SALAT", "1 Kopfsalat", "# Deko", "Minze", "Salz", "2 Eier")
        assertEquals(
            listOf("Für die Sauce:", "SALAT", "# Deko"),
            other.indices.filter { RecipeText.isIngredientHeading(other, it) }.map { other[it] },
        )
    }

    @Test
    fun plainIngredientsAreNotHeadings() {
        val lines = listOf("2 Eier", "", "Salz", "200 g Mehl", "Gehackte frische Petersilie", "Olivenöl")
        assertTrue(lines.indices.none { RecipeText.isIngredientHeading(lines, it) })
    }

    @Test
    fun directionHeadings() {
        assertTrue(RecipeText.isDirectionHeading("Teig"))
        assertTrue(RecipeText.isDirectionHeading("Für die Streusel"))
        assertTrue(RecipeText.isDirectionHeading("Fertigstellen:"))
        assertTrue(RecipeText.isDirectionHeading("Anrichten"))
        assertFalse(RecipeText.isDirectionHeading("Gut verrühren"))
        assertFalse(RecipeText.isDirectionHeading("Den Teig kneten"))
        assertFalse(RecipeText.isDirectionHeading("Den ausgekühlten Kuchen mit Puderzucker bestäuben"))
        assertFalse(RecipeText.isDirectionHeading("Mehl und Salz mischen."))
    }

    @Test
    fun shoppingItemsSkipHeadings() {
        assertEquals(
            listOf(
                "500g Zwetschgen, entsteint und halbiert", "125g Butter, weich", "100g Zucker", "1 Prise Salz", "2 Eier", "180g Mehl",
                "Butter für die Form", "50g Butter, weich", "1/4 TL Zimt", "90g Mehl", "Puderzucker zum Bestäuben",
            ),
            RecipeText.shoppingItems(zwetschgen.joinToString("\n")),
        )
    }

    private fun durations(s: String) = RecipeText.durations(s).map { s.substring(it.start, it.end) to (it.seconds to it.maxSeconds) }

    @Test
    fun findsDurations() {
        assertEquals(listOf("10 Minuten" to (600L to null)), durations("Eier zugeben und 10 Minuten kneten."))
        assertEquals(listOf("30 Min." to (1800L to null)), durations("30 Min. bei 180 Grad backen."))
        assertEquals(listOf("40-45 Minuten" to (2400L to 2700L)), durations("Den Kuchen bei 170 Grad für 40-45 Minuten backen"))
        assertEquals(listOf("1 Std. 30 Min." to (5400L to null)), durations("1 Std. 30 Min. ruhen lassen"))
        assertEquals(listOf("1,5 Stunden" to (5400L to null)), durations("1,5 Stunden schmoren"))
        assertEquals(listOf("2 h" to (7200L to null)), durations("2 h gehen lassen, dann 2 heiße Bleche"))
        assertEquals(listOf("eine halbe Stunde" to (1800L to null)), durations("eine halbe Stunde ziehen lassen"))
        assertEquals(listOf("30 Sekunden" to (30L to null)), durations("30 Sekunden mixen"))
        assertEquals(listOf("5 bis 10 Minuten" to (300L to 600L)), durations("5 bis 10 Minuten köcheln"))
        assertEquals(emptyList(), durations("2 Minzblätter und 180 Grad"))
    }

    @Test
    fun parsesTimeFields() {
        assertEquals(90L, RecipeText.parseMinutes("90"))
        assertEquals(90L, RecipeText.parseMinutes("1:30"))
        assertEquals(90L, RecipeText.parseMinutes("1 Std. 30 Min."))
        assertEquals(90L, RecipeText.parseMinutes("1,5 Std."))
        assertEquals(null, RecipeText.parseMinutes(""))
        assertEquals("1:05", RecipeText.minutesField(65))
        assertEquals("45", RecipeText.minutesField(45))
        assertEquals("4:59", RecipeText.clock(299))
        assertEquals("1:00:00", RecipeText.clock(3600))
    }

    @Test
    fun composeAndSplitRoundTrip() {
        val d = RecipeDraft(
            title = "Zwetschgen-Streuselkuchen", servingsText = "12", prepMin = 30, cookMin = 45, source = "Oma",
            ingredients = zwetschgen.joinToString("\n"),
            directions = "Teig\nButter und Zucker schaumig schlagen.\nStreusel\nAlles verkneten.",
            notes = "Schmeckt lauwarm am besten.",
        )
        val back = TextSplitter.split(RecipeText.compose(d)).toDraft()
        assertEquals(d.title, back.title)
        assertEquals("12", back.servingsText)
        assertEquals(30L, back.prepMin)
        assertEquals(45L, back.cookMin)
        assertEquals("Oma", back.source)
        assertEquals(d.ingredients, back.ingredients)
        assertEquals(d.directions, back.directions)
        assertEquals(d.notes, back.notes)
    }

    @Test
    fun splitsFreeTextWithSectionsAndMeta() {
        val s = TextSplitter.split(
            """
            Apfelkuchen
            Portionen: 8
            Arbeitszeit: 1 Std. 15 Min.

            Zutaten für den Teig
            200 g Mehl
            2 Stück Eier

            Belag
            4 Äpfel

            Zubereitung
            Teig 10 Minuten kneten.
            Bei 180 Grad 40 Min. backen.
            """.trimIndent(),
        )
        assertEquals("8", s.servingsText)
        assertEquals(8L, s.servingsCount)
        assertEquals(75L, s.prepMin)
        assertEquals("200 g Mehl\n2 Stück Eier\n\nBelag\n4 Äpfel", s.ingredients)
        assertEquals("Teig 10 Minuten kneten.\nBei 180 Grad 40 Min. backen.", s.directions)
    }

    @Test
    fun ingredientCountLineIsNotServings() {
        val s = TextSplitter.split("Salat\n2 Stück Gurken\n1 Kopfsalat\nAlles schneiden und mischen.")
        assertEquals(null, s.servingsText)
        assertEquals("2 Stück Gurken\n1 Kopfsalat", s.ingredients)
    }
}
