package app.crome1394.almanac

import android.app.Application
import app.crome1394.almanac.data.Birthdays
import app.crome1394.almanac.data.CalendarRepository
import app.crome1394.almanac.data.Prefs
import app.crome1394.almanac.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AlmanacApp : Application() {
    lateinit var prefs: Prefs; private set
    lateinit var repo: CalendarRepository; private set
    lateinit var birthdays: Birthdays; private set
    /** For work that must finish even if the screen goes away (a delete waiting out its Undo). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repo = CalendarRepository(this)
        repo.calendarColorOverride = { prefs.current.calendarColors[it] }
        birthdays = Birthdays(this)
        Notifier.createChannel(this)
        repo.startObserving()
    }
}

val android.content.Context.app: AlmanacApp get() = applicationContext as AlmanacApp
