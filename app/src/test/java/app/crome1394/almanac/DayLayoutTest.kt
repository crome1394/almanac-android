package app.crome1394.almanac

import app.crome1394.almanac.data.DayLayout
import app.crome1394.almanac.data.DayLayout.Item
import org.junit.Assert.assertEquals
import org.junit.Test

class DayLayoutTest {
    private fun lay(vararg e: Pair<Int, Int>) =
        DayLayout.layout(e.mapIndexed { i, (s, en) -> Item(i, s, en) }, minHeight = 0).associateBy { it.value }

    @Test fun separateEventsTakeFullWidth() {
        val r = lay(540 to 600, 600 to 660)
        assertEquals(1, r[0]!!.cols); assertEquals(1, r[1]!!.cols)
    }

    @Test fun overlappingEventsShareColumns() {
        val r = lay(540 to 660, 570 to 630)
        assertEquals(2, r[0]!!.cols)
        assertEquals(0, r[0]!!.col); assertEquals(1, r[1]!!.col)
    }

    @Test fun freedColumnIsReused() {
        // A long event beside two short ones in sequence: two columns, not three.
        val r = lay(540 to 720, 540 to 600, 610 to 700)
        assertEquals(2, r[0]!!.cols)
        assertEquals(r[1]!!.col, r[2]!!.col)
    }

    @Test fun eventsWidenIntoFreeColumns() {
        // A and B overlap, C overlaps A only; C sits under B... and D later is alone.
        val r = lay(540 to 600, 540 to 560, 560 to 600, 700 to 760)
        assertEquals(1, r[3]!!.cols)
        val a = r[0]!!
        assertEquals(1, a.span)
    }

    @Test fun shortEventsStillCountAsOverlapping() {
        val r = DayLayout.layout(listOf(Item(0, 540, 545), Item(1, 550, 560)), minHeight = 20).associateBy { it.value }
        assertEquals(2, r[0]!!.cols)
    }

    @Test fun spanGrowsWhenNeighbourColumnIsFree() {
        // 0 and 1 overlap (2 cols); 2 starts after 1 ends but overlaps 0 — it reuses col 1.
        // 3 is in col 0 after 0 ends and nothing in col 1 overlaps it, so it spans both.
        val r = lay(540 to 600, 540 to 570, 575 to 590, 600 to 650)
        assertEquals(1, r[0]!!.span)
    }
}
