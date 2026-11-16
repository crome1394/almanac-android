package app.crome1394.almanac.data

import android.provider.CalendarContract.Calendars
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A calendar from the system calendar store (DAVx5, a phone-only calendar, …). */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val accessLevel: Int,
    val visible: Boolean,
    val ownerAccount: String?,
    val allowedReminders: Set<Int>,
    val allowedAvailability: Set<Int>,
    val timeZone: String?,
) {
    val writable: Boolean get() = accessLevel >= Calendars.CAL_ACCESS_CONTRIBUTOR
    val isLocal: Boolean get() = accountType == android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
}

/**
 * One occurrence of an event in a date range. All-day events are stored by the calendar
 * store at UTC midnight, so their dates are read in UTC; timed events in the phone's zone.
 */
data class Instance(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val title: String,
    val location: String,
    val color: Int,
    val calendarId: Long,
    val recurring: Boolean,
    val selfStatus: Int,
    val declined: Boolean,
    /** Shown dimmed (for example an invitation not yet answered). */
    val tentative: Boolean,
    val birthdayContactLookup: String? = null,
) {
    val isBirthday: Boolean get() = birthdayContactLookup != null

    fun startDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()

    /** The last day the event covers (inclusive). */
    fun lastDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val start = startDate(zone)
        if (end <= begin) return start
        val z = if (allDay) ZoneOffset.UTC else zone
        val last = Instant.ofEpochMilli(end - 1).atZone(z).toLocalDate()
        return if (last.isBefore(start)) start else last
    }

    fun covers(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        !day.isBefore(startDate(zone)) && !day.isAfter(lastDate(zone))

    /** Timed events that cross midnight are shown with the all-day ones, as Google does. */
    fun isMultiDayTimed(zone: ZoneId = ZoneId.systemDefault()): Boolean =
        !allDay && lastDate(zone) != startDate(zone)

    val key: String get() = "$eventId@$begin"
}

data class Reminder(val minutes: Int, val method: Int)

data class Attendee(
    val id: Long = 0,
    val email: String,
    val name: String = "",
    val status: Int = android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_NONE,
    val relationship: Int = android.provider.CalendarContract.Attendees.RELATIONSHIP_ATTENDEE,
    val type: Int = android.provider.CalendarContract.Attendees.TYPE_REQUIRED,
) {
    val label: String get() = name.ifBlank { email }
    val isOrganizer: Boolean get() = relationship == android.provider.CalendarContract.Attendees.RELATIONSHIP_ORGANIZER
}

/** A full event row, as stored (for a recurring event this is the series, not one occurrence). */
data class EventDetails(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val description: String,
    val location: String,
    val dtStart: Long,
    val dtEnd: Long?,
    val duration: String?,
    val allDay: Boolean,
    val timeZone: String,
    val rrule: String?,
    val rdate: String?,
    val exdate: String?,
    val color: Int,
    val colorKey: String?,
    val hasOwnColor: Boolean,
    val availability: Int,
    val accessLevel: Int,
    val organizer: String?,
    val guestsCanModify: Boolean,
    val originalId: Long?,
    val originalInstanceTime: Long?,
    val selfStatus: Int,
    val status: Int?,
    val reminders: List<Reminder>,
    val attendees: List<Attendee>,
) {
    val isRecurring: Boolean get() = !rrule.isNullOrBlank() || !rdate.isNullOrBlank()
    val isException: Boolean get() = originalId != null
}

/** An event color offered by the calendar's account (Colors table). */
data class EventColor(val key: String, val color: Int)

/** Which occurrences of a recurring event an edit or delete applies to. */
enum class Scope { THIS, FOLLOWING, ALL }
