package app.crome1394.almanac.data

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset

/** Birthdays from the phone's contacts, shown as all-day occurrences (read-only). */
class Birthdays(private val context: Context) {
    data class Entry(val lookup: String, val name: String, val day: MonthDay, val year: Int?)

    @Volatile private var cache: List<Entry>? = null

    fun invalidate() { cache = null }

    suspend fun entries(): List<Entry> = withContext(Dispatchers.IO) {
        cache ?: load().also { cache = it }
    }

    private fun load(): List<Entry> {
        if (!Permissions.hasContacts(context)) return emptyList()
        val out = mutableListOf<Entry>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.LOOKUP_KEY, ContactsContract.Data.DISPLAY_NAME, Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE}=? AND ${Event.TYPE}=${Event.TYPE_BIRTHDAY}",
                arrayOf(Event.CONTENT_ITEM_TYPE), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val (day, year) = parse(c.getString(2) ?: continue) ?: continue
                    out += Entry(c.getString(0) ?: continue, c.getString(1) ?: "", day, year)
                }
            }
        }
        return out.distinctBy { it.lookup to it.day }
    }

    fun occurrences(entries: List<Entry>, from: LocalDate, to: LocalDate, color: Int): List<Instance> {
        val out = mutableListOf<Instance>()
        for (year in from.year..to.year) for (e in entries) {
            if (e.day.month.value == 2 && e.day.dayOfMonth == 29 && !java.time.Year.isLeap(year.toLong())) continue
            val date = e.day.atYear(year)
            if (date < from || date > to) continue
            val age = e.year?.let { year - it }?.takeIf { it in 1..150 }
            val begin = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            out += Instance(
                eventId = -1, begin = begin, end = begin + 86_400_000L, allDay = true,
                title = if (e.name.isBlank()) "Birthday" else "${e.name}'s birthday" + (age?.let { " ($it)" } ?: ""),
                location = "", color = color, calendarId = BIRTHDAY_CALENDAR, recurring = true,
                selfStatus = 0, declined = false, tentative = false, birthdayContactLookup = e.lookup,
            )
        }
        return out
    }

    companion object {
        const val BIRTHDAY_CALENDAR = -2L
        const val COLOR = 0xFF33B679.toInt()

        /** Contacts store birthdays as "yyyy-MM-dd", "--MM-dd" or sometimes "yyyyMMdd". */
        fun parse(s: String): Pair<MonthDay, Int?>? = runCatching {
            val t = s.trim().take(10)
            when {
                t.startsWith("--") -> MonthDay.parse(t) to null
                t.length == 8 && t.all(Char::isDigit) -> MonthDay.of(t.substring(4, 6).toInt(), t.substring(6, 8).toInt()) to t.take(4).toInt()
                else -> LocalDate.parse(t).let { MonthDay.from(it) to it.year }
            }
        }.getOrNull()
    }
}
