package app.crome1394.almanac.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

enum class ViewMode { SCHEDULE, DAY, THREE_DAY, WEEK, MONTH }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App settings. Every value has a safe default; nothing here is private data. */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 0 = follow the phone's region, else DayOfWeek.value. */
    val weekStart: Int = 0,
    val showWeekNumbers: Boolean = false,
    val hideDeclined: Boolean = false,
    val dimPast: Boolean = true,
    val defaultDurationMin: Int = 60,
    /** Minutes before start; -1 = none. */
    val defaultReminder: Int = 30,
    /** Minutes before midnight at the start of the day; negative = on the day; NONE = none. */
    val defaultAllDayReminder: Int = REMINDER_NONE,
    val notifications: Boolean = true,
    val snoozeMin: Int = 10,
    val showBirthdays: Boolean = false,
    val defaultCalendarId: Long = -1,
    val viewMode: ViewMode = ViewMode.SCHEDULE,
    val hourHeightDp: Float = 52f,
) {
    fun firstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek =
        if (weekStart in 1..7) DayOfWeek.of(weekStart) else WeekFields.of(locale).firstDayOfWeek

    companion object { const val REMINDER_NONE = Int.MIN_VALUE }
}

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current: Settings get() = _settings.value

    private fun read() = Settings(
        theme = enumOr(sp.getString("theme", null), ThemeMode.SYSTEM),
        weekStart = sp.getInt("weekStart", 0),
        showWeekNumbers = sp.getBoolean("weekNumbers", false),
        hideDeclined = sp.getBoolean("hideDeclined", false),
        dimPast = sp.getBoolean("dimPast", true),
        defaultDurationMin = sp.getInt("duration", 60),
        defaultReminder = sp.getInt("reminder", 30),
        defaultAllDayReminder = sp.getInt("allDayReminder", Settings.REMINDER_NONE),
        notifications = sp.getBoolean("notifications", true),
        snoozeMin = sp.getInt("snooze", 10),
        showBirthdays = sp.getBoolean("birthdays", false),
        defaultCalendarId = sp.getLong("defaultCalendar", -1),
        viewMode = enumOr(sp.getString("view", null), ViewMode.SCHEDULE),
        hourHeightDp = sp.getFloat("hourHeight", 52f),
    )

    fun update(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        sp.edit()
            .putString("theme", s.theme.name)
            .putInt("weekStart", s.weekStart)
            .putBoolean("weekNumbers", s.showWeekNumbers)
            .putBoolean("hideDeclined", s.hideDeclined)
            .putBoolean("dimPast", s.dimPast)
            .putInt("duration", s.defaultDurationMin)
            .putInt("reminder", s.defaultReminder)
            .putInt("allDayReminder", s.defaultAllDayReminder)
            .putBoolean("notifications", s.notifications)
            .putInt("snooze", s.snoozeMin)
            .putBoolean("birthdays", s.showBirthdays)
            .putLong("defaultCalendar", s.defaultCalendarId)
            .putString("view", s.viewMode.name)
            .putFloat("hourHeight", s.hourHeightDp)
            .apply()
        _settings.value = s
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: def
}
