package com.zxn.musicplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxn.musicplayer.ui.components.ArtImage
import com.zxn.musicplayer.ui.components.glass
import com.zxn.musicplayer.ui.components.rememberArtColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background

@Composable
fun MiniPlayer(player: PlayerViewModel, state: PlayerState, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val artColor = rememberArtColor(state.artUri)
    val tint by animateColorAsState((artColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.22f), label = "miniTint")
    Surface(
        onClick = onOpen,
        shape = shape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).glass(shape, 0.86f),
    ) {
        Column(Modifier.background(tint)) {
            Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(state.artUri, Modifier.size(48.dp), RoundedCornerShape(16.dp), sizePx = 128)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = player::togglePlayPause) {
                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play", Modifier.size(32.dp))
                }
                IconButton(onClick = player::next, enabled = state.hasNext) { Icon(Icons.Filled.SkipNext, "Next") }
            }
            MiniProgress(player, state.durationMs, artColor ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Separate composable so the 4x/second position updates don't recompose the whole mini player. */
@Composable
private fun MiniProgress(player: PlayerViewModel, durationMs: Long, color: Color) {
    val position by player.positionMs.collectAsStateWithLifecycle()
    LinearProgressIndicator(
        progress = { if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().height(3.dp),
        color = color,
        trackColor = Color.Transparent,
    )
}
