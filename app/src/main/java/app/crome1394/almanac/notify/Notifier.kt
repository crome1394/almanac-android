package app.crome1394.almanac.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import android.util.Log
import app.crome1394.almanac.MainActivity
import app.crome1394.almanac.R
import app.crome1394.almanac.app
import app.crome1394.almanac.data.Permissions
import app.crome1394.almanac.ui.Fmt
import org.json.JSONArray
import org.json.JSONObject

/**
 * Posts event reminders. The system calendar store schedules the alarms and tells calendar
 * apps when one is due (EVENT_REMINDER); this reads the due alerts and shows them.
 */
object Notifier {
    const val CHANNEL = "reminders"
    private const val TAG = "Almanac"

    fun createChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders for upcoming events"
                enableVibration(true)
            },
        )
    }

    data class Alert(val id: Long, val eventId: Long, val begin: Long, val end: Long, val title: String,
                     val location: String, val allDay: Boolean, val color: Int)

    /** Shows every alert that is due and not yet shown, then marks them shown. */
    fun showDue(c: Context) {
        if (!Permissions.hasCalendar(c)) return
        val enabled = c.app.prefs.current.notifications
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<Alert>()
        runCatching {
            c.contentResolver.query(
                CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                arrayOf(CalendarAlerts._ID, CalendarAlerts.EVENT_ID, CalendarAlerts.BEGIN, CalendarAlerts.END,
                    CalendarAlerts.TITLE, CalendarAlerts.EVENT_LOCATION, CalendarAlerts.ALL_DAY, CalendarAlerts.DISPLAY_COLOR),
                "${CalendarAlerts.STATE}=? AND ${CalendarAlerts.ALARM_TIME}<=?",
                arrayOf(CalendarAlerts.STATE_SCHEDULED.toString(), now.toString()),
                "${CalendarAlerts.BEGIN} ASC",
            )?.use { cur ->
                while (cur.moveToNext()) alerts += Alert(cur.getLong(0), cur.getLong(1), cur.getLong(2), cur.getLong(3),
                    cur.getString(4) ?: "", cur.getString(5) ?: "", cur.getInt(6) == 1, cur.getInt(7))
            }
        }.onFailure { Log.w(TAG, "could not read alerts", it) }
        for (a in alerts) {
            // Skip reminders for events that are long over (the phone was off, say).
            val stale = a.end < now - 15 * 60_000L && !a.allDay
            if (enabled && !stale) post(c, a)
            setState(c, a.id, CalendarAlerts.STATE_FIRED)
        }
    }

    fun post(c: Context, a: Alert) {
        if (!Permissions.hasNotifications(c)) return
        val fmt = Fmt(c)
        val whenText = fmt.rangeShort(a.begin, a.end, a.allDay)
        val text = if (a.location.isBlank()) whenText else "$whenText · ${a.location}"
        val tag = notifTag(a.eventId, a.begin)
        val open = PendingIntent.getActivity(c, tag.hashCode(),
            Intent(c, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, a.eventId)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, a.begin)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, a.end)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = android.app.Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(a.title.ifBlank { "(No title)" })
            .setContentText(text)
            .setColor(a.color)
            .setCategory(android.app.Notification.CATEGORY_EVENT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setWhen(a.begin)
            .setShowWhen(false)
            .addAction(android.app.Notification.Action.Builder(null, "Snooze",
                actionIntent(c, ReminderActionReceiver.SNOOZE, a)).build())
            .addAction(android.app.Notification.Action.Builder(null, "Dismiss",
                actionIntent(c, ReminderActionReceiver.DISMISS, a)).build())
        if (a.location.isNotBlank()) {
            val map = PendingIntent.getActivity(c, tag.hashCode() + 1,
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.location))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE)
            b.addAction(android.app.Notification.Action.Builder(null, "Map", map).build())
        }
        runCatching { c.getSystemService(NotificationManager::class.java).notify(tag, 1, b.build()) }
    }

    private fun actionIntent(c: Context, action: String, a: Alert): PendingIntent =
        PendingIntent.getBroadcast(c, (notifTag(a.eventId, a.begin) + action).hashCode(),
            Intent(c, ReminderActionReceiver::class.java).setAction(action).putExtra("alert", toJson(a).toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun notifTag(eventId: Long, begin: Long) = "event-$eventId-$begin"

    fun cancel(c: Context, a: Alert) =
        c.getSystemService(NotificationManager::class.java).cancel(notifTag(a.eventId, a.begin), 1)

    private fun setState(c: Context, alertId: Long, state: Int) {
        runCatching {
            c.contentResolver.update(ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, alertId),
                ContentValues().apply { put(CalendarAlerts.STATE, state) }, null, null)
        }
    }

    // ---- Snooze: our own alarm, remembered so it survives a restart ----

    fun snooze(c: Context, a: Alert) {
        cancel(c, a)
        val at = System.currentTimeMillis() + c.app.prefs.current.snoozeMin * 60_000L
        val sp = c.getSharedPreferences("snoozed", Context.MODE_PRIVATE)
        val list = JSONArray(sp.getString("list", "[]"))
        list.put(toJson(a).put("at", at))
        sp.edit().putString("list", list.toString()).apply()
        schedule(c, a, at)
    }

    private fun schedule(c: Context, a: Alert, at: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, (notifTag(a.eventId, a.begin) + "fire").hashCode(),
            Intent(c, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.FIRE)
                .putExtra("alert", toJson(a).toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Called when a snooze ends: show it again and forget it. */
    fun fireSnoozed(c: Context, a: Alert) {
        removeSnoozed(c, a)
        if (c.app.prefs.current.notifications) post(c, a)
    }

    private fun removeSnoozed(c: Context, a: Alert) {
        val sp = c.getSharedPreferences("snoozed", Context.MODE_PRIVATE)
        val list = JSONArray(sp.getString("list", "[]"))
        val keep = JSONArray()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.getLong("eventId") != a.eventId || o.getLong("begin") != a.begin) keep.put(o)
        }
        sp.edit().putString("list", keep.toString()).apply()
    }

    /** After a reboot alarms are gone; set the snoozes again (late ones fire now). */
    fun restoreSnoozes(c: Context) {
        val sp = c.getSharedPreferences("snoozed", Context.MODE_PRIVATE)
        val list = runCatching { JSONArray(sp.getString("list", "[]")) }.getOrElse { JSONArray() }
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            schedule(c, fromJson(o), maxOf(o.optLong("at"), System.currentTimeMillis() + 5_000))
        }
    }

    fun toJson(a: Alert): JSONObject = JSONObject().put("id", a.id).put("eventId", a.eventId).put("begin", a.begin)
        .put("end", a.end).put("title", a.title).put("location", a.location).put("allDay", a.allDay).put("color", a.color)

    fun fromJson(o: JSONObject) = Alert(o.optLong("id"), o.getLong("eventId"), o.getLong("begin"), o.optLong("end"),
        o.optString("title"), o.optString("location"), o.optBoolean("allDay"), o.optInt("color"))

    fun dismiss(c: Context, a: Alert) {
        cancel(c, a)
        removeSnoozed(c, a)
        if (a.id > 0) setState(c, a.id, CalendarAlerts.STATE_DISMISSED)
    }
}
