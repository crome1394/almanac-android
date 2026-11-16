package app.crome1394.almanac.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.crome1394.almanac.data.Permissions

val LocalFmt = staticCompositionLocalOf<Fmt> { error("no Fmt") }
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("no snackbar") }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AlmanacRoot(vm: AppViewModel) {
    val ctx = LocalContext.current
    val fmt = remember { Fmt(ctx) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        vm.undoMessages.collect { m ->
            launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(m.text, actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) m.undo()
            }
        }
    }

    val settings by vm.settings.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalFmt provides fmt, LocalSnackbar provides snackbar, LocalDimPast provides settings.dimPast) {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize()) {
                if (!vm.hasCalendarPermission) {
                    Welcome(vm)
                } else {
                    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
                    AnimatedContent(
                        targetState = vm.screen,
                        transitionSpec = {
                            (fadeIn() + slideInVertically { it / 12 }) togetherWith fadeOut()
                        },
                        contentKey = { it::class to (it as? Screen.Detail)?.eventId },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            Screen.Main -> MainScreen(vm)
                            is Screen.Detail -> EventDetailScreen(vm, s)
                            is Screen.Edit -> EditEventScreen(vm, s.request)
                            Screen.Search -> SearchScreen(vm)
                            Screen.Settings -> SettingsScreen(vm)
                            is Screen.Import -> ImportScreen(vm, s.events)
                        }
                    }
                    EventActionsHost(vm)
                    NotificationPermissionAsk()
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 72.dp))
            }
        }
    }
}

@Composable
private fun Welcome(vm: AppViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsChanged()
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Welcome to Almanac", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Almanac shows and edits the calendars on this phone, including ones synced by DAVx5 " +
                "from your own server. It needs access to your calendars to do that. " +
                "Nothing leaves the phone except through your own sync app.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { launcher.launch(Permissions.CALENDAR) }, Modifier.semantics { }) { Text("Allow calendar access") }
    }
}

/** Asks once for notification permission so reminders can show (Android 13+). */
@Composable
private fun NotificationPermissionAsk() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val sp = ctx.getSharedPreferences("state", 0)
        if (!Permissions.hasNotifications(ctx) && !sp.getBoolean("askedNotif", false)) {
            sp.edit().putBoolean("askedNotif", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
