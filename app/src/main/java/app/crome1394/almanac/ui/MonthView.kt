package app.crome1394.almanac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import app.crome1394.almanac.data.MonthLayout
import java.time.temporal.ChronoUnit
import androidx.compose.foundation.layout.Arrangement
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

    HorizontalPager(pager, Modifier.fillMaxSize().testTag("month"), key = { it }) { page ->
        val month = base.plusMonths((page - center).toLong())
        val weeks = monthWeeks(month, first)
        val gridStart = weeks.first()
        val gridEnd = weeks.last().plusDays(6)
        val events by rememberInstances(vm, gridStart, gridEnd)
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
                    WeekRow(vm, ws, month, events.orEmpty(), Modifier.weight(1f).fillMaxHeight())
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/**
 * One week of the grid: day numbers, then every event as a bar across the days it covers
 * (multi-day events first, then each day's events by time), with "+N" where a day overflows.
 */
@Composable
private fun WeekRow(vm: AppViewModel, weekStart: LocalDate, month: YearMonth, events: List<Instance>, modifier: Modifier) {
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
                    Modifier.weight(1f).fillMaxHeight().clickable { vm.selectedDate = d; vm.setView(ViewMode.DAY) }.testTag("cell-$d"),
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
            EventChip(
                i,
                Modifier.offset(x = cellW * b.start + 1.dp, y = 28.dp + 18.dp * b.lane).width(cellW * (b.end - b.start + 1) - 2.dp),
                small = true, onLongClick = { vm.menuFor = i },
            ) { vm.open(i) }
        }
        week.more.forEach { (col, n) ->
            Text(
                "+$n",
                Modifier.offset(x = cellW * col + 1.dp, y = 28.dp + 18.dp * (lanes - 1).coerceAtLeast(0)).width(cellW - 2.dp)
                    .height(17.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .clickable { vm.selectedDate = weekStart.plusDays(col.toLong()); vm.setView(ViewMode.DAY) }
                    .padding(start = 3.dp),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
