package com.zxn.musicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class Feature(val icon: ImageVector, val title: String, val text: String)

private val features = listOf(
    Feature(Icons.AutoMirrored.Filled.QueueMusic, "Playlists & queue", "Build playlists, play next, add to queue and drag to reorder."),
    Feature(Icons.Filled.Equalizer, "Equalizer & sound", "Presets, bass boost and virtualizer to tune the sound."),
    Feature(Icons.Filled.Speed, "Speed & pitch", "Slow songs down or speed them up."),
    Feature(Icons.Filled.Bedtime, "Sleep timer", "Fade out after a set time or at the end of the song."),
    Feature(Icons.Filled.AutoFixHigh, "Tidy tags", "Optionally fix titles, artists and albums, with your approval."),
)

/**
 * First-run flow: welcome → privacy → features → permissions. When [startAtPermissions] is true (already onboarded but
 * access was revoked) it opens directly on the permission page, which replaces the old plain "access needed" screen.
 */
@Composable
fun OnboardingScreen(
    startAtPermissions: Boolean,
    permissionDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val last = 4
    val pager = rememberPagerState(initialPage = if (startAtPermissions) last else 0) { last + 1 }
    val scope = rememberCoroutineScope()
    // Onboarding always uses the brand red, whatever the wallpaper colors are.
    val red = Color(0xFFFA2D48)
    val base = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = base.copy(primary = red, onPrimary = Color.White, primaryContainer = red.copy(alpha = 0.2f), onPrimaryContainer = red), shapes = MaterialTheme.shapes, typography = MaterialTheme.typography) {
    val cs = MaterialTheme.colorScheme
    val tint = red

    Box(Modifier.fillMaxSize().background(cs.background)) {
        // soft colored glow that changes per page
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent), endY = 1400f)
            )
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Spacer(Modifier.height(24.dp))

            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                // gentle parallax: content scales/fades as it leaves the center
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp).graphicsLayer {
                        val f = 1f - abs(offset).coerceIn(0f, 1f)
                        alpha = 0.3f + 0.7f * f
                        scaleX = 0.92f + 0.08f * f; scaleY = scaleX
                        translationX = offset * 60f
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> Hero(Icons.Filled.MusicNote, tint, "Your music,\nbeautifully played", "A fast, simple player for the songs already on your phone.")
                        1 -> Hero(Icons.Filled.Lock, tint, "Private by design", "No account, no ads, no tracking.") {
                            Pill(Icons.Filled.WifiOff, "Works fully offline")
                        }
                        2 -> Hero(Icons.Filled.LibraryMusic, tint, "Everything you expect", "Build playlists, queue songs, and organize your music.")
                        3 -> ThemePage()
                        else -> PermissionPage(tint, permissionDenied)
                    }
                }
            }

            // page dots
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(last + 1) { i ->
                    val w by animateDpAsState(if (i == pager.currentPage) 24.dp else 8.dp, spring(), label = "dot")
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(8.dp).width(w).clip(CircleShape)
                            .background(if (i == pager.currentPage) tint else cs.onSurface.copy(alpha = 0.2f))
                    )
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
                if (pager.currentPage < last) {
                    Spacer(Modifier.height(54.dp)) // swipe to continue; keeps the layout from jumping on the last page
                } else {
                    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(if (permissionDenied) "Try again" else "Allow & get started", style = MaterialTheme.typography.titleMedium)
                    }
                    if (permissionDenied) TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Open app settings") }
                }
            }
        }
    }
    }
}

@Composable
private fun GlowIcon(icon: ImageVector, tint: Color, size: Int = 132) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp))
            .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size((size * 0.5f).dp), tint = Color.White) }
}

@Composable
private fun ColumnScope.Hero(icon: ImageVector, tint: Color, title: String, text: String, extra: @Composable () -> Unit = {}) {
    Spacer(Modifier.weight(1f))
    GlowIcon(icon, tint, 160)
    Spacer(Modifier.height(36.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    extra()
    Spacer(Modifier.weight(1.3f))
}

@Composable
private fun Pill(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ColumnScope.ThemePage() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Spacer(Modifier.weight(1f))
    Text("Choose your look", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text("You can change this any time in Settings.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(32.dp))
    com.zxn.musicplayer.ui.components.ThemeChooser(com.zxn.musicplayer.ui.theme.ThemePrefs.mode, { com.zxn.musicplayer.ui.theme.ThemePrefs.setMode(ctx, it) })
    Spacer(Modifier.weight(1.3f))
}

@Composable
private fun ColumnScope.FeaturePage(tint: Color) {
    Spacer(Modifier.height(8.dp))
    Text("Everything you expect", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        features.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(f.icon, null, tint = tint)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(f.title, style = MaterialTheme.typography.titleSmall)
                    Text(f.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PermissionPage(tint: Color, denied: Boolean) {
    Spacer(Modifier.height(8.dp))
    GlowIcon(Icons.Filled.LibraryMusic, tint, 96)
    Spacer(Modifier.height(20.dp))
    Text("Let's find your music", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    PermissionCard(Icons.Filled.LibraryMusic, "Music & audio", "Required. Lets the app list and play the songs on this device.", tint)
    Spacer(Modifier.height(12.dp))
    PermissionCard(Icons.Filled.Notifications, "Notifications", "Optional. Shows playback controls on your lock screen and notification shade.", tint)
    if (denied) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Music access was not granted, so there's nothing to play yet.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.weight(1f))
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, text: String, tint: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = tint)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
