package app.crome1394.almanac

import app.crome1394.almanac.data.Birthdays
import app.crome1394.almanac.data.Ics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId

class IcsTest {
    @Test fun readsATimedEventWithZoneAlarmAndFolding() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:1
            DTSTART;TZID=Europe/Berlin:20261120T100000
            DTEND;TZID=Europe/Berlin:20261120T113000
            SUMMARY:Team\, weekly
            DESCRIPTION:Line one\nLine two that is folded
              and continues
            LOCATION:Room 1
            RRULE:FREQ=WEEKLY;BYDAY=FR
            BEGIN:VALARM
            TRIGGER:-PT15M
            ACTION:DISPLAY
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val e = Ics.parse(ics).single()
        assertEquals("Team, weekly", e.title)
        assertEquals("Line one\nLine two that is folded and continues", e.description)
        assertEquals("Room 1", e.location)
        assertEquals(LocalDateTime.of(2026, 11, 20, 10, 0), e.start)
        assertEquals(LocalDateTime.of(2026, 11, 20, 11, 30), e.end)
        assertEquals(ZoneId.of("Europe/Berlin"), e.zone)
        assertEquals("FREQ=WEEKLY;BYDAY=FR", e.rrule)
        assertEquals(listOf(15), e.reminderMinutes)
    }

    @Test fun allDayEndIsExclusiveInTheFile() {
        val e = Ics.parse("BEGIN:VEVENT\nDTSTART;VALUE=DATE:20261224\nDTEND;VALUE=DATE:20261227\nSUMMARY:Holiday\nEND:VEVENT").single()
        assertTrue(e.allDay)
        assertEquals(LocalDate.of(2026, 12, 24), e.start.toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 26), e.end.toLocalDate())
    }

    @Test fun skipsEventsWithoutStartAndBadRules() {
        val list = Ics.parse("BEGIN:VEVENT\nSUMMARY:No start\nEND:VEVENT\nBEGIN:VEVENT\nDTSTART:20261120T100000Z\nRRULE:FREQ=NOPE\nEND:VEVENT")
        assertEquals(1, list.size)
        assertNull(list[0].rrule)
    }

    @Test fun triggers() {
        assertEquals(15, Ics.triggerMinutes("-PT15M"))
        assertEquals(1440, Ics.triggerMinutes("-P1D"))
        assertEquals(10080, Ics.triggerMinutes("-P1W"))
        assertEquals(-60, Ics.triggerMinutes("PT1H"))
        assertNull(Ics.triggerMinutes("garbage"))
    }

    @Test fun birthdays() {
        assertEquals(MonthDay.of(3, 14) to 1990, Birthdays.parse("1990-03-14"))
        assertEquals(MonthDay.of(3, 14) to null, Birthdays.parse("--03-14"))
        assertEquals(MonthDay.of(3, 14) to 1990, Birthdays.parse("19900314"))
        assertNull(Birthdays.parse("not a date"))
    }
}
