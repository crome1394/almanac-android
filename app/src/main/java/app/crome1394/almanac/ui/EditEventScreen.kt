package app.crome1394.almanac.ui

import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crome1394.almanac.data.Attendee
import app.crome1394.almanac.data.CalendarInfo
import app.crome1394.almanac.data.ContactsSearch
import app.crome1394.almanac.data.EventDetails
import app.crome1394.almanac.data.EventDraft
import app.crome1394.almanac.data.Palette
import app.crome1394.almanac.data.RRule
import app.crome1394.almanac.data.RecurrenceText
import app.crome1394.almanac.data.Reminder
import app.crome1394.almanac.data.Scope
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventScreen(vm: AppViewModel, req: EditRequest) {
    val cals by vm.calendars.collectAsStateWithLifecycle()
    // Load the event being edited (once); a new event starts from the request's draft.
    val loaded by produceState<Pair<EventDetails?, EventDraft?>?>(null, req) {
        value = if (req.eventId == null) null to req.draft
        else runCatching { vm.repo.event(req.eventId) }.getOrNull()?.let { it to draftFrom(it, req.instanceBegin) } ?: (null to null)
    }
    val l = loaded
    if (l == null) { Box(Modifier.fillMaxSize()); return }
    val (original, initial) = l
    if (initial == null) {
        LaunchedEffect(Unit) { vm.say("This event no longer exists"); vm.back() }
        return
    }
    Editor(vm, original, initial, req.instanceBegin, cals)
}

/** The editable form of an existing event at the occurrence starting [instanceBegin]. */
private fun draftFrom(e: EventDetails, instanceBegin: Long): EventDraft {
    val evZone = runCatching { ZoneId.of(e.timeZone) }.getOrDefault(ZoneId.systemDefault())
    val zone = if (e.allDay) ZoneOffset.UTC else evZone
    val len = (e.dtEnd?.let { it - e.dtStart }) ?: durationMs(e.duration)
    val begin = if (instanceBegin > 0) instanceBegin else e.dtStart
    val s = Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
    var en = Instant.ofEpochMilli(begin + len).atZone(zone).toLocalDateTime()
    if (e.allDay) en = en.minusDays(1).let { if (it < s) s else it }
    return EventDraft(
        calendarId = e.calendarId, title = e.title, allDay = e.allDay, start = s, end = en,
        zone = if (e.allDay) ZoneId.systemDefault() else evZone,
        rrule = e.rrule, location = e.location, description = e.description,
        colorKey = e.colorKey?.takeIf { it.isNotBlank() }, availability = e.availability, accessLevel = e.accessLevel,
        reminders = e.reminders, attendees = e.attendees,
        organizer = e.organizer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(vm: AppViewModel, original: EventDetails?, initial: EventDraft, instanceBegin: Long, cals: List<CalendarInfo>) {
    val fmt = LocalFmt.current
    var d by remember { mutableStateOf(initial) }
    val writable = cals.filter { it.writable }
    val cal = cals.firstOrNull { it.id == d.calendarId }
    val colors by produceState(emptyList<app.crome1394.almanac.data.EventColor>(), cal?.id) {
        value = cal?.let { vm.repo.eventColors(it) }.orEmpty()
    }
    var dialog by remember { mutableStateOf<String?>(null) }
    var askScope by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val changed = d != initial

    BackHandler { if (changed) askDiscard = true else vm.back() }

    fun save(scope: Scope) {
        if (saving) return
        saving = true
        val draft = d.copy(title = d.title.trim())
        vm.write(
            done = if (original == null) "Event saved" else "Event updated",
            // A repeating event may now be split or have a new occurrence row: go back to the calendar.
            onOk = { if (original != null && (original.isRecurring || original.isException)) vm.home() else vm.back() },
        ) {
            try {
                if (original == null) vm.repo.insert(draft)
                else vm.repo.update(original, draft, scope, if (instanceBegin > 0) instanceBegin else original.dtStart)
            } finally { saving = false }
        }
    }

    fun trySave() {
        when {
            !d.isValid -> vm.say("The event ends before it starts")
            original != null && !changed -> vm.back()
            original != null && (original.isRecurring || original.isException) -> askScope = true
            else -> save(Scope.ALL)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { if (changed) askDiscard = true else vm.back() }, Modifier.testTag("cancel")) {
                        Icon(Icons.Filled.Close, "Cancel")
                    }
                },
                actions = {
                    Button(onClick = ::trySave, enabled = !saving, modifier = Modifier.padding(end = 8.dp).testTag("save")) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            PlainField(d.title, { d = d.copy(title = it.take(1000)) }, "Add title", big = true, tag = "title-field",
                modifier = Modifier.padding(start = 56.dp, end = 16.dp))
            HorizontalDivider()

            // Calendar
            Row_(Icons.Outlined.CalendarToday, onClick = { if (writable.size > 1) dialog = "calendar" }, tag = "calendar") {
                Column {
                    Text(cal?.name ?: "Calendar", style = MaterialTheme.typography.bodyLarge)
                    Text(if (cal?.isLocal == true) "On this phone only" else cal?.accountName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()

            // Time
            Row_(Icons.Outlined.AccessTime) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("All-day", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(d.allDay, { on ->
                        val timedDefault = vm.defaultReminders(false, cal)
                        val allDayDefault = vm.defaultReminders(true, cal)
                        d = if (on) {
                            d.copy(allDay = true, end = maxOf(d.end, d.start).with(maxOf(d.end.toLocalDate(), d.start.toLocalDate())),
                                reminders = if (d.reminders == timedDefault) allDayDefault else d.reminders)
                        } else {
                            val st = d.start.toLocalDate().atTime(9, 0)
                            d.copy(allDay = false, start = st, end = st.plusMinutes(vm.settings.value.defaultDurationMin.toLong()),
                                reminders = if (d.reminders == allDayDefault) timedDefault else d.reminders)
                        }
                    }, modifier = Modifier.testTag("all-day"))
                }
            }
            DateTimeRow(fmt, d.start, d.allDay, tag = "start",
                onDate = { dialog = "startDate" }, onTime = { dialog = "startTime" })
            DateTimeRow(fmt, d.end, d.allDay, tag = "end", error = !d.isValid,
                onDate = { dialog = "endDate" }, onTime = { dialog = "endTime" })
            if (!d.allDay) {
                Row_(Icons.Outlined.Public, onClick = { dialog = "zone" }, tag = "zone") {
                    Text(zoneLabel(d.zone), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row_(Icons.Outlined.Repeat, onClick = { dialog = "repeat" }, tag = "repeat") {
                Text(RRule.parse(d.rrule)?.let { RecurrenceText.describe(it, d.start.toLocalDate()) } ?: "Does not repeat",
                    style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider()

            // Guests
            GuestsEditor(vm, d.attendees, cal) { d = d.copy(attendees = it, organizer = d.organizer ?: cal?.ownerAccount) }
            HorizontalDivider()

            Row_(Icons.Outlined.LocationOn) {
                PlainField(d.location, { d = d.copy(location = it.take(1000)) }, "Add location", tag = "location-field")
            }
            HorizontalDivider()

            // Reminders
            d.reminders.forEachIndexed { idx, r ->
                Row_(if (idx == 0) Icons.Outlined.Notifications else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.reminder(r.minutes, r.method, d.allDay), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { d = d.copy(reminders = d.reminders - r) }) { Icon(Icons.Filled.Close, "Remove reminder") }
                    }
                }
            }
            Row_(if (d.reminders.isEmpty()) Icons.Outlined.Notifications else null, onClick = { dialog = "reminder" }, tag = "add-reminder") {
                Text("Add notification", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()

            // Color
            if (colors.isNotEmpty()) {
                val c = colors.firstOrNull { it.key == d.colorKey }
                Row_(Icons.Outlined.Palette, onClick = { dialog = "color" }, tag = "color") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(c?.color ?: cal?.color ?: 0)))
                        Spacer(Modifier.width(12.dp))
                        Text(c?.let { Palette.nameOf(it.color) ?: it.key } ?: "Default color", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider()
            }

            Row_(Icons.AutoMirrored.Outlined.Notes) {
                PlainField(d.description, { d = d.copy(description = it.take(100_000)) }, "Add description", singleLine = false, tag = "description-field")
            }
            HorizontalDivider()
            Row_(Icons.Outlined.Work, onClick = { dialog = "availability" }, tag = "availability") {
                Text(if (d.availability == Events.AVAILABILITY_FREE) "Free" else "Busy", style = MaterialTheme.typography.bodyLarge)
            }
            Row_(Icons.Outlined.Lock, onClick = { dialog = "visibility" }, tag = "visibility") {
                Text(visibilityLabel(d.accessLevel), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    // ---- Dialogs ----
    when (dialog) {
        "calendar" -> PickDialog("Calendar", writable, { it.name + if (it.isLocal) " (phone)" else " · " + it.accountName },
            selected = cal, onDismiss = { dialog = null }) { c ->
            dialog = null
            d = d.copy(calendarId = c.id, colorKey = null, organizer = c.ownerAccount)
        }
        "startDate" -> DatePick(d.start.toLocalDate(), { dialog = null }) { date ->
            dialog = null
            val shift = Duration.between(d.start, d.end)
            val ns = d.start.with(date)
            d = d.copy(start = ns, end = ns.plus(shift))
        }
        "endDate" -> DatePick(d.end.toLocalDate(), { dialog = null }) { date -> dialog = null; d = d.copy(end = d.end.with(date)) }
        "startTime" -> TimePick(d.start.toLocalTime(), fmt.is24, { dialog = null }) { t ->
            dialog = null
            val shift = Duration.between(d.start, d.end)
            val ns = d.start.with(t)
            d = d.copy(start = ns, end = ns.plus(shift))
        }
        "endTime" -> TimePick(d.end.toLocalTime(), fmt.is24, { dialog = null }) { t ->
            dialog = null
            var ne = d.end.with(t)
            // Ending earlier on the same day means it runs past midnight, as Google assumes.
            if (ne < d.start && d.end.toLocalDate() == d.start.toLocalDate()) ne = ne.plusDays(1)
            d = d.copy(end = ne)
        }
        "zone" -> ZonePick(d.zone, { dialog = null }) { z -> dialog = null; d = d.copy(zone = z) }
        "repeat" -> RepeatDialog(d.rrule, d.start.toLocalDate(), d.allDay, d.zone, { dialog = null }) { r -> dialog = null; d = d.copy(rrule = r) }
        "reminder" -> ReminderDialog(d.allDay, cal, { dialog = null }) { r ->
            dialog = null
            if (r !in d.reminders) d = d.copy(reminders = (d.reminders + r).sortedBy { it.minutes })
        }
        "color" -> ColorDialog(colors, cal, d.colorKey, { dialog = null }) { k -> dialog = null; d = d.copy(colorKey = k) }
        "availability" -> PickDialog("Show as", listOf(Events.AVAILABILITY_BUSY, Events.AVAILABILITY_FREE)
            .filter { cal == null || it in cal.allowedAvailability },
            { if (it == Events.AVAILABILITY_FREE) "Free" else "Busy" }, d.availability, { dialog = null }) {
            dialog = null; d = d.copy(availability = it)
        }
        "visibility" -> PickDialog("Visibility", listOf(Events.ACCESS_DEFAULT, Events.ACCESS_PUBLIC, Events.ACCESS_PRIVATE),
            ::visibilityLabel, d.accessLevel, { dialog = null }) { dialog = null; d = d.copy(accessLevel = it) }
    }
    if (askScope && original != null) {
        val ruleChanged = d.rrule != initial.rrule
        ScopeDialog("Save recurring event", "OK", { askScope = false }, allowThis = !ruleChanged || original.isException) { s ->
            askScope = false
            save(s)
        }
    }
    if (askDiscard) AlertDialog(
        onDismissRequest = { askDiscard = false },
        title = { Text("Discard unsaved changes?") },
        confirmButton = { TextButton(onClick = { askDiscard = false; vm.back() }, Modifier.testTag("discard")) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep editing") } },
    )
}

private fun visibilityLabel(a: Int) = when (a) {
    Events.ACCESS_PUBLIC -> "Public"
    Events.ACCESS_PRIVATE -> "Private"
    else -> "Default visibility"
}

fun zoneLabel(z: ZoneId): String {
    val off = z.rules.getOffset(Instant.now())
    val id = z.id.substringAfterLast('/').replace('_', ' ')
    return "$id (GMT${if (off.totalSeconds == 0) "" else off.id})"
}

@Composable
private fun Row_(icon: ImageVector?, onClick: (() -> Unit)? = null, tag: String? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp)) { if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun DateTimeRow(fmt: Fmt, t: java.time.LocalDateTime, allDay: Boolean, tag: String, error: Boolean = false,
                        onDate: () -> Unit, onTime: () -> Unit) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(fmt.dayMedium(t.toLocalDate()), Modifier.weight(1f).clickable(onClick = onDate).padding(vertical = 12.dp).testTag("$tag-date"),
            style = MaterialTheme.typography.bodyLarge, color = color)
        if (!allDay) Text(fmt.time(t.toLocalTime()), Modifier.clickable(onClick = onTime).padding(12.dp).testTag("$tag-time"),
            style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, big: Boolean = false,
               singleLine: Boolean = true, tag: String, keyboard: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
               actions: KeyboardActions = KeyboardActions.Default) {
    val style = (if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge)
    BasicTextField(
        value, onChange,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = singleLine,
        keyboardOptions = keyboard,
        keyboardActions = actions,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth().padding(vertical = if (big) 20.dp else 10.dp).testTag(tag),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        },
    )
}

@Composable
private fun GuestsEditor(vm: AppViewModel, guests: List<Attendee>, cal: CalendarInfo?, onChange: (List<Attendee>) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val hits by produceState(emptyList<ContactsSearch.Hit>(), q) {
        kotlinx.coroutines.delay(150)
        value = ContactsSearch.find(ctx, q).filter { h -> guests.none { it.email.equals(h.email, true) } }
    }
    fun add(email: String, name: String = "") {
        val e = email.trim()
        if (!ContactsSearch.isEmail(e)) { vm.say("Enter a full email address"); return }
        if (guests.none { it.email.equals(e, true) }) onChange(guests + Attendee(email = e, name = name))
        q = ""
    }
    Row_(Icons.Outlined.People) {
        PlainField(q, { q = it.take(254) }, "Add people", tag = "guest-field",
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            actions = KeyboardActions(onDone = { if (q.isNotBlank()) add(q) }))
    }
    hits.forEach { h ->
        Row(Modifier.fillMaxWidth().clickable { add(h.email, h.name) }.padding(start = 56.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Avatar(h.name.ifBlank { h.email })
            Spacer(Modifier.width(12.dp))
            Column {
                if (h.name.isNotBlank()) Text(h.name, style = MaterialTheme.typography.bodyMedium)
                Text(h.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (q.isNotBlank() && ContactsSearch.isEmail(q) && hits.none { it.email.equals(q.trim(), true) }) {
        Text("Add ${q.trim()}", Modifier.fillMaxWidth().clickable { add(q) }.padding(start = 56.dp, top = 10.dp, bottom = 10.dp),
            color = MaterialTheme.colorScheme.primary)
    }
    guests.filter { !it.isOrganizer }.forEach { g ->
        Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(g.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                if (g.name.isNotBlank()) Text(g.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onChange(guests - g) }) { Icon(Icons.Filled.Close, "Remove ${g.label}") }
        }
    }
    if (guests.isNotEmpty() && cal?.ownerAccount?.contains('@') != true) {
        Text("Guests are only invited when the calendar's server sends invitations (Davit does).",
            Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColorDialog(colors: List<app.crome1394.almanac.data.EventColor>, cal: CalendarInfo?, selected: String?,
                        onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Event color") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ColorOption(Color(cal?.color ?: 0), "Calendar color", selected == null) { onPick(null) }
                colors.forEach { c -> ColorOption(Color(c.color), Palette.nameOf(c.color) ?: c.key, c.key == selected) { onPick(c.key) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ColorOption(c: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}
