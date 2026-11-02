package com.zxn.musicplayer.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One launcher icon style. [id] matches the activity-alias "Icon<Id>" in the manifest; "default" is the original. */
class AppIconOption(val id: String, val label: String, val start: Color, val end: Color, val note: Color) {
    val aliasName get() = "com.zxn.musicplayer.alias.Icon" + id.replaceFirstChar { it.uppercase() }
}

val AppIconOptions = listOf(
    AppIconOption("default", "Red", Color(0xFFFA2D48), Color(0xFF8A1928), Color.White),
    AppIconOption("sunset", "Sunset", Color(0xFFFF9A3C), Color(0xFFFF2D6F), Color.White),
    AppIconOption("ocean", "Ocean", Color(0xFF2DB5FF), Color(0xFF1B4DFF), Color.White),
    AppIconOption("violet", "Violet", Color(0xFFB06BFF), Color(0xFF4B2DFF), Color.White),
    AppIconOption("forest", "Forest", Color(0xFF3DDC84), Color(0xFF0E7C66), Color.White),
    AppIconOption("graphite", "Graphite", Color(0xFF5A5F6B), Color(0xFF16181D), Color.White),
    AppIconOption("light", "Light", Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color.Black),
    AppIconOption("dark", "Dark", Color(0xFF000000), Color(0xFF000000), Color.White),
)

/** Switches the launcher icon by enabling one activity-alias and disabling the others. */
object AppIcons {
    private fun component(context: Context, o: AppIconOption) = ComponentName(context, o.aliasName)

    fun current(context: Context): AppIconOption {
        val pm = context.packageManager
        AppIconOptions.firstOrNull { pm.getComponentEnabledSetting(component(context, it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }?.let { return it }
        return AppIconOptions.first()
    }

    fun set(context: Context, option: AppIconOption) {
        val pm = context.packageManager
        // enable the new one first so the app is never without a launcher entry
        pm.setComponentEnabledSetting(component(context, option), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIconOptions.filter { it.id != option.id }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

@Composable
private fun IconPreview(o: AppIconOption, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(o.start, o.end)))
            .border(1.dp, Color.Gray.copy(alpha = 0.35f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.MusicNote, null, Modifier.size(size * 0.5f), tint = o.note) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf(AppIcons.current(context).id) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Customize App Icon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text(
            "Pick how the app looks on your home screen. Your launcher may take a few seconds to update, and some launchers need the app to be added to the home screen again.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AppIconOptions) { o ->
                val on = o.id == selected
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .border(if (on) 2.dp else 0.dp, if (on) cs.onSurface else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { selected = o.id; AppIcons.set(context, o) }
                        .padding(vertical = 10.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconPreview(o, 64.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(o.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
