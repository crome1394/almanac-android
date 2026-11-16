package app.crome1394.almanac.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import kotlin.concurrent.thread

/** The calendar store says a reminder is due (or the phone has started). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.applicationContext
        thread(name = "reminders") {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) Notifier.restoreSnoozes(c)
                Notifier.showDue(c)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Snooze / dismiss buttons on a reminder, and the end of a snooze. Not exported. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alert = intent.getStringExtra("alert")?.let { runCatching { Notifier.fromJson(JSONObject(it)) }.getOrNull() } ?: return
        val pending = goAsync()
        val c = context.applicationContext
        thread(name = "reminder-action") {
            try {
                when (intent.action) {
                    SNOOZE -> Notifier.snooze(c, alert)
                    DISMISS -> Notifier.dismiss(c, alert)
                    FIRE -> Notifier.fireSnoozed(c, alert)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val SNOOZE = "app.crome1394.almanac.SNOOZE"
        const val DISMISS = "app.crome1394.almanac.DISMISS"
        const val FIRE = "app.crome1394.almanac.FIRE"
    }
}
