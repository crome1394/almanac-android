package app.crome1394.almanac.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object Permissions {
    val CALENDAR = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasCalendar(c: Context) = CALENDAR.all { granted(c, it) }
    fun hasContacts(c: Context) = granted(c, Manifest.permission.READ_CONTACTS)
    fun hasNotifications(c: Context) = Build.VERSION.SDK_INT < 33 || granted(c, Manifest.permission.POST_NOTIFICATIONS)

    private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
}

/** Google Calendar's event and calendar colors (names as Google shows them). */
object Palette {
    val EVENT: List<Pair<String, Int>> = listOf(
        "Tomato" to 0xFFD50000.toInt(),
        "Flamingo" to 0xFFE67C73.toInt(),
        "Tangerine" to 0xFFF4511E.toInt(),
        "Banana" to 0xFFF6BF26.toInt(),
        "Sage" to 0xFF33B679.toInt(),
        "Basil" to 0xFF0B8043.toInt(),
        "Peacock" to 0xFF039BE5.toInt(),
        "Blueberry" to 0xFF3F51B5.toInt(),
        "Lavender" to 0xFF7986CB.toInt(),
        "Grape" to 0xFF8E24AA.toInt(),
        "Graphite" to 0xFF616161.toInt(),
    )

    fun nameOf(color: Int): String? = EVENT.firstOrNull { it.second == color }?.first

    /**
     * Google's colors as the account can store them. Phone-only calendars carry Google's own
     * colors; DAVx5 offers the CSS3 named colors (when its "Event colors" setting is on), so each
     * Google color is matched to the nearest one the account has. Returns (Google name, Google
     * color, account color key) for each Google color that has a match.
     */
    fun choices(available: List<EventColor>): List<Triple<String, Int, String>> {
        if (available.isEmpty()) return emptyList()
        return EVENT.map { (name, c) ->
            val exact = available.firstOrNull { it.key.equals(name, ignoreCase = true) }
            Triple(name, c, (exact ?: available.minBy { distance(it.color, c) }).key)
        }
    }

    /** Account color key → the Google color drawn for it (first Google color to claim a key wins). */
    fun shades(available: List<EventColor>): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        choices(available).forEach { (_, c, key) -> out.putIfAbsent(key, c) }
        return out
    }

    /** Perceptual-ish RGB distance ("redmean"), good enough to pick the nearest named color. */
    internal fun distance(a: Int, b: Int): Double {
        val r1 = (a shr 16) and 0xFF; val g1 = (a shr 8) and 0xFF; val b1 = a and 0xFF
        val r2 = (b shr 16) and 0xFF; val g2 = (b shr 8) and 0xFF; val b2 = b and 0xFF
        val rm = (r1 + r2) / 2.0
        val dr = (r1 - r2).toDouble(); val dg = (g1 - g2).toDouble(); val db = (b1 - b2).toDouble()
        return (2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db
    }
}
