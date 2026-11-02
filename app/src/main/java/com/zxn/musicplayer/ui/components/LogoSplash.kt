package com.zxn.musicplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Brief logo screen shown when the app starts. The logo has its own red background, so it sits on plain
 * white or black depending on the app theme (which follows the device setting until the user picks one).
 */
@Composable
fun LogoSplash(onFinished: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(450)
        leave.animateTo(1f, tween(300))
        onFinished()
    }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - leave.value }
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }, // block touches while visible
        contentAlignment = Alignment.Center,
    ) {
        AppLogo(
            160.dp,
            Modifier.graphicsLayer {
                val s = 0.85f + 0.15f * appear.value - 0.1f * leave.value
                scaleX = s; scaleY = s; alpha = appear.value
            },
        )
    }
}

/** The app logo: a red rounded tile with a white music note (same artwork as the launcher icon). */
@Composable
fun AppLogo(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val red = Color(0xFFFA2D48)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(red, red.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.MusicNote, null, Modifier.size(size * 0.5f), tint = Color.White) }
}
