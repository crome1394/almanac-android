package app.crome1394.almanac.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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

/** Swipe an event to the right to delete it (with Undo), as in Google Calendar's schedule. */
@Composable
fun SwipeToDelete(vm: AppViewModel, i: app.crome1394.almanac.data.Instance, content: @Composable () -> Unit) {
    if (i.isBirthday) { content(); return }
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = false,
        onDismiss = { v ->
            if (v == SwipeToDismissBoxValue.StartToEnd) scope.launch {
                vm.askDelete(i)
                state.reset()
            }
        },
        backgroundContent = {
            // Only while swiping: past events are drawn translucent and would show it through.
            if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.errorContainer)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) { content() }
}

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
