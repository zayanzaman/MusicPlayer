package com.zxn.musicplayer.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import com.zxn.musicplayer.ui.components.AppMenu
import com.zxn.musicplayer.ui.components.AppMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.zxn.musicplayer.ui.theme.MonoScope
import com.zxn.musicplayer.ui.theme.isAppDark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.zxn.musicplayer.data.formatDuration
import com.zxn.musicplayer.ui.PlayerState
import com.zxn.musicplayer.ui.PlayerViewModel
import com.zxn.musicplayer.ui.PlaylistViewModel
import com.zxn.musicplayer.ui.SongActions
import com.zxn.musicplayer.ui.components.ArtImage
import com.zxn.musicplayer.ui.components.SeekPrefs
import com.zxn.musicplayer.ui.components.StyledSeekBar
import com.zxn.musicplayer.ui.components.rememberArtColor
import com.zxn.musicplayer.ui.components.formatSpeed

private fun readableAccent(c: Color, dark: Boolean): Color {
    val l = c.luminance()
    return when {
        dark && l < 0.5f -> lerp(c, Color.White, 0.35f)
        !dark && l > 0.4f -> lerp(c, Color.Black, 0.35f)
        else -> c
    }
}

/** Soft fade at both ends, so scrolling (marquee) text slides in and out of view instead of being cut. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 0.05f to Color.Black, 0.95f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    player: PlayerViewModel,
    playlistVm: PlaylistViewModel,
    actions: SongActions,
    onOpenEqualizer: () -> Unit,
    onBack: () -> Unit,
) {
    val state by player.state.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(state)
    val favorites by playlistVm.favoriteIds.collectAsStateWithLifecycle()
    val position by player.positionMs.collectAsStateWithLifecycle()
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val timer by player.sleepTimerState.collectAsStateWithLifecycle()
    val base = MaterialTheme.colorScheme
    val dark = isAppDark()

    // Colors come from the cover art: seek bar, buttons and background glow all follow the current song.
    val artColor = rememberArtColor(state.artUri)
    val accent by animateColorAsState(readableAccent(artColor ?: base.onSurface, dark), tween(600), label = "accent")
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val glow by animateColorAsState((artColor ?: base.surfaceContainerHigh).copy(alpha = if (dark) 0.55f else 0.35f), tween(600), label = "npGlow")
    val artScale by animateFloatAsState(if (state.isPlaying) 1f else 0.88f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "artScale")
    val inactive = base.onSurface.copy(alpha = 0.6f)
    val scheme = base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.25f), onPrimaryContainer = base.onSurface,
        surfaceContainerHighest = base.onSurface.copy(alpha = 0.18f), // frosted inactive track
    )

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // pull down anywhere to close
    var dragY by remember { mutableFloatStateOf(0f) }
    val dismissAt = with(density) { 140.dp.toPx() }
    // swipe the cover / title sideways for next / previous
    var swipeX by remember { mutableFloatStateOf(0f) }
    val swipeAt = with(density) { 90.dp.toPx() }
    val swipeModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                when {
                    swipeX < -swipeAt && latest.hasNext -> player.next()
                    swipeX > swipeAt -> player.previous()
                }
                scope.launch { animate(swipeX, 0f) { v, _ -> swipeX = v } }
            },
            onDragCancel = { scope.launch { animate(swipeX, 0f) { v, _ -> swipeX = v } } },
        ) { change, amount -> change.consume(); swipeX += amount }
    }

    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    // feels like a card being pulled away: follows the finger, shrinks a little, corners round off, soft shadow
                    val progress = (dragY / 900f).coerceIn(0f, 1f)
                    translationY = dragY
                    val sc = 1f - 0.07f * progress
                    scaleX = sc; scaleY = sc
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    shape = RoundedCornerShape(36.dp * (progress * 6f).coerceAtMost(1f))
                    clip = dragY > 0f
                    shadowElevation = 30f * (progress * 6f).coerceAtMost(1f)
                }
                .background(base.surface) // opaque base so the app never shows through the tinted glow
                .background(Brush.verticalGradient(listOf(glow, glow.copy(alpha = 0f)), endY = 2000f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > dismissAt) onBack() else scope.launch { animate(dragY, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f)) { v, _ -> dragY = v } }
                        },
                        onDragCancel = { scope.launch { animate(dragY, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f)) { v, _ -> dragY = v } } },
                    ) { change, amount ->
                        if (dragY > 0f || amount > 0f) { change.consume(); dragY = (dragY + amount).coerceAtLeast(0f) }
                    }
                },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                val artSize = minOf(maxWidth - 48.dp, maxHeight * 0.44f)
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // top bar: close on the left, queue + more on the right (no title)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, "Close player", tint = base.onSurface) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = base.onSurface) }
                            Box {
                                var more by remember { mutableStateOf(false) }
                                IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "More", tint = base.onSurface) }
                                AppMenu(expanded = more, onDismissRequest = { more = false }) {
                                    AppMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.Bedtime, null) },
                                        text = {
                                            Text(
                                                when {
                                                    !timer.active -> "Sleep timer"
                                                    timer.endOfTrack -> "Sleep timer · end of song"
                                                    else -> "Sleep timer · ${formatDuration(timer.remainingMs)}"
                                                }
                                            )
                                        },
                                        onClick = { more = false; showSleep = true },
                                    )
                                    AppMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.Equalizer, null) },
                                        text = { Text("Equalizer") },
                                        onClick = { more = false; onOpenEqualizer() },
                                    )
                                    AppMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.Speed, null) },
                                        text = { Text("Playback speed · " + formatSpeed(state.speed) + if (state.pitch != 1f) " / ${formatSpeed(state.pitch)} pitch" else "") },
                                        onClick = { more = false; showSpeed = true },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(0.6f))
                    ArtImage(
                        state.artUri,
                        Modifier.size(artSize).then(swipeModifier).graphicsLayer {
                            val s = artScale * (1f - (kotlin.math.abs(swipeX) / 3000f).coerceAtMost(0.1f))
                            scaleX = s; scaleY = s
                            translationX = swipeX * 0.7f
                            alpha = (1f - kotlin.math.abs(swipeX) / 900f).coerceIn(0.3f, 1f)
                            shadowElevation = 40f; shape = RoundedCornerShape(28.dp); clip = false
                        },
                        RoundedCornerShape(28.dp), sizePx = 768,
                    )
                    Spacer(Modifier.weight(0.7f))

                    // title / artist: one line each; long text scrolls through with faded edges
                    Row(Modifier.fillMaxWidth().then(swipeModifier), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).graphicsLayer { translationX = swipeX * 0.4f }) {
                            Text(
                                state.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                maxLines = 1, softWrap = false, color = base.onSurface,
                                modifier = Modifier.fadingEdges().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 1500, velocity = 36.dp),
                            )
                            Text(
                                state.artist, style = MaterialTheme.typography.titleMedium, color = inactive,
                                maxLines = 1, softWrap = false,
                                modifier = Modifier.fadingEdges().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 1500, velocity = 36.dp),
                            )
                        }
                        val songId = state.songId
                        if (songId != null) {
                            val isFav = songId in favorites
                            IconButton(onClick = { playlistVm.toggleFavorite(songId) }) {
                                Icon(
                                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    if (isFav) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFav) accent else inactive,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(0.4f))
                    StyledSeekBar(
                        style = SeekPrefs.style,
                        positionMs = position, durationMs = state.durationMs, isPlaying = state.isPlaying,
                        onSeek = { scrubMs = null; player.seekTo(it) }, onScrub = { scrubMs = it },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(scrubMs ?: position), style = MaterialTheme.typography.labelMedium, color = inactive)
                        Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = inactive)
                    }

                    Spacer(Modifier.weight(0.5f))
                    val corner by animateDpAsState(if (state.isPlaying) 28.dp else 40.dp, label = "playCorner")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = player::toggleShuffle) {
                            Icon(Icons.Filled.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", tint = if (state.shuffle) accent else inactive)
                        }
                        IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp), tint = base.onSurface)
                        }
                        FilledIconButton(
                            onClick = player::togglePlayPause, modifier = Modifier.size(80.dp), shape = RoundedCornerShape(corner),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = onAccent),
                        ) {
                            Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "Pause" else "Play", Modifier.size(42.dp))
                        }
                        IconButton(onClick = player::next, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp), tint = if (state.hasNext) base.onSurface else inactive.copy(alpha = 0.3f))
                        }
                        IconButton(onClick = player::cycleRepeat) {
                            val on = state.repeatMode != Player.REPEAT_MODE_OFF
                            Icon(
                                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                when (state.repeatMode) { Player.REPEAT_MODE_ONE -> "Repeat one"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat off" },
                                tint = if (on) accent else inactive,
                            )
                        }
                    }
                    Spacer(Modifier.weight(0.9f))
                }
            }
        }
    }

    // utility sheets stay black & white
    if (showSpeed) MonoScope {
        SpeedPitchSheet(state.speed, state.pitch, onChange = { sp, pi -> player.setSpeed(sp, pi) }, onDismiss = { showSpeed = false })
    }
    if (showSleep) MonoScope {
        SleepTimerSheet(
            timer,
            onMinutes = player::startSleepTimer,
            onEndOfTrack = player::startSleepTimerEndOfTrack,
            onCancel = player::cancelSleepTimer,
            onDismiss = { showSleep = false },
        )
    }
    if (showQueue) MonoScope {
        QueueSheet(player, state, onSaveAsPlaylist = { ids -> showQueue = false; actions.openAddToPlaylist(ids) }) { showQueue = false }
    }
}

/** Queue bottom sheet. Drag a row by its handle to reorder; the move is committed when you let go. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(player: PlayerViewModel, state: PlayerState, onSaveAsPlaylist: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false) // opens half-height, drag up for full
    val listState = rememberLazyListState()
    var local by remember(state.queue) { mutableStateOf(state.queue) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragCenterY by remember { mutableFloatStateOf(0f) }
    var startIndex by remember { mutableIntStateOf(-1) }
    var menu by remember { mutableStateOf(false) }
    val currentId = state.queue.getOrNull(state.currentIndex)?.mediaId

    LaunchedEffect(Unit) { listState.scrollToItem(state.currentIndex.coerceAtLeast(0)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Queue", style = MaterialTheme.typography.titleLarge)
                Text("${local.size} ${if (local.size == 1) "song" else "songs"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Queue options") }
                AppMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    AppMenuItem(text = { Text("Save queue as playlist") }, onClick = {
                        menu = false
                        onSaveAsPlaylist(state.queue.mapNotNull { it.songId })
                    })
                    AppMenuItem(text = { Text("Clear upcoming") }, onClick = { menu = false; player.clearUpcoming() })
                    AppMenuItem(text = { Text("Clear queue") }, onClick = { menu = false; player.clearQueue(); onDismiss() })
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth(), state = listState) {
            itemsIndexed(local, key = { _, e -> e.mediaId }) { _, e ->
                val isDragging = draggingKey == e.mediaId
                val rowModifier = Modifier
                    .then(if (isDragging) Modifier.zIndex(1f) else Modifier.animateItem())
                    .graphicsLayer {
                        if (isDragging) {
                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == e.mediaId }
                            if (info != null) translationY = dragCenterY - (info.offset + info.size / 2f)
                        }
                    }
                    .then(if (isDragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
                    .alpha(if (isDragging) 0.95f else 1f)

                ListItem(
                    modifier = rowModifier.clickable { player.skipToQueueItem(local.indexOfFirst { it.mediaId == e.mediaId }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { ArtImage(e.artUri, Modifier.size(48.dp), RoundedCornerShape(14.dp), sizePx = 128) },
                    headlineContent = {
                        Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (e.mediaId == currentId) MaterialTheme.colorScheme.primary else Color.Unspecified)
                    },
                    supportingContent = { Text(e.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { player.removeQueueItem(local.indexOfFirst { it.mediaId == e.mediaId }) }) {
                                Icon(Icons.Filled.Close, "Remove from queue")
                            }
                            Icon(
                                Icons.Filled.DragHandle, "Drag to reorder",
                                Modifier.padding(horizontal = 8.dp).size(28.dp).pointerInput(e.mediaId) {
                                    detectDragGestures(
                                        onDragStart = {
                                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == e.mediaId }
                                            if (info != null) {
                                                draggingKey = e.mediaId
                                                startIndex = local.indexOfFirst { it.mediaId == e.mediaId }
                                                dragCenterY = info.offset + info.size / 2f
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragCenterY += amount.y
                                            val key = draggingKey ?: return@detectDragGestures
                                            val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                                it.key != key && dragCenterY >= it.offset && dragCenterY <= it.offset + it.size
                                            }
                                            if (target != null) {
                                                val from = local.indexOfFirst { it.mediaId == key }
                                                val to = local.indexOfFirst { it.mediaId == target.key }
                                                if (from >= 0 && to >= 0 && from != to) {
                                                    local = local.toMutableList().apply { add(to, removeAt(from)) }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            val key = draggingKey
                                            if (key != null) {
                                                val end = local.indexOfFirst { it.mediaId == key }
                                                if (startIndex >= 0 && end >= 0 && end != startIndex) player.moveQueueItem(startIndex, end)
                                            }
                                            draggingKey = null
                                        },
                                        onDragCancel = { draggingKey = null; local = state.queue },
                                    )
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}
