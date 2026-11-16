package app.crome1394.almanac.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.crome1394.almanac.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondaryContainer = Color(0xFFC2E7FF),
    onSecondaryContainer = Color(0xFF001D35),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    outlineVariant = Color(0xFFDADCE0),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFC2E7FF),
    surface = Color(0xFF131314),
    background = Color(0xFF131314),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainerHigh = Color(0xFF282A2C),
    outlineVariant = Color(0xFF444746),
)

@Composable
fun AlmanacTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Text color that reads on an event's own color, as Google uses on event chips. */
fun onEventColor(c: Color): Color = if (c.luminance() > 0.55f) Color(0xFF1F1F1F) else Color.White

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Google dims past events; in dark mode it also mutes the event colors. */
fun eventFill(color: Int, dark: Boolean, past: Boolean): Color {
    val c = Color(color)
    val base = if (dark) lerpColor(c, Color(0xFF131314), 0.25f) else c
    return if (past) base.copy(alpha = 0.55f) else base
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha,
)
