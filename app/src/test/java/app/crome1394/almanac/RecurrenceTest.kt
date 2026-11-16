package app.crome1394.almanac

import app.crome1394.almanac.data.Freq
import app.crome1394.almanac.data.RRule
import app.crome1394.almanac.data.RecurrenceText
import app.crome1394.almanac.data.WeekdayNum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class RecurrenceTest {
    private val en = Locale.US
    private val utc = ZoneOffset.UTC

    @Test fun parsesAndFormatsRoundTrip() {
        val s = "FREQ=WEEKLY;UNTIL=20271231T235959Z;INTERVAL=2;BYDAY=MO,WE"
        assertEquals(s, RRule.parse(s)!!.format())
    }

    @Test fun keepsPartsItDoesNotEdit() {
        val r = RRule.parse("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1;WKST=SU")!!
        assertEquals(listOf("BYSETPOS" to "-1", "WKST" to "SU"), r.other)
        assertTrue(r.format().contains("BYSETPOS=-1"))
        assertFalse(r.isSimple)
    }

    @Test fun rejectsBadRules() {
        assertNull(RRule.parse(""))
        assertNull(RRule.parse("FREQ=SECONDLY"))
        assertNull(RRule.parse("INTERVAL=2"))
        assertNull(RRule.parse("FREQ=WEEKLY;BYDAY=XX"))
    }

    @Test fun parsesOrdinalWeekdays() {
        val r = RRule.parse("FREQ=MONTHLY;BYDAY=-1FR")!!
        assertEquals(listOf(WeekdayNum(-1, DayOfWeek.FRIDAY)), r.byDay)
        assertEquals("-1FR", r.byDay[0].toString())
    }

    @Test fun describesLikeGoogle() {
        val start = LocalDate.of(2026, 11, 16) // a Monday
        fun d(s: String) = RecurrenceText.describe(RRule.parse(s)!!, start, utc, en)
        assertEquals("Daily", d("FREQ=DAILY"))
        assertEquals("Every 3 days", d("FREQ=DAILY;INTERVAL=3"))
        assertEquals("Weekly on Monday", d("FREQ=WEEKLY;BYDAY=MO"))
        assertEquals("Every weekday (Monday to Friday)", d("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
        assertEquals("Every 2 weeks on Monday, Wednesday", d("FREQ=WEEKLY;INTERVAL=2;BYDAY=WE,MO"))
        assertEquals("Monthly on the third Monday", d("FREQ=MONTHLY;BYDAY=3MO"))
        assertEquals("Monthly on the last Friday", d("FREQ=MONTHLY;BYDAY=-1FR"))
        assertEquals("Monthly on day 16", d("FREQ=MONTHLY;BYMONTHDAY=16"))
        assertEquals("Annually on November 16", d("FREQ=YEARLY"))
        assertEquals("Daily, 5 times", d("FREQ=DAILY;COUNT=5"))
        assertEquals("Daily, until Dec 31, 2026", d("FREQ=DAILY;UNTIL=20261231"))
    }

    @Test fun presetsFollowTheStartDate() {
        val lastMonday = LocalDate.of(2026, 11, 30)
        val p = RRule.presets(lastMonday).map { it.format() }
        assertTrue("FREQ=MONTHLY;BYDAY=4MO" in p)
        assertTrue("FREQ=MONTHLY;BYDAY=-1MO" in p)
        assertTrue("FREQ=WEEKLY;BYDAY=MO" in p)
        val mid = RRule.presets(LocalDate.of(2026, 11, 16)).map { it.format() }
        assertFalse(mid.any { it.contains("-1MO") })
    }

    @Test fun untilForTimedEventsIsEndOfDayInUtc() {
        val ny = ZoneId.of("America/New_York")
        assertEquals("20270101T045959Z", RRule.formatUntil(LocalDate.of(2026, 12, 31), false, ny))
        assertEquals("20261231", RRule.formatUntil(LocalDate.of(2026, 12, 31), true, ny))
        assertEquals(LocalDate.of(2026, 12, 31), RRule.parseUntil("20270101T045959Z", ny))
    }

    @Test fun untilBeforeEndsJustBeforeAnOccurrence() {
        val t = LocalDate.of(2026, 11, 20).atTime(10, 0).toInstant(utc).toEpochMilli()
        assertEquals("20261120T095959Z", RRule.untilBefore(t, false))
        val day = LocalDate.of(2026, 11, 20).atStartOfDay(utc).toInstant().toEpochMilli()
        assertEquals("20261119", RRule.untilBefore(day, true))
    }

    @Test fun weekOfMonth() {
        assertEquals(1, RRule.weekOfMonth(LocalDate.of(2026, 11, 7)))
        assertEquals(3, RRule.weekOfMonth(LocalDate.of(2026, 11, 16)))
        assertEquals(5, RRule.weekOfMonth(LocalDate.of(2026, 11, 30)))
        assertTrue(RRule.isLastWeekdayOfMonth(LocalDate.of(2026, 11, 24)))
        assertEquals(Freq.WEEKLY, RRule.parse("freq=weekly")!!.freq)
    }
}

class ExdateShiftTest {
    @Test fun shiftsUtcAndDateEntries() {
        assertEquals("20261116T153000Z,20261117T153000Z",
            app.crome1394.almanac.data.shiftExdates("20261116T143000Z, 20261117T143000Z", 3_600_000))
        assertEquals("20261118", app.crome1394.almanac.data.shiftExdates("20261116", 2 * 86_400_000L))
        assertEquals("America/New_York;20261116T093000", app.crome1394.almanac.data.shiftExdates("America/New_York;20261116T093000", 3_600_000))
        assertNull(app.crome1394.almanac.data.shiftExdates(null, 5))
    }
}
