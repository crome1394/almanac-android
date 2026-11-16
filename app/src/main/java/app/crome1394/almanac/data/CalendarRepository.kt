package app.crome1394.almanac.data

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Colors
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads and writes the phone's calendar store (CalendarContract). Sync with the server is
 * done by the account's sync adapter (DAVx5 for Davit); this class never talks to a network.
 *
 * Writes go through the normal app API, not as a sync adapter, so the store marks every
 * change dirty and the sync adapter uploads it. The one exception is phone-only (local)
 * calendars, which only a "sync adapter" may create.
 */
class CalendarRepository(private val context: Context) {
    private val cr: ContentResolver = context.contentResolver

    /** Bumped whenever the calendar store changes, so screens reload. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { _version.value++ }
    }
    private var observing = false

    fun startObserving() {
        if (observing || !Permissions.hasCalendar(context)) return
        runCatching {
            cr.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        }
    }

    fun bump() { _version.value++ }

    // ---- Calendars ----

    suspend fun calendars(): List<CalendarInfo> = io {
        val out = mutableListOf<CalendarInfo>()
        cr.query(
            Calendars.CONTENT_URI,
            arrayOf(
                Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE,
                Calendars.CALENDAR_COLOR, Calendars.CALENDAR_ACCESS_LEVEL, Calendars.VISIBLE, Calendars.OWNER_ACCOUNT,
                Calendars.ALLOWED_REMINDERS, Calendars.ALLOWED_AVAILABILITY, Calendars.CALENDAR_TIME_ZONE,
            ),
            "${Calendars.SYNC_EVENTS}=1", null, "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}",
        )?.use { c ->
            while (c.moveToNext()) {
                out += CalendarInfo(
                    id = c.getLong(0),
                    name = c.str(1).ifBlank { c.str(2) },
                    accountName = c.str(2),
                    accountType = c.str(3),
                    color = c.getInt(4),
                    accessLevel = c.getInt(5),
                    visible = c.getInt(6) == 1,
                    ownerAccount = c.getString(7),
                    allowedReminders = intSet(c.getString(8), setOf(Reminders.METHOD_ALERT)),
                    allowedAvailability = intSet(c.getString(9), setOf(Events.AVAILABILITY_BUSY, Events.AVAILABILITY_FREE)),
                    timeZone = c.getString(10),
                )
            }
        }
        out
    }

    suspend fun setCalendarVisible(id: Long, visible: Boolean) = io {
        val v = ContentValues().apply { put(Calendars.VISIBLE, if (visible) 1 else 0) }
        cr.update(ContentUris.withAppendedId(Calendars.CONTENT_URI, id), v, null, null)
    }

    /** Creates a calendar that lives only on this phone (not synced anywhere). */
    suspend fun createLocalCalendar(name: String, color: Int): Long = io {
        val uri = localAdapterUri(Calendars.CONTENT_URI)
        val v = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, name)
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, color)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
            put(Calendars.ALLOWED_REMINDERS, "${Reminders.METHOD_DEFAULT},${Reminders.METHOD_ALERT}")
            put(Calendars.ALLOWED_AVAILABILITY, "${Events.AVAILABILITY_BUSY},${Events.AVAILABILITY_FREE}")
            put(Calendars.ALLOWED_ATTENDEE_TYPES, "${Attendees.TYPE_NONE},${Attendees.TYPE_REQUIRED},${Attendees.TYPE_OPTIONAL}")
        }
        val id = cr.insert(uri, v)?.let(ContentUris::parseId) ?: error("Could not create the calendar")
        ensureLocalColors()
        id
    }

    suspend fun updateLocalCalendar(id: Long, name: String, color: Int) = io {
        val v = ContentValues().apply {
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, color)
        }
        cr.update(ContentUris.withAppendedId(localAdapterUri(Calendars.CONTENT_URI), id), v, null, null)
    }

    /** Deletes a phone-only calendar and all its events. Never used for synced calendars. */
    suspend fun deleteLocalCalendar(cal: CalendarInfo) = io {
        require(cal.isLocal) { "Only phone-only calendars can be deleted here" }
        cr.delete(ContentUris.withAppendedId(localAdapterUri(Calendars.CONTENT_URI), cal.id), null, null)
    }

    /** Local calendars get Google's event colors so events can be colored, as on synced ones. */
    private fun ensureLocalColors() {
        val uri = localAdapterUri(Colors.CONTENT_URI)
        val have = cr.query(uri, arrayOf(Colors.COLOR_KEY),
            "${Colors.ACCOUNT_NAME}=? AND ${Colors.ACCOUNT_TYPE}=? AND ${Colors.COLOR_TYPE}=${Colors.TYPE_EVENT}",
            arrayOf(LOCAL_ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL), null)?.use { it.count } ?: 0
        if (have > 0) return
        Palette.EVENT.forEach { (key, color) ->
            cr.insert(uri, ContentValues().apply {
                put(Colors.ACCOUNT_NAME, LOCAL_ACCOUNT)
                put(Colors.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(Colors.COLOR_TYPE, Colors.TYPE_EVENT)
                put(Colors.COLOR_KEY, key)
                put(Colors.COLOR, color)
            })
        }
    }

    suspend fun eventColors(cal: CalendarInfo): List<EventColor> = io {
        runCatching {
            cr.query(Colors.CONTENT_URI, arrayOf(Colors.COLOR_KEY, Colors.COLOR),
                "${Colors.ACCOUNT_NAME}=? AND ${Colors.ACCOUNT_TYPE}=? AND ${Colors.COLOR_TYPE}=${Colors.TYPE_EVENT}",
                arrayOf(cal.accountName, cal.accountType), null)?.use { c ->
                buildList { while (c.moveToNext()) add(EventColor(c.str(0), c.getInt(1))) }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Asks every account's sync adapter to sync calendars now (DAVx5 honours this). */
    suspend fun requestSync(cals: List<CalendarInfo>) = io {
        cals.filter { !it.isLocal }.map { Account(it.accountName, it.accountType) }.distinct().forEach { acct ->
            runCatching {
                ContentResolver.requestSync(acct, CalendarContract.AUTHORITY, Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                })
            }.onFailure { Log.w(TAG, "sync request failed", it) }
        }
    }

    // ---- Instances ----

    /**
     * Occurrences that touch [from]..[to] (inclusive dates) in visible calendars. The query is
     * widened by a day on each side because all-day events are stored in UTC.
     */
    suspend fun instances(from: LocalDate, to: LocalDate, hideDeclined: Boolean, search: String? = null): List<Instance> = io {
        val zone = ZoneId.systemDefault()
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val builder = (if (search != null) Instances.CONTENT_SEARCH_URI else Instances.CONTENT_URI).buildUpon()
        ContentUris.appendId(builder, begin)
        ContentUris.appendId(builder, end)
        if (search != null) builder.appendPath(search)
        val out = mutableListOf<Instance>()
        val sel = "${Instances.VISIBLE}=1 AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS}!=${Events.STATUS_CANCELED})"
        cr.query(builder.build(), INSTANCE_COLS, sel, null, "${Instances.BEGIN} ASC, ${Instances.END} DESC")?.use { c ->
            while (c.moveToNext()) {
                val self = c.getInt(9)
                val declined = self == Attendees.ATTENDEE_STATUS_DECLINED
                if (declined && hideDeclined) continue
                val i = Instance(
                    eventId = c.getLong(0),
                    begin = c.getLong(1),
                    end = c.getLong(2),
                    allDay = c.getInt(3) == 1,
                    title = c.str(4),
                    location = c.str(5),
                    color = c.getInt(6),
                    calendarId = c.getLong(7),
                    recurring = !c.getString(8).isNullOrBlank() || !c.getString(11).isNullOrBlank(),
                    selfStatus = self,
                    declined = declined,
                    tentative = self == Attendees.ATTENDEE_STATUS_INVITED || self == Attendees.ATTENDEE_STATUS_TENTATIVE,
                )
                if (i.lastDate(zone) >= from && i.startDate(zone) <= to) out += i
            }
        }
        out
    }

    // ---- One event ----

    suspend fun event(id: Long): EventDetails? = io {
        cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, id), EVENT_COLS, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            EventDetails(
                id = c.getLong(0),
                calendarId = c.getLong(1),
                title = c.str(2),
                description = c.str(3),
                location = c.str(4),
                dtStart = c.getLong(5),
                dtEnd = c.longOrNull(6),
                duration = c.getString(7),
                allDay = c.getInt(8) == 1,
                timeZone = c.getString(9) ?: ZoneId.systemDefault().id,
                rrule = c.getString(10)?.takeIf { it.isNotBlank() },
                rdate = c.getString(11)?.takeIf { it.isNotBlank() },
                exdate = c.getString(12)?.takeIf { it.isNotBlank() },
                color = c.getInt(13),
                colorKey = c.getString(14),
                hasOwnColor = !c.isNull(15) && c.getInt(15) != 0 || !c.getString(14).isNullOrBlank(),
                availability = c.getInt(16),
                accessLevel = c.getInt(17),
                organizer = c.getString(18),
                guestsCanModify = c.getInt(19) == 1,
                originalId = c.longOrNull(20),
                originalInstanceTime = c.longOrNull(21),
                selfStatus = c.getInt(22),
                status = c.intOrNull(23),
                syncId = c.getString(24)?.takeIf { it.isNotBlank() },
                reminders = reminders(id),
                attendees = attendees(id),
            )
        }
    }

    private fun reminders(eventId: Long): List<Reminder> =
        cr.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES, Reminders.METHOD),
            "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()), "${Reminders.MINUTES} ASC")?.use { c ->
            buildList { while (c.moveToNext()) add(Reminder(c.getInt(0), c.getInt(1))) }
        }.orEmpty().distinct()

    private fun attendees(eventId: Long): List<Attendee> =
        cr.query(Attendees.CONTENT_URI,
            arrayOf(Attendees._ID, Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_NAME, Attendees.ATTENDEE_STATUS,
                Attendees.ATTENDEE_RELATIONSHIP, Attendees.ATTENDEE_TYPE),
            "${Attendees.EVENT_ID}=?", arrayOf(eventId.toString()), null)?.use { c ->
            buildList {
                while (c.moveToNext()) add(Attendee(c.getLong(0), c.str(1), c.str(2), c.getInt(3), c.getInt(4), c.getInt(5)))
            }
        }.orEmpty().filter { it.email.isNotBlank() || it.name.isNotBlank() }

    // ---- Writes ----

    /** Creates an event and returns its id. */
    suspend fun insert(d: EventDraft): Long = io {
        val v = d.toValues(isNew = true)
        val id = cr.insert(Events.CONTENT_URI, v)?.let(ContentUris::parseId) ?: error("The calendar store refused the event")
        writeChildren(id, d, replace = false)
        id
    }

    /**
     * Saves an edit of [d] (which was opened on the occurrence starting at [instanceBegin]).
     * For recurring events [scope] says which occurrences change, as in Google Calendar.
     */
    suspend fun update(original: EventDetails, d: EventDraft, scope: Scope, instanceBegin: Long): Unit = io {
        when {
            original.isException && scope != Scope.THIS -> {
                // A changed occurrence: "following" and "all" apply to the series it belongs to.
                val master = event(original.originalId!!)
                if (master == null) {
                    saveInPlace(original.id, d)
                } else {
                    update(master, d, scope, original.originalInstanceTime ?: instanceBegin)
                }
            }
            !original.isRecurring || original.isException -> saveInPlace(original.id, d)
            scope == Scope.ALL || (scope == Scope.FOLLOWING && instanceBegin <= original.dtStart) -> {
                // Move the whole series by however much this occurrence moved.
                val shift = d.startMillis() - instanceBegin
                val draft = d.copy(seriesStartMillis = original.dtStart + shift)
                val v = draft.toValues(isNew = false)
                if (!draft.rrule.isNullOrBlank()) {
                    // Keep the series' extra dates and left-out occurrences (see seriesValues).
                    v.put(Events.RDATE, original.rdate)
                    v.put(Events.EXDATE, shiftExdates(original.exdate, shift))
                }
                cr.update(eventUri(original.id), v, null, null)
                writeChildren(original.id, d, replace = true)
            }
            scope == Scope.THIS && original.syncId == null -> {
                // See excludeOccurrence: an unsynced series can't hold exception rows safely.
                excludeOccurrence(original, instanceBegin)
                val newId = cr.insert(Events.CONTENT_URI, d.copy(rrule = null).toValues(isNew = true).apply {
                    put(Events.CALENDAR_ID, original.calendarId)
                })?.let(ContentUris::parseId) ?: error("The calendar store refused the change")
                writeChildren(newId, d, replace = false)
            }
            scope == Scope.THIS -> {
                val v = d.toValues(isNew = false, asException = true).apply {
                    put(Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
                    put(Events.STATUS, Events.STATUS_CONFIRMED)
                }
                val uri = ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, original.id)
                val newId = cr.insert(uri, v)?.let(ContentUris::parseId) ?: error("The calendar store refused the change")
                writeChildren(newId, d, replace = true)
            }
            else -> { // FOLLOWING, from a later occurrence: split the series in two.
                val before = endSeriesBefore(original, instanceBegin)
                val oldRule = RRule.parse(original.rrule)
                val newRule = RRule.parse(d.rrule)
                // A series that ran N times keeps running N times in total across both halves.
                val rule = if (newRule != null && oldRule?.count != null && newRule.count == oldRule.count) {
                    newRule.copy(count = (oldRule.count - before).coerceAtLeast(1)).format()
                } else d.rrule
                val draft = d.copy(rrule = rule)
                val newId = cr.insert(Events.CONTENT_URI, draft.toValues(isNew = true).apply {
                    put(Events.CALENDAR_ID, original.calendarId)
                })?.let(ContentUris::parseId) ?: error("The calendar store refused the change")
                writeChildren(newId, draft, replace = false)
            }
        }
    }

    private fun saveInPlace(id: Long, d: EventDraft) {
        cr.update(eventUri(id), d.toValues(isNew = false), null, null)
        writeChildren(id, d, replace = true)
    }

    suspend fun delete(original: EventDetails, scope: Scope, instanceBegin: Long): Unit = io {
        when {
            !original.isRecurring && !original.isException -> cr.delete(eventUri(original.id), null, null)
            original.isException && scope == Scope.THIS -> {
                // Deleting an exception row would bring the original occurrence back; cancel it instead.
                cr.update(eventUri(original.id), ContentValues().apply { put(Events.STATUS, Events.STATUS_CANCELED) }, null, null)
            }
            original.isException -> {
                val master = event(original.originalId!!) ?: return@io
                delete(master, scope, original.originalInstanceTime ?: instanceBegin)
            }
            scope == Scope.ALL || (scope == Scope.FOLLOWING && instanceBegin <= original.dtStart) ->
                cr.delete(eventUri(original.id), null, null)
            scope == Scope.FOLLOWING -> endSeriesBefore(original, instanceBegin)
            original.syncId == null -> excludeOccurrence(original, instanceBegin)
            else -> {
                val v = ContentValues().apply {
                    put(Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
                    put(Events.STATUS, Events.STATUS_CANCELED)
                }
                cr.insert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, original.id), v)
            }
        }
        Unit
    }

    /**
     * Ends a series just before [instanceBegin] and returns how many occurrences it kept.
     * A COUNT becomes an UNTIL so the series stops at the same place.
     */
    private fun endSeriesBefore(original: EventDetails, instanceBegin: Long): Int {
        val rule = RRule.parse(original.rrule) ?: return 0
        val kept = countOccurrences(original.id, original.dtStart, instanceBegin)
        val ended = rule.copy(count = null, until = RRule.untilBefore(instanceBegin, original.allDay))
        cr.update(eventUri(original.id), seriesValues(original).apply { put(Events.RRULE, ended.format()) }, null, null)
        return kept
    }

    /**
     * Leaves one occurrence out of a series by adding it to EXDATE.
     *
     * Android's calendar store links exception rows to their series only through the series'
     * _sync_id. A series that has not been synced yet (and every series in a phone-only
     * calendar) has none, and then the store drops the series' occurrences when an exception is
     * added, and shows the exception twice on the next full rebuild (see AOSP
     * CalendarInstancesHelper.updateRecurrenceInstancesLocked). EXDATE has no such problem and
     * is plain iCalendar, so sync adapters upload it as is.
     */
    private fun excludeOccurrence(original: EventDetails, instanceBegin: Long) {
        val date = if (original.allDay) java.time.Instant.ofEpochMilli(instanceBegin).atZone(ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        else java.time.Instant.ofEpochMilli(instanceBegin).atZone(ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val existing = original.exdate?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (date in existing) return
        val v = seriesValues(original).apply { put(Events.EXDATE, (existing + date).joinToString(",")) }
        cr.update(eventUri(original.id), v, null, null)
    }

    /**
     * The columns the calendar store needs to re-expand a series. Its update path rebuilds a
     * series' occurrences from the values passed in, not from the stored row (AOSP
     * CalendarProvider2.handleUpdateEvents → updateInstancesLocked(modValues)), so an update
     * that leaves any of these out expands the series wrongly until the next full rebuild.
     */
    private fun seriesValues(e: EventDetails): ContentValues = ContentValues().apply {
        put(Events.DTSTART, e.dtStart)
        put(Events.DURATION, e.duration ?: EventDraft.durationOf((e.dtEnd ?: e.dtStart) - e.dtStart, e.allDay))
        put(Events.ALL_DAY, if (e.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, e.timeZone)
        put(Events.RRULE, e.rrule)
        put(Events.RDATE, e.rdate)
        put(Events.EXDATE, e.exdate)
    }

    private fun countOccurrences(eventId: Long, from: Long, before: Long): Int {
        if (before <= from) return 0
        val b = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, from)
        ContentUris.appendId(b, before - 1)
        return cr.query(b.build(), arrayOf(Instances.BEGIN), "${Instances.EVENT_ID}=? AND ${Instances.BEGIN}<?",
            arrayOf(eventId.toString(), before.toString()), null)?.use { it.count } ?: 0
    }

    /** Answers an invitation for the calendar's owner. */
    suspend fun respond(eventId: Long, ownerEmail: String, status: Int) = io {
        val v = ContentValues().apply { put(Attendees.ATTENDEE_STATUS, status) }
        val n = cr.update(Attendees.CONTENT_URI, v,
            "${Attendees.EVENT_ID}=? AND LOWER(${Attendees.ATTENDEE_EMAIL})=LOWER(?)", arrayOf(eventId.toString(), ownerEmail))
        if (n == 0) error("You are not on this event's guest list")
        Unit
    }

    private fun writeChildren(eventId: Long, d: EventDraft, replace: Boolean) {
        if (replace) {
            cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()))
        }
        d.reminders.distinct().forEach { r ->
            cr.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.MINUTES, r.minutes)
                put(Reminders.METHOD, r.method)
            })
        }
        // Guests: change only what changed, so the server keeps everyone's replies.
        val existing = if (replace) attendees(eventId) else emptyList()
        val wanted = d.attendeesWithOrganizer()
        val wantedKeys = wanted.map { it.email.lowercase() }.toSet()
        existing.filter { it.email.lowercase() !in wantedKeys }.forEach {
            cr.delete(ContentUris.withAppendedId(Attendees.CONTENT_URI, it.id), null, null)
        }
        val existingByEmail = existing.associateBy { it.email.lowercase() }
        wanted.forEach { a ->
            val old = existingByEmail[a.email.lowercase()]
            val v = ContentValues().apply {
                put(Attendees.ATTENDEE_NAME, a.name)
                put(Attendees.ATTENDEE_TYPE, a.type)
                put(Attendees.ATTENDEE_RELATIONSHIP, a.relationship)
            }
            if (old == null) {
                v.put(Attendees.EVENT_ID, eventId)
                v.put(Attendees.ATTENDEE_EMAIL, a.email)
                v.put(Attendees.ATTENDEE_STATUS, a.status)
                cr.insert(Attendees.CONTENT_URI, v)
            } else if (old.name != a.name || old.type != a.type || old.relationship != a.relationship) {
                cr.update(ContentUris.withAppendedId(Attendees.CONTENT_URI, old.id), v, null, null)
            }
        }
    }

    // ---- Helpers ----

    private fun eventUri(id: Long) = ContentUris.withAppendedId(Events.CONTENT_URI, id)

    private fun localAdapterUri(base: Uri): Uri = base.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val TAG = "Almanac"
        const val LOCAL_ACCOUNT = "Phone"

        private val INSTANCE_COLS = arrayOf(
            Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.TITLE,
            Instances.EVENT_LOCATION, Instances.DISPLAY_COLOR, Instances.CALENDAR_ID, Instances.RRULE,
            Instances.SELF_ATTENDEE_STATUS, Instances.STATUS, Instances.RDATE,
        )
        private val EVENT_COLS = arrayOf(
            Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION,
            Events.DTSTART, Events.DTEND, Events.DURATION, Events.ALL_DAY, Events.EVENT_TIMEZONE,
            Events.RRULE, Events.RDATE, Events.EXDATE, Events.DISPLAY_COLOR, Events.EVENT_COLOR_KEY,
            Events.EVENT_COLOR, Events.AVAILABILITY, Events.ACCESS_LEVEL, Events.ORGANIZER,
            Events.GUESTS_CAN_MODIFY, Events.ORIGINAL_ID, Events.ORIGINAL_INSTANCE_TIME,
            Events.SELF_ATTENDEE_STATUS, Events.STATUS, Events._SYNC_ID,
        )

        private fun intSet(s: String?, def: Set<Int>): Set<Int> =
            s?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()?.ifEmpty { null } ?: def
    }
}

/** The editable form of an event, as the editor holds it. */
data class EventDraft(
    val calendarId: Long,
    val title: String = "",
    val allDay: Boolean = false,
    val start: java.time.LocalDateTime,
    val end: java.time.LocalDateTime,
    val zone: ZoneId = ZoneId.systemDefault(),
    val rrule: String? = null,
    val location: String = "",
    val description: String = "",
    /** null = the calendar's color. */
    val colorKey: String? = null,
    val availability: Int = Events.AVAILABILITY_BUSY,
    val accessLevel: Int = Events.ACCESS_DEFAULT,
    val reminders: List<Reminder> = emptyList(),
    val attendees: List<Attendee> = emptyList(),
    val organizer: String? = null,
    /** Set only when a whole series is moved; overrides the start used for DTSTART. */
    val seriesStartMillis: Long? = null,
) {
    /** All-day: [start]/[end] dates are inclusive and times are ignored. */
    fun startMillis(): Long = if (allDay) start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    else start.atZone(zone).toInstant().toEpochMilli()

    fun endMillis(): Long = if (allDay) end.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    else end.atZone(zone).toInstant().toEpochMilli()

    val isValid: Boolean get() = endMillis() >= startMillis()

    fun attendeesWithOrganizer(): List<Attendee> {
        if (attendees.isEmpty()) return emptyList()
        val org = organizer?.takeIf { it.contains('@') } ?: return attendees
        if (attendees.any { it.email.equals(org, ignoreCase = true) }) return attendees
        return attendees + Attendee(email = org, status = Attendees.ATTENDEE_STATUS_ACCEPTED,
            relationship = Attendees.RELATIONSHIP_ORGANIZER)
    }

    fun toValues(isNew: Boolean, asException: Boolean = false): ContentValues = ContentValues().apply {
        if (isNew) put(Events.CALENDAR_ID, calendarId)
        put(Events.TITLE, title.trim())
        put(Events.EVENT_LOCATION, location.trim())
        put(Events.DESCRIPTION, description)
        put(Events.ALL_DAY, if (allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (allDay) "UTC" else zone.id)
        put(Events.AVAILABILITY, availability)
        put(Events.ACCESS_LEVEL, accessLevel)
        put(Events.HAS_ALARM, if (reminders.isEmpty()) 0 else 1)
        if (colorKey != null) put(Events.EVENT_COLOR_KEY, colorKey) else if (!isNew) {
            putNull(Events.EVENT_COLOR_KEY); putNull(Events.EVENT_COLOR)
        }
        if (attendees.isNotEmpty()) {
            put(Events.HAS_ATTENDEE_DATA, 1)
            if (isNew && organizer != null) put(Events.ORGANIZER, organizer)
        }
        val startMs = seriesStartMillis ?: startMillis()
        put(Events.DTSTART, startMs)
        val rule = rrule?.takeIf { it.isNotBlank() && !asException }
        if (asException) {
            // The calendar store only accepts DTSTART + DURATION for a changed occurrence.
            put(Events.DURATION, durationOf(endMillis() - startMillis(), allDay))
        } else if (rule != null) {
            put(Events.RRULE, rule)
            put(Events.DURATION, durationOf(endMillis() - startMillis(), allDay))
            putNull(Events.DTEND)
        } else {
            put(Events.DTEND, startMs + (endMillis() - startMillis()))
            putNull(Events.DURATION)
            putNull(Events.RRULE); putNull(Events.RDATE); putNull(Events.EXDATE)
        }
    }

    companion object {
        fun durationOf(ms: Long, allDay: Boolean): String {
            val d = Duration.ofMillis(ms.coerceAtLeast(0))
            return if (allDay) "P${d.toDays().coerceAtLeast(1)}D" else "P${d.seconds}S"
        }
    }
}

/**
 * Moves left-out occurrences along with a series that moved by [shiftMs], so a deleted
 * occurrence stays deleted. Entries in UTC ("…Z") and plain dates are moved; anything else
 * (a TZID-prefixed list from another app) is kept as it was.
 */
internal fun shiftExdates(exdate: String?, shiftMs: Long): String? {
    if (exdate.isNullOrBlank() || shiftMs == 0L || exdate.contains(';')) return exdate
    val utc = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    return exdate.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",") { e ->
        runCatching {
            when {
                e.length == 16 && e.endsWith("Z") -> java.time.LocalDateTime.parse(e, utc).atZone(ZoneOffset.UTC)
                    .plusNanos(shiftMs * 1_000_000).format(utc)
                e.length == 8 -> LocalDate.parse(e, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                    .plusDays(Math.floorDiv(shiftMs, 86_400_000L)).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                else -> e
            }
        }.getOrDefault(e)
    }
}

private fun Cursor.str(i: Int): String = getString(i) ?: ""
private fun Cursor.longOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)
private fun Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)
