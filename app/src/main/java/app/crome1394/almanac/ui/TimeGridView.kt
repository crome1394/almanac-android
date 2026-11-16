package app.crome1394.almanac.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
    LaunchedEffect(todayReq) { if (todayReq > 0) pager.scrollToPage(pageOf(vm.selectedDate)) }
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

/** All-day and multi-day events above the grid, as chips per day (up to three, then "+N"). */
@Composable
private fun AllDayRow(vm: AppViewModel, dates: List<LocalDate>, events: List<Instance>) {
    val zone = ZoneId.systemDefault()
    val top = events.filter { it.inAllDayRow }
    if (top.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(Modifier.width(GUTTER))
        dates.forEach { d ->
            val here = top.filter { it.covers(d, zone) }
            Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                here.take(if (here.size > 3) 2 else 3).forEach { i ->
                    EventChip(i, Modifier.fillMaxWidth(), small = dates.size > 3) { vm.open(i) }
                }
                if (here.size > 3) Text(
                    "+${here.size - 2}",
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                        .clickable { vm.selectedDate = d; vm.setView(ViewMode.SCHEDULE); vm.todayRequests.value++ }
                        .padding(horizontal = 4.dp),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

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
            .verticalScroll(scroll)
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
            Box(
                Modifier.weight(1f).fillMaxHeight().drawBehind {
                    val hp = hourDp.toPx()
                    for (h in 0..24) drawLine(line, Offset(0f, h * hp), Offset(size.width, h * hp), 1f)
                    val w = size.width / dates.size
                    for (k in 1 until dates.size) drawLine(line, Offset(k * w, 0f), Offset(k * w, size.height), 1f)
                },
            ) {
                Row(Modifier.fillMaxSize()) {
                    dates.forEach { d ->
                        DayColumn(vm, d, events.filter { !it.inAllDayRow && it.covers(d, zone) && it.end > d.atStartOfDay(zone).toInstant().toEpochMilli() },
                            hourDp, now, compact = dates.size > 3, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumn(vm: AppViewModel, day: LocalDate, events: List<Instance>, hourDp: Dp, now: Long, compact: Boolean, modifier: Modifier) {
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
    BoxWithConstraints(
        modifier.pointerInput(day, hourDp) {
            detectTapGestures { pos ->
                val minutes = (pos.y / with(density) { hourDp.toPx() } * 60).toInt()
                vm.newEvent(day, LocalTime.of((minutes / 60).coerceIn(0, 23), if (minutes % 60 >= 30) 30 else 0))
            }
        },
    ) {
        val colW = maxWidth
        placed.forEach { p ->
            val i = p.value
            val top = hourDp * (p.start / 60f)
            val height = (hourDp * ((p.end - p.start).coerceAtLeast(24) / 60f)) - 1.dp
            val w = colW / p.cols
            GridEvent(vm, i, compact, narrow = w * p.span < 60.dp, modifier = Modifier.offset(x = w * p.col, y = top).width(w * p.span - 2.dp).height(height))
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
private fun GridEvent(vm: AppViewModel, i: Instance, compact: Boolean, narrow: Boolean, modifier: Modifier) {
    val fmt = LocalFmt.current
    val dark = isDark()
    val outlined = i.tentative || i.declined
    val fill = eventFill(i.color, dark, i.dimmed())
    val fg = if (outlined) Color(i.color) else onEventColor(Color(i.color))
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier.clip(shape)
            .then(if (outlined) Modifier.background(MaterialTheme.colorScheme.surface).androidxBorder(fill, shape) else Modifier.background(fill))
            .androidxBorder(MaterialTheme.colorScheme.surface, shape)
            .clickable { vm.open(i) }
            .padding(horizontal = if (compact) 3.dp else 6.dp, vertical = 2.dp)
            .testTag("event"),
    ) {
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
