package app.crome1394.almanac

import app.crome1394.almanac.data.EventTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventThemeTest {
    @Test fun picksByWord() {
        assertEquals(EventTheme.DINNER, EventTheme.of("Dinner with Sam"))
        assertEquals(EventTheme.BIRTHDAY, EventTheme.of("Alex's birthday dinner"))
        assertEquals(EventTheme.FLIGHT, EventTheme.of("Flight to Lisbon (TP 1337)"))
        assertEquals(EventTheme.ANNIVERSARY, EventTheme.of("Mum's anniversary"))
        assertEquals(EventTheme.CHRISTMAS, EventTheme.of("Christmas Day"))
        assertEquals(EventTheme.NEW_YEAR, EventTheme.of("New Year's Day"))
        assertEquals(EventTheme.DENTIST, EventTheme.of("Dentist appointment"))
        assertEquals(EventTheme.PET, EventTheme.of("Walk the dog"))
        assertEquals(EventTheme.READING, EventTheme.of("Book club"))
        assertEquals(EventTheme.GYM, EventTheme.of("GYM"))
        assertEquals(EventTheme.SWIM, EventTheme.of("Kids' swimming"))
        assertEquals(EventTheme.MOVIE, EventTheme.of("Movies"))
        assertEquals(EventTheme.CAR, EventTheme.of("Defensive driving training course"))
        assertEquals(EventTheme.STUDY, EventTheme.of("First aid training"))
        assertEquals(EventTheme.GYM, EventTheme.of("Gym workout"))
    }

    @Test fun wholeWordsOnly() {
        assertNull(EventTheme.of("Team sync"))          // not "tea"
        assertNull(EventTheme.of("Barcelona offsite"))  // not "bar"
        assertNull(EventTheme.of("Carpool"))            // not "car"
        assertNull(EventTheme.of(""))
        assertNull(EventTheme.of("Stand-up"))
    }
}
