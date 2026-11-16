package app.crome1394.almanac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import app.crome1394.almanac.data.EventTheme
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    val density = LocalDensity.current

    // An event being dragged by its handle to another day. While dragging, every day gets a
    // row (empty ones are slim drop targets) so any day can be chosen.
    var drag by remember { mutableStateOf<ScheduleDrag?>(null) }
    val dragging = drag != null
    val rows = remember(events, from, to, first, dragging) {
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
                if (!list.isNullOrEmpty() || d == today || dragging) {
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

    // Where the dragged event would land: the day row under the finger, or the next day after
    // a week or month heading.
    var listTop by remember { mutableFloatStateOf(0f) }
    val target: LocalDate? = drag?.let { g ->
        val info = list.layoutInfo
        val y = g.pointerY - listTop
        val hit = info.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size } ?: return@let null
        rows.drop(hit.index).firstOrNull { it is DayRow }?.date
    }
    LaunchedEffect(drag) {
        val g = drag ?: return@LaunchedEffect
        val edge = with(density) { 64.dp.toPx() }
        val step = with(density) { 10.dp.toPx() }
        while (drag === g) {
            val y = g.pointerY - listTop
            val h = list.layoutInfo.viewportSize.height
            if (y < edge) list.scrollBy(-step) else if (y > h - edge) list.scrollBy(step)
            delay(16)
        }
    }
    val dragHandle: (Instance, LocalDate) -> Modifier? = { i, day ->
        if (i.isBirthday || vm.calendars.value.firstOrNull { it.id == i.calendarId }?.writable != true) null
        else Modifier.dragHandle(
            onStart = { y -> drag = ScheduleDrag(i, day, y) },
            onMove = { dy -> drag?.let { it.pointerY += dy } },
            onEnd = {
                val g = drag; val t = target
                drag = null
                if (g != null && t != null) vm.move(g.i, ChronoUnit.DAYS.between(g.fromDay, t).toInt(), 0)
            },
            onCancel = { drag = null },
        )
    }

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(1500); refreshing = false } }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, userScrollEnabled = !dragging,
            modifier = Modifier.fillMaxSize().testTag("schedule").onGloballyPositioned { listTop = it.positionInRoot().y },
            contentPadding = PaddingValues(bottom = 96.dp)) {
            items(rows.size, key = { rows[it].key }) { idx ->
                when (val r = rows[idx]) {
                    is MonthHeader -> MonthBanner(r.date) {
                        // -1 at the bottom of the list, 0 in the middle, 1 at the top.
                        val info = list.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == idx } ?: return@MonthBanner 0f
                        val vh = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
                        -((item.offset + item.size / 2f) - vh / 2f) / (vh / 2f)
                    }
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
                    is DayRow -> DayEvents(vm, r.date, r.events, dropHere = r.date == target, dragging = drag?.i, dragHandle = dragHandle)
                }
            }
        }
        // The dragged event follows the finger, with the day it would move to.
        drag?.let { g ->
            val fmt = LocalFmt.current
            Column(
                Modifier.offset { IntOffset(0, (g.pointerY - listTop - with(density) { 28.dp.toPx() }).roundToInt()) }
                    .padding(start = 72.dp, end = 16.dp).fillMaxWidth().testTag("drag-ghost"),
            ) {
                Box(Modifier.shadow(8.dp, RoundedCornerShape(8.dp))) { ScheduleCard(vm, g.i, g.fromDay, handle = Modifier) }
                if (target != null) Text(
                    "Move to " + fmt.weekdayShort(target) + ", " + fmt.monthDay(target),
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp,
                )
            }
        }
    }
}

/** An event being dragged in Schedule; [pointerY] is in root coordinates. */
private class ScheduleDrag(val i: Instance, val fromDay: LocalDate, y: Float) {
    var pointerY by mutableFloatStateOf(y)
}

/**
 * The six-dot handle's gesture: dragging starts as soon as the finger moves (no long press),
 * and reports the finger's position in root coordinates.
 */
internal fun Modifier.dragHandle(onStart: (Float) -> Unit, onMove: (Float) -> Unit, onEnd: () -> Unit, onCancel: () -> Unit): Modifier =
    composed {
        var top by remember { mutableFloatStateOf(0f) }
        // The gesture outlives recompositions; always call the latest callbacks (they see the current drop target).
        val start by androidx.compose.runtime.rememberUpdatedState(onStart)
        val move by androidx.compose.runtime.rememberUpdatedState(onMove)
        val end by androidx.compose.runtime.rememberUpdatedState(onEnd)
        val cancel by androidx.compose.runtime.rememberUpdatedState(onCancel)
        this.onGloballyPositioned { top = it.positionInRoot().y }.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { o -> start(top + o.y) },
                onDrag = { c, d -> c.consume(); move(d.y) },
                onDragEnd = { end() },
                onDragCancel = { cancel() },
            )
        }
    }

/** Month banner: a landscape for the month whose layers drift as the list scrolls past. */
@Composable
private fun MonthBanner(d: LocalDate, parallax: () -> Float) {
    val fmt = LocalFmt.current
    val dark = isDark()
    Box(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).height(128.dp)
            .clip(RoundedCornerShape(16.dp)).testTag("month-banner"),
    ) {
        MonthScene(d.monthValue, dark, parallax, Modifier.fillMaxSize())
        Text(fmt.monthYear(d), Modifier.align(Alignment.TopStart).padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium,
            color = if (dark || d.monthValue == 12) Color.White else Color(0xFF1F1F1F))
    }
}

@Composable
private fun DayEvents(
    vm: AppViewModel, date: LocalDate, events: List<Instance>,
    dropHere: Boolean = false, dragging: Instance? = null, dragHandle: ((Instance, LocalDate) -> Modifier?)? = null,
) {
    val fmt = LocalFmt.current
    val today = date == LocalDate.now()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    if (today) LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    // While dragging, an empty day is a slim row you can drop on.
    if (events.isEmpty() && !today && dragHandle != null) {
        Row(Modifier.fillMaxWidth().height(36.dp).padding(start = 8.dp, end = 16.dp).testTag("day-$date"), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.weekdayShort(date) + " " + date.dayOfMonth, Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.padding(start = 8.dp).weight(1f).height(28.dp).clip(RoundedCornerShape(8.dp))
                .background(if (dropHere) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer))
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
        .then(if (dropHere) Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
        .testTag("day-$date")) {
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
                Box(if (dragging?.key == i.key) Modifier.alpha(0.3f) else Modifier) {
                    SwipeToDelete(vm, i) { ScheduleCard(vm, i, date, dragHandle?.invoke(i, date)) }
                }
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
internal fun ScheduleCard(vm: AppViewModel, i: Instance, day: LocalDate, handle: Modifier? = null) {
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
    val haptic = LocalHapticFeedback.current
    val theme = remember(i.title) { if (i.isBirthday) EventTheme.BIRTHDAY else EventTheme.of(i.title) }
    if (theme != null && !outlined) {
        // A picture card, as Google draws for dinners, birthdays, flights and so on.
        val ink = if (dark) Color.White else Color(0xFF1F1F1F)
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(shape)
                .combinedClickable(onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.menuFor = i }) { vm.open(i) }
                .testTag("event"),
        ) {
            EventArt(theme, Color(i.color), dark, Modifier.fillMaxSize())
            if (handle != null) DragHandle(ink, handle.align(Alignment.CenterStart))
            Column(Modifier.padding(start = if (handle != null) 28.dp else 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp).fillMaxWidth(0.68f)
                .then(if (i.dimmed()) Modifier.alpha(0.6f) else Modifier)) {
                Text(i.title.ifBlank { "(No title)" }, color = ink, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 2)
                if (timeText != null) Text(timeText, color = ink.copy(alpha = 0.8f), fontSize = 12.sp)
                if (i.location.isNotBlank()) Text(placeLabel(i.location), color = ink.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape)
            .then(if (outlined) Modifier.background(MaterialTheme.colorScheme.surface).androidxBorder(fill, shape) else Modifier.background(fill))
            .combinedClickable(onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.menuFor = i }) { vm.open(i) }
            .testTag("event"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    if (handle != null) DragHandle(fg, handle.fillMaxHeight())
    Column(Modifier.weight(1f).padding(start = if (handle != null) 0.dp else 12.dp, end = 12.dp,
        top = if (timeText == null) 6.dp else 8.dp, bottom = if (timeText == null) 6.dp else 8.dp)) {
        Text(i.title.ifBlank { "(No title)" }, color = fg, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2,
            textDecoration = if (i.declined) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
        if (timeText != null) {
            Spacer(Modifier.height(2.dp))
            Text(timeText, color = fg.copy(alpha = 0.9f), fontSize = 12.sp)
        }
        if (i.location.isNotBlank()) Text(placeLabel(i.location), color = fg.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1)
    }
    }
}

/** The six-dot "drag to move" handle on the left of a movable event. */
@Composable
internal fun DragHandle(tint: Color, modifier: Modifier = Modifier, small: Boolean = false) {
    Box(modifier.width(if (small) 14.dp else 28.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.DragIndicator, "Drag to move",
            tint = tint.copy(alpha = 0.75f), modifier = Modifier.size(if (small) 14.dp else 20.dp).testTag("drag-handle"))
    }
}

/** A video-call link as a location reads better as the service's name. */
private fun placeLabel(location: String): String =
    app.crome1394.almanac.data.VideoCall.find(location)?.takeIf { it.url == location.trim() }?.service ?: location
