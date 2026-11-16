package app.crome1394.almanac.ui

import android.provider.CalendarContract.Reminders
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.crome1394.almanac.data.CalendarInfo
import app.crome1394.almanac.data.Freq
import app.crome1394.almanac.data.RRule
import app.crome1394.almanac.data.RecurrenceText
import app.crome1394.almanac.data.Reminder
import app.crome1394.almanac.data.WeekdayNum
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun <T> PickDialog(title: String, options: List<T>, label: (T) -> String, selected: T?, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    Row(Modifier.fillMaxWidth().selectable(o == selected) { onPick(o) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(o == selected, { onPick(o) })
                        Text(label(o))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }, Modifier.testTag("date-ok")) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePick(initial: LocalTime, is24: Boolean, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }, Modifier.testTag("time-ok")) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ZonePick(current: ZoneId, onDismiss: () -> Unit, onPick: (ZoneId) -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = remember {
        ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .map { ZoneId.of(it) }.sortedBy { it.rules.getOffset(Instant.now()).totalSeconds }
    }
    val shown = remember(q) { all.filter { q.isBlank() || it.id.replace('_', ' ').contains(q.trim(), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time zone") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    item {
                        val sys = ZoneId.systemDefault()
                        Text("Phone's time zone: ${zoneLabel(sys)}", Modifier.fillMaxWidth().clickable { onPick(sys) }.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.primary)
                    }
                    items(shown, key = { it.id }) { z ->
                        Text("${z.id.replace('_', ' ')}  (GMT${z.rules.getOffset(Instant.now()).id.replace("Z", "")})",
                            Modifier.fillMaxWidth().clickable { onPick(z) }.padding(vertical = 10.dp),
                            color = if (z == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Google's repeat menu: quick choices for the start date, then "Custom…". */
@Composable
fun RepeatDialog(current: String?, start: LocalDate, allDay: Boolean, zone: ZoneId, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    if (custom) { CustomRepeatDialog(RRule.parse(current), start, allDay, zone, onDismiss, onPick); return }
    val presets = RRule.presets(start)
    val cur = RRule.parse(current)
    val options: List<RRule?> = buildList {
        add(null)
        addAll(presets)
        if (cur != null && presets.none { it.format() == cur.format() }) add(cur)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    val sel = o?.format() == cur?.format()
                    Row(Modifier.fillMaxWidth().selectable(sel) { onPick(o?.format()) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sel, { onPick(o?.format()) })
                        Text(o?.let { RecurrenceText.describe(it, start, zone) } ?: "Does not repeat")
                    }
                }
                Text("Custom…", Modifier.fillMaxWidth().clickable { custom = true }.padding(start = 12.dp, top = 14.dp, bottom = 14.dp).testTag("repeat-custom"))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomRepeatDialog(initial: RRule?, start: LocalDate, allDay: Boolean, zone: ZoneId, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val locale = Locale.getDefault()
    var freq by remember { mutableStateOf(initial?.freq ?: Freq.WEEKLY) }
    var interval by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var days by remember { mutableStateOf(initial?.byDay?.filter { it.ordinal == 0 }?.map { it.day }?.toSet()?.ifEmpty { null } ?: setOf(start.dayOfWeek)) }
    var monthlyByWeekday by remember { mutableStateOf(initial?.freq == Freq.MONTHLY && initial.byDay.any { it.ordinal != 0 }) }
    var ends by remember { mutableStateOf(when { initial?.count != null -> "count"; initial?.until != null -> "until"; else -> "never" }) }
    var until by remember { mutableStateOf(initial?.untilDate(zone) ?: start.plusMonths(1)) }
    var count by remember { mutableStateOf((initial?.count ?: 13).toString()) }
    var pickUntil by remember { mutableStateOf(false) }
    var freqMenu by remember { mutableStateOf(false) }
    val n = interval.toIntOrNull()?.coerceIn(1, 999) ?: 1

    fun build(): RRule {
        val nth = RRule.weekOfMonth(start).coerceAtMost(4)
        val byDay = when (freq) {
            Freq.WEEKLY -> days.sortedBy { (it.value + 6) % 7 }.map { WeekdayNum(0, it) }
            Freq.MONTHLY -> if (monthlyByWeekday) {
                // Keep "last" if that is what the series used; otherwise the nth weekday of the start.
                val last = initial?.byDay?.firstOrNull()?.ordinal == -1
                listOf(WeekdayNum(if (last) -1 else nth, start.dayOfWeek))
            } else emptyList()
            else -> emptyList()
        }
        return RRule(
            freq = freq, interval = n, byDay = byDay,
            byMonthDay = if (freq == Freq.MONTHLY && !monthlyByWeekday) listOf(start.dayOfMonth) else emptyList(),
            count = if (ends == "count") count.toIntOrNull()?.coerceIn(1, 9999) ?: 1 else null,
            until = if (ends == "until") RRule.formatUntil(until, allDay, zone) else null,
            // Parts this dialog does not show are kept only if the frequency is unchanged.
            other = if (initial?.freq == freq) initial.other else emptyList(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom recurrence") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Repeats every", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(3) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(72.dp))
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(onClick = { freqMenu = true }) { Text(freqLabel(freq, n)) }
                        DropdownMenu(freqMenu, { freqMenu = false }) {
                            Freq.entries.forEach { f -> DropdownMenuItem(text = { Text(freqLabel(f, n)) }, onClick = { freq = f; freqMenu = false }) }
                        }
                    }
                }
                if (freq == Freq.WEEKLY) {
                    Spacer(Modifier.height(12.dp))
                    Text("Repeats on", style = MaterialTheme.typography.labelLarge)
                    val order = (0 until 7).map { DayOfWeek.MONDAY.plus(it.toLong()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        order.forEach { day ->
                            FilterChip(selected = day in days, onClick = {
                                days = if (day in days && days.size > 1) days - day else days + day
                            }, label = { Text(day.getDisplayName(TextStyle.NARROW, locale)) })
                        }
                    }
                }
                if (freq == Freq.MONTHLY) {
                    Spacer(Modifier.height(12.dp))
                    val nthRule = RRule(Freq.MONTHLY, byDay = listOf(WeekdayNum(RRule.weekOfMonth(start).coerceAtMost(4), start.dayOfWeek)))
                    Radio("Monthly on day ${start.dayOfMonth}", !monthlyByWeekday) { monthlyByWeekday = false }
                    Radio(RecurrenceText.describe(nthRule, start), monthlyByWeekday) { monthlyByWeekday = true }
                }
                Spacer(Modifier.height(12.dp))
                Text("Ends", style = MaterialTheme.typography.labelLarge)
                Radio("Never", ends == "never") { ends = "never" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Radio("On", ends == "until") { ends = "until" }
                    TextButton(onClick = { ends = "until"; pickUntil = true }) { Text(LocalFmt.current.dayMedium(until)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Radio("After", ends == "count") { ends = "count" }
                    OutlinedTextField(count, { count = it.filter(Char::isDigit).take(4); ends = "count" }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("occurrences")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(build().format()) }, Modifier.testTag("custom-done")) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickUntil) DatePick(until, { pickUntil = false }) { until = if (it < start) start else it; pickUntil = false }
}

private fun freqLabel(f: Freq, n: Int) = when (f) {
    Freq.DAILY -> if (n == 1) "day" else "days"
    Freq.WEEKLY -> if (n == 1) "week" else "weeks"
    Freq.MONTHLY -> if (n == 1) "month" else "months"
    Freq.YEARLY -> if (n == 1) "year" else "years"
}

@Composable
private fun Radio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.selectable(selected, onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(label)
    }
}

/** Google's notification choices, then a custom one. */
@Composable
fun ReminderDialog(allDay: Boolean, cal: CalendarInfo?, onDismiss: () -> Unit, onPick: (Reminder) -> Unit) {
    val fmt = LocalFmt.current
    var custom by remember { mutableStateOf(false) }
    val alert = if (cal == null || Reminders.METHOD_ALERT in cal.allowedReminders) Reminders.METHOD_ALERT else Reminders.METHOD_DEFAULT
    val email = cal != null && Reminders.METHOD_EMAIL in cal.allowedReminders
    if (custom) { CustomReminderDialog(allDay, alert, email, onDismiss, onPick); return }
    val presets = if (allDay) listOf(Fmt.allDayMinutes(0, LocalTime.of(9, 0)), Fmt.allDayMinutes(1, LocalTime.of(9, 0)),
        Fmt.allDayMinutes(2, LocalTime.of(9, 0)), Fmt.allDayMinutes(7, LocalTime.of(9, 0)))
    else listOf(0, 5, 10, 15, 30, 60, 1440)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                presets.forEach { m ->
                    Text(fmt.reminder(m, alert, allDay), Modifier.fillMaxWidth().clickable { onPick(Reminder(m, alert)) }.padding(vertical = 12.dp))
                }
                Text("Custom…", Modifier.fillMaxWidth().clickable { custom = true }.padding(vertical = 12.dp).testTag("reminder-custom"))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomReminderDialog(allDay: Boolean, alert: Int, emailAllowed: Boolean, onDismiss: () -> Unit, onPick: (Reminder) -> Unit) {
    val fmt = LocalFmt.current
    var amount by remember { mutableStateOf(if (allDay) "1" else "10") }
    var unit by remember { mutableStateOf(if (allDay) 1440 else 1) }
    var method by remember { mutableStateOf(alert) }
    var at by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var pickTime by remember { mutableStateOf(false) }
    val n = amount.toIntOrNull()?.coerceIn(0, 9999) ?: 0
    val units = if (allDay) listOf(1440 to "days", 10_080 to "weeks") else listOf(1 to "minutes", 60 to "hours", 1440 to "days", 10_080 to "weeks")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom notification") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(4) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("before")
                }
                units.forEach { (u, label) -> Radio(label, unit == u) { unit = u } }
                if (allDay) TextButton(onClick = { pickTime = true }) { Text("at ${fmt.time(at)}") }
                if (emailAllowed) {
                    Spacer(Modifier.height(8.dp))
                    Radio("As notification", method != Reminders.METHOD_EMAIL) { method = alert }
                    Radio("As email", method == Reminders.METHOD_EMAIL) { method = Reminders.METHOD_EMAIL }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = if (allDay) Fmt.allDayMinutes(n * unit / 1440, at) else n * unit
                onPick(Reminder(minutes.coerceAtMost(40_320 * 2), method))
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickTime) TimePick(at, fmt.is24, { pickTime = false }) { at = it; pickTime = false }
}
