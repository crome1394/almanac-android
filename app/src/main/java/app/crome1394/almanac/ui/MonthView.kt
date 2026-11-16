package app.crome1394.almanac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.VerticalDivider
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
        val byDay = remember(events) {
            val m = HashMap<LocalDate, MutableList<Instance>>()
            events.orEmpty().forEach { i -> datesOf(i, gridStart, gridEnd, zone).forEach { m.getOrPut(it) { mutableListOf() } += i } }
            m.mapValues { (_, l) -> l.sortedWith(compareBy<Instance> { !(it.allDay || it.isMultiDayTimed(zone)) }.thenBy { it.begin }) }
        }
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
                    (0 until 7).forEach { k ->
                        val d = ws.plusDays(k.toLong())
                        if (k > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MonthCell(vm, d, d.month == month.month, byDay[d].orEmpty(), Modifier.weight(1f).fillMaxHeight())
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun MonthCell(vm: AppViewModel, d: LocalDate, inMonth: Boolean, events: List<Instance>, modifier: Modifier) {
    val today = d == LocalDate.now()
    BoxWithConstraints(
        modifier.clickable { vm.selectedDate = d; vm.setView(ViewMode.DAY) }.testTag("cell-$d"),
    ) {
        // Each chip is ~16dp; the date takes ~24dp.
        val fit = ((maxHeight.value - 26f) / 17f).toInt().coerceAtLeast(0)
        Column(Modifier.fillMaxSize().padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 3.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (today) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.dayOfMonth.toString(), fontSize = 12.sp,
                        color = when {
                            today -> MaterialTheme.colorScheme.onPrimary
                            !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            val shown = if (events.size > fit) (fit - 1).coerceAtLeast(0) else events.size
            events.take(shown).forEach { i -> EventChip(i, Modifier.fillMaxWidth(), small = true) { vm.open(i) } }
            if (events.size > shown) {
                Text("+${events.size - shown}", Modifier.padding(start = 3.dp), fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
