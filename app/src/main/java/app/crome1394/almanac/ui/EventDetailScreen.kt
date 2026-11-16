package app.crome1394.almanac.ui

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crome1394.almanac.data.EventDetails
import app.crome1394.almanac.data.Instance
import app.crome1394.almanac.data.RRule
import app.crome1394.almanac.data.RecurrenceText
import app.crome1394.almanac.data.Scope
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(vm: AppViewModel, s: Screen.Detail, inCard: Boolean = false) {
    if (s.instance != null) { BirthdayDetail(vm, s.instance, inCard); return }
    val version by vm.dataVersion.collectAsStateWithLifecycle()
    val cals by vm.calendars.collectAsStateWithLifecycle()
    val loaded by produceState<Pair<Boolean, EventDetails?>>(false to null, s.eventId, version) {
        value = true to runCatching { vm.repo.event(s.eventId) }.getOrNull()
    }
    val (done, e) = loaded
    val cal = cals.firstOrNull { it.id == e?.calendarId }
    var menu by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val fmt = LocalFmt.current

    // Times of this occurrence (falls back to the event's own when opened without them).
    // A one-off event shows its stored times (they may have just been edited).
    val ownTimes = e != null && (!e.isRecurring || s.begin == 0L)
    val begin = if (!ownTimes) s.begin else e?.dtStart ?: 0L
    val end = if (!ownTimes) s.end else e?.let { it.dtEnd ?: (it.dtStart + durationMs(it.duration)) } ?: 0L

    Scaffold(
        contentWindowInsets = cardInsets(inCard), containerColor = cardColor(inCard),
        topBar = {
        TopAppBar(
            windowInsets = if (inCard) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
            colors = if (inCard) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent) else TopAppBarDefaults.topAppBarColors(),
            title = {},
            navigationIcon = { IconButton(onClick = { vm.back() }, Modifier.testTag("close")) { Icon(Icons.Filled.Close, "Close") } },
            actions = {
                if (e != null && cal?.writable == true) {
                    IconButton(onClick = { vm.push(Screen.Edit(EditRequest(eventId = e.id, instanceBegin = begin))) }, Modifier.testTag("edit")) {
                        Icon(Icons.Outlined.Edit, "Edit")
                    }
                }
                if (e != null) Box {
                    IconButton(onClick = { menu = true }, Modifier.testTag("more")) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        if (cal?.writable == true) DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleteAsk = true },
                            modifier = Modifier.testTag("delete"))
                        DropdownMenuItem(text = { Text("Duplicate") }, modifier = Modifier.testTag("duplicate"), onClick = {
                            menu = false
                            vm.push(Screen.Edit(EditRequest(draft = duplicateDraft(vm, e, begin, end))))
                        })
                        DropdownMenuItem(text = { Text("Share") }, onClick = {
                            menu = false
                            val text = buildString {
                                appendLine(e.title.ifBlank { "(No title)" })
                                appendLine(fmt.detailWhen(begin, end, e.allDay))
                                if (e.location.isNotBlank()) appendLine(e.location)
                                if (e.description.isNotBlank()) { appendLine(); append(e.description) }
                            }
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text), null))
                        })
                    }
                }
            },
        )
    }) { pad ->
        when {
            !done -> Box(Modifier.padding(pad))
            e == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This event no longer exists", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Column(Modifier.padding(pad).fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    Details(vm, e, cal, begin, end)
                }
                val me = cal?.ownerAccount
                val mine = e.attendees.firstOrNull { me != null && it.email.equals(me, true) }
                if (mine != null && !mine.isOrganizer && cal?.writable == true) Rsvp(mine.status) { vm.respond(e, cal, it) }
            }
        }
    }
    if (deleteAsk && e != null) {
        DeleteDialog(e.isRecurring || e.isException, onDismiss = { deleteAsk = false }) { scope ->
            deleteAsk = false
            vm.back()
            vm.deleteWithUndo(e, scope, begin)
        }
    }
}

@Composable
private fun Details(vm: AppViewModel, e: EventDetails, cal: app.crome1394.almanac.data.CalendarInfo?, begin: Long, end: Long) {
    val fmt = LocalFmt.current
    val ctx = LocalContext.current
    Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp)) {
        Box(Modifier.padding(top = 8.dp).size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color(e.color)))
        Spacer(Modifier.width(22.dp))
        Column {
            Text(e.title.ifBlank { "(No title)" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("title"))
            Spacer(Modifier.height(6.dp))
            Text(fmt.detailWhen(begin, end, e.allDay), style = MaterialTheme.typography.bodyMedium)
            val zone = runCatching { ZoneId.of(e.timeZone) }.getOrNull()
            if (!e.allDay && zone != null && zone.rules.getOffset(Instant.ofEpochMilli(begin)) != ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(begin))) {
                Text("${fmt.time(begin, zone)} – ${fmt.time(end, zone)} ${zone.id.replace('_', ' ')}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val rule = RRule.parse(e.rrule)
            if (rule != null) {
                val startDate = Instant.ofEpochMilli(e.dtStart).atZone(if (e.allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
                Text(RecurrenceText.describe(rule, startDate), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    app.crome1394.almanac.data.VideoCall.find(e.location, e.description)?.let { call ->
        InfoRow(Icons.Outlined.Videocam) {
            androidx.compose.material3.Button(
                onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(call.url))) }.onFailure { vm.say("No app can open this link") } },
                modifier = Modifier.testTag("join"),
            ) { Text("Join with ${call.service}") }
        }
    }
    if (e.location.isNotBlank()) {
        InfoRow(Icons.Outlined.LocationOn, onClick = {
            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(e.location)))
            runCatching { ctx.startActivity(geo) }.onFailure { vm.say("No maps app") }
        }) { Linkified(e.location) }
    }
    val organizer = e.attendees.firstOrNull { it.isOrganizer }
    val guests = e.attendees
    if (guests.isNotEmpty()) {
        InfoRow(Icons.Outlined.People) {
            val yes = guests.count { it.status == Attendees.ATTENDEE_STATUS_ACCEPTED }
            val no = guests.count { it.status == Attendees.ATTENDEE_STATUS_DECLINED }
            val maybe = guests.count { it.status == Attendees.ATTENDEE_STATUS_TENTATIVE }
            val waiting = guests.size - yes - no - maybe
            Column {
                Text("${guests.size} guest" + if (guests.size == 1) "" else "s", style = MaterialTheme.typography.bodyLarge)
                Text(listOfNotNull(
                    "$yes yes".takeIf { yes > 0 }, "$no no".takeIf { no > 0 }, "$maybe maybe".takeIf { maybe > 0 },
                    "$waiting awaiting".takeIf { waiting > 0 },
                ).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        guests.sortedWith(compareBy({ !it.isOrganizer }, { it.label.lowercase() })).forEach { g -> GuestRow(g, g == organizer) }
        val emails = guests.map { it.email }.filter { it.contains('@') && !it.equals(cal?.ownerAccount, true) }
        if (emails.isNotEmpty()) {
            InfoRow(Icons.Outlined.Email, onClick = {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, emails.toTypedArray())
                    .putExtra(Intent.EXTRA_SUBJECT, e.title)
                runCatching { ctx.startActivity(i) }.onFailure { vm.say("No email app") }
            }) { Text("Email guests", color = MaterialTheme.colorScheme.primary) }
        }
    }
    if (e.reminders.isNotEmpty()) {
        InfoRow(Icons.Outlined.Notifications) {
            Column { e.reminders.forEach { Text(fmt.reminder(it.minutes, it.method, e.allDay), style = MaterialTheme.typography.bodyMedium) } }
        }
    }
    if (e.description.isNotBlank()) {
        InfoRow(Icons.AutoMirrored.Outlined.Notes) { Linkified(stripHtml(e.description)) }
    }
    if (cal != null) {
        InfoRow(Icons.Outlined.CalendarToday) {
            Column {
                Text(cal.name, style = MaterialTheme.typography.bodyMedium)
                if (!cal.isLocal && cal.accountName != cal.name) Text(cal.accountName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (cal.isLocal) Text("On this phone only", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (e.availability == Events.AVAILABILITY_FREE || e.accessLevel == Events.ACCESS_PRIVATE || e.accessLevel == Events.ACCESS_PUBLIC) {
        InfoRow(Icons.Outlined.Work) {
            Text(listOfNotNull(
                if (e.availability == Events.AVAILABILITY_FREE) "Free" else "Busy",
                when (e.accessLevel) { Events.ACCESS_PRIVATE -> "Private"; Events.ACCESS_PUBLIC -> "Public"; else -> null },
            ).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GuestRow(g: app.crome1394.almanac.data.Attendee, organizer: Boolean) {
    val (icon, tint) = when (g.status) {
        Attendees.ATTENDEE_STATUS_ACCEPTED -> Icons.Filled.CheckCircle to Color(0xFF188038)
        Attendees.ATTENDEE_STATUS_DECLINED -> Icons.Outlined.Cancel to Color(0xFFD93025)
        else -> Icons.Outlined.Help to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Avatar(g.label)
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp).align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.surface, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(g.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (organizer) Text("Organiser", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (g.type == Attendees.TYPE_OPTIONAL) Text("Optional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Avatar(label: String) {
    val hue = (label.lowercase().hashCode().toLong() and 0xffffffffL) % 360
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color.hsv(hue.toFloat(), 0.45f, 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun InfoRow(icon: ImageVector, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

private val URL = Regex("""(https?://[^\s<>"]+)|([\w.+-]+@[\w-]+\.[\w.-]+)""")

/** Text with web links and email addresses tappable. */
@Composable
fun Linkified(text: String) {
    val color = MaterialTheme.colorScheme.primary
    val styles = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    val annotated = remember(text, color) {
        buildAnnotatedString {
            var last = 0
            URL.findAll(text).forEach { m ->
                append(text.substring(last, m.range.first))
                val raw = m.value.trimEnd('.', ',', ')', ';')
                val url = if (m.groups[1] != null) raw else "mailto:$raw"
                withLink(LinkAnnotation.Url(url, styles)) { append(raw) }
                last = m.range.first + raw.length
            }
            append(text.substring(last))
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium)
}

/** Some servers send descriptions as HTML; show them as plain text. */
fun stripHtml(s: String): String {
    if (!s.contains('<') || !Regex("</?(p|br|a|div|span|b|i|ul|li)\\b", RegexOption.IGNORE_CASE).containsMatchIn(s)) return s
    return android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
}

@Composable
private fun Rsvp(status: Int, onPick: (Int) -> Unit) {
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Going?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        listOf(
            Attendees.ATTENDEE_STATUS_ACCEPTED to "Yes", Attendees.ATTENDEE_STATUS_DECLINED to "No",
            Attendees.ATTENDEE_STATUS_TENTATIVE to "Maybe",
        ).forEach { (st, label) ->
            FilterChip(selected = status == st, onClick = { onPick(st) }, label = { Text(label) }, modifier = Modifier.testTag("rsvp-$label"))
        }
    }
}

/** Asks which occurrences to change, as Google does for repeating events. */
@Composable
fun ScopeDialog(title: String, confirm: String, onDismiss: () -> Unit, allowThis: Boolean = true, onPick: (Scope) -> Unit) {
    var scope by remember { mutableStateOf(if (allowThis) Scope.THIS else Scope.FOLLOWING) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                listOf(Scope.THIS to "This event", Scope.FOLLOWING to "This and following events", Scope.ALL to "All events")
                    .filter { allowThis || it.first != Scope.THIS }.forEach { (s, label) ->
                    Row(Modifier.fillMaxWidth().selectable(scope == s) { scope = s }.padding(vertical = 4.dp).testTag("scope-${s.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(scope == s, { scope = s })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(scope) }, Modifier.testTag("scope-ok")) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DeleteDialog(recurring: Boolean, onDismiss: () -> Unit, onDelete: (Scope) -> Unit) {
    if (recurring) ScopeDialog("Delete recurring event", "OK", onDismiss, onPick = onDelete)
    else AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this event?") },
        confirmButton = { TextButton(onClick = { onDelete(Scope.ALL) }, Modifier.testTag("confirm-delete")) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayDetail(vm: AppViewModel, i: Instance, inCard: Boolean) {
    val ctx = LocalContext.current
    val fmt = LocalFmt.current
    Scaffold(contentWindowInsets = cardInsets(inCard), containerColor = cardColor(inCard), topBar = {
        TopAppBar(title = {}, navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.Filled.Close, "Close") } },
            windowInsets = if (inCard) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
            colors = if (inCard) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent) else TopAppBarDefaults.topAppBarColors())
    }) { pad ->
        Column(Modifier.padding(pad)) {
            InfoRow(Icons.Outlined.Cake) {
                Column {
                    Text(i.title, style = MaterialTheme.typography.headlineSmall)
                    Text(fmt.detailWhen(i.begin, i.end, true), style = MaterialTheme.typography.bodyMedium)
                }
            }
            InfoRow(Icons.Outlined.People, onClick = {
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, i.birthdayContactLookup)
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }) { Text("Open contact", color = MaterialTheme.colorScheme.primary) }
            InfoRow(Icons.Outlined.Lock) {
                Text("From your contacts. Change the birthday in the contact.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun durationMs(d: String?): Long = d?.let { runCatching { java.time.Duration.parse(
    // RFC 5545 durations like "P1D" or "P3600S"; java wants a T before time parts.
    if (it.contains('T') || it.endsWith('D') || it.endsWith('W')) it.replace(Regex("^P(\\d+)W$")) { m -> "P${m.groupValues[1].toInt() * 7}D" }
    else it.replaceFirst("P", "PT"),
).toMillis() }.getOrNull() } ?: 3_600_000L

internal fun duplicateDraft(vm: AppViewModel, e: EventDetails, begin: Long, end: Long): app.crome1394.almanac.data.EventDraft {
    val evZone = runCatching { ZoneId.of(e.timeZone) }.getOrDefault(ZoneId.systemDefault())
    val zone = if (e.allDay) ZoneOffset.UTC else evZone
    val s = Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
    var en = Instant.ofEpochMilli(end).atZone(zone).toLocalDateTime()
    if (e.allDay) en = en.minusDays(1).coerceAtLeast(s)
    val cal = vm.writableCalendars().firstOrNull { it.id == e.calendarId } ?: vm.defaultCalendar()
    return app.crome1394.almanac.data.EventDraft(
        calendarId = cal?.id ?: e.calendarId, title = e.title, allDay = e.allDay, start = s, end = en,
        zone = if (e.allDay) ZoneId.systemDefault() else evZone,
        rrule = e.rrule, location = e.location, description = e.description, colorKey = e.colorKey,
        availability = e.availability, accessLevel = e.accessLevel, reminders = e.reminders,
        attendees = e.attendees.filter { !it.isOrganizer }.map { it.copy(id = 0, status = Attendees.ATTENDEE_STATUS_NONE) },
        organizer = cal?.ownerAccount,
    )
}

/** In a card the system bars are the card's business, not the screen's. */
@Composable
internal fun cardInsets(inCard: Boolean): WindowInsets =
    if (inCard) WindowInsets(0, 0, 0, 0) else androidx.compose.material3.ScaffoldDefaults.contentWindowInsets

@Composable
internal fun cardColor(inCard: Boolean): Color = if (inCard) Color.Transparent else MaterialTheme.colorScheme.background
