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
            try { runBlocking { seed(context.applicationContext) } } catch (e: Exception) { Log.e("Almanac", "seed failed", e) }
            finally { pending.finish() }
        }
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
