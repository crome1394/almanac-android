package app.crome1394.almanac.ui

import android.content.Context
import android.provider.CalendarContract.Reminders
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Date and time text, following the phone's 12/24-hour setting and language. */
class Fmt(context: Context, val locale: Locale = Locale.getDefault()) {
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    private val timeF = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", locale)
    private val hourF = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h a", locale)
    private fun pattern(skeleton: String) = DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    private val dayLong = pattern("EEEEdMMMM")
    private val dayLongYear = pattern("EEEEdMMMMyyyy")
    private val dayMed = pattern("EEEdMMM")
    private val dayMedYear = pattern("EEEdMMMyyyy")
    private val monthDay = pattern("dMMM")
    private val monthYear = pattern("MMMMyyyy")
    private val monthOnly = DateTimeFormatter.ofPattern("LLLL", locale)

    fun time(t: LocalTime): String = timeF.format(t)
    fun time(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = timeF.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun hourLabel(h: Int): String = hourF.format(LocalTime.of(h, 0))

    fun dayLong(d: LocalDate): String = (if (d.year == LocalDate.now().year) dayLong else dayLongYear).format(d)
    fun dayMedium(d: LocalDate): String = (if (d.year == LocalDate.now().year) dayMed else dayMedYear).format(d)
    fun monthDay(d: LocalDate): String = monthDay.format(d)
    fun monthTitle(d: LocalDate): String = (if (d.year == LocalDate.now().year) monthOnly else monthYear).format(d).replaceFirstChar { it.titlecase(locale) }
    fun monthYear(d: LocalDate): String = monthYear.format(d).replaceFirstChar { it.titlecase(locale) }
    fun weekdayShort(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    fun weekdayNarrow(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)

    /** "10:00 – 11:00", or "All day". */
    fun rangeShort(begin: Long, end: Long, allDay: Boolean): String {
        if (allDay) return "All day"
        val z = ZoneId.systemDefault()
        val s = Instant.ofEpochMilli(begin).atZone(z)
        val e = Instant.ofEpochMilli(end).atZone(z)
        return if (s.toLocalDate() == e.toLocalDate() || end <= begin) "${time(begin)} – ${time(end)}"
        else "${monthDay(s.toLocalDate())}, ${time(begin)} – ${monthDay(e.toLocalDate())}, ${time(end)}"
    }

    /** The detail screen's when line: "Monday, 17 November · 10:00 – 11:00". */
    fun detailWhen(begin: Long, end: Long, allDay: Boolean): String {
        val z = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val s = Instant.ofEpochMilli(begin).atZone(z).toLocalDate()
        val last = Instant.ofEpochMilli(maxOf(begin, end - 1)).atZone(z).toLocalDate()
        return when {
            allDay && s == last -> dayLong(s)
            allDay -> "${dayMedium(s)} – ${dayMedium(last)}"
            s == Instant.ofEpochMilli(end).atZone(z).toLocalDate() || end <= begin -> "${dayLong(s)} · ${time(begin)} – ${time(end)}"
            else -> "${dayMedium(s)}, ${time(begin)} – ${dayMedium(Instant.ofEpochMilli(end).atZone(z).toLocalDate())}, ${time(end)}"
        }
    }

    /** "30 minutes before", "1 day before at 9:00", "On the day at 9:00". */
    fun reminder(minutes: Int, method: Int, allDay: Boolean): String {
        val base = if (allDay) allDayReminder(minutes) else timedReminder(minutes)
        return if (method == Reminders.METHOD_EMAIL) "$base as email" else base
    }

    private fun timedReminder(m: Int): String = when {
        m == 0 -> "At the time of the event"
        m < 0 -> "${amount(-m)} after"
        else -> "${amount(m)} before"
    }

    private fun amount(m: Int): String = when {
        m % 10_080 == 0 -> plural(m / 10_080, "week")
        m % 1440 == 0 -> plural(m / 1440, "day")
        m % 60 == 0 -> plural(m / 60, "hour")
        else -> plural(m, "minute")
    }

    private fun allDayReminder(m: Int): String {
        // Minutes before midnight at the start of the event day.
        val daysBefore = if (m <= 0) 0 else (m + 1439) / 1440
        val minuteOfDay = daysBefore * 1440 - m
        val at = time(LocalTime.MIDNIGHT.plusMinutes(minuteOfDay.toLong().mod(1440L)))
        return when (daysBefore) {
            0 -> "On the day at $at"
            1 -> "The day before at $at"
            else -> if (daysBefore % 7 == 0) "${plural(daysBefore / 7, "week")} before at $at" else "$daysBefore days before at $at"
        }
    }

    companion object {
        fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"

        /** Minutes value for an all-day reminder [daysBefore] days earlier at [at]. */
        fun allDayMinutes(daysBefore: Int, at: LocalTime): Int = daysBefore * 1440 - (at.hour * 60 + at.minute)
    }
}
