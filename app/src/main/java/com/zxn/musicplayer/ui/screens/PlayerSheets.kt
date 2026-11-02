package com.zxn.musicplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zxn.musicplayer.data.formatDuration
import com.zxn.musicplayer.player.SleepTimerState
import com.zxn.musicplayer.ui.components.formatSpeed
import kotlin.math.roundToInt

/** Speed (0.5x-2x) and pitch (0.5x-2x) controls. Values are committed when you let go of a slider. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedPitchSheet(speed: Float, pitch: Float, onChange: (speed: Float, pitch: Float) -> Unit, onDismiss: () -> Unit) {
    var s by remember(speed) { mutableFloatStateOf(speed) }
    var p by remember(pitch) { mutableFloatStateOf(pitch) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Speed & pitch", style = MaterialTheme.typography.titleLarge)

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Speed"); Text(formatSpeed(s), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = s, onValueChange = { s = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { onChange(s, p) }, valueRange = 0.5f..2f, steps = 29,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                    FilterChip(selected = s == v, onClick = { s = v; onChange(v, p) }, label = { Text(formatSpeed(v)) })
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pitch"); Text(formatSpeed(p), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = p, onValueChange = { p = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { onChange(s, p) }, valueRange = 0.5f..2f, steps = 29,
            )
            Text(
                "Pitch changes how high or low the voice sounds without affecting speed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { s = 1f; p = 1f; onChange(1f, 1f) }, modifier = Modifier.align(Alignment.End)) { Text("Reset") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onMinutes: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        if (state.active) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (state.endOfTrack) "Stops at the end of this song" else "Stops in ${formatDuration(state.remainingMs)}",
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = { onCancel(); onDismiss() }) { Text("Cancel timer") }
            }
        } else {
            Text(
                "Music fades out over the last 10 seconds.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        listOf(5, 10, 15, 30, 45, 60).forEach { m ->
            ListItem(
                modifier = Modifier.clickable { onMinutes(m); onDismiss() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.Timer, null) },
                headlineContent = { Text("$m minutes") },
            )
        }
        ListItem(
            modifier = Modifier.clickable { onEndOfTrack(); onDismiss() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Filled.Timer, null) },
            headlineContent = { Text("End of current song") },
        )
    }
}
