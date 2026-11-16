package app.crome1394.almanac

import app.crome1394.almanac.data.MonthLayout
import app.crome1394.almanac.data.MonthLayout.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthLayoutTest {
    @Test fun multiDayIsOneBar() {
        val w = MonthLayout.layout(listOf(Item("trip", 2, 4), Item("lunch", 3, 3)), lanes = 4)
        assertEquals(2, w.bars.size)
        val trip = w.bars.first { it.value == "trip" }
        assertEquals(2, trip.start); assertEquals(4, trip.end); assertEquals(0, trip.lane)
        assertEquals(1, w.bars.first { it.value == "lunch" }.lane)
        assertTrue(w.more.isEmpty())
    }

    @Test fun freeLanesAreReused() {
        val w = MonthLayout.layout(listOf(Item("a", 0, 1), Item("b", 2, 3), Item("c", 1, 2)), lanes = 4)
        assertEquals(0, w.bars.first { it.value == "b" }.lane)
        assertEquals(1, w.bars.first { it.value == "c" }.lane)
    }

    @Test fun overflowShowsMoreInLastLane() {
        val items = listOf(Item("long", 0, 6)) + (1..3).map { Item("d$it", 2, 2) }
        val w = MonthLayout.layout(items, lanes = 3)
        // Column 2 has 4 events in 3 lanes: two shown, "+2".
        assertEquals(2, w.more[2])
        assertEquals(setOf("long", "d1"), w.bars.map { it.value }.toSet())
        assertEquals(null, w.more[0])
    }

    @Test fun clampsToWeek() {
        val w = MonthLayout.layout(listOf(Item("x", -3, 9)), lanes = 2)
        assertEquals(0, w.bars[0].start); assertEquals(6, w.bars[0].end)
    }

    @Test fun noRoom() {
        val w = MonthLayout.layout(listOf(Item("x", 1, 2)), lanes = 0)
        assertTrue(w.bars.isEmpty()); assertEquals(mapOf(1 to 1, 2 to 1), w.more)
    }
}
