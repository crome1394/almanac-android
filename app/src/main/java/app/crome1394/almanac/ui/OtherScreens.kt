package app.crome1394.almanac.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crome1394.almanac.BuildConfig
import app.crome1394.almanac.data.CalendarInfo
import app.crome1394.almanac.data.Ics
import app.crome1394.almanac.data.Settings
import app.crome1394.almanac.data.ThemeMode
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel) {
    var q by remember { mutableStateOf("") }
    val fmt = LocalFmt.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val version by vm.dataVersion.collectAsStateWithLifecycle()
    val results by produceState<List<app.crome1394.almanac.data.Instance>?>(null, q, version) {
        delay(250)
        value = if (q.isBlank()) null else vm.search(q)
    }
    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            title = {
                TextField(q, { q = it.take(200) }, placeholder = { Text("Search") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search-field"))
            },
            actions = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "Clear") } },
        )
    }) { pad ->
        val zone = ZoneId.systemDefault()
        val list = results
        when {
            list == null -> {}
            list.isEmpty() -> Text("No results", Modifier.padding(pad).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
              // Start at the first result from today on, as Google does; older ones are above.
              val listState = androidx.compose.foundation.lazy.rememberLazyListState()
              LaunchedEffect(list) {
                  val days = list.map { it.startDate(zone) }.distinct().sorted()
                  val first = days.firstOrNull { it >= java.time.LocalDate.now() } ?: days.lastOrNull()
                  // Each day is one item, plus one heading per month up to and including that day.
                  val idx = if (first == null) 0 else days.indexOf(first) + days.filter { it <= first }.map { it.withDayOfMonth(1) }.distinct().size - 1
                  if (idx > 0) listState.scrollToItem(idx)
              }
              LazyColumn(Modifier.padding(pad).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
                val byDay = list.groupBy { it.startDate(zone) }.toSortedMap()
                var lastMonth: java.time.YearMonth? = null
                byDay.forEach { (day, events) ->
                    val ym = java.time.YearMonth.from(day)
                    if (ym != lastMonth) {
                        lastMonth = ym
                        item(key = "m$ym") {
                            Text(fmt.monthYear(day), Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    item(key = "d$day") {
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp)) {
                            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(fmt.weekdayShort(day), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                DayNumber(day)
                                if (day.year != java.time.LocalDate.now().year) Text(day.year.toString(),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                                events.forEach { i -> ScheduleCard(vm, i, day) }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cals by vm.calendars.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    var editCal by remember { mutableStateOf<CalendarInfo?>(null) }
    var deleteCal by remember { mutableStateOf<CalendarInfo?>(null) }
    var renameCal by remember { mutableStateOf<CalendarInfo?>(null) }
    fun set(f: (Settings) -> Settings) = vm.prefs.update(f)
    val locale = Locale.getDefault()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Header("General") }
            item { Choice("Theme", when (s.theme) { ThemeMode.SYSTEM -> "System default"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }) { dialog = "theme" } }
            item {
                Choice("Start of the week", if (s.weekStart == 0) "Region default (${s.firstDayOfWeek().getDisplayName(TextStyle.FULL, locale)})"
                else DayOfWeek.of(s.weekStart).getDisplayName(TextStyle.FULL, locale)) { dialog = "week" }
            }
            item { Toggle("Show week numbers", s.showWeekNumbers) { v -> set { it.copy(showWeekNumbers = v) } } }
            item { Toggle("Show declined events", !s.hideDeclined) { v -> set { it.copy(hideDeclined = !v) } } }
            item { Toggle("Dim past events", s.dimPast) { v -> set { it.copy(dimPast = v) } } }
            item { Choice("Default event duration", Fmt.plural(s.defaultDurationMin, "minute")) { dialog = "duration" } }
            item { Header("Notifications") }
            item { Toggle("Event notifications", s.notifications) { v -> set { it.copy(notifications = v) } } }
            item {
                Choice("Default notification", if (s.defaultReminder < 0) "None" else fmt.reminder(s.defaultReminder, 1, false)) { dialog = "reminder" }
            }
            item {
                Choice("Default all-day notification", if (s.defaultAllDayReminder == Settings.REMINDER_NONE) "None"
                else fmt.reminder(s.defaultAllDayReminder, 1, true)) { dialog = "allDayReminder" }
            }
            item { Choice("Snooze for", Fmt.plural(s.snoozeMin, "minute")) { dialog = "snooze" } }
            item {
                Choice("Notification sound and style", "Android settings") {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
            }
            item {
                Text("If another calendar app is installed, turn off its notifications to avoid getting every reminder twice.",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Header("Calendars") }
            item {
                val w = cals.filter { it.writable }
                Choice("Default calendar for new events", w.firstOrNull { it.id == s.defaultCalendarId }?.name ?: vm.defaultCalendar()?.name ?: "None") {
                    dialog = "defaultCal"
                }
            }
            items(cals.filter { it.isLocal }, key = { it.id }) { c ->
                Choice(c.name, "Phone-only calendar · tap to edit") { editCal = c }
            }
            item {
                Text("Calendars from your server are added, renamed and removed in DAVx5 or on the server.",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Header("About") }
            item { Choice("Almanac", "Version ${BuildConfig.VERSION_NAME}") {} }
        }
    }
    when (dialog) {
        "theme" -> PickDialog("Theme", ThemeMode.entries, { when (it) { ThemeMode.SYSTEM -> "System default"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" } },
            s.theme, { dialog = null }) { v -> dialog = null; set { it.copy(theme = v) } }
        "week" -> PickDialog("Start of the week", listOf(0, 6, 7, 1), {
            if (it == 0) "Region default" else DayOfWeek.of(it).getDisplayName(TextStyle.FULL, locale)
        }, s.weekStart, { dialog = null }) { v -> dialog = null; set { it.copy(weekStart = v) } }
        "duration" -> PickDialog("Default event duration", listOf(15, 20, 30, 45, 60, 90, 120), { Fmt.plural(it, "minute") },
            s.defaultDurationMin, { dialog = null }) { v -> dialog = null; set { it.copy(defaultDurationMin = v) } }
        "reminder" -> PickDialog("Default notification", listOf(-1, 0, 5, 10, 15, 30, 60, 1440),
            { if (it < 0) "None" else fmt.reminder(it, 1, false) }, s.defaultReminder, { dialog = null }) { v -> dialog = null; set { it.copy(defaultReminder = v) } }
        "allDayReminder" -> PickDialog("Default all-day notification",
            listOf(Settings.REMINDER_NONE) + listOf(0, 1, 2, 7).map { Fmt.allDayMinutes(it, java.time.LocalTime.of(9, 0)) },
            { if (it == Settings.REMINDER_NONE) "None" else fmt.reminder(it, 1, true) }, s.defaultAllDayReminder, { dialog = null }) { v ->
            dialog = null; set { it.copy(defaultAllDayReminder = v) }
        }
        "snooze" -> PickDialog("Snooze for", listOf(5, 10, 15, 30, 60), { Fmt.plural(it, "minute") }, s.snoozeMin, { dialog = null }) { v ->
            dialog = null; set { it.copy(snoozeMin = v) }
        }
        "defaultCal" -> PickDialog("Default calendar", cals.filter { it.writable }, { it.name + if (it.isLocal) " (phone)" else " · " + it.accountName },
            cals.firstOrNull { it.id == s.defaultCalendarId }, { dialog = null }) { c -> dialog = null; set { it.copy(defaultCalendarId = c.id) } }
    }
    editCal?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editCal = null },
            title = { Text(c.name) },
            text = { Text("Rename, recolor or delete this phone-only calendar.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { editCal = null; renameCal = c }) { Text("Edit") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editCal = null; deleteCal = c }) { Text("Delete") } },
        )
    }
    renameCal?.let { c ->
        CalendarDialog(c, onDismiss = { renameCal = null }) { name, color ->
            renameCal = null
            vm.write("Calendar saved") { vm.repo.updateLocalCalendar(c.id, name, color) }
        }
    }
    deleteCal?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteCal = null },
            title = { Text("Delete \"${c.name}\"?") },
            text = { Text("All its events will be deleted from this phone. This can't be undone.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                deleteCal = null
                vm.write("Calendar deleted") { vm.repo.deleteLocalCalendar(c) }
            }) { Text("Delete") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleteCal = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(t: String) {
    Text(t, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Choice(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(value, onChange)
    }
}

/** Shown when an .ics file is opened: pick the calendar, then add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: AppViewModel, events: List<Ics.Parsed>) {
    val fmt = LocalFmt.current
    val writable = vm.writableCalendars()
    var cal by remember { mutableStateOf(vm.defaultCalendar()) }
    var pick by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Add to calendar") },
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.Filled.Close, "Cancel") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Choice("Calendar", cal?.name ?: "None") { pick = true }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f)) {
                items(events) { e ->
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(e.title.ifBlank { "(No title)" }, style = MaterialTheme.typography.titleMedium)
                        val zone = if (e.allDay) java.time.ZoneOffset.UTC else e.zone
                        val b = e.start.atZone(zone).toInstant().toEpochMilli()
                        val en = (if (e.allDay) e.end.plusDays(1) else e.end).atZone(zone).toInstant().toEpochMilli()
                        Text(fmt.detailWhen(b, en, e.allDay), style = MaterialTheme.typography.bodyMedium)
                        if (e.location.isNotBlank()) Text(e.location, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Spacer(Modifier.weight(1f))
                Button(onClick = { cal?.let { vm.importEvents(events, it) } }, enabled = cal != null) {
                    Text("Add ${events.size} event" + if (events.size == 1) "" else "s")
                }
            }
        }
    }
    if (pick) PickDialog("Calendar", writable, { it.name }, cal, { pick = false }) { cal = it; pick = false }
}

