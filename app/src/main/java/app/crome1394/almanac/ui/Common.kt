package app.crome1394.almanac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crome1394.almanac.data.Instance
import app.crome1394.almanac.data.startOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Occurrences for a date range, reloaded when the calendar data changes. Keeps the old list while loading. */
@Composable
fun rememberInstances(vm: AppViewModel, from: LocalDate, to: LocalDate): State<List<Instance>?> {
    val version by vm.dataVersion.collectAsStateWithLifecycle()
    return produceState<List<Instance>?>(null, from, to, version) { value = vm.instances(from, to) }
}

/** Opens an occurrence: birthdays are read-only, so their detail comes from the instance itself. */
fun AppViewModel.open(i: Instance) =
    push(Screen.Detail(i.eventId, i.begin, i.end, if (i.isBirthday) i else null))

fun Instance.isPast(now: Long = System.currentTimeMillis()): Boolean = end <= now && !(allDay && covers(LocalDate.now()))

@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.surface.run { red + green + blue < 1.2f }

/** The month weeks shown in a grid: whole weeks covering [month]. */
fun monthWeeks(month: YearMonth, firstDay: java.time.DayOfWeek): List<LocalDate> {
    val start = month.atDay(1).startOfWeek(firstDay)
    val end = month.atEndOfMonth()
    val out = mutableListOf<LocalDate>()
    var d = start
    while (d <= end) { out += d; d = d.plusWeeks(1) }
    return out
}

/** Small month calendar under the title (tap the month name), with dots on days with events. */
@Composable
fun MiniMonth(vm: AppViewModel, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt = LocalFmt.current
    val base = remember { YearMonth.from(selected) }
    val center = 1200
    val pager = rememberPagerState(initialPage = center) { center * 2 }
    LaunchedEffect(pager.settledPage) {
        val m = base.plusMonths((pager.settledPage - center).toLong())
        if (YearMonth.from(vm.selectedDate) != m) vm.selectedDate = m.atDay(1)
    }
    HorizontalPager(pager, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) { page ->
        val month = base.plusMonths((page - center).toLong())
        val first = settings.firstDayOfWeek()
        val weeks = monthWeeks(month, first)
        val events by rememberInstances(vm, weeks.first(), weeks.last().plusDays(6))
        val busy = remember(events) { events.orEmpty().flatMap { i -> datesOf(i, weeks.first(), weeks.last().plusDays(6)) }.toSet() }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { k ->
                    Text(fmt.weekdayNarrow(weeks.first().plusDays(k.toLong())), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            weeks.forEach { ws ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { k ->
                        val d = ws.plusDays(k.toLong())
                        val isToday = d == LocalDate.now()
                        val isSel = d == selected
                        Box(Modifier.weight(1f).aspectRatio(1.25f).clickable { onPick(d) }, contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(
                                    when { isToday -> MaterialTheme.colorScheme.primary; isSel -> MaterialTheme.colorScheme.primaryContainer; else -> Color.Transparent },
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.dayOfMonth.toString(), fontSize = 13.sp,
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.onPrimary
                                        d.month != month.month -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            if (d in busy && !isToday) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(4.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }
        }
    }
}

fun datesOf(i: Instance, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<LocalDate> {
    var d = maxOf(i.startDate(zone), from)
    val last = minOf(i.lastDate(zone), to)
    val out = mutableListOf<LocalDate>()
    while (d <= last) { out += d; d = d.plusDays(1) }
    return out
}

/** An event block in Google's style: filled with the event color. */
@Composable
fun EventChip(i: Instance, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    val dark = isDark()
    val fill = eventFill(i.color, dark, past = i.isPast())
    val text = onEventColor(Color(i.color))
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(if (small) 4.dp else 6.dp)
    Box(
        modifier.clip(shape)
            .then(
                if (i.tentative || i.declined) Modifier.background(MaterialTheme.colorScheme.surface)
                    .androidxBorder(fill, shape)
                else Modifier.background(fill),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 4.dp else 8.dp, vertical = if (small) 1.dp else 4.dp),
    ) {
        Text(
            i.title.ifBlank { "(No title)" },
            color = if (i.tentative || i.declined) Color(i.color) else text.copy(alpha = if (i.isPast()) 0.85f else 1f),
            fontSize = if (small) 11.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textDecoration = if (i.declined) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
        )
    }
}

fun Modifier.androidxBorder(c: Color, shape: androidx.compose.ui.graphics.Shape) = border(1.dp, c, shape)

@Composable
fun DayNumber(d: LocalDate, big: Boolean = true, onClick: (() -> Unit)? = null) {
    val today = d == LocalDate.now()
    Box(
        Modifier.size(if (big) 36.dp else 28.dp).clip(CircleShape)
            .background(if (today) MaterialTheme.colorScheme.primary else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            d.dayOfMonth.toString(),
            fontSize = if (big) 20.sp else 14.sp,
            color = if (today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
