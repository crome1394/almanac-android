package app.crome1394.almanac.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** A BYDAY entry: a weekday, optionally with an ordinal (2MO = second Monday, -1FR = last Friday). */
data class WeekdayNum(val ordinal: Int, val day: DayOfWeek) {
    override fun toString(): String = (if (ordinal != 0) ordinal.toString() else "") + dayCode(day)
}

private val DAY_CODES = mapOf(
    "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
    "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
)

fun dayCode(d: DayOfWeek): String = DAY_CODES.entries.first { it.value == d }.key

/**
 * An RFC 5545 RRULE. Parts the app does not edit (BYSETPOS, BYHOUR, …) are kept in [other] and
 * written back unchanged, so editing an event never loses part of a rule made elsewhere.
 */
data class RRule(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val count: Int? = null,
    val until: String? = null,
    val other: List<Pair<String, String>> = emptyList(),
) {
    /** True when the app's editor can show and change every part of the rule. */
    val isSimple: Boolean get() = other.isEmpty() && byMonth.size <= 1 && byMonthDay.size <= 1 &&
        (freq == Freq.WEEKLY || byDay.size <= 1)

    fun format(): String = buildList {
        add("FREQ=${freq.name}")
        if (until != null) add("UNTIL=$until")
        if (count != null) add("COUNT=$count")
        if (interval > 1) add("INTERVAL=$interval")
        if (byDay.isNotEmpty()) add("BYDAY=" + byDay.joinToString(","))
        if (byMonthDay.isNotEmpty()) add("BYMONTHDAY=" + byMonthDay.joinToString(","))
        if (byMonth.isNotEmpty()) add("BYMONTH=" + byMonth.joinToString(","))
        other.forEach { (k, v) -> add("$k=$v") }
    }.joinToString(";")

    /** The UNTIL date in the event's own terms, if any. */
    fun untilDate(zone: ZoneId): LocalDate? = until?.let { parseUntil(it, zone) }

    companion object {
        fun parse(text: String?): RRule? {
            if (text.isNullOrBlank()) return null
            var freq: Freq? = null
            var interval = 1
            var byDay = emptyList<WeekdayNum>()
            var byMonthDay = emptyList<Int>()
            var byMonth = emptyList<Int>()
            var count: Int? = null
            var until: String? = null
            val other = mutableListOf<Pair<String, String>>()
            for (part in text.removePrefix("RRULE:").split(';')) {
                val eq = part.indexOf('=')
                if (eq <= 0) continue
                val k = part.substring(0, eq).uppercase()
                val v = part.substring(eq + 1)
                when (k) {
                    "FREQ" -> freq = runCatching { Freq.valueOf(v.uppercase()) }.getOrNull() ?: return null
                    "INTERVAL" -> interval = v.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    "COUNT" -> count = v.toIntOrNull()
                    "UNTIL" -> until = v
                    "BYDAY" -> byDay = v.split(',').mapNotNull(::parseWeekdayNum).ifEmpty { return null }
                    "BYMONTHDAY" -> byMonthDay = v.split(',').mapNotNull { it.toIntOrNull() }
                    "BYMONTH" -> byMonth = v.split(',').mapNotNull { it.toIntOrNull() }
                    else -> other += k to v
                }
            }
            return RRule(freq ?: return null, interval, byDay, byMonthDay, byMonth, count, until, other)
        }

        private fun parseWeekdayNum(s: String): WeekdayNum? {
            val t = s.trim().uppercase()
            if (t.length < 2) return null
            val day = DAY_CODES[t.takeLast(2)] ?: return null
            val ord = t.dropLast(2).let { if (it.isEmpty() || it == "+") 0 else it.removePrefix("+").toIntOrNull() ?: return null }
            return WeekdayNum(ord, day)
        }

        /** UNTIL for an event: all-day events use a plain date, timed ones UTC at the end of that day. */
        fun formatUntil(date: LocalDate, allDay: Boolean, zone: ZoneId): String =
            if (allDay) date.format(DateTimeFormatter.BASIC_ISO_DATE)
            else LocalDateTime.of(date, LocalTime.of(23, 59, 59)).atZone(zone)
                .withZoneSameInstant(ZoneOffset.UTC).format(UNTIL_UTC)

        /** UNTIL that ends a series just before [instantMillis] (for "this and following"). */
        fun untilBefore(instantMillis: Long, allDay: Boolean): String =
            if (allDay) java.time.Instant.ofEpochMilli(instantMillis).atZone(ZoneOffset.UTC).toLocalDate()
                .minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
            else java.time.Instant.ofEpochMilli(instantMillis - 1000).atZone(ZoneOffset.UTC).format(UNTIL_UTC)

        fun parseUntil(s: String, zone: ZoneId): LocalDate? = runCatching {
            when {
                s.length == 8 -> LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE)
                s.endsWith("Z") -> LocalDateTime.parse(s, UNTIL_UTC).atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(zone).toLocalDate()
                else -> LocalDateTime.parse(s, UNTIL_LOCAL).toLocalDate()
            }
        }.getOrNull()

        private val UNTIL_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        private val UNTIL_LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        /** The nth weekday of the month for [date], or -1 when it is the last one. */
        fun weekOfMonth(date: LocalDate): Int = (date.dayOfMonth - 1) / 7 + 1
        fun isLastWeekdayOfMonth(date: LocalDate): Boolean = date.plusWeeks(1).month != date.month

        /** Google Calendar's quick choices for an event starting on [start]. */
        fun presets(start: LocalDate): List<RRule> {
            val dow = start.dayOfWeek
            val list = mutableListOf(
                RRule(Freq.DAILY),
                RRule(Freq.WEEKLY, byDay = listOf(WeekdayNum(0, dow))),
                RRule(Freq.MONTHLY, byDay = listOf(WeekdayNum(weekOfMonth(start).coerceAtMost(4), dow))),
            )
            if (isLastWeekdayOfMonth(start)) list += RRule(Freq.MONTHLY, byDay = listOf(WeekdayNum(-1, dow)))
            list += RRule(Freq.YEARLY)
            list += RRule(Freq.WEEKLY, byDay = WEEKDAYS.map { WeekdayNum(0, it) })
            return list
        }

        val WEEKDAYS = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}

/** Human description in Google Calendar's style ("Every 2 weeks on Monday, Wednesday; 5 times"). */
object RecurrenceText {
    private val ORDINALS = mapOf(1 to "first", 2 to "second", 3 to "third", 4 to "fourth", 5 to "fifth", -1 to "last", -2 to "second-to-last")

    fun describe(rule: RRule, start: LocalDate, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, locale)
        val n = rule.interval
        val base = when (rule.freq) {
            Freq.DAILY -> if (n == 1) "Daily" else "Every $n days"
            Freq.WEEKLY -> {
                val days = rule.byDay.map { it.day }.ifEmpty { listOf(start.dayOfWeek) }
                val sorted = days.distinct().sortedBy { (it.value + 6) % 7 }
                when {
                    n == 1 && sorted == RRule.WEEKDAYS -> "Every weekday (Monday to Friday)"
                    n == 1 && sorted.size == 7 -> "Daily"
                    n == 1 -> "Weekly on " + sorted.joinToString(", ") { dayName(it) }
                    else -> "Every $n weeks on " + sorted.joinToString(", ") { dayName(it) }
                }
            }
            Freq.MONTHLY -> {
                val what = when {
                    rule.byDay.size == 1 && rule.byDay[0].ordinal != 0 ->
                        "the ${ORDINALS[rule.byDay[0].ordinal] ?: "${rule.byDay[0].ordinal}th"} ${dayName(rule.byDay[0].day)}"
                    rule.byMonthDay.size == 1 && rule.byMonthDay[0] == -1 -> "the last day"
                    rule.byMonthDay.size == 1 -> "day ${rule.byMonthDay[0]}"
                    else -> "day ${start.dayOfMonth}"
                }
                if (n == 1) "Monthly on $what" else "Every $n months on $what"
            }
            Freq.YEARLY -> {
                val md = start.format(DateTimeFormatter.ofPattern("MMMM d", locale))
                if (n == 1) "Annually on $md" else "Every $n years on $md"
            }
        }
        val ends = when {
            rule.count != null -> if (rule.count == 1) ", once" else ", ${rule.count} times"
            rule.until != null -> rule.untilDate(zone)?.let { ", until " + it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) } ?: ""
            else -> ""
        }
        val extra = if (rule.other.isNotEmpty() || (rule.freq != Freq.WEEKLY && rule.byDay.size > 1)) " (custom)" else ""
        return base + ends + extra
    }
}

/** Moves a date to the start of its week. */
fun LocalDate.startOfWeek(first: DayOfWeek): LocalDate = with(TemporalAdjusters.previousOrSame(first))
