package app.crome1394.almanac

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.crome1394.almanac.data.EventDraft
import app.crome1394.almanac.data.Ics
import app.crome1394.almanac.data.Permissions
import app.crome1394.almanac.data.Attendee
import app.crome1394.almanac.data.RRule
import app.crome1394.almanac.ui.AlmanacRoot
import app.crome1394.almanac.ui.AlmanacTheme
import app.crome1394.almanac.ui.AppViewModel
import app.crome1394.almanac.ui.EditRequest
import app.crome1394.almanac.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            AlmanacTheme(settings.theme) { AlmanacRoot(vm) }
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onPermissionsChanged()
    }

    /** Opens what another app (or a reminder) asked for. Extras from other apps are untrusted. */
    private fun handle(intent: Intent?) {
        intent ?: return
        val data = intent.data
        val type = intent.type ?: data?.let { runCatching { contentResolver.getType(it) }.getOrNull() }
        when {
            intent.action == Intent.ACTION_SEND || isIcs(type, data) -> importIcs(
                if (intent.action == Intent.ACTION_SEND) @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else data)
            intent.action == Intent.ACTION_INSERT || (intent.action == Intent.ACTION_EDIT && eventId(data) == null) -> insertFrom(intent)
            data != null && data.authority == CalendarContract.AUTHORITY && data.pathSegments.firstOrNull() == "time" -> {
                data.lastPathSegment?.toLongOrNull()?.let {
                    vm.home(); vm.selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                }
            }
            eventId(data) != null -> {
                val id = eventId(data)!!
                val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0)
                val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0)
                vm.home()
                if (intent.action == Intent.ACTION_EDIT) vm.push(Screen.Edit(EditRequest(eventId = id, instanceBegin = begin)))
                else vm.push(Screen.Detail(id, begin, end))
            }
        }
    }

    private fun eventId(u: Uri?): Long? =
        u?.takeIf { it.authority == CalendarContract.AUTHORITY && it.pathSegments.size == 2 && it.pathSegments[0] == "events" }
            ?.lastPathSegment?.toLongOrNull()

    private fun isIcs(type: String?, u: Uri?) =
        type in setOf("text/calendar", "text/x-vcalendar", "application/ics") || u?.lastPathSegment?.endsWith(".ics", true) == true

    private fun insertFrom(i: Intent) {
        if (!Permissions.hasCalendar(this)) return
        val base = vm.newDraft() ?: run { vm.say("Add a calendar first"); return }
        val allDay = i.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
        val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        fun toLdt(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
        val beginMs = i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1)
        val endMs = i.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1)
        val start = if (beginMs > 0) toLdt(beginMs) else base.start
        var end = if (endMs > beginMs && beginMs > 0) toLdt(endMs) else start.plusMinutes(
            java.time.Duration.between(base.start, base.end).toMinutes())
        if (allDay && endMs > beginMs) end = end.minusDays(1).coerceAtLeast(start)
        val guests = i.getStringExtra(Intent.EXTRA_EMAIL).orEmpty().split(',', ';').map { it.trim() }
            .filter { it.contains('@') && it.length <= 254 }.map { Attendee(email = it) }
        val draft: EventDraft = base.copy(
            title = i.getStringExtra(Events.TITLE).orEmpty().take(1000),
            description = i.getStringExtra(Events.DESCRIPTION).orEmpty().take(100_000),
            location = i.getStringExtra(Events.EVENT_LOCATION).orEmpty().take(1000),
            allDay = allDay,
            start = start, end = end,
            rrule = i.getStringExtra(Events.RRULE)?.takeIf { RRule.parse(it) != null },
            attendees = guests,
            reminders = vm.defaultReminders(allDay, vm.defaultCalendar()),
            availability = i.getIntExtra(Events.AVAILABILITY, base.availability).coerceIn(0, 2),
            accessLevel = i.getIntExtra(Events.ACCESS_LEVEL, base.accessLevel).coerceIn(0, 3),
        )
        vm.home()
        vm.push(Screen.Edit(EditRequest(draft = draft)))
    }

    private fun importIcs(uri: Uri?) {
        uri ?: return
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { s ->
                        // A calendar file is small; refuse anything huge rather than run out of memory.
                        val bytes = readAtMost(s, MAX_ICS + 1)
                        if (bytes.size > MAX_ICS) null else Ics.parse(String(bytes, Charsets.UTF_8))
                    }
                }.getOrNull()
            }
            when {
                events == null -> vm.say("Couldn't read that file")
                events.isEmpty() -> vm.say("No events in that file")
                else -> { vm.home(); vm.push(Screen.Import(events)) }
            }
        }
    }

    companion object { private const val MAX_ICS = 5 * 1024 * 1024 }
}

/** Reads up to [max] bytes (InputStream.readNBytes needs Android 13; Almanac runs on 10). */
private fun readAtMost(s: java.io.InputStream, max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = s.read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
