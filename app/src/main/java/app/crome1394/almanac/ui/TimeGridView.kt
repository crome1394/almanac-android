package app.crome1394.almanac.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crome1394.almanac.data.DayLayout
import app.crome1394.almanac.data.MonthLayout
import app.crome1394.almanac.data.Instance
import app.crome1394.almanac.data.ViewMode
import app.crome1394.almanac.data.startOfWeek
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

private val GUTTER = 52.dp
private const val CENTER = 10_000
/** How long a finger must rest on a handle or bottom edge before it picks the event up. */
private const val EDGE_HOLD_MS = 180L

/** Day, 3-day and week views: a scrollable 24-hour grid, swiped sideways between periods. */
@Composable
fun TimeGridView(vm: AppViewModel, days: Int) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val first = settings.firstDayOfWeek()
    fun startOf(d: LocalDate) = if (days == 7) d.startOfWeek(first) else d
    val base = remember(days, first) { startOf(vm.selectedDate) }
    fun pageOf(d: LocalDate) = CENTER + Math.floorDiv(ChronoUnit.DAYS.between(base, startOf(d)), days.toLong()).toInt()
    fun startOfPage(p: Int) = base.plusDays((p - CENTER).toLong() * days)
    val pager = rememberPagerState(initialPage = pageOf(vm.selectedDate)) { CENTER * 2 }

    val todayReq by vm.todayRequests.collectAsStateWithLifecycle()
    // Bumped on each "go to a date" request; pages scroll to the current time when it is today.
    var timeJump by remember { mutableIntStateOf(0) }
    LaunchedEffect(todayReq) {
        if (todayReq > 0) {
            pager.scrollToPage(pageOf(vm.selectedDate))
            if (vm.selectedDate == LocalDate.now()) timeJump++
        }
    }
    LaunchedEffect(pager.settledPage) {
        val s = startOfPage(pager.settledPage)
        if (vm.selectedDate < s || vm.selectedDate > s.plusDays(days - 1L)) vm.selectedDate = s
    }

    // Zoom (pinch) and vertical position are shared by all pages.
    var hourHeight by remember { mutableFloatStateOf(settings.hourHeightDp) }
    var scrollY by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        snapshotFlow { hourHeight }.distinctUntilChanged().collect {
            delay(500); vm.prefs.update { s -> s.copy(hourHeightDp = it) }
        }
    }

    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
        val start = startOfPage(page)
        val dates = (0 until days).map { start.plusDays(it.toLong()) }
        val events by rememberInstances(vm, dates.first(), dates.last())
        val scroll = rememberScrollState()
        LaunchedEffect(timeJump) {
            if (timeJump > 0 && dates.contains(LocalDate.now())) {
                scroll.animateScrollTo(with(density) { ((LocalTime.now().hour - 1).coerceAtLeast(0) * hourHeight).dp.roundToPx() })
            }
        }
        LaunchedEffect(Unit) {
            val target = if (scrollY >= 0) scrollY else with(density) {
                val h = if (dates.contains(LocalDate.now())) (LocalTime.now().hour - 1).coerceAtLeast(0) else 7
                (h * hourHeight).dp.roundToPx()
            }
            scroll.scrollTo(target)
            snapshotFlow { scroll.value }.collect { if (pager.currentPage == page) scrollY = it }
        }
        Column(Modifier.fillMaxSize()) {
            DayHeaders(vm, dates, days, settings.showWeekNumbers)
            AllDayRow(vm, dates, events.orEmpty())
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Grid(vm, dates, events.orEmpty(), hourHeight, scroll, onZoom = { z -> hourHeight = (hourHeight * z).coerceIn(24f, 180f) })
        }
    }
}

@Composable
private fun DayHeaders(vm: AppViewModel, dates: List<LocalDate>, days: Int, weekNumbers: Boolean) {
    val fmt = LocalFmt.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
        Box(Modifier.width(GUTTER), contentAlignment = Alignment.Center) {
            if (weekNumbers) {
                val wk = dates.first().get(WeekFields.ISO.weekOfWeekBasedYear())
                Text("W$wk", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        dates.forEach { d ->
            val today = d == LocalDate.now()
            Column(
                Modifier.weight(1f).clickable(enabled = days > 1) { vm.selectedDate = d; vm.setView(ViewMode.DAY) },
                horizontalAlignment = if (days == 1) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                Text(
                    fmt.weekdayShort(d).uppercase(), fontSize = 11.sp,
                    color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (days == 1) Modifier.padding(start = 6.dp) else Modifier,
                )
                DayNumber(d, big = days < 7)
            }
        }
    }
}

/** All-day and multi-day events above the grid, as bars across their days (three lanes, then "+N"). */
@Composable
private fun AllDayRow(vm: AppViewModel, dates: List<LocalDate>, events: List<Instance>) {
    val zone = ZoneId.systemDefault()
    val first = dates.first(); val last = dates.last()
    val items = remember(events, first, last) {
        events.filter { it.inAllDayRow && it.lastDate(zone) >= first && it.startDate(zone) <= last }
            .sortedWith(compareBy<Instance> { maxOf(it.startDate(zone), first) }
                .thenByDescending { it.lastDate(zone).toEpochDay() - it.startDate(zone).toEpochDay() }
                .thenBy { it.begin })
            .map {
                MonthLayout.Item(it,
                    ChronoUnit.DAYS.between(first, it.startDate(zone)).toInt().coerceIn(0, dates.size - 1),
                    ChronoUnit.DAYS.between(first, it.lastDate(zone)).toInt().coerceIn(0, dates.size - 1))
            }
    }
    if (items.isEmpty()) return
    val week = remember(items) { MonthLayout.layout(items, lanes = 3) }
    val lanes = (week.bars.maxOfOrNull { it.lane + 1 } ?: 0).coerceAtLeast(if (week.more.isNotEmpty()) 3 else 0)
    // Lanes fit the chip: the small one in week view, the taller one (with padding) in day and 3-day view.
    val small = dates.size > 3
    val lane = if (small) 20.dp else 30.dp
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(Modifier.width(GUTTER))
        BoxWithConstraints(Modifier.weight(1f).height(lane * lanes)) {
            val colW = maxWidth / dates.size
            week.bars.forEach { b ->
                val i = b.value
                EventChip(i, Modifier.offset(x = colW * b.start + 1.dp, y = lane * b.lane).width(colW * (b.end - b.start + 1) - 2.dp)
                    .then(if (small) Modifier else Modifier.height(lane - 3.dp)),
                    small = small, onLongClick = { vm.menuFor = i }) { vm.open(i) }
            }
            week.more.forEach { (col, n) ->
                Text(
                    "+$n",
                    Modifier.offset(x = colW * col + 1.dp, y = lane * 2).width(colW - 2.dp).clip(RoundedCornerShape(4.dp))
                        .clickable { vm.selectedDate = dates[col]; vm.setView(ViewMode.SCHEDULE); vm.todayRequests.value++ }
                        .padding(horizontal = 4.dp),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What a drag in the grid does: move an event, change its end, or mark out a new one. */
private enum class DragMode { MOVE, RESIZE, CREATE }

/**
 * A drag in the grid. For [DragMode.CREATE] there is no event: [startMin] is the 15-minute slot
 * where the finger came down.
 */
private class GridDrag(val mode: DragMode, val i: Instance?, val dayIndex: Int, val startMin: Int, val lenMin: Int, val anchorY: Float) {
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)

    private fun snap(m: Float) = Math.round(m / 15f) * 15

    /** A resize: the new length in minutes (15 minutes at least, not past midnight). */
    fun newLength(hourPx: Float): Int = snap(lenMin + dy / hourPx * 60f).coerceIn(15, 1440 - startMin)

    /** A new event: start and end minute of the marked-out range (the slot itself at least). */
    fun range(hourPx: Float): Pair<Int, Int> {
        val b = snap(startMin + dy / hourPx * 60f).coerceIn(0, 1440)
        return if (b >= startMin) startMin to maxOf(b, startMin + 15) else b to startMin + 15
    }

    /** Where the event would land: days moved and minutes moved (snapped to 15). */
    fun target(hourPx: Float, colPx: Float, days: Int): Pair<Int, Int> {
        val dd = if (colPx > 0) Math.round(dx / colPx).coerceIn(-dayIndex, days - 1 - dayIndex) else 0
        val raw = startMin + dy / hourPx * 60f
        val snapped = (Math.round(raw / 15f) * 15).coerceIn(0, 1440 - 15)
        return dd to snapped - startMin
    }
}

@Composable
private fun Grid(
    vm: AppViewModel, dates: List<LocalDate>, events: List<Instance>, hourHeight: Float,
    scroll: ScrollState, onZoom: (Float) -> Unit,
) {
    val fmt = LocalFmt.current
    val zone = ZoneId.systemDefault()
    val line = MaterialTheme.colorScheme.outlineVariant
    val hourDp = hourHeight.dp
    val density = LocalDensity.current
    val hourPx = with(density) { hourDp.toPx() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    var drag by remember { mutableStateOf<GridDrag?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val cals by vm.calendars.collectAsStateWithLifecycle()

    // While dragging near the top or bottom edge, scroll the grid (and the event with it).
    LaunchedEffect(drag) {
        val d = drag ?: return@LaunchedEffect
        val edge = with(density) { 48.dp.toPx() }
        while (drag === d) {
            val y = d.anchorY + d.dy - scroll.value
            val step = when {
                y < edge -> -with(density) { 6.dp.toPx() }
                y > scroll.viewportSize - edge -> with(density) { 6.dp.toPx() }
                else -> 0f
            }
            if (step != 0f) d.dy += scroll.scrollBy(step)
            delay(16)
        }
    }

    Box(
        Modifier.fillMaxSize()
            // Two-finger pinch changes the hour height; one finger still scrolls.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.count { it.pressed } >= 2) {
                            val z = ev.calculateZoom()
                            if (z != 1f) onZoom(z)
                            ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
            .verticalScroll(scroll, enabled = drag == null)
            .testTag("grid"),
    ) {
        Row(Modifier.fillMaxWidth().height(hourDp * 24 + 8.dp).padding(top = 8.dp)) {
            // Hour labels
            Box(Modifier.width(GUTTER).fillMaxHeight()) {
                for (h in 1 until 24) {
                    Text(
                        fmt.hourLabel(h), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(GUTTER - 8.dp).offset(y = hourDp * h - 6.dp),
                        lineHeight = 12.sp,
                    )
                }
            }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxHeight().drawBehind {
                    val hp = hourDp.toPx()
                    for (h in 0..24) drawLine(line, Offset(0f, h * hp), Offset(size.width, h * hp), 1f)
                    val w = size.width / dates.size
                    for (k in 1 until dates.size) drawLine(line, Offset(k * w, 0f), Offset(k * w, size.height), 1f)
                },
            ) {
                val colW = maxWidth / dates.size
                val colPx = with(density) { colW.toPx() }
                Row(Modifier.fillMaxSize()) {
                    dates.forEachIndexed { k, d ->
                        val dayStart = d.atStartOfDay(zone).toInstant().toEpochMilli()
                        DayColumn(
                            vm, d, events.filter { !it.inAllDayRow && it.covers(d, zone) && it.end > dayStart },
                            hourDp, now, compact = dates.size > 3, dragging = drag?.i,
                            canDrag = { i -> i.startDate(zone) == d && cals.firstOrNull { it.id == i.calendarId }?.writable == true },
                            onDragStart = { mode, i, startMin, lenMin, y ->
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                drag = GridDrag(mode, i, k, startMin, lenMin, startMin / 60f * hourPx + y)
                            },
                            onDrag = { dx, dy -> drag?.let { it.dx += dx; it.dy += dy } },
                            onDragEnd = {
                                val g = drag
                                drag = null
                                if (g != null) when (g.mode) {
                                    DragMode.MOVE -> {
                                        val (dd, dm) = g.target(hourPx, colPx, dates.size)
                                        if (dd == 0 && dm == 0) vm.menuFor = g.i else vm.move(g.i!!, dd, dm)
                                    }
                                    DragMode.RESIZE -> vm.resize(g.i!!, g.newLength(hourPx) - g.lenMin)
                                    DragMode.CREATE -> {
                                        val (a, b) = g.range(hourPx)
                                        // A hold without moving makes an event of the usual length.
                                        val moved = kotlin.math.abs(g.dy) > hourPx / 8
                                        vm.newEvent(dates[g.dayIndex], LocalTime.of(a / 60 % 24, a % 60), durationMin = if (moved) b - a else null)
                                    }
                                }
                            },
                            onDragCancel = { drag = null },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                drag?.let { g ->
                    // Where the event (or the new one) would be: day column, start and length in minutes.
                    val (col, start, len) = when (g.mode) {
                        DragMode.MOVE -> g.target(hourPx, colPx, dates.size).let { (dd, dm) -> Triple(g.dayIndex + dd, g.startMin + dm, g.lenMin) }
                        DragMode.RESIZE -> Triple(g.dayIndex, g.startMin, g.newLength(hourPx))
                        DragMode.CREATE -> g.range(hourPx).let { (a, b) -> Triple(g.dayIndex, a, b - a) }
                    }
                    val shape = RoundedCornerShape(6.dp)
                    val color = g.i?.color
                    val fill = if (color != null) eventFill(color, isDark(), false) else MaterialTheme.colorScheme.primaryContainer
                    val fg = if (color != null) onEventColor(Color(color)) else MaterialTheme.colorScheme.onPrimaryContainer
                    val begin = dates[col].atStartOfDay(zone).plusMinutes(start.toLong()).toInstant().toEpochMilli()
                    Column(
                        Modifier.offset(x = colW * col, y = hourDp * (start / 60f))
                            .width(colW - 2.dp).height(hourDp * (len.coerceAtLeast(24) / 60f))
                            .shadow(8.dp, shape).clip(shape).background(fill)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("drag-ghost"),
                    ) {
                        Text(g.i?.title?.ifBlank { "(No title)" } ?: "New event", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("${fmt.time(begin)} – ${fmt.time(begin + len * 60_000L)}", color = fg, fontSize = 11.sp, maxLines = 1, modifier = Modifier.testTag("drag-time"))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    vm: AppViewModel, day: LocalDate, events: List<Instance>, hourDp: Dp, now: Long, compact: Boolean,
    dragging: Instance?, canDrag: (Instance) -> Boolean,
    onDragStart: (DragMode, Instance?, Int, Int, Float) -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit,
    modifier: Modifier,
) {
    val zone = ZoneId.systemDefault()
    val density = LocalDensity.current
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val placed = remember(events, day) {
        DayLayout.layout(events.map {
            val s = ((it.begin - dayStart) / 60_000).toInt().coerceIn(0, 1440)
            val e = ((it.end - dayStart) / 60_000).toInt().coerceIn(s, 1440)
            DayLayout.Item(it, s, e)
        }, minHeight = 24)
    }
    val writable = vm.writableCalendars().isNotEmpty()
    BoxWithConstraints(
        // Empty grid: tap for a new event at that half hour; press, hold and drag to mark out its length.
        modifier.pointerInput(day, hourDp, writable) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val hourPx = hourDp.toPx()
                val long = awaitLongPressOrCancellation(down.id)
                if (long == null) {
                    val up = currentEvent.changes.firstOrNull { it.id == down.id }
                    if (up != null && up.changedToUp() && !up.isConsumed) {
                        up.consume()
                        val minutes = (down.position.y / hourPx * 60).toInt()
                        vm.newEvent(day, LocalTime.of((minutes / 60).coerceIn(0, 23), if (minutes % 60 >= 30) 30 else 0))
                    }
                    return@awaitEachGesture
                }
                if (!writable) return@awaitEachGesture
                val slot = ((long.position.y / hourPx * 60).toInt() / 15 * 15).coerceIn(0, 1440 - 15)
                onDragStart(DragMode.CREATE, null, slot, 15, long.position.y - slot / 60f * hourPx)
                val ended = drag(long.id) { c -> val d = c.positionChange(); c.consume(); onDrag(d.x, d.y) }
                if (ended) onDragEnd() else onDragCancel()
            }
        },
    ) {
        val colW = maxWidth
        placed.forEach { p ->
            val i = p.value
            val top = hourDp * (p.start / 60f)
            val height = (hourDp * ((p.end - p.start).coerceAtLeast(24) / 60f)) - 1.dp
            val w = colW / p.cols
            val draggable = canDrag(i)
            val showHandle = draggable && w * p.span >= 60.dp && height >= 24.dp
            // The bottom edge changes the end; only for events that end on this day and are tall enough to grab.
            val resizable = draggable && height >= 36.dp && p.end < 1440 && i.end - dayStart <= 1440 * 60_000L
            // One gesture handler tells a tap (open), a hold (menu) and a hold-then-move (drag) apart.
            val gestures = if (draggable) Modifier.pointerInput(i.key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume() // so the grid's tap-to-create doesn't also fire
                    // The six-dot handle and the bottom edge pick up after a brief hold; elsewhere after a long press.
                    val onHandle = showHandle && down.position.x < 18.dp.toPx()
                    val onEdge = resizable && down.position.y > size.height - 14.dp.toPx()
                    val long = if (onHandle || onEdge) {
                        val interrupted = withTimeoutOrNull(EDGE_HOLD_MS) {
                            while (true) {
                                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                            }
                            @Suppress("UNREACHABLE_CODE") true
                        }
                        if (interrupted == true) null else down
                    } else awaitLongPressOrCancellation(down.id)
                    if (long == null) {
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up != null && up.changedToUp()) { up.consume(); vm.open(i) }
                        return@awaitEachGesture
                    }
                    onDragStart(if (onEdge) DragMode.RESIZE else DragMode.MOVE, i, p.start, ((i.end - i.begin) / 60_000).toInt(), long.position.y)
                    val ended = drag(long.id) { c -> val d = c.positionChange(); c.consume(); onDrag(d.x, d.y) }
                    // A hold without moving ends with the event where it was, which opens the menu.
                    if (ended) onDragEnd() else onDragCancel()
                }
            } else Modifier
            GridEvent(vm, i, compact, narrow = w * p.span < 60.dp, clickable = !draggable, handle = showHandle, resizable = resizable,
                modifier = Modifier.offset(x = w * p.col, y = top).width(w * p.span - 2.dp).height(height)
                    .then(if (dragging?.key == i.key) Modifier.alpha(0.35f) else Modifier).then(gestures))
        }
        if (day == LocalDate.now()) {
            val mins = Instant.ofEpochMilli(now).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
            val red = Color(0xFFEA4335)
            Box(Modifier.offset(y = hourDp * (mins / 60f) - 1.dp).fillMaxWidth().height(2.dp).background(red))
            Box(Modifier.offset(x = (-5).dp, y = hourDp * (mins / 60f) - 5.dp).size(10.dp).clip(CircleShape).background(red))
        }
    }
}

@Composable
private fun GridEvent(vm: AppViewModel, i: Instance, compact: Boolean, narrow: Boolean, clickable: Boolean, handle: Boolean, resizable: Boolean = false, modifier: Modifier) {
    val fmt = LocalFmt.current
    val dark = isDark()
    val outlined = i.tentative || i.declined
    val fill = eventFill(i.color, dark, i.dimmed())
    val fg = if (outlined) Color(i.color) else onEventColor(Color(i.color))
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.clip(shape)
            .then(if (outlined) Modifier.background(MaterialTheme.colorScheme.surface).androidxBorder(fill, shape) else Modifier.background(fill))
            .androidxBorder(MaterialTheme.colorScheme.surface, shape)
            .then(if (clickable) Modifier.combinedClickable(onLongClick = { vm.menuFor = i }) { vm.open(i) } else Modifier)
            .testTag("event"),
    ) {
    if (handle) DragHandle(fg, Modifier.padding(start = 1.dp, top = 3.dp), small = true)
    // A small bar on the bottom edge: hold and drag it to change when the event ends.
    if (resizable) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(width = 18.dp, height = 3.dp)
        .clip(RoundedCornerShape(2.dp)).background(fg.copy(alpha = 0.6f)).testTag("resize-handle"))
    Column(Modifier.padding(start = if (handle) 15.dp else if (compact) 3.dp else 6.dp, end = if (compact) 3.dp else 6.dp, top = 2.dp, bottom = 2.dp)) {
        Text(i.title.ifBlank { "(No title)" }, color = fg, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.Medium,
            maxLines = if (narrow) 1 else 3, softWrap = !narrow, lineHeight = 14.sp,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            style = androidx.compose.ui.text.TextStyle(hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph),
            textDecoration = if (i.declined) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
        if (!compact) Text("${fmt.time(i.begin)} – ${fmt.time(i.end)}", color = fg.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1)
        if (!compact && i.location.isNotBlank()) Text(i.location, color = fg.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1)
    }
    }
}
