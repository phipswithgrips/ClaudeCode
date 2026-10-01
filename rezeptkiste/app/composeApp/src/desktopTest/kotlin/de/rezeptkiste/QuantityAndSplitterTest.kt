package de.rezeptkiste

import de.rezeptkiste.data.Quantity
import de.rezeptkiste.data.TextSplitter
import kotlin.test.Test
import kotlin.test.assertEquals

class QuantityAndSplitterTest {
    @Test
    fun scalesLeadingQuantities() {
        assertEquals("500 g Mehl", Quantity.scaleLine("250 g Mehl", 2.0))
        assertEquals("1 1/2 EL Zucker", Quantity.scaleLine("3/4 EL Zucker", 2.0))
        assertEquals("3 EL Zucker", Quantity.scaleLine("1 1/2 EL Zucker", 2.0))
        assertEquals("1 TL Salz", Quantity.scaleLine("½ TL Salz", 2.0))
        assertEquals("4-6 Eier", Quantity.scaleLine("2-3 Eier", 2.0))
        assertEquals("3 l Milch", Quantity.scaleLine("1,5 l Milch", 2.0))
        assertEquals("2lb Pflaumentomaten", Quantity.scaleLine("1lb Pflaumentomaten", 2.0))
        assertEquals("Salz nach Geschmack", Quantity.scaleLine("Salz nach Geschmack", 2.0))
        assertEquals("Füllung:", Quantity.scaleLine("Füllung:", 2.0))
    }

    @Test
    fun scalesTrailingQuantities() {
        assertEquals("Olivenöl 150 g", Quantity.scaleLine("Olivenöl 75 g", 2.0))
        assertEquals("Salz 1,2 g", Quantity.scaleLine("Salz 2,4 g", 0.5))
    }

    @Test
    fun boldPrefixLength() {
        assertEquals(1, Quantity.leadingLength("4 Esslöffel Olivenöl"))
        assertEquals(3, Quantity.leadingLength("1/8 Tasse Butter"))
        assertEquals(5, Quantity.leadingLength("1 1/2 EL"))
        assertEquals(0, Quantity.leadingLength("Parmesan Käse"))
    }

    @Test
    fun splitsStructuredText() {
        val s = TextSplitter.split(
            """
            Limettensorbet
            Für 4 Portionen
            Arbeitszeit: 15 Min.

            Zutaten
            250 g Zucker
            3 Limetten

            Zubereitung
            1. Zucker mit Wasser aufkochen.
            2. Abkühlen lassen und gefrieren.
            """.trimIndent(),
        )
        assertEquals("Limettensorbet", s.title)
        assertEquals(4L, s.servingsCount)
        assertEquals(15L, s.prepMin)
        assertEquals("250 g Zucker\n3 Limetten", s.ingredients)
        assertEquals("Zucker mit Wasser aufkochen.\nAbkühlen lassen und gefrieren.", s.directions)
    }

    @Test
    fun splitsTextWithoutHeadings() {
        val s = TextSplitter.split("Pasta\n200 g Spaghetti\n2 EL Olivenöl\nNudeln in Salzwasser kochen, abgießen und mit dem Öl vermengen.")
        assertEquals("200 g Spaghetti\n2 EL Olivenöl", s.ingredients)
        assertEquals("Nudeln in Salzwasser kochen, abgießen und mit dem Öl vermengen.", s.directions)
    }
}
