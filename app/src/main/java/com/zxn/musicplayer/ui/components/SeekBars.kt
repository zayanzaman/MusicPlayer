package com.zxn.musicplayer.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class SeekStyle(val label: String, val description: String) {
    WAVY("Wavy", "Animated wave while playing"),
    LINE("Classic", "Thin line with a round handle"),
    PILL("Pill", "Thick rounded bar that swells as you drag"),
    BARS("Waveform", "Vertical bars that pulse with playback"),
    GLOW("Glow", "Gradient bar with a glowing handle"),
}

/** Which seek bar look is used on the Now Playing screen (Settings → Appearance). */
object SeekPrefs {
    var style by mutableStateOf(SeekStyle.WAVY)
        private set

    fun load(context: Context) {
        val v = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getString("seek_style", SeekStyle.WAVY.name)
        style = runCatching { SeekStyle.valueOf(v!!) }.getOrDefault(SeekStyle.WAVY)
    }

    fun set(context: Context, value: SeekStyle) {
        style = value
        context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).edit().putString("seek_style", value.name).apply()
    }
}

@Composable
fun StyledSeekBar(
    style: SeekStyle,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onScrub: ((Long) -> Unit)? = null,
) {
    if (style == SeekStyle.WAVY) {
        WavySeekBar(positionMs, durationMs, isPlaying, onSeek, modifier, onScrub)
        return
    }
    val duration by rememberUpdatedState(durationMs)
    val seek by rememberUpdatedState(onSeek)
    val scrub by rememberUpdatedState(onScrub)
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val dragging = drag != null

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "pulse",
    )
    val pillH by animateDpAsState(if (dragging) 18.dp else 10.dp, label = "pillH")

    val active = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.tertiary
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        modifier.fillMaxWidth().height(44.dp)
            .semantics { contentDescription = "Seek bar" }
            .pointerInput(Unit) {
                detectTapGestures { o -> if (duration > 0) seek(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong()) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { if (duration > 0) seek((it * duration).toLong()) }; drag = null },
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
        val px = w * fraction
        when (style) {
            SeekStyle.LINE -> {
                val t = 3.dp.toPx()
                drawLine(inactive, Offset(0f, cy), Offset(w, cy), t, StrokeCap.Round)
                drawLine(active, Offset(0f, cy), Offset(px, cy), t, StrokeCap.Round)
                drawCircle(active, (if (dragging) 10.dp else 7.dp).toPx(), Offset(px, cy))
            }
            SeekStyle.PILL -> {
                val h = pillH.toPx()
                val r = CornerRadius(h / 2)
                drawRoundRect(inactive, Offset(0f, cy - h / 2), Size(w, h), r)
                if (px > 0f) drawRoundRect(active, Offset(0f, cy - h / 2), Size(px.coerceAtLeast(h), h), r)
            }
            SeekStyle.BARS -> {
                val bw = 3.dp.toPx(); val gap = 3.dp.toPx()
                val n = (w / (bw + gap)).toInt().coerceAtLeast(1)
                val maxH = size.height * 0.9f
                for (i in 0 until n) {
                    val pos = i.toFloat() / n
                    var hh = (0.30f + 0.70f * abs(sin(i * 1.7f) * cos(i * 0.55f + 1f))) * maxH
                    if (isPlaying && abs(pos - fraction) < 0.05f) hh *= 1f + 0.25f * sin(pulse + i)
                    val x = i * (bw + gap)
                    drawRoundRect(
                        if (pos <= fraction) active else inactive,
                        Offset(x, cy - hh / 2), Size(bw, hh), CornerRadius(bw / 2),
                    )
                }
            }
            SeekStyle.GLOW -> {
                val t = 6.dp.toPx()
                drawLine(inactive, Offset(0f, cy), Offset(w, cy), t, StrokeCap.Round)
                if (px > 0f) drawLine(Brush.horizontalGradient(listOf(active, second), endX = w.coerceAtLeast(1f)), Offset(0f, cy), Offset(px, cy), t, StrokeCap.Round)
                drawCircle(active.copy(alpha = 0.22f), (if (dragging) 22.dp else 16.dp).toPx(), Offset(px, cy))
                drawCircle(active.copy(alpha = 0.40f), 12.dp.toPx(), Offset(px, cy))
                drawCircle(active, 7.dp.toPx(), Offset(px, cy))
            }
            else -> Unit
        }
    }
}
