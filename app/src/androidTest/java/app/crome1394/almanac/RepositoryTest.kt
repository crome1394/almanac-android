package app.crome1394.almanac

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.crome1394.almanac.data.CalendarRepository
import app.crome1394.almanac.data.Scope
import app.crome1394.almanac.ui.draftFrom
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Repeating-event edits against the real calendar store, on an emulator only (AGENTS.md).
 * "Synced" series live in a calendar of a made-up account and carry a _sync_id, the way DAVx5
 * leaves them; each test checks the occurrences the store actually expands.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val cr = ctx.contentResolver
    private val repo = CalendarRepository(ctx)
    private val zone = ZoneId.systemDefault()
    private val day0 = LocalDate.of(2030, 3, 4)
    private var syncedCal = -1L
    private var localCal = -1L
    private var hadLocalColors = false

    private val acct = "test@example.com"
    private val type = "app.crome1394.almanac.test"

    private fun adapter(base: Uri, name: String = acct, t: String = type) = base.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, name)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, t).build()

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("READ_CALENDAR", "WRITE_CALENDAR").forEach {
            ui.executeShellCommand("pm grant ${ctx.packageName} android.permission.$it").close()
        }
        Thread.sleep(300)
        syncedCal = cr.insert(adapter(Calendars.CONTENT_URI), ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, acct); put(Calendars.ACCOUNT_TYPE, type)
            put(Calendars.NAME, "Synced test"); put(Calendars.CALENDAR_DISPLAY_NAME, "Synced test")
            put(Calendars.CALENDAR_COLOR, 0xFF039BE5.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, acct); put(Calendars.VISIBLE, 1); put(Calendars.SYNC_EVENTS, 1)
        })!!.let(ContentUris::parseId)
        hadLocalColors = localColorCount() > 0
        localCal = runBlocking { repo.createLocalCalendar("Local test", 0xFF0B8043.toInt()) }
    }

    @After fun tearDown() {
        cr.delete(adapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, syncedCal)), null, null)
        cr.delete(adapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, localCal), CalendarRepository.LOCAL_ACCOUNT,
            CalendarContract.ACCOUNT_TYPE_LOCAL), null, null)
        // Making a phone-only calendar adds Google's colors for that account; leave none behind.
        if (!hadLocalColors) cr.delete(adapter(CalendarContract.Colors.CONTENT_URI, CalendarRepository.LOCAL_ACCOUNT,
            CalendarContract.ACCOUNT_TYPE_LOCAL), "${CalendarContract.Colors.ACCOUNT_TYPE}=?", arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL))
    }

    private fun localColorCount(): Int = cr.query(CalendarContract.Colors.CONTENT_URI, arrayOf(CalendarContract.Colors.COLOR_KEY),
        "${CalendarContract.Colors.ACCOUNT_TYPE}=?", arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL), null)?.use { it.count } ?: 0

    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(day0.plusDays(day.toLong()), java.time.LocalTime.of(h, m))
        .atZone(zone).toInstant().toEpochMilli()

    /** A daily series of [count] at 10:00, inserted as the sync adapter would (with a _sync_id when [synced]). */
    private fun series(title: String, count: Int, synced: Boolean): Long {
        val cal = if (synced) syncedCal else localCal
        val uri = if (synced) adapter(Events.CONTENT_URI) else Events.CONTENT_URI
        return cr.insert(uri, ContentValues().apply {
            put(Events.CALENDAR_ID, cal); put(Events.TITLE, title)
            put(Events.DTSTART, at(0, 10)); put(Events.DURATION, "PT1H")
            put(Events.RRULE, "FREQ=DAILY;COUNT=$count"); put(Events.EVENT_TIMEZONE, zone.id)
            if (synced) put(Events._SYNC_ID, "sync-$title")
        })!!.let(ContentUris::parseId)
    }

    /** (begin, event id, title) of every occurrence in the test fortnight, in order. */
    private fun occurrences(): List<Triple<Long, Long, String>> {
        val b = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, at(-1, 0)); ContentUris.appendId(b, at(20, 0))
        return cr.query(b.build(), arrayOf(Instances.BEGIN, Instances.EVENT_ID, Instances.TITLE, Instances.STATUS),
            "${Instances.CALENDAR_ID} IN (?,?) AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS}!=${Events.STATUS_CANCELED})",
            arrayOf(syncedCal.toString(), localCal.toString()), "${Instances.BEGIN} ASC")!!.use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getLong(1), c.getString(2) ?: "")) }
        }
    }

    private fun hourOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).hour
    private fun dayOf(ms: Long) = java.time.temporal.ChronoUnit.DAYS.between(day0, java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()).toInt()

    @Test fun editingAChangedOccurrenceForAllKeepsTheSeries() = runBlocking {
        val id = series("Standup", 5, synced = true)
        val master = repo.event(id)!!
        // Move day 1 to 14:00 (an exception row).
        val d = draftFrom(master, at(1, 10))
        repo.update(master, d.copy(start = d.start.withHour(14), end = d.end.withHour(15)), Scope.THIS, at(1, 10))
        val exc = occurrences().first { dayOf(it.first) == 1 }
        val excRow = repo.event(exc.second)!!
        assertTrue(excRow.isException)
        // Rename it for all events, without touching the time.
        repo.update(excRow, draftFrom(excRow, excRow.dtStart).copy(title = "Daily standup"), Scope.ALL, excRow.dtStart)
        val occ = occurrences()
        assertEquals("five occurrences, none lost or doubled", 5, occ.size)
        assertEquals(listOf(0, 1, 2, 3, 4), occ.map { dayOf(it.first) })
        assertTrue("series still at 10:00", occ.filter { dayOf(it.first) != 1 }.all { hourOf(it.first) == 10 })
        assertNotNull("series still repeats", repo.event(id)!!.rrule)
    }

    @Test fun movingASyncedSeriesKeepsItsDeletedOccurrenceDeleted() = runBlocking {
        val id = series("Gym", 5, synced = true)
        val master = repo.event(id)!!
        repo.delete(master, Scope.THIS, at(2, 10)) // a cancelled exception row
        assertEquals(4, occurrences().size)
        val d = draftFrom(master, at(0, 10))
        repo.update(repo.event(id)!!, d.copy(start = d.start.plusHours(1), end = d.end.plusHours(1)), Scope.ALL, at(0, 10))
        val occ = occurrences()
        assertEquals(listOf(0, 1, 3, 4), occ.map { dayOf(it.first) })
        assertTrue(occ.all { hourOf(it.first) == 11 })
    }

    @Test fun movingASyncedSeriesDoesNotDoubleARenamedOccurrence() = runBlocking {
        val id = series("Sync", 5, synced = true)
        val master = repo.event(id)!!
        repo.update(master, draftFrom(master, at(1, 10)).copy(title = "Sync (cancelled lunch)"), Scope.THIS, at(1, 10))
        val d = draftFrom(repo.event(id)!!, at(0, 10))
        repo.update(repo.event(id)!!, d.copy(start = d.start.plusHours(1), end = d.end.plusHours(1)), Scope.ALL, at(0, 10))
        val occ = occurrences()
        assertEquals("one entry per day", listOf(0, 1, 2, 3, 4), occ.map { dayOf(it.first) })
        assertEquals("Sync (cancelled lunch)", occ.first { dayOf(it.first) == 1 }.third)
    }

    @Test fun deletingOneDayOfAnAllDaySeries() = runBlocking {
        val utcDay = day0.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val id = cr.insert(Events.CONTENT_URI, ContentValues().apply {
            put(Events.CALENDAR_ID, localCal); put(Events.TITLE, "Holiday"); put(Events.ALL_DAY, 1)
            put(Events.DTSTART, utcDay); put(Events.DURATION, "P1D"); put(Events.RRULE, "FREQ=DAILY;COUNT=4")
            put(Events.EVENT_TIMEZONE, "UTC")
        })!!.let(ContentUris::parseId)
        repo.delete(repo.event(id)!!, Scope.THIS, utcDay + 2 * 86_400_000L)
        assertEquals("20300306", repo.event(id)!!.exdate)
        assertEquals(3, occurrences().size)
    }

    @Test fun splittingKeepsLeftOutOccurrences() = runBlocking {
        val id = series("Lesson", 10, synced = false)
        repo.delete(repo.event(id)!!, Scope.THIS, at(6, 10)) // EXDATE on day 6
        val master = repo.event(id)!!
        val d = draftFrom(master, at(3, 10))
        repo.update(master, d.copy(start = d.start.withHour(12), end = d.end.withHour(13)), Scope.FOLLOWING, at(3, 10))
        val occ = occurrences()
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 7, 8, 9), occ.map { dayOf(it.first) })
        assertTrue(occ.filter { dayOf(it.first) < 3 }.all { hourOf(it.first) == 10 })
        assertTrue(occ.filter { dayOf(it.first) >= 3 }.all { hourOf(it.first) == 12 })
    }

    @Test fun splittingASyncedSeriesDoesNotDoubleAChangedOccurrence() = runBlocking {
        val id = series("Review", 6, synced = true)
        val master = repo.event(id)!!
        repo.update(master, draftFrom(master, at(4, 10)).copy(title = "Review (moved room)"), Scope.THIS, at(4, 10))
        val d = draftFrom(repo.event(id)!!, at(2, 10))
        repo.update(repo.event(id)!!, d.copy(start = d.start.withHour(12), end = d.end.withHour(13)), Scope.FOLLOWING, at(2, 10))
        val occ = occurrences()
        assertEquals("one entry per day", listOf(0, 1, 2, 3, 4, 5), occ.map { dayOf(it.first) })
        assertEquals("Review (moved room)", occ.first { dayOf(it.first) == 4 }.third)
    }

    @Test fun changingTheCalendarMovesAOneOffEvent() = runBlocking {
        val id = cr.insert(adapter(Events.CONTENT_URI), ContentValues().apply {
            put(Events.CALENDAR_ID, syncedCal); put(Events.TITLE, "Dentist")
            put(Events.DTSTART, at(1, 9)); put(Events.DTEND, at(1, 10)); put(Events.EVENT_TIMEZONE, zone.id)
        })!!.let(ContentUris::parseId)
        val e = repo.event(id)!!
        repo.update(e, draftFrom(e, 0).copy(calendarId = localCal), Scope.ALL, e.dtStart)
        val occ = occurrences()
        assertEquals(1, occ.size)
        assertEquals(localCal, repo.event(occ[0].second)!!.calendarId)
    }

    @Test fun anUnrelatedEditKeepsAColorSetWithoutAKey() = runBlocking {
        val id = cr.insert(adapter(Events.CONTENT_URI), ContentValues().apply {
            put(Events.CALENDAR_ID, syncedCal); put(Events.TITLE, "Party")
            put(Events.DTSTART, at(2, 18)); put(Events.DTEND, at(2, 20)); put(Events.EVENT_TIMEZONE, zone.id)
            put(Events.EVENT_COLOR, 0xFF8E24AA.toInt())
        })!!.let(ContentUris::parseId)
        val e = repo.event(id)!!
        repo.update(e, draftFrom(e, 0).copy(location = "Town hall"), Scope.ALL, e.dtStart)
        cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, id), arrayOf(Events.EVENT_COLOR, Events.EVENT_LOCATION), null, null, null)!!.use { c ->
            c.moveToFirst()
            assertEquals(0xFF8E24AA.toInt(), c.getInt(0))
            assertEquals("Town hall", c.getString(1))
        }
    }
}
