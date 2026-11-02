package com.zxn.musicplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pixel-style "squiggly" seek bar: the played part waves while playing and flattens when paused/scrubbing.
 * Tap or drag to seek. [onSeek] is called with the target position when the gesture ends.
 */
@Composable
fun WavySeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onScrub: ((Long) -> Unit)? = null,
) {
    val duration by rememberUpdatedState(durationMs)
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val amplitude by animateFloatAsState(if (isPlaying && drag == null) 1f else 0f, tween(400), label = "amplitude")

    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics { contentDescription = "Seek bar" }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    if (duration > 0) seek(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let { if (duration > 0) seek((it * duration).toLong()) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        drag = f
                        scrub?.invoke((f * duration).toLong())
                    },
                )
            },
    ) {
        val w = size.width
        val cy = size.height / 2
        val strokeW = 4.dp.toPx()
        val amp = 5.dp.toPx() * amplitude
        val waveLen = 28.dp.toPx()
        val px = w * fraction

        // remaining (straight) track
        drawLine(inactive, Offset(px, cy), Offset(w, cy), strokeWidth = strokeW, cap = StrokeCap.Round)

        // played (wavy) part
        if (px > 1f) {
            val path = Path()
            var x = 0f
            path.moveTo(0f, cy + amp * sin(-phase))
            while (x < px) {
                x = (x + 3f).coerceAtMost(px)
                path.lineTo(x, cy + amp * sin((2 * PI * x / waveLen).toFloat() - phase))
            }
            drawPath(path, active, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // thumb
        val thumbW = 4.dp.toPx()
        drawRoundRect(
            active,
            topLeft = Offset(px - thumbW / 2, cy - 11.dp.toPx()),
            size = Size(thumbW, 22.dp.toPx()),
            cornerRadius = CornerRadius(thumbW / 2),
        )
    }
}
