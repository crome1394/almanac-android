package app.crome1394.almanac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Reminders
import android.util.Log
import app.crome1394.almanac.data.Attendee
import app.crome1394.almanac.data.EventDraft
import app.crome1394.almanac.data.Reminder
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import kotlin.concurrent.thread

/**
 * Debug-only test data: `adb shell am broadcast -n app.crome1394.almanac.debug/app.crome1394.almanac.DemoSeeder`.
 * Creates two phone-only calendars with made-up events around today. Never part of release builds.
 */
class DemoSeeder : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread {
            try { runBlocking {
                val c = context.applicationContext
                when {
                    intent.hasExtra("soon") -> soon(c)
                    intent.hasExtra("css") -> css(c)
                    else -> seed(c)
                }
            } } catch (e: Exception) { Log.e("Almanac", "seed failed", e) }
            finally { pending.finish() }
        }
    }

    /** `--ez soon true`: an event two minutes from now with a one-minute reminder, to test notifications. */
    private suspend fun soon(c: Context) {
        val cal = c.app.repo.calendars().first { it.isLocal }
        val start = java.time.LocalDateTime.now().plusMinutes(2).withSecond(0).withNano(0)
        c.app.repo.insert(EventDraft(calendarId = cal.id, title = "Reminder test", start = start, end = start.plusMinutes(30),
            location = "Test room", reminders = listOf(Reminder(1, Reminders.METHOD_ALERT))))
    }

    /**
     * `--ez css true`: a calendar that looks synced (made-up account type) and offers CSS3 named
     * event colors the way DAVx5 does, with one event colored "deepskyblue".
     */
    private suspend fun css(c: Context) {
        val acct = "test@example.com"; val type = "app.crome1394.almanac.test"
        fun uri(base: android.net.Uri) = base.buildUpon()
            .appendQueryParameter(android.provider.CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(android.provider.CalendarContract.Calendars.ACCOUNT_NAME, acct)
            .appendQueryParameter(android.provider.CalendarContract.Calendars.ACCOUNT_TYPE, type).build()
        val cr = c.contentResolver
        val colors = mapOf("red" to 0xFF0000, "crimson" to 0xDC143C, "salmon" to 0xFA8072, "orangered" to 0xFF4500,
            "gold" to 0xFFD700, "mediumseagreen" to 0x3CB371, "seagreen" to 0x2E8B57, "deepskyblue" to 0x00BFFF,
            "darkslateblue" to 0x483D8B, "mediumpurple" to 0x9370DB, "darkorchid" to 0x9932CC, "dimgray" to 0x696969, "teal" to 0x008080)
        colors.forEach { (k, v) ->
            cr.insert(uri(android.provider.CalendarContract.Colors.CONTENT_URI), android.content.ContentValues().apply {
                put("account_name", acct); put("account_type", type)
                put(android.provider.CalendarContract.Colors.COLOR_TYPE, android.provider.CalendarContract.Colors.TYPE_EVENT)
                put(android.provider.CalendarContract.Colors.COLOR_KEY, k); put(android.provider.CalendarContract.Colors.COLOR, (0xFF000000 or v.toLong()).toInt())
            })
        }
        val calId = cr.insert(uri(android.provider.CalendarContract.Calendars.CONTENT_URI), android.content.ContentValues().apply {
            put("account_name", acct); put("account_type", type)
            put(android.provider.CalendarContract.Calendars.NAME, "Server")
            put(android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Server")
            put(android.provider.CalendarContract.Calendars.CALENDAR_COLOR, 0xFFE67C73.toInt())
            put(android.provider.CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, android.provider.CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(android.provider.CalendarContract.Calendars.OWNER_ACCOUNT, acct)
            put(android.provider.CalendarContract.Calendars.VISIBLE, 1); put(android.provider.CalendarContract.Calendars.SYNC_EVENTS, 1)
        })!!.let(android.content.ContentUris::parseId)
        val day = LocalDate.now().plusDays(1)
        c.app.repo.insert(EventDraft(calendarId = calId, title = "Colored on the server", start = day.atTime(8, 0), end = day.atTime(9, 0), colorKey = "deepskyblue"))
        c.app.repo.insert(EventDraft(calendarId = calId, title = "Teal from another app", start = day.atTime(9, 0), end = day.atTime(10, 0), colorKey = "teal"))
        c.app.repo.insert(EventDraft(calendarId = calId, title = "Server calendar color", start = day.atTime(10, 0), end = day.atTime(11, 0)))
    }

    private suspend fun seed(c: Context) {
        val repo = c.app.repo
        val existing = repo.calendars()
        val personal = existing.firstOrNull { it.isLocal && it.name == "Personal" }?.id ?: repo.createLocalCalendar("Personal", 0xFF039BE5.toInt())
        val work = existing.firstOrNull { it.isLocal && it.name == "Work" }?.id ?: repo.createLocalCalendar("Work", 0xFF0B8043.toInt())
        val today = LocalDate.now()
        fun ev(cal: Long, title: String, day: LocalDate, from: LocalTime, to: LocalTime, block: EventDraft.() -> EventDraft = { this }) =
            EventDraft(calendarId = cal, title = title, start = day.atTime(from), end = day.atTime(to),
                reminders = listOf(Reminder(10, Reminders.METHOD_ALERT))).block()
        val list = listOf(
            ev(work, "Stand-up", today.minusDays(14), LocalTime.of(9, 30), LocalTime.of(9, 45)) { copy(rrule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR") },
            ev(work, "Design review", today, LocalTime.of(10, 0), LocalTime.of(11, 30)) {
                copy(location = "Room 4", description = "Agenda: https://example.com/agenda",
                    attendees = listOf(Attendee(email = "alex@example.com", name = "Alex", status = Attendees.ATTENDEE_STATUS_ACCEPTED),
                        Attendee(email = "sam@example.com", name = "Sam")), organizer = "Phone")
            },
            ev(personal, "Dentist", today, LocalTime.of(11, 0), LocalTime.of(12, 0)) { copy(location = "12 High Street") },
            ev(personal, "Lunch with Jo", today, LocalTime.of(12, 30), LocalTime.of(13, 30)),
            ev(work, "Quarterly planning", today.plusDays(1), LocalTime.of(14, 0), LocalTime.of(16, 0)) { copy(colorKey = "Tangerine") },
            ev(personal, "Gym", today.minusDays(7), LocalTime.of(18, 0), LocalTime.of(19, 0)) { copy(rrule = "FREQ=WEEKLY;BYDAY=TU,TH") },
            ev(personal, "Trip to the coast", today.plusDays(3), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT) {
                copy(allDay = true, end = today.plusDays(5).atStartOfDay(), reminders = emptyList())
            },
            ev(personal, "Mum's anniversary", today.plusDays(9), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT) {
                copy(allDay = true, end = today.plusDays(9).atStartOfDay(), rrule = "FREQ=YEARLY")
            },
            ev(work, "Late deploy", today.plusDays(2), LocalTime.of(22, 0), LocalTime.of(1, 0)) { copy(end = today.plusDays(3).atTime(1, 0)) },
            ev(personal, "Book club", today.minusDays(3), LocalTime.of(19, 30), LocalTime.of(21, 0)) { copy(rrule = "FREQ=MONTHLY;BYDAY=3TH") },
        )
        list.forEach { repo.insert(it) }
        Log.i("Almanac", "seeded ${list.size} events")
    }
}
