package com.zxn.musicplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxn.musicplayer.ui.EffectsViewModel
import com.zxn.musicplayer.ui.LibraryViewModel
import com.zxn.musicplayer.ui.components.EmptyState
import com.zxn.musicplayer.ui.components.SectionHeader
import com.zxn.musicplayer.ui.theme.ThemePrefs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.zxn.musicplayer.ui.components.LargeTitleScaffold
import com.zxn.musicplayer.ui.components.SeekPrefs
import com.zxn.musicplayer.ui.components.SeekStyle
import com.zxn.musicplayer.ui.components.StyledSeekBar
import com.zxn.musicplayer.ui.components.ThemeChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubScreen(title: String, onBack: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}, snackbarHost: @Composable () -> Unit = {}, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions,
            )
        },
        content = content,
    )
}

// ------------------------------------------------------------------ Settings

@Composable
fun SettingsScreen(library: LibraryViewModel, onBack: () -> Unit, onEqualizer: () -> Unit, onHidden: () -> Unit, onTags: () -> Unit) {
    val hidden by library.hiddenSongs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    var showSeekSheet by remember { mutableStateOf(false) }
    var showIconSheet by remember { mutableStateOf(false) }
    var iconLabel by remember { mutableStateOf(com.zxn.musicplayer.ui.components.AppIcons.current(context).label) }

    LargeTitleScaffold("Settings", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            GroupTitle("Appearance")
            SettingsGroup {
                Column(Modifier.padding(16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    ThemeChooser(ThemePrefs.mode, { ThemePrefs.setMode(context, it) })
                }
                RowDivider()
                SettingsRow(Icons.Filled.GraphicEq, "Seek bar style", SeekPrefs.style.label, onClick = { showSeekSheet = true }) { Chevron() }
                RowDivider()
                SettingsRow(Icons.Filled.Palette, "Customize App Icon", iconLabel, onClick = { showIconSheet = true }) { Chevron() }
            }

            GroupTitle("Audio")
            SettingsGroup {
                SettingsRow(Icons.Filled.Equalizer, "Equalizer", "Presets, bands, bass boost, virtualizer", onClick = onEqualizer) { Chevron() }
            }

            GroupTitle("Library")
            SettingsGroup {
                SettingsRow(Icons.Filled.VisibilityOff, "Hidden songs", if (hidden.isEmpty()) "None" else "${hidden.size} hidden", onClick = onHidden) { Chevron() }
                RowDivider()
                SettingsRow(Icons.Filled.AutoFixHigh, "Update tags", "Fix titles, artists, albums and more. Optional, uses the internet.", onClick = onTags) { Chevron() }
            }

            GroupTitle("About")
            SettingsGroup {
                SettingsRow(
                    Icons.Filled.Info, "Music Player${if (version.isNotEmpty()) " $version" else ""}",
                    "Play and organize music stored on your device. Made by Zayan Zaman.",
                    onClick = null,
                ) {}
            }
            Spacer(Modifier.height(12.dp))
            SettingsGroup {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                SettingsRow(
                    Icons.Filled.Code, "More from Zayan Zaman", "github.com/zayanzaman",
                    onClick = { runCatching { uriHandler.openUri("https://github.com/zayanzaman") } },
                ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (showSeekSheet) SeekStyleSheet(onDismiss = { showSeekSheet = false })
    if (showIconSheet) com.zxn.musicplayer.ui.components.AppIconSheet(onDismiss = { showIconSheet = false; iconLabel = com.zxn.musicplayer.ui.components.AppIcons.current(context).label })
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer), content = content)
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Monochrome settings row: neutral icon tile, title, quiet subtitle, optional trailing control. */
@Composable
private fun SettingsRow(
    icon: ImageVector, title: String, subtitle: String,
    onClick: (() -> Unit)?, enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (enabled) cs.onSurface else cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) cs.onSurface else cs.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        trailing()
    }
}

/** Seek bar picker: every style shown live so you can compare before choosing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekStyleSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Text("Seek bar style", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(SeekStyle.entries.toList()) { style ->
                val on = style == SeekPrefs.style
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer)
                        .border(if (on) 2.dp else 0.dp, if (on) cs.onSurface else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickable { SeekPrefs.set(context, style) }.padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(style.label, style = MaterialTheme.typography.titleMedium)
                            Text(style.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        if (on) Icon(Icons.Filled.Check, null)
                    }
                    Spacer(Modifier.height(8.dp))
                    // live preview (not interactive)
                    Box(Modifier.fillMaxWidth()) {
                        StyledSeekBar(style = style, positionMs = 72_000, durationMs = 200_000, isPlaying = true, onSeek = {})
                        Box(Modifier.matchParentSize().clickable { SeekPrefs.set(context, style) })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ------------------------------------------------------------------ Hidden songs

@Composable
fun HiddenSongsScreen(library: LibraryViewModel, onBack: () -> Unit) {
    val hidden by library.hiddenSongs.collectAsStateWithLifecycle()
    SubScreen("Hidden songs", onBack) { padding ->
        if (hidden.isEmpty()) {
            EmptyState(Icons.Filled.VisibilityOff, "No hidden songs", "Songs you hide from their ⋮ menu show up here so you can bring them back.", Modifier.padding(padding))
        } else {
            LazyColumn(contentPadding = padding) {
                items(hidden, key = { it.id }) { s ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { TextButton(onClick = { library.unhide(s.id) }) { Text("Unhide") } },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Equalizer

private fun freqLabel(hz: Int) = if (hz >= 1000) "${"%.1f".format(hz / 1000f).removeSuffix(".0")} kHz" else "$hz Hz"

@Composable
fun EqualizerScreen(vm: EffectsViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    SubScreen("Equalizer", onBack, actions = { TextButton(onClick = vm::reset, enabled = s.available) { Text("Reset") } }) { padding ->
        if (!s.available) {
            EmptyState(
                Icons.Filled.Equalizer, "Equalizer unavailable",
                "Audio effects aren't ready yet. Play a song and try again. If it still doesn't work, this device may not support them.",
                Modifier.padding(padding),
            )
        } else {
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Enable audio effects") },
                    trailingContent = { Switch(checked = s.enabled, onCheckedChange = vm::setEnabled) },
                )

                SectionHeader("Presets")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = s.currentPreset == -1, onClick = {}, enabled = s.enabled, label = { Text("Custom") })
                    s.presets.forEachIndexed { i, name ->
                        FilterChip(selected = s.currentPreset == i, onClick = { vm.usePreset(i) }, enabled = s.enabled, label = { Text(name) })
                    }
                }

                SectionHeader("Bands")
                s.bands.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(freqLabel(b.centerHz), modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = b.level.toFloat(),
                            onValueChange = { vm.setBand(b.index, it.toInt()) },
                            valueRange = s.minLevel.toFloat()..s.maxLevel.toFloat(),
                            enabled = s.enabled,
                            modifier = Modifier.weight(1f),
                        )
                        Text("%+.1f dB".format(b.level / 100f), modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (s.bassBoostSupported || s.virtualizerSupported) SectionHeader("Enhancements")
                if (s.bassBoostSupported) {
                    Text("Bass boost", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = s.bassBoost.toFloat(), onValueChange = { vm.setBassBoost(it.toInt()) },
                        valueRange = 0f..1000f, enabled = s.enabled, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                if (s.virtualizerSupported) {
                    Text("Virtualizer (wider sound)", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = s.virtualizer.toFloat(), onValueChange = { vm.setVirtualizer(it.toInt()) },
                        valueRange = 0f..1000f, enabled = s.enabled, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                Text(
                    "Effects vary by device and may not apply to every audio output (e.g. some Bluetooth devices).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
        }
    }
}
