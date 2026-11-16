package app.crome1394.almanac.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Screens drawn as a card over the one below instead of replacing it. */
val Screen.isOverlay: Boolean get() = this is Screen.Search || this is Screen.Edit || this is Screen.Detail

/** Heights of the event and editor cards as a share of the screen, kept while the app runs. */
private val editorCardShare = mutableFloatStateOf(0.62f)
private val detailCardShare = mutableFloatStateOf(0.55f)

/**
 * Draws [s] as a card: search floats at the top (or bottom, with the toolbar there); an
 * event, and the editor, open in a card from the bottom whose height you drag by its handle.
 */
@Composable
fun OverlayHost(vm: AppViewModel, s: Screen?) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = s != null
    // The last overlay stays drawn while it animates away.
    val last = remember { Holder<Screen?>(null) }
    if (s != null) last.value = s
    val shown = s ?: last.value ?: return
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val bottomBar = vm.settings.value.toolbarAtBottom

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            // Tapping outside the card is the same as pressing Back (the editor asks before discarding).
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { back?.onBackPressed() })
        }
        when (shown) {
            Screen.Search -> {
                val fromBottom = bottomBar
                AnimatedVisibility(
                    visible,
                    enter = fadeIn() + slideInVertically { if (fromBottom) it / 4 else -it / 4 },
                    exit = fadeOut() + slideOutVertically { if (fromBottom) it / 4 else -it / 4 },
                    modifier = Modifier.align(if (fromBottom) Alignment.BottomCenter else Alignment.TopCenter),
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, shadowElevation = 8.dp,
                        modifier = Modifier.safeDrawingPadding().padding(horizontal = 8.dp, vertical = 8.dp)
                            .fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8f).dp).testTag("search-card"),
                    ) { SearchCard(vm, fieldAtBottom = fromBottom) }
                }
            }
            is Screen.Edit, is Screen.Detail -> {
                AnimatedVisibility(
                    visible, enter = slideInVertically { it }, exit = slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    if (shown is Screen.Edit) ResizableCard(editorCardShare, "editor-card") { EditEventScreen(vm, shown.request, inCard = true) }
                    else if (shown is Screen.Detail) ResizableCard(detailCardShare, "detail-card") { EventDetailScreen(vm, shown, inCard = true) }
                }
            }
            else -> {}
        }
    }
}

private class Holder<T>(var value: T)

/** A card anchored to the bottom; drag its handle to make it taller or shorter ([share] of the screen). */
@Composable
private fun ResizableCard(share: androidx.compose.runtime.MutableFloatState, tag: String, content: @Composable () -> Unit) {
    var editorCardShare by share
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding().windowInsetsPadding(WindowInsets.ime)) {
        val maxH = maxHeight
        val density = androidx.compose.ui.platform.LocalDensity.current
        val h = (screenH * editorCardShare).coerceAtMost(maxH)
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tonalElevation = 1.dp, shadowElevation = 12.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(h).testTag(tag),
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Box(
                    Modifier.fillMaxWidth().height(22.dp).testTag("card-handle").pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = {
                            // Near the top snaps to full height.
                            if (editorCardShare > 0.9f) editorCardShare = 1f
                        }) { c, dy ->
                            c.consume()
                            editorCardShare = (editorCardShare - dy / with(density) { screenH.toPx() }).coerceIn(0.35f, 1f)
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

