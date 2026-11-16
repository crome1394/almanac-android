package app.crome1394.almanac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crome1394.almanac.data.Instance
import app.crome1394.almanac.data.startOfWeek
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private sealed interface Row_ { val key: String; val date: LocalDate }
private data class MonthHeader(override val date: LocalDate) : Row_ { override val key = "m$date" }
private data class WeekLabel(override val date: LocalDate) : Row_ { override val key = "w$date" }
private data class DayRow(override val date: LocalDate, val events: List<Instance>) : Row_ { override val key = "d$date" }

/** Google's Schedule view: an endless list of days with events, month banners and week labels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleView(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val todayReq by vm.todayRequests.collectAsStateWithLifecycle()
    var anchor by remember { mutableStateOf(vm.selectedDate) }
    var from by remember { mutableStateOf(anchor.minusDays(60)) }
    var to by remember { mutableStateOf(anchor.plusDays(120)) }
    LaunchedEffect(todayReq) {
        if (todayReq > 0) { anchor = vm.selectedDate; from = anchor.minusDays(60); to = anchor.plusDays(120) }
    }
    val events by rememberInstances(vm, from, to)
    val first = settings.firstDayOfWeek()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    val rows = remember(events, from, to, first) {
        val byDay = HashMap<LocalDate, MutableList<Instance>>()
        events.orEmpty().forEach { i -> datesOf(i, from, to, zone).forEach { byDay.getOrPut(it) { mutableListOf() } += i } }
        buildList {
            var d = from
            var lastMonth: YearMonth? = null
            var lastWeek: LocalDate? = null
            while (d <= to) {
                val ym = YearMonth.from(d)
                if (ym != lastMonth) { add(MonthHeader(ym.atDay(1))); lastMonth = ym; lastWeek = null }
                val ws = d.startOfWeek(first)
                if (ws != lastWeek) { add(WeekLabel(maxOf(ws, ym.atDay(1)))); lastWeek = ws }
                val list = byDay[d]
                if (!list.isNullOrEmpty() || d == today) {
                    // All-day entries first (and multi-day ones after their first day), then by start time.
                    val day = d
                    add(DayRow(d, list.orEmpty().sortedWith(compareBy<Instance> { !(it.allDay || it.startDate(zone) != day) }.thenBy { it.begin })))
                }
                d = d.plusDays(1)
            }
        }
    }

    val list = rememberLazyListState()
    // Jump to the anchor day once its rows exist.
    var scrolledFor by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(rows, anchor) {
        if (events != null && scrolledFor != anchor) {
            val idx = rows.indexOfFirst { it is DayRow && it.date >= anchor }.let { if (it < 0) rows.indexOfFirst { r -> r.date >= anchor } else it }
            if (idx >= 0) list.scrollToItem((idx - 1).coerceAtLeast(0))
            scrolledFor = anchor
        }
    }
    // Keep the title in step with the list, and load more near either end.
    LaunchedEffect(list, rows) {
        snapshotFlow { list.firstVisibleItemIndex to list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { (firstIdx, lastIdx) ->
                if (scrolledFor == null) return@collect
                rows.getOrNull(firstIdx + 1)?.date?.let { if (YearMonth.from(it) != YearMonth.from(vm.selectedDate)) vm.selectedDate = it }
                if (lastIdx != null && lastIdx >= rows.size - 8) to = to.plusDays(120)
                if (firstIdx <= 3 && rows.isNotEmpty()) { delay(100); from = from.minusDays(120) }
            }
    }

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(1500); refreshing = false } }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("schedule"), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(rows.size, key = { rows[it].key }) { idx ->
                when (val r = rows[idx]) {
                    is MonthHeader -> MonthBanner(r.date)
                    is WeekLabel -> {
                        val end = minOf(r.date.startOfWeek(first).plusDays(6), YearMonth.from(r.date).atEndOfMonth())
                        val fmt = LocalFmt.current
                        Text(
                            "${fmt.monthDay(r.date)} – ${fmt.monthDay(end)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 72.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    is DayRow -> DayEvents(vm, r.date, r.events)
                }
            }
        }
    }
}

/** Month banner. Google uses illustrations; this is a gradient tinted by season. */
@Composable
private fun MonthBanner(d: LocalDate) {
    val fmt = LocalFmt.current
    val (a, b) = seasonColors(d.monthValue, isDark())
    Box(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).height(88.dp)
            .clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(a, b))),
    ) {
        Text(fmt.monthYear(d), Modifier.align(Alignment.BottomStart).padding(16.dp),
            style = MaterialTheme.typography.titleLarge, color = if (isDark()) Color.White else Color(0xFF1F1F1F))
    }
}

private fun seasonColors(month: Int, dark: Boolean): Pair<Color, Color> {
    val pair = when (month) {
        12, 1, 2 -> Color(0xFFD3E3FD) to Color(0xFFE8F0FE)
        3, 4, 5 -> Color(0xFFCEEAD6) to Color(0xFFFEF7E0)
        6, 7, 8 -> Color(0xFFFEEFC3) to Color(0xFFFAD2CF)
        else -> Color(0xFFFAD2CF) to Color(0xFFFEEFC3)
    }
    return if (dark) lerpColor(pair.first, Color(0xFF131314), 0.7f) to lerpColor(pair.second, Color(0xFF131314), 0.75f) else pair
}

@Composable
private fun DayEvents(vm: AppViewModel, date: LocalDate, events: List<Instance>) {
    val fmt = LocalFmt.current
    val today = date == LocalDate.now()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    if (today) LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp).testTag("day-$date")) {
        Column(Modifier.width(56.dp).clickable { vm.selectedDate = date; vm.setView(app.crome1394.almanac.data.ViewMode.DAY) },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmt.weekdayShort(date), style = MaterialTheme.typography.labelSmall,
                color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            DayNumber(date)
        }
        Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (events.isEmpty()) {
                Text("Nothing planned. Tap to create.", Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable { vm.newEvent(date) }.padding(vertical = 10.dp, horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var nowShown = !today
            events.forEach { i ->
                if (!nowShown && !i.allDay && i.begin > now) { NowLine(); nowShown = true }
                ScheduleCard(vm, i, date)
            }
            if (!nowShown && events.isNotEmpty()) NowLine()
        }
    }
}

@Composable
private fun NowLine() {
    val c = Color(0xFFEA4335)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Box(Modifier.weight(1f).height(2.dp).background(c))
    }
}

@Composable
private fun ScheduleCard(vm: AppViewModel, i: Instance, day: LocalDate) {
    val fmt = LocalFmt.current
    val dark = isDark()
    val outlined = i.tentative || i.declined
    val fill = eventFill(i.color, dark, i.dimmed())
    val fg = if (outlined) Color(i.color) else onEventColor(Color(i.color))
    val zone = ZoneId.systemDefault()
    val timeText = when {
        i.allDay -> null
        i.isMultiDayTimed(zone) -> when (day) {
            i.startDate(zone) -> "From ${fmt.time(i.begin)}"
            i.lastDate(zone) -> "Until ${fmt.time(i.end)}"
            else -> "All day"
        }
        else -> "${fmt.time(i.begin)} – ${fmt.time(i.end)}"
    }
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .then(if (outlined) Modifier.background(MaterialTheme.colorScheme.surface).androidxBorder(fill, shape) else Modifier.background(fill))
            .clickable { vm.open(i) }
            .padding(horizontal = 12.dp, vertical = if (timeText == null) 6.dp else 8.dp)
            .testTag("event"),
    ) {
        Text(i.title.ifBlank { "(No title)" }, color = fg, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2,
            textDecoration = if (i.declined) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
        if (timeText != null) {
            Spacer(Modifier.height(2.dp))
            Text(timeText, color = fg.copy(alpha = 0.9f), fontSize = 12.sp)
        }
        if (i.location.isNotBlank()) Text(i.location, color = fg.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1)
    }
}
