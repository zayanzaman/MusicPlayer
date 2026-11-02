package com.zxn.musicplayer.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode(val label: String) { SYSTEM("Follow device"), LIGHT("Light"), DARK("Dark") }

/** Appearance settings: light/dark/follow-device. */
object ThemePrefs {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        mode = runCatching { ThemeMode.valueOf(p.getString("theme_mode", ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setMode(context: Context, value: ThemeMode) { mode = value; prefs(context).edit().putString("theme_mode", value.name).apply() }
}

// Fallback when dynamic color is off / unavailable (Android 11): clean black & white, no accent color.
internal val MonoDark = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    primaryContainer = Color(0xFF2C2C2E), onPrimaryContainer = Color.White,
    secondary = Color(0xFF8E8E93), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFB0B0B5),
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF9A9AA0),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF101012),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF48484A), outlineVariant = Color(0xFF38383A),
)

internal val MonoLight = lightColorScheme(
    primary = Color.Black, onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E9EE), onPrimaryContainer = Color.Black,
    secondary = Color(0xFF6E6E73), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9EE), onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF48484A),
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF2F2F7), surfaceContainerHigh = Color(0xFFE9E9EE), surfaceContainerHighest = Color(0xFFDCDCE1),
    outline = Color(0xFFC6C6CC), outlineVariant = Color(0xFFE0E0E5),
)

private val AppleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun isAppDark(): Boolean = when (ThemePrefs.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Forces the plain black/white scheme (ignores wallpaper colors). Used for utility UI like speed/pitch, timers, equalizer, settings. */
@Composable
fun MonoScope(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isAppDark()) MonoDark else MonoLight, shapes = AppleShapes, typography = AppTypography, content = content)
}

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    val dark = isAppDark()
    val scheme = if (dark) MonoDark else MonoLight
    // keep status/navigation bar icon colors right when the in-app theme differs from the system one
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, shapes = AppleShapes, typography = AppTypography, content = content)
}
