package app.crome1394.almanac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import app.crome1394.almanac.data.MonthLayout
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crome1394.almanac.data.Instance
import app.crome1394.almanac.data.ViewMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields

/** Month grid, swiped sideways between months; tap a day to open it. */
@Composable
fun MonthView(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val first = settings.firstDayOfWeek()
    val base = remember { YearMonth.from(vm.selectedDate) }
    val center = 1200
    fun pageOf(d: LocalDate) = center + ((YearMonth.from(d).year - base.year) * 12 + YearMonth.from(d).monthValue - base.monthValue)
    val pager = rememberPagerState(initialPage = pageOf(vm.selectedDate)) { center * 2 }
    val todayReq by vm.todayRequests.collectAsStateWithLifecycle()
    LaunchedEffect(todayReq) { if (todayReq > 0) pager.scrollToPage(pageOf(vm.selectedDate)) }
    LaunchedEffect(pager.settledPage) {
        val m = base.plusMonths((pager.settledPage - center).toLong())
        if (YearMonth.from(vm.selectedDate) != m) vm.selectedDate = if (m == YearMonth.now()) LocalDate.now() else m.atDay(1)
    }
    val zone = ZoneId.systemDefault()
    // An event being dragged to another day (press and hold a bar, then move).
    var drag by remember { mutableStateOf<MonthDrag?>(null) }
    // The "+N" list for a day.
    var dayList by remember { mutableStateOf<LocalDate?>(null) }
    val cals by vm.calendars.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    HorizontalPager(pager, Modifier.fillMaxSize().testTag("month"), key = { it }, userScrollEnabled = drag == null) { page ->
        val month = base.plusMonths((page - center).toLong())
        val weeks = monthWeeks(month, first)
        val gridStart = weeks.first()
        val gridEnd = weeks.last().plusDays(6)
        val events by rememberInstances(vm, gridStart, gridEnd)
        // Where each week row is on screen, to find the day under a dragged event.
        val weekRects = remember { mutableStateMapOf<LocalDate, Rect>() }
        var pageTopLeft by remember { mutableStateOf(Offset.Zero) }
        fun dayAt(x: Float, y: Float): LocalDate? =
            weekRects.entries.firstOrNull { y >= it.value.top && y < it.value.bottom }?.let { (ws, r) ->
                ws.plusDays(((x - r.left) / (r.width / 7)).toInt().coerceIn(0, 6).toLong())
            }
        val target = drag?.let { dayAt(it.x, it.y) }
        val callbacks: (Instance) -> DragCallbacks? = { i ->
            if (i.isBirthday || cals.firstOrNull { it.id == i.calendarId }?.writable != true) null
            else DragCallbacks(
                onStart = { x, y -> dayAt(x, y)?.let { grabbed -> drag = MonthDrag(i, grabbed, x, y) } },
                onMove = { dx, dy -> drag?.let { it.x += dx; it.y += dy } },
                onEnd = {
                    val g = drag; drag = null
                    val t = g?.let { dayAt(it.x, it.y) }
                    if (g != null && t != null) vm.move(g.i, java.time.temporal.ChronoUnit.DAYS.between(g.grabbed, t).toInt(), 0)
                },
                onCancel = { drag = null },
            )
        }
        Box(Modifier.fillMaxSize().onGloballyPositioned { pageTopLeft = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (settings.showWeekNumbers) Box(Modifier.width(20.dp))
                (0 until 7).forEach { k ->
                    Text(fmt.weekdayShort(gridStart.plusDays(k.toLong())).take(3), Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            weeks.forEach { ws ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    if (settings.showWeekNumbers) {
                        Text(ws.get(WeekFields.ISO.weekOfWeekBasedYear()).toString(), Modifier.width(20.dp).padding(top = 6.dp),
                            fontSize = 9.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    WeekRow(vm, ws, month, events.orEmpty(),
                        Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { weekRects[ws] = it.boundsInRoot() },
                        dragging = drag?.i, dropTarget = target, callbacks = callbacks, onMore = { dayList = it },
                        onHold = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        // The dragged event follows the finger.
        drag?.let { g ->
            val density = LocalDensity.current
            Column(Modifier.offset { IntOffset((g.x - pageTopLeft.x - with(density) { 40.dp.toPx() }).roundToInt(), (g.y - pageTopLeft.y - with(density) { 30.dp.toPx() }).roundToInt()) }
                .testTag("drag-ghost")) {
                EventChip(g.i, Modifier.width(96.dp).shadow(6.dp, RoundedCornerShape(4.dp)), small = true) {}
                if (target != null) Text(fmt.weekdayShort(target) + ", " + fmt.monthDay(target),
                    Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 11.sp)
            }
        }
        }
    }
    dayList?.let { d -> DayListDialog(vm, d) { dayList = null } }
}

/** An event dragged in the month grid; [grabbed] is the day under the finger when it was picked up. */
private class MonthDrag(val i: Instance, val grabbed: LocalDate, x: Float, y: Float) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
}

/** "+N": every event of one day in a small card, as Google shows it. */
@Composable
private fun DayListDialog(vm: AppViewModel, day: LocalDate, onDismiss: () -> Unit) {
    val fmt = LocalFmt.current
    val events by rememberInstances(vm, day, day)
    val zone = ZoneId.systemDefault()
    val list = events.orEmpty().filter { it.covers(day, zone) }.sortedWith(compareBy<Instance> { !it.inAllDayRow }.thenBy { it.begin })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fmt.weekdayShort(day) + ", " + fmt.monthDay(day)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("day-list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (i.inAllDayRow) "All day" else fmt.time(i.begin), Modifier.width(64.dp), fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        EventChip(i, Modifier.weight(1f)) { onDismiss(); vm.open(i) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); vm.selectedDate = day; vm.setView(ViewMode.DAY) }) { Text("Open day") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * One week of the grid: day numbers, then every event as a bar across the days it covers
 * (multi-day events first, then each day's events by time), with "+N" where a day overflows.
 */
@Composable
private fun WeekRow(
    vm: AppViewModel, weekStart: LocalDate, month: YearMonth, events: List<Instance>, modifier: Modifier,
    dragging: Instance?, dropTarget: LocalDate?, callbacks: (Instance) -> DragCallbacks?, onMore: (LocalDate) -> Unit, onHold: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val weekEnd = weekStart.plusDays(6)
    val line = MaterialTheme.colorScheme.outlineVariant
    val items = remember(events, weekStart) {
        events.filter { it.lastDate(zone) >= weekStart && it.startDate(zone) <= weekEnd }
            .sortedWith(
                compareBy<Instance> { it.startDate(zone) == it.lastDate(zone) } // multi-day first
                    .thenBy { maxOf(it.startDate(zone), weekStart) }
                    .thenByDescending { it.lastDate(zone).toEpochDay() - it.startDate(zone).toEpochDay() }
                    .thenBy { !it.allDay }
                    .thenBy { it.begin },
            )
            .map {
                MonthLayout.Item(it, ChronoUnit.DAYS.between(weekStart, it.startDate(zone)).toInt(),
                    ChronoUnit.DAYS.between(weekStart, it.lastDate(zone)).toInt())
            }
    }
    BoxWithConstraints(
        modifier.drawBehind {
            val w = size.width / 7
            for (k in 1 until 7) drawLine(line, Offset(k * w, 0f), Offset(k * w, size.height), 1f)
        },
    ) {
        val cellW = maxWidth / 7
        // Each bar is 17dp plus 1dp spacing; the date takes 28dp.
        val lanes = ((maxHeight.value - 28f) / 18f).toInt().coerceAtLeast(0)
        val week = remember(items, lanes) { MonthLayout.layout(items, lanes) }
        Row(Modifier.fillMaxSize()) {
            (0 until 7).forEach { k ->
                val d = weekStart.plusDays(k.toLong())
                val today = d == LocalDate.now()
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .then(if (d == dropTarget) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) else Modifier)
                        .clickable { vm.selectedDate = d; vm.setView(ViewMode.DAY) }.testTag("cell-$d"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier.padding(top = 3.dp).size(22.dp).clip(CircleShape)
                            .background(if (today) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(), fontSize = 12.sp,
                            color = when {
                                today -> MaterialTheme.colorScheme.onPrimary
                                d.month != month.month -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
        week.bars.forEach { b ->
            val i = b.value
            val cb = callbacks(i)
            EventChip(
                i,
                Modifier.offset(x = cellW * b.start + 1.dp, y = 28.dp + 18.dp * b.lane).width(cellW * (b.end - b.start + 1) - 2.dp)
                    .then(if (dragging?.key == i.key) Modifier.alpha(0.35f) else Modifier),
                small = true, onLongClick = { vm.menuFor = i },
                // Movable events: tap opens, hold opens the menu, hold then move drags to another day.
                gestures = cb?.let { Modifier.holdToDrag(it, onTap = { vm.open(i) }, onHold = onHold, onMenu = { vm.menuFor = i }) },
            ) { vm.open(i) }
        }
        week.more.forEach { (col, n) ->
            Text(
                "+$n",
                Modifier.offset(x = cellW * col + 1.dp, y = 28.dp + 18.dp * (lanes - 1).coerceAtLeast(0)).width(cellW - 2.dp)
                    .height(17.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .clickable { onMore(weekStart.plusDays(col.toLong())) }
                    .testTag("more-$col")
                    .padding(start = 3.dp),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
