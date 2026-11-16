package app.crome1394.almanac.ui

import android.app.Application
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Reminders
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.crome1394.almanac.app
import app.crome1394.almanac.data.Birthdays
import app.crome1394.almanac.data.CalendarInfo
import app.crome1394.almanac.data.EventDetails
import app.crome1394.almanac.data.EventDraft
import app.crome1394.almanac.data.Ics
import app.crome1394.almanac.data.Instance
import app.crome1394.almanac.data.Permissions
import app.crome1394.almanac.data.Reminder
import app.crome1394.almanac.data.Scope
import app.crome1394.almanac.data.Settings
import app.crome1394.almanac.data.ViewMode
import app.crome1394.almanac.widget.Widgets
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

sealed interface Screen {
    data object Main : Screen
    /** [instance] is set for read-only entries such as birthdays. */
    data class Detail(val eventId: Long, val begin: Long, val end: Long, val instance: Instance? = null) : Screen
    data class Edit(val request: EditRequest) : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Import(val events: List<Ics.Parsed>) : Screen
}

/** Open the editor on an existing event ([eventId]) or a new one prefilled with [draft]. */
data class EditRequest(
    val eventId: Long? = null,
    val instanceBegin: Long = 0,
    val draft: EventDraft? = null,
    val scope: Scope = Scope.ALL,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx = application
    val repo = ctx.app.repo
    val prefs = ctx.app.prefs
    private val birthdays = ctx.app.birthdays

    val settings: StateFlow<Settings> = prefs.settings
    val stack = mutableStateListOf<Screen>(Screen.Main)
    val screen: Screen get() = stack.last()

    var selectedDate by mutableStateOf(LocalDate.now())
    var hasCalendarPermission by mutableStateOf(Permissions.hasCalendar(ctx))
        private set

    /** False until the calendar list has been read once (so "no calendars" doesn't flash). */
    var calendarsLoaded by mutableStateOf(false)
        private set
    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Changes whenever what is shown might change (store, settings that filter, birthdays). */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.version, settings) { v, s -> listOf(v, s.hideDeclined, s.showBirthdays, s.calendarColors) }
                .distinctUntilChanged()
                .collect {
                    reloadCalendars()
                    _dataVersion.value++
                    Widgets.refresh(ctx)
                }
        }
    }

    fun onPermissionsChanged() {
        val had = hasCalendarPermission
        hasCalendarPermission = Permissions.hasCalendar(ctx)
        if (hasCalendarPermission && !had) {
            repo.startObserving()
            repo.bump()
        }
        birthdays.invalidate()
        _dataVersion.value++
    }

    private suspend fun reloadCalendars() {
        if (!hasCalendarPermission) return
        _calendars.value = runCatching { repo.calendars() }.getOrDefault(emptyList())
        calendarsLoaded = true
    }

    fun say(msg: String) { _messages.tryEmit(msg) }

    // ---- Navigation ----

    fun push(s: Screen) { stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun replaceTop(s: Screen) { stack[stack.lastIndex] = s }
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }

    fun setView(mode: ViewMode) = prefs.update { it.copy(viewMode = mode) }
    fun goToday() { selectedDate = LocalDate.now(); todayRequests.value++ }
    /** Views scroll back to today when this changes. */
    val todayRequests = MutableStateFlow(0)

    // ---- Data ----

    suspend fun instances(from: LocalDate, to: LocalDate): List<Instance> {
        if (!hasCalendarPermission) return emptyList()
        val s = settings.value
        val list = runCatching { repo.instances(from, to, s.hideDeclined) }.getOrDefault(emptyList())
        if (!s.showBirthdays) return list
        val b = birthdays.occurrences(birthdays.entries(), from, to, Birthdays.COLOR)
        return (list + b).sortedWith(compareBy<Instance> { !it.allDay }.thenBy { it.begin })
    }

    suspend fun search(q: String): List<Instance> {
        if (q.isBlank() || !hasCalendarPermission) return emptyList()
        val today = LocalDate.now()
        return runCatching { repo.instances(today.minusYears(2), today.plusYears(3), settings.value.hideDeclined, q.trim()) }
            .getOrDefault(emptyList())
    }

    fun writableCalendars(): List<CalendarInfo> = calendars.value.filter { it.writable }

    fun defaultCalendar(): CalendarInfo? {
        val w = writableCalendars()
        val visible = w.filter { it.visible }
        return w.firstOrNull { it.id == settings.value.defaultCalendarId }
            ?: visible.firstOrNull { !it.isLocal } ?: visible.firstOrNull() ?: w.firstOrNull()
    }

    /** A new event as Google prefills it: next whole hour, default length and reminder. */
    fun newDraft(date: LocalDate = selectedDate, time: LocalTime? = null, allDay: Boolean = false): EventDraft? {
        val cal = defaultCalendar() ?: return null
        val s = settings.value
        val now = LocalDateTime.now()
        val start = when {
            time != null -> LocalDateTime.of(date, time)
            date == now.toLocalDate() -> now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
            else -> LocalDateTime.of(date, LocalTime.of(9, 0))
        }
        return EventDraft(
            calendarId = cal.id,
            allDay = allDay,
            start = start,
            end = if (allDay) start else start.plusMinutes(s.defaultDurationMin.toLong()),
            reminders = defaultReminders(allDay, cal),
            organizer = cal.ownerAccount,
        )
    }

    fun defaultReminders(allDay: Boolean, cal: CalendarInfo?): List<Reminder> {
        val s = settings.value
        val m = if (allDay) s.defaultAllDayReminder else s.defaultReminder
        if (m == Settings.REMINDER_NONE || (!allDay && m < 0)) return emptyList()
        val method = if (cal == null || Reminders.METHOD_ALERT in cal.allowedReminders) Reminders.METHOD_ALERT else Reminders.METHOD_DEFAULT
        return listOf(Reminder(m, method))
    }

    fun newEvent(date: LocalDate = selectedDate, time: LocalTime? = null, allDay: Boolean = false) {
        val d = newDraft(date, time, allDay)
        if (d == null) say("Add a calendar first: open the menu and choose \"Add calendar\"")
        else push(Screen.Edit(EditRequest(draft = d)))
    }

    /** Runs a write; reports a failure instead of crashing and returns whether it worked. */
    fun write(done: String? = null, onOk: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            val r = runCatching { block() }
            if (r.isSuccess) { done?.let(::say); onOk() }
            else say("Couldn't save: " + (r.exceptionOrNull()?.message ?: "unknown error"))
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repo.requestSync(calendars.value)
            birthdays.invalidate()
            _dataVersion.value++
        }
    }

    /** Recolors a calendar. Phone-only calendars store it; synced ones keep it in Almanac only. */
    fun setCalendarColor(cal: CalendarInfo, color: Int?) {
        if (cal.isLocal && color != null) {
            write { repo.updateLocalCalendar(cal.id, cal.name, color) }
        } else {
            prefs.update { s ->
                s.copy(calendarColors = if (color == null || color == cal.storedColor) s.calendarColors - cal.id
                else s.calendarColors + (cal.id to color))
            }
        }
    }

    // ---- Delete with Undo ----

    /** Occurrences hidden while their delete waits out the Undo snackbar. */
    data class PendingDelete(val eventId: Long, val begin: Long, val scope: Scope)
    class UndoMessage(val text: String, val undo: () -> Unit)

    val pendingDeletes = mutableStateListOf<PendingDelete>()
    private val _undo = MutableSharedFlow<UndoMessage>(extraBufferCapacity = 2)
    val undoMessages: SharedFlow<UndoMessage> = _undo

    /** The occurrence whose press-and-hold menu is open. */
    var menuFor by mutableStateOf<Instance?>(null)
    /** A recurring occurrence waiting for "this / following / all" before it is deleted. */
    var deleteAsk by mutableStateOf<Pair<Instance, EventDetails>?>(null)

    fun isPendingDelete(i: Instance): Boolean = pendingDeletes.any { p ->
        p.eventId == i.eventId && when (p.scope) {
            Scope.THIS -> i.begin == p.begin
            Scope.FOLLOWING -> i.begin >= p.begin
            Scope.ALL -> true
        }
    }

    /**
     * Deletes an occurrence from a list (swipe or menu): a one-off event goes at once with Undo;
     * a recurring one first asks which occurrences ([deleteAsk]).
     */
    suspend fun askDelete(i: Instance) {
        if (i.isBirthday) return say("Birthdays come from your contacts")
        val cal = calendars.value.firstOrNull { it.id == i.calendarId }
        if (cal?.writable != true) return say("Events in this calendar can't be changed")
        val e = runCatching { repo.event(i.eventId) }.getOrNull() ?: return say("This event no longer exists")
        if (e.isRecurring || e.isException) deleteAsk = i to e else deleteWithUndo(e, Scope.ALL, i.begin)
    }

    /**
     * Hides the occurrence(s) now and deletes them once the Undo snackbar has gone. The delete
     * runs in the app's scope, so leaving the screen doesn't cancel it; if the app is killed
     * first, nothing is deleted.
     */
    fun deleteWithUndo(e: EventDetails, scope: Scope, begin: Long) {
        val p = PendingDelete(e.id, begin, scope)
        pendingDeletes += p
        val job = ctx.app.scope.launch {
            delay(UNDO_MS)
            runCatching { repo.delete(e, scope, begin) }
                .onFailure { say("Couldn't delete: " + (it.message ?: "unknown error")) }
            pendingDeletes.remove(p)
        }
        _undo.tryEmit(UndoMessage(if (scope == Scope.THIS || !e.isRecurring) "Event deleted" else "Events deleted") {
            if (job.isActive) { job.cancel(); pendingDeletes.remove(p) } else say("Too late to undo")
        })
    }

    // ---- Drag to move ----

    /** A recurring occurrence dragged to a new day or time, waiting for "this / following / all". */
    data class MoveRequest(val i: Instance, val e: EventDetails, val days: Int, val minutes: Int)
    var moveAsk by mutableStateOf<MoveRequest?>(null)

    /** Moves a dragged occurrence by [days] and [minutes] (wall-clock, in the event's own zone). */
    fun move(i: Instance, days: Int, minutes: Int) {
        if (days == 0 && minutes == 0) return
        viewModelScope.launch {
            val e = runCatching { repo.event(i.eventId) }.getOrNull() ?: return@launch say("This event no longer exists")
            val r = MoveRequest(i, e, days, if (e.allDay) 0 else minutes)
            if (r.days == 0 && r.minutes == 0) return@launch
            if (e.isRecurring || e.isException) moveAsk = r else applyMove(r, Scope.ALL)
        }
    }

    fun applyMove(r: MoveRequest, scope: Scope) {
        val (i, e) = r
        val before = draftFrom(e, i.begin)
        fun shift(t: LocalDateTime) = t.plusDays(r.days.toLong()).plusMinutes(r.minutes.toLong())
        val moved = before.copy(start = shift(before.start), end = shift(before.end))
        viewModelScope.launch {
            val res = runCatching { repo.update(e, moved, scope, i.begin) }
            if (res.isFailure) return@launch say("Couldn't move: " + (res.exceptionOrNull()?.message ?: "unknown error"))
            if (e.isRecurring || e.isException) say("Event moved")
            else _undo.tryEmit(UndoMessage("Event moved") {
                // A one-off event goes back to where it was.
                viewModelScope.launch {
                    runCatching { repo.event(e.id)?.let { now -> repo.update(now, before, Scope.ALL, now.dtStart) } }
                        .onFailure { say("Couldn't undo: " + (it.message ?: "unknown error")) }
                }
            })
        }
    }

    fun setCalendarVisible(cal: CalendarInfo, visible: Boolean) = write { repo.setCalendarVisible(cal.id, visible) }

    fun respond(event: EventDetails, cal: CalendarInfo, status: Int) {
        val me = cal.ownerAccount ?: return say("This calendar has no owner address")
        val label = when (status) {
            Attendees.ATTENDEE_STATUS_ACCEPTED -> "Going"
            Attendees.ATTENDEE_STATUS_DECLINED -> "Not going"
            else -> "Maybe"
        }
        write("Replied: $label") { repo.respond(event.id, me, status) }
    }

    companion object { const val UNDO_MS = 5_000L }

    fun importEvents(events: List<Ics.Parsed>, cal: CalendarInfo) {
        write("Added ${events.size} event" + (if (events.size == 1) "" else "s"), onOk = { home() }) {
            for (e in events) {
                repo.insert(EventDraft(
                    calendarId = cal.id, title = e.title, description = e.description, location = e.location,
                    allDay = e.allDay, start = e.start, end = e.end, zone = if (e.allDay) ZoneId.systemDefault() else e.zone,
                    rrule = e.rrule,
                    reminders = e.reminderMinutes.map { Reminder(it, Reminders.METHOD_ALERT) },
                ))
            }
        }
    }
}
