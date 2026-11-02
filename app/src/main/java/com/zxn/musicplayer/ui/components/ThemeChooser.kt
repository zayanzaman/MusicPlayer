package com.zxn.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zxn.musicplayer.ui.theme.ThemeMode

/** Light / Dark / Follow device picker with little phone previews. Used in onboarding and Settings. */
@Composable
fun ThemeChooser(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeMode.entries.forEach { mode ->
            val on = mode == selected
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.weight(1f).clip(shape).clickable { onSelect(mode) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(132.dp).clip(shape)
                        .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape),
                ) {
                    when (mode) {
                        ThemeMode.LIGHT -> Preview(Color.White, Color(0xFFD9D9DE), Color.Black)
                        ThemeMode.DARK -> Preview(Color(0xFF0B0B0D), Color(0xFF3A3A3C), Color.White)
                        ThemeMode.SYSTEM -> Row(Modifier.fillMaxWidth().height(132.dp)) {
                            Box(Modifier.weight(1f)) { Preview(Color.White, Color(0xFFD9D9DE), Color.Black) }
                            Box(Modifier.weight(1f)) { Preview(Color(0xFF0B0B0D), Color(0xFF3A3A3C), Color.White) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    mode.label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun Preview(bg: Color, line: Color, strong: Color) {
    Column(Modifier.fillMaxWidth().height(132.dp).background(bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(44.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(strong))
        repeat(3) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(22.dp).height(22.dp).clip(RoundedCornerShape(6.dp)).background(line))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.width(30.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(line))
            }
        }
    }
}
