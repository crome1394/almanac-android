package app.crome1394.almanac.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A small iCalendar (RFC 5545) reader for importing .ics files: VEVENTs with their times,
 * text, repeat rule and alarms. It never touches the network and ignores what it cannot read.
 */
object Ics {
    data class Parsed(
        val title: String,
        val description: String,
        val location: String,
        val allDay: Boolean,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val zone: ZoneId,
        val rrule: String?,
        val reminderMinutes: List<Int>,
    )

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    fun parse(text: String): List<Parsed> {
        val lines = unfold(text)
        val out = mutableListOf<Parsed>()
        var props: MutableList<Prop>? = null
        val alarms = mutableListOf<Int>()
        var inAlarm = false
        var alarmTrigger: Int? = null
        for (line in lines) {
            val p = parseLine(line) ?: continue
            when {
                p.name == "BEGIN" && p.value.equals("VEVENT", true) -> { props = mutableListOf(); alarms.clear() }
                p.name == "BEGIN" && p.value.equals("VALARM", true) -> { inAlarm = true; alarmTrigger = null }
                p.name == "END" && p.value.equals("VALARM", true) -> { inAlarm = false; alarmTrigger?.let(alarms::add) }
                p.name == "END" && p.value.equals("VEVENT", true) -> {
                    props?.let { build(it, alarms.toList()) }?.let(out::add)
                    props = null
                }
                inAlarm && p.name == "TRIGGER" && p.params["VALUE"] == null -> alarmTrigger = triggerMinutes(p.value)
                !inAlarm -> props?.add(p)
            }
        }
        return out
    }

    private fun build(props: List<Prop>, alarms: List<Int>): Parsed? {
        fun get(n: String) = props.firstOrNull { it.name == n }
        val ds = get("DTSTART") ?: return null
        val allDay = ds.params["VALUE"].equals("DATE", true) || ds.value.length == 8
        val zone = ds.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: if (ds.value.endsWith("Z")) ZoneOffset.UTC else ZoneId.systemDefault()
        val start = parseTime(ds.value, zone) ?: return null
        val de = get("DTEND")
        val end = when {
            de != null -> parseTime(de.value, de.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone)
            get("DURATION") != null -> runCatching { start.plus(Duration.parse(get("DURATION")!!.value)) }.getOrNull()
            else -> null
        }?.let { if (allDay) it.minusDays(1) else it }?.takeIf { it >= start } ?: start.plusHours(if (allDay) 0 else 1)
        return Parsed(
            title = unescape(get("SUMMARY")?.value ?: ""),
            description = unescape(get("DESCRIPTION")?.value ?: ""),
            location = unescape(get("LOCATION")?.value ?: ""),
            allDay = allDay,
            start = start, end = end, zone = if (allDay) ZoneId.systemDefault() else convertZone(zone),
            rrule = get("RRULE")?.value?.takeIf { RRule.parse(it) != null },
            reminderMinutes = alarms.filter { it in -10_080..40_320 }.distinct(),
        )
    }

    /** Times in UTC are shown in the phone's zone; others keep their own. */
    private fun convertZone(z: ZoneId): ZoneId = if (z == ZoneOffset.UTC) ZoneId.systemDefault() else z

    private fun parseTime(v: String, zone: ZoneId): LocalDateTime? = runCatching {
        when {
            v.length == 8 -> LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay()
            v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), LOCAL).atZone(ZoneOffset.UTC)
                .withZoneSameInstant(convertZone(zone)).toLocalDateTime()
            else -> LocalDateTime.parse(v, LOCAL)
        }
    }.getOrNull()

    /** "-PT15M" → 15 (minutes before the start); "PT1H" → -60 (after). */
    fun triggerMinutes(v: String): Int? {
        val neg = v.startsWith("-")
        val body = v.trimStart('-', '+')
        val weeks = Regex("^P(\\d+)W$").find(body)
        val minutes = weeks?.let { it.groupValues[1].toLong() * 7 * 24 * 60 }
            ?: runCatching { Duration.parse(body).toMinutes() }.getOrNull() ?: return null
        return (if (neg) minutes else -minutes).toInt()
    }

    private val LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) out[out.lastIndex] += raw.substring(1)
            else if (raw.isNotEmpty()) out += raw
        }
        return out
    }

    private fun parseLine(line: String): Prop? {
        // Find the first ':' that is not inside a quoted parameter value.
        var quoted = false
        var colon = -1
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '"') quoted = !quoted
            else if (ch == ':' && !quoted) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull {
            val eq = it.indexOf('=')
            if (eq <= 0) null else it.substring(0, eq).uppercase() to it.substring(eq + 1).trim('"')
        }.toMap()
        return Prop(head[0].uppercase(), params, line.substring(colon + 1))
    }

    fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 'n', 'N' -> sb.append('\n'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(ch); i++ }
        }
        return sb.toString()
    }
}
