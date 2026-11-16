package app.crome1394.almanac.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.crome1394.almanac.MainActivity
import app.crome1394.almanac.R
import app.crome1394.almanac.app
import app.crome1394.almanac.data.Instance
import app.crome1394.almanac.data.Permissions
import app.crome1394.almanac.data.startOfWeek
import app.crome1394.almanac.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    /** Redraws all widgets, coalescing bursts of changes (a sync writes many rows). */
    fun refresh(c: Context) {
        val ctx = c.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(1500)
            runCatching { ScheduleWidget().updateAll(ctx) }
            runCatching { MonthWidget().updateAll(ctx) }
        }
    }

    fun openApp(c: Context) = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openEvent(c: Context, i: Instance) = Intent(c, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, i.eventId)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, i.begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, i.end)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun openDay(c: Context, d: LocalDate) = Intent(c, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("content://${CalendarContract.AUTHORITY}/time/${d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    fun newEvent(c: Context) = Intent(c, MainActivity::class.java).apply {
        action = Intent.ACTION_INSERT
        data = CalendarContract.Events.CONTENT_URI
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    suspend fun load(c: Context, from: LocalDate, to: LocalDate): List<Instance>? {
        if (!Permissions.hasCalendar(c)) return null
        return runCatching { c.app.repo.instances(from, to, c.app.prefs.current.hideDeclined) }.getOrNull()
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

private sealed interface WRow
private data class WDay(val date: LocalDate) : WRow
private data class WEvent(val i: Instance) : WRow

/** Home-screen agenda: today and the next weeks, as in Google's Schedule widget. */
class ScheduleWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val events = Widgets.load(context, today, today.plusDays(30))
        val fmt = Fmt(context)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val rows = buildList<WRow> {
            add(WDay(today))
            val byDay = events.orEmpty().filter { it.end > now || it.allDay }
                .flatMap { i -> generateSequence(maxOf(i.startDate(zone), today)) { it.plusDays(1) }.takeWhile { it <= i.lastDate(zone) && it <= today.plusDays(30) }.map { it to i } }
                .groupBy({ it.first }, { it.second }).toSortedMap()
            byDay.forEach { (d, list) ->
                if (d != today) add(WDay(d))
                list.sortedWith(compareBy<Instance> { !it.allDay }.thenBy { it.begin }).forEach { add(WEvent(it)) }
            }
        }
        provideContent {
            GlanceTheme {
                Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.dayLong(today), GlanceModifier.defaultWeight().clickable(actionStartActivity(Widgets.openApp(context))),
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                        Box(GlanceModifier.size(32.dp).cornerRadius(16.dp).background(GlanceTheme.colors.primaryContainer)
                            .clickable(actionStartActivity(Widgets.newEvent(context))), contentAlignment = Alignment.Center) {
                            Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 20.sp))
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    when {
                        events == null -> Text("Open Almanac to allow calendar access",
                            GlanceModifier.clickable(actionStartActivity(Widgets.openApp(context))),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                        else -> LazyColumn {
                            items(rows) { r ->
                                when (r) {
                                    is WDay -> Text(
                                        if (r.date == today) "Today" else fmt.dayLong(r.date),
                                        GlanceModifier.padding(top = 8.dp, bottom = 2.dp).clickable(actionStartActivity(Widgets.openDay(context, r.date))),
                                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                    )
                                    is WEvent -> EventRow(context, fmt, r.i)
                                }
                            }
                            if (rows.size == 1) item {
                                Text("Nothing planned", GlanceModifier.padding(top = 4.dp),
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EventRow(context: Context, fmt: Fmt, i: Instance) {
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(actionStartActivity(Widgets.openEvent(context, i))),
            verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.width(4.dp).height(32.dp).cornerRadius(2.dp).background(ColorProvider(Color(i.color)))) {}
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(i.title.ifBlank { "(No title)" }, maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
                Text(fmt.rangeShort(i.begin, i.end, i.allDay), maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            }
        }
    }
}

/** Home-screen month: the current month with dots on busy days. */
class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val first = context.app.prefs.current.firstDayOfWeek()
        val start = month.atDay(1).startOfWeek(first)
        val weeks = generateSequence(start) { it.plusWeeks(1) }.takeWhile { it <= month.atEndOfMonth() }.toList()
        val events = Widgets.load(context, weeks.first(), weeks.last().plusDays(6)).orEmpty()
        val zone = ZoneId.systemDefault()
        val busy = events.flatMap { i ->
            generateSequence(i.startDate(zone)) { it.plusDays(1) }.takeWhile { it <= i.lastDate(zone) }.take(62).toList()
        }.toSet()
        val fmt = Fmt(context)
        provideContent {
            GlanceTheme {
                Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(10.dp)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.monthYear(today), GlanceModifier.defaultWeight().clickable(actionStartActivity(Widgets.openApp(context))),
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                        Box(GlanceModifier.size(28.dp).cornerRadius(14.dp).background(GlanceTheme.colors.primaryContainer)
                            .clickable(actionStartActivity(Widgets.newEvent(context))), contentAlignment = Alignment.Center) {
                            Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 18.sp))
                        }
                    }
                    Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                        (0 until 7).forEach { k ->
                            Text(fmt.weekdayNarrow(start.plusDays(k.toLong())), GlanceModifier.defaultWeight(),
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center))
                        }
                    }
                    weeks.forEach { ws ->
                        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            (0 until 7).forEach { k ->
                                val d = ws.plusDays(k.toLong())
                                val isToday = d == today
                                Column(GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity(Widgets.openDay(context, d))),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                                    Box(GlanceModifier.size(24.dp).cornerRadius(12.dp)
                                        .background(if (isToday) GlanceTheme.colors.primary else ColorProvider(Color.Transparent)),
                                        contentAlignment = Alignment.Center) {
                                        Text(d.dayOfMonth.toString(), style = TextStyle(
                                            color = when {
                                                isToday -> GlanceTheme.colors.onPrimary
                                                d.month != month.month -> GlanceTheme.colors.outline
                                                else -> GlanceTheme.colors.onSurface
                                            }, fontSize = 12.sp, textAlign = TextAlign.Center))
                                    }
                                    if (d in busy) Image(ImageProvider(R.drawable.dot), null, GlanceModifier.size(4.dp))
                                    else Spacer(GlanceModifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
