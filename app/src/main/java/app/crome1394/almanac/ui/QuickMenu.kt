package app.crome1394.almanac.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.crome1394.almanac.data.ViewMode

/**
 * The floating "⋮" button: opens a column of actions just above the thumb (create, the five
 * views, refresh, today), so the app can be used one-handed.
 */
@Composable
fun BoxScope.QuickMenu(vm: AppViewModel, bottomOffset: Dp = 0.dp) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }

    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = false })
    }

    Column(
        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp + bottomOffset),
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            open,
            enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(1f, 1f), initialScale = 0.85f) + slideInVertically { it / 6 },
            exit = fadeOut() + scaleOut(transformOrigin = TransformOrigin(1f, 1f), targetScale = 0.85f) + slideOutVertically { it / 6 },
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp).testTag("quick-menu")) {
                fun go(block: () -> Unit) { open = false; block() }
                Item("Create event", Icons.Outlined.Add, tag = "quick-create", strong = true) { go { vm.newEvent() } }
                listOf(
                    Triple(ViewMode.SCHEDULE, "Schedule", Icons.AutoMirrored.Outlined.ViewList),
                    Triple(ViewMode.DAY, "Day", Icons.Outlined.CalendarViewDay),
                    Triple(ViewMode.THREE_DAY, "3 days", Icons.Outlined.ViewColumn),
                    Triple(ViewMode.WEEK, "Week", Icons.Outlined.CalendarViewWeek),
                    Triple(ViewMode.MONTH, "Month", Icons.Outlined.CalendarMonth),
                ).forEach { (m, label, icon) ->
                    Item(label, icon, tag = "quick-${m.name.lowercase()}", selected = settings.viewMode == m) { go { vm.setView(m) } }
                }
                Item("Refresh", Icons.Outlined.Refresh, tag = "quick-refresh") { go { vm.refresh(); vm.say("Syncing…") } }
                Item("Jump to today", Icons.Outlined.Today, tag = "quick-today") { go { vm.goToday() } }
            }
        }
        FloatingActionButton(
            onClick = { open = !open },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("new-event"),
        ) { Icon(if (open) Icons.Filled.Close else Icons.Filled.MoreVert, if (open) "Close menu" else "Menu", Modifier.size(28.dp)) }
    }
}

@Composable
private fun Item(label: String, icon: ImageVector, tag: String, selected: Boolean = false, strong: Boolean = false, onClick: () -> Unit) {
    val container = when {
        strong -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        strong -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = container, contentColor = content,
        shadowElevation = 3.dp, modifier = Modifier.testTag(tag)) {
        Row(Modifier.padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
