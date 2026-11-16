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
}
