package app.crome1394.almanac

import android.app.Application
import app.crome1394.almanac.data.Birthdays
import app.crome1394.almanac.data.CalendarRepository
import app.crome1394.almanac.data.Prefs
import app.crome1394.almanac.notify.Notifier

class AlmanacApp : Application() {
    lateinit var prefs: Prefs; private set
    lateinit var repo: CalendarRepository; private set
    lateinit var birthdays: Birthdays; private set

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
