package app.crome1394.almanac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
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
    // Set on every "go to a date" request (Today, mini month), even when the date hasn't changed.
    var jumpPending by remember { mutableStateOf(true) }
    LaunchedEffect(todayReq) {
        if (todayReq > 0) {
            anchor = vm.selectedDate
            if (anchor < from.plusDays(14) || anchor > to.minusDays(30)) { from = anchor.minusDays(60); to = anchor.plusDays(120) }
            jumpPending = true
        }
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
    var scrolledOnce by remember { mutableStateOf(false) }
    LaunchedEffect(rows, jumpPending) {
        if (events != null && jumpPending) {
            val idx = rows.indexOfFirst { it is DayRow && it.date >= anchor }.let { if (it < 0) rows.indexOfFirst { r -> r.date >= anchor } else it }
            if (idx >= 0) list.scrollToItem((idx - 1).coerceAtLeast(0))
            jumpPending = false
            scrolledOnce = true
        }
    }
    // Keep the title in step with the list, and load more near either end.
    LaunchedEffect(list, rows) {
        snapshotFlow { list.firstVisibleItemIndex to list.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { (firstIdx, lastIdx) ->
                if (!scrolledOnce || jumpPending) return@collect
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
    // Sliding sideways while dragging changes the time (see ScheduleDrag.minutes).
    val screenW = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val cals by vm.calendars.collectAsStateWithLifecycle()
    val dragHandle: (Instance, LocalDate) -> DragCallbacks? = { i, day ->
        if (i.isBirthday || cals.firstOrNull { it.id == i.calendarId }?.writable != true) null
        else DragCallbacks(
            onStart = { x, y -> drag = ScheduleDrag(i, day, x, y) },
            onMove = { dx, dy -> drag?.let { it.pointerY += dy; it.pointerX += dx } },
            onEnd = {
                val g = drag; val t = target
                drag = null
                if (g != null && t != null) vm.move(g.i, ChronoUnit.DAYS.between(g.fromDay, t).toInt(), g.minutes(screenW))
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
                // The floating copy shows the time it would get.
                val shift = g.minutes(screenW) * 60_000L
                Box(Modifier.shadow(8.dp, RoundedCornerShape(8.dp))) {
                    ScheduleCard(vm, g.i.copy(begin = g.i.begin + shift, end = g.i.end + shift), g.fromDay, showHandle = true)
                }
                if (target != null) {
                    val m = g.minutes(screenW)
                    val begin = g.i.begin + m * 60_000L
                    val time = if (g.i.allDay) "" else " · " + fmt.time(begin) + " – " + fmt.time(begin + (g.i.end - g.i.begin))
                    Column(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("Move to " + fmt.weekdayShort(target) + ", " + fmt.monthDay(target) + time,
                            color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 12.sp, modifier = Modifier.testTag("drag-label"))
                        if (!g.i.allDay) Text("Slide left for earlier, right for later",
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** An event being dragged in Schedule; [pointerY] is in root coordinates. */
private class ScheduleDrag(val i: Instance, val fromDay: LocalDate, val startX: Float, y: Float) {
    var pointerY by mutableFloatStateOf(y)
    var pointerX by mutableFloatStateOf(startX)
    private val startMin = java.time.Instant.ofEpochMilli(i.begin).atZone(ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute }

    /**
     * Minutes the time moves, in 15-minute steps. From where the finger started, the left edge
     * of the screen is midnight and the right edge is 23:45, so any time can be reached from a
     * handle at the left. All-day events keep their dates whole.
     */
    fun minutes(screenW: Float, dead: Float = screenW / 12): Int {
        if (i.allDay) return 0
        // Sideways wobble while dragging up or down leaves the time alone.
        val raw = pointerX.coerceIn(0f, screenW)
        val x = when {
            kotlin.math.abs(raw - startX) < dead -> startX
            raw > startX -> startX + (raw - startX - dead) * (screenW - startX) / (screenW - startX - dead).coerceAtLeast(1f)
            else -> startX - (startX - raw - dead) * startX / (startX - dead).coerceAtLeast(1f)
        }.coerceIn(0f, screenW)
        val target = if (x <= startX) startMin * (x / startX.coerceAtLeast(1f))
        else startMin + (1425 - startMin) * ((x - startX) / (screenW - startX).coerceAtLeast(1f))
        return Math.round(target / 15f) * 15 - startMin
    }
}

/** What dragging an event in Schedule reports: start and moves in root coordinates, then drop or cancel. */
internal class DragCallbacks(
    val onStart: (Float, Float) -> Unit,
    val onMove: (Float, Float) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The card's own gesture: a tap opens the event; press and hold then move drags it (like the
 * day/week grid); press and hold without moving opens the event menu. A quick swipe still
 * scrolls the list or swipes the card away, because nothing is claimed before the hold.
 */
internal fun Modifier.holdToDrag(cb: DragCallbacks, onTap: () -> Unit, onHold: () -> Unit, onMenu: () -> Unit): Modifier =
    composed {
        var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val callbacks by androidx.compose.runtime.rememberUpdatedState(cb)
        val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
        val hold by androidx.compose.runtime.rememberUpdatedState(onHold)
        val menu by androidx.compose.runtime.rememberUpdatedState(onMenu)
        this.onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val long = awaitLongPressOrCancellation(down.id)
                if (long == null) {
                    val up = currentEvent.changes.firstOrNull { it.id == down.id }
                    if (up != null && up.changedToUp() && !up.isConsumed) { up.consume(); tap() }
                    return@awaitEachGesture
                }
                hold()
                var moved = false
                var started = false
                val slop = viewConfiguration.touchSlop
                var total = androidx.compose.ui.geometry.Offset.Zero
                val ended = drag(long.id) { c ->
                    val d = c.positionChange()
                    c.consume()
                    total += d
                    if (!started && total.getDistance() > slop) {
                        started = true; moved = true
                        callbacks.onStart(origin.x + c.position.x, origin.y + c.position.y)
                    } else if (started) callbacks.onMove(d.x, d.y)
                }
                when {
                    !moved -> { currentEvent.changes.forEach { it.consume() }; menu() }
                    ended -> callbacks.onEnd()
                    else -> callbacks.onCancel()
                }
            }
        }
    }

/**
 * The six-dot handle's gesture: dragging starts as soon as the finger moves (no long press),
 * and reports the finger's position in root coordinates.
 */
internal fun Modifier.dragHandle(onStart: (Float, Float) -> Unit, onMove: (Float, Float) -> Unit, onEnd: () -> Unit, onCancel: () -> Unit): Modifier =
    composed {
        var top by remember { mutableFloatStateOf(0f) }
        var left by remember { mutableFloatStateOf(0f) }
        // The gesture outlives recompositions; always call the latest callbacks (they see the current drop target).
        val start by androidx.compose.runtime.rememberUpdatedState(onStart)
        val move by androidx.compose.runtime.rememberUpdatedState(onMove)
        val end by androidx.compose.runtime.rememberUpdatedState(onEnd)
        val cancel by androidx.compose.runtime.rememberUpdatedState(onCancel)
        this.onGloballyPositioned { it.positionInRoot().let { p -> top = p.y; left = p.x } }.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { o -> start(left + o.x, top + o.y) },
                onDrag = { c, d -> c.consume(); move(d.x, d.y) },
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
    dropHere: Boolean = false, dragging: Instance? = null, dragHandle: ((Instance, LocalDate) -> DragCallbacks?)? = null,
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
internal fun ScheduleCard(vm: AppViewModel, i: Instance, day: LocalDate, drag: DragCallbacks? = null, showHandle: Boolean = drag != null) {
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
    // Tap opens; press and hold opens the menu, or drags the event if the finger then moves.
    val press = if (drag != null) Modifier.holdToDrag(drag, onTap = { vm.open(i) },
        onHold = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }, onMenu = { vm.menuFor = i })
    else Modifier.combinedClickable(onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.menuFor = i }) { vm.open(i) }
    val handle: Modifier? = when {
        drag != null -> Modifier.dragHandle(drag.onStart, drag.onMove, drag.onEnd, drag.onCancel)
        showHandle -> Modifier
        else -> null
    }
    val theme = remember(i.title) { if (i.isBirthday) EventTheme.BIRTHDAY else EventTheme.of(i.title) }
    if (theme != null && !outlined) {
        // A picture card, as Google draws for dinners, birthdays, flights and so on.
        val ink = if (dark) Color.White else Color(0xFF1F1F1F)
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(shape)
                .then(press)
                .testTag("event"),
        ) {
            EventArt(theme, Color(i.color), dark, Modifier.fillMaxSize())
            if (handle != null) DragHandle(ink, handle.align(Alignment.CenterStart))
            Column(Modifier.padding(start = if (handle != null) 40.dp else 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp).fillMaxWidth(0.68f)
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
            .then(press)
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
    Box(modifier.width(if (small) 14.dp else 40.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.DragIndicator, "Drag to move",
            tint = tint.copy(alpha = 0.75f), modifier = Modifier.size(if (small) 14.dp else 20.dp).testTag("drag-handle"))
    }
}

/** A video-call link as a location reads better as the service's name. */
private fun placeLabel(location: String): String =
    app.crome1394.almanac.data.VideoCall.find(location)?.takeIf { it.url == location.trim() }?.service ?: location
