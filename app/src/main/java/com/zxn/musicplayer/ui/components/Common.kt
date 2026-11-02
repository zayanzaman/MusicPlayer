package com.zxn.musicplayer.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.zxn.musicplayer.data.Song
import com.zxn.musicplayer.data.formatDuration
import com.zxn.musicplayer.ui.coil.SongArt

/**
 * Album art. Shows an animated shimmer placeholder immediately, so lists scroll freely while the real cover
 * loads in the background; falls back to a music-note tile when the song has no art.
 */
@Composable
fun ArtImage(
    uri: Uri?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    sizePx: Int = 256,
) {
    // 0 = loading, 1 = loaded, 2 = no art
    var state by remember(uri) { mutableIntStateOf(if (uri == null) 2 else 0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (state == 0) Box(Modifier.matchParentSize().shimmer(shape))
        if (state == 2) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxSize(0.45f),
                )
            }
        }
        if (uri != null) {
            val request = remember(uri, sizePx) {
                ImageRequest.Builder(ctx).data(SongArt(uri, sizePx)).size(sizePx).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onSuccess = { state = 1 },
                onError = { state = 2 },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Apple-Music-style song row: rounded cover, bold title, quiet subtitle, tiny overflow button, press feedback. */
@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(song.uri, Modifier.size(52.dp), RoundedCornerShape(10.dp), sizePx = 128)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${song.artist} · ${formatDuration(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.MoreHoriz, "More options for ${song.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Apple-Music-style pair of soft pills: neutral background, bold label, no heavy fills. */
@Composable
fun PlayShuffleButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Play", Icons.Filled.PlayArrow, onPlay, Modifier.weight(1f))
        PillButton("Shuffle", Icons.Filled.Shuffle, onShuffle, Modifier.weight(1f))
    }
}

@Composable
fun PillButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(46.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Apple-style large bold title that collapses on scroll; content scrolls behind the glass bars via the bottom inset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeTitleScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val bg = MaterialTheme.colorScheme.background
    val bottomInset = LocalBottomInset.current
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        containerColor = bg,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) }, actions = actions, scrollBehavior = scroll,
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = bg, scrolledContainerColor = bg.copy(alpha = 0.92f)),
            )
        },
    ) { padding ->
        content(PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + bottomInset))
    }
}

/** Search + settings buttons shown on every top-level tab. */
@Composable
fun RowScope.TabActions(onSearch: () -> Unit, onSettings: () -> Unit) {
    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
}

fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "%.0fx".format(speed) else "%.2f".format(speed).trimEnd('0') + "x"
