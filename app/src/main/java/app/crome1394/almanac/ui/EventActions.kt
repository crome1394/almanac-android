package app.crome1394.almanac.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Swipe an event to the right to delete it (with Undo). Made hard to trigger by accident while
 * scrolling: the swipe only starts when the finger moves clearly sideways, it has to travel
 * [ARM_SHARE] of the card's width (a haptic buzz marks the point where letting go deletes), a
 * fast flick alone never deletes, and the card stays open for a moment before it goes.
 */
@Composable
fun SwipeToDelete(vm: AppViewModel, i: app.crome1394.almanac.data.Instance, content: @Composable () -> Unit) {
    if (i.isBirthday) { content(); return }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    var armed by remember { mutableStateOf(false) }
    val event by rememberUpdatedState(i)

    Box(
        Modifier.onSizeChanged { width = it.width.coerceAtLeast(1) }.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop * 1.5f
                var total = Offset.Zero
                var swiping = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    // Someone else (the list's scroll, a drag handle, a held card) took the gesture.
                    if (!swiping && c.isConsumed) return@awaitEachGesture
                    val d = c.positionChange()
                    total += d
                    if (!swiping) {
                        if (total.getDistance() < slop) continue
                        // Only a clearly sideways, rightward move starts a swipe.
                        if (total.x <= 0f || abs(total.x) < abs(total.y) * 2.5f) return@awaitEachGesture
                        swiping = true
                    }
                    c.consume()
                    val next = (offset.value + d.x).coerceIn(0f, width.toFloat())
                    scope.launch { offset.snapTo(next) }
                    val nowArmed = next >= width * ARM_SHARE
                    if (nowArmed != armed) {
                        armed = nowArmed
                        haptic.performHapticFeedback(if (nowArmed) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                    }
                }
                if (!swiping) return@awaitEachGesture
                val delete = armed
                armed = false
                scope.launch {
                    if (delete) {
                        offset.animateTo(width.toFloat(), tween(120))
                        delay(220)
                        vm.askDelete(event)
                    }
                    offset.animateTo(0f, spring())
                }
            }
        },
    ) {
        // Only while swiping: past events are drawn translucent and would show it through.
        if (offset.value > 0f) Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(8.dp))
                .background(if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer)
                .padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(Icons.Outlined.Delete, "Delete",
                tint = if (armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(if (armed) 28.dp else 24.dp))
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) { content() }
    }
}

/** How far (share of the card's width) a swipe must go before letting go deletes. */
private const val ARM_SHARE = 0.4f

/** What press-and-hold on an event offers, and the "which occurrences?" question for deletes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventActionsHost(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val fmt = LocalFmt.current
    vm.menuFor?.let { i ->
        val writable = !i.isBirthday && vm.calendars.value.firstOrNull { it.id == i.calendarId }?.writable == true
        ModalBottomSheet(onDismissRequest = { vm.menuFor = null }, modifier = Modifier.testTag("event-menu")) {
            Row(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(i.color)))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(i.title.ifBlank { "(No title)" }, style = MaterialTheme.typography.titleMedium)
                    Text(fmt.detailWhen(i.begin, i.end, i.allDay), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            fun close(then: () -> Unit) { vm.menuFor = null; then() }
            Action(Icons.Outlined.OpenInNew, "Open", "menu-open") { close { vm.open(i) } }
            if (writable) {
                Action(Icons.Outlined.Edit, "Edit", "menu-edit") {
                    close { vm.push(Screen.Edit(EditRequest(eventId = i.eventId, instanceBegin = i.begin))) }
                }
                Action(Icons.Outlined.ContentCopy, "Duplicate", "menu-duplicate") {
                    close {
                        scope.launch {
                            val e = runCatching { vm.repo.event(i.eventId) }.getOrNull() ?: return@launch vm.say("This event no longer exists")
                            vm.push(Screen.Edit(EditRequest(draft = duplicateDraft(vm, e, i.begin, i.end))))
                        }
                    }
                }
                Action(Icons.Outlined.Delete, "Delete", "menu-delete") { close { scope.launch { vm.askDelete(i) } } }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
    vm.moveAsk?.let { r ->
        ScopeDialog("Move recurring event", "OK", onDismiss = { vm.moveAsk = null }) { s ->
            vm.moveAsk = null
            vm.applyMove(r, s)
        }
    }
    vm.deleteAsk?.let { (i, e) ->
        DeleteDialog(recurring = true, onDismiss = { vm.deleteAsk = null }) { s ->
            vm.deleteAsk = null
            vm.deleteWithUndo(e, s, i.begin)
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.testTag(tag).clickable(onClick = onClick),
    )
}
