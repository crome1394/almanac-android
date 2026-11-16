package app.crome1394.almanac.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crome1394.almanac.data.Birthdays
import app.crome1394.almanac.data.CalendarInfo
import app.crome1394.almanac.data.Palette
import app.crome1394.almanac.data.Permissions
import app.crome1394.almanac.data.ViewMode
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val fmt = LocalFmt.current
    var miniMonth by rememberSaveable { mutableStateOf(false) }
    var addCalendar by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            Drawer(vm, settings.viewMode, onPick = { scope.launch { drawer.close() } }, onAddCalendar = { addCalendar = true })
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawer.open() } }, Modifier.testTag("menu")) {
                                Icon(Icons.Filled.Menu, "Menu")
                            }
                        },
                        title = {
                            val canPick = settings.viewMode != ViewMode.MONTH
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = canPick) { miniMonth = !miniMonth }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("month-title"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(fmt.monthTitle(vm.selectedDate), style = MaterialTheme.typography.titleLarge)
                                if (canPick) {
                                    val rot by animateFloatAsState(if (miniMonth) 180f else 0f, label = "arrow")
                                    Icon(Icons.Filled.ArrowDropDown, null, Modifier.rotate(rot))
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { vm.push(Screen.Search) }, Modifier.testTag("search")) {
                                Icon(Icons.Filled.Search, "Search")
                            }
                            IconButton(onClick = { vm.goToday(); miniMonth = false }, Modifier.testTag("today")) {
                                TodayIcon(LocalDate.now().dayOfMonth)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    AnimatedVisibility(miniMonth && settings.viewMode != ViewMode.MONTH, enter = expandVertically(), exit = shrinkVertically()) {
                        MiniMonth(vm, vm.selectedDate) { d -> vm.selectedDate = d; vm.todayRequests.value++; miniMonth = false }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { vm.newEvent() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("new-event"),
                ) { Icon(Icons.Filled.Add, "New event", Modifier.size(28.dp)) }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                val cals by vm.calendars.collectAsStateWithLifecycle()
                if (!vm.calendarsLoaded) {
                    // Nothing yet: the list arrives in a moment.
                } else if (cals.isEmpty()) {
                    NoCalendars(onAdd = { addCalendar = true })
                } else when (settings.viewMode) {
                    ViewMode.SCHEDULE -> ScheduleView(vm)
                    ViewMode.DAY -> TimeGridView(vm, days = 1)
                    ViewMode.THREE_DAY -> TimeGridView(vm, days = 3)
                    ViewMode.WEEK -> TimeGridView(vm, days = 7)
                    ViewMode.MONTH -> MonthView(vm)
                }
            }
        }
    }
    if (addCalendar) CalendarDialog(null, onDismiss = { addCalendar = false }) { name, color ->
        addCalendar = false
        vm.write("Calendar added") { vm.repo.createLocalCalendar(name, color) }
    }
}

/** Google's "today" button: a small calendar page with today's date. */
@Composable
fun TodayIcon(day: Int) {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(22.dp).border(1.8.dp, c, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(4.dp).background(c, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
        Text(day.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun NoCalendars(onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("No calendars yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "To see your server's calendars, add your account in DAVx5 and tick the calendars under CalDAV. " +
                "Or keep events on this phone only.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdd) { Text("Add a phone-only calendar") }
    }
}

@Composable
private fun Drawer(vm: AppViewModel, mode: ViewMode, onPick: () -> Unit, onAddCalendar: () -> Unit) {
    val cals by vm.calendars.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { vm.prefs.update { it.copy(showBirthdays = true) }; vm.onPermissionsChanged() }
    }
    ModalDrawerSheet(Modifier.width(300.dp)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    TodayIconLogo()
                    Spacer(Modifier.width(12.dp))
                    Text("Almanac", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val views = listOf(
                Triple(ViewMode.SCHEDULE, "Schedule", Icons.AutoMirrored.Outlined.ViewList),
                Triple(ViewMode.DAY, "Day", Icons.Outlined.CalendarViewDay),
                Triple(ViewMode.THREE_DAY, "3 days", Icons.Outlined.ViewColumn),
                Triple(ViewMode.WEEK, "Week", Icons.Outlined.CalendarViewWeek),
                Triple(ViewMode.MONTH, "Month", Icons.Outlined.CalendarMonth),
            )
            items(views) { (m, label, icon) ->
                DrawerItem(label, icon, selected = m == mode, tag = "view-${m.name.lowercase()}") { vm.setView(m); onPick() }
            }
            item {
                DrawerItem("Refresh", Icons.Outlined.Refresh, tag = "refresh") { vm.refresh(); onPick(); vm.say("Syncing…") }
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
            }
            cals.groupBy { if (it.isLocal) "On this phone" else it.accountName }.forEach { (account, list) ->
                item(key = "acct-$account") {
                    Text(account, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp, end = 16.dp))
                }
                items(list, key = { "cal-${it.id}" }) { c ->
                    CalendarToggle(c.name, Color(c.color), c.visible) { vm.setCalendarVisible(c, it) }
                }
            }
            item {
                Text("Other", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp))
                CalendarToggle("Birthdays", Color(Birthdays.COLOR), settings.showBirthdays) { on ->
                    if (on && !Permissions.hasContacts(ctx)) contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                    else vm.prefs.update { it.copy(showBirthdays = on) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 28.dp))
                DrawerItem("Add calendar", Icons.Filled.Add, tag = "add-calendar") { onPick(); onAddCalendar() }
                DrawerItem("Settings", Icons.Outlined.Settings, tag = "settings") { onPick(); vm.push(Screen.Settings) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TodayIconLogo() {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Text(LocalDate.now().dayOfMonth.toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean = false, tag: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag(tag),
    )
}

@Composable
private fun CalendarToggle(name: String, color: Color, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color))
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

/** Add or rename a phone-only calendar. */
@Composable
fun CalendarDialog(existing: CalendarInfo?, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: Palette.EVENT[6].second) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New phone-only calendar" else "Edit calendar") },
        text = {
            Column {
                if (existing == null) Text("Events in it stay on this phone and are not synced. " +
                    "To add a calendar on your server, use DAVx5 (the + on its CalDAV tab).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it.take(100) }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.testTag("calendar-name"))
                Spacer(Modifier.height(12.dp))
                ColorGrid(Palette.EVENT, color) { color = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ColorGrid(colors: List<Pair<String, Int>>, selected: Int?, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, c) ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(c)).clickable { onPick(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c == selected) Icon(Icons.Filled.Check, label, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
