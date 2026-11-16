package app.crome1394.almanac

import app.crome1394.almanac.data.EventColor
import app.crome1394.almanac.data.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    // A few of the CSS3 named colors DAVx5 offers.
    private val css = listOf(
        "red" to 0xFF0000, "crimson" to 0xDC143C, "salmon" to 0xFA8072, "orangered" to 0xFF4500,
        "gold" to 0xFFD700, "mediumseagreen" to 0x3CB371, "seagreen" to 0x2E8B57, "deepskyblue" to 0x00BFFF,
        "darkslateblue" to 0x483D8B, "mediumpurple" to 0x9370DB, "darkorchid" to 0x9932CC, "dimgray" to 0x696969,
        "white" to 0xFFFFFF, "black" to 0x000000,
    ).map { (k, c) -> EventColor(k, (0xFF000000 or c.toLong()).toInt()) }

    @Test fun matchesNearestNamedColor() {
        val m = Palette.choices(css).associate { it.first to it.third }
        assertEquals("deepskyblue", m["Peacock"])
        assertEquals("dimgray", m["Graphite"])
        assertEquals("gold", m["Banana"])
        assertTrue(m["Tomato"] in setOf("red", "crimson"))
        assertTrue(m["Basil"] in setOf("seagreen", "mediumseagreen"))
    }

    @Test fun prefersGoogleNamedKeys() {
        val own = Palette.EVENT.map { EventColor(it.first, it.second) }
        Palette.choices(own).forEach { (name, _, key) -> assertEquals(name, key) }
        assertEquals(Palette.EVENT.size, Palette.shades(own).size)
    }

    @Test fun noColorsNoChoices() = assertTrue(Palette.choices(emptyList()).isEmpty())

    @Test fun shadesDrawGoogleColors() {
        val s = Palette.shades(css)
        assertEquals(0xFF039BE5.toInt(), s["deepskyblue"])
    }
}
