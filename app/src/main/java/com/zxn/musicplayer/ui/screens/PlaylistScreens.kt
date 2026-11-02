package com.zxn.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import com.zxn.musicplayer.ui.components.AppMenu
import com.zxn.musicplayer.ui.components.AppMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxn.musicplayer.data.Song
import com.zxn.musicplayer.ui.LibraryViewModel
import com.zxn.musicplayer.ui.PlayerViewModel
import com.zxn.musicplayer.ui.PlaylistItem
import com.zxn.musicplayer.ui.PlaylistViewModel
import com.zxn.musicplayer.ui.SongActions
import com.zxn.musicplayer.ui.components.ArtImage
import com.zxn.musicplayer.ui.components.EmptyState
import com.zxn.musicplayer.ui.components.LargeTitleScaffold
import com.zxn.musicplayer.ui.components.PlayShuffleButtons
import com.zxn.musicplayer.ui.components.ReorderState
import com.zxn.musicplayer.ui.components.SectionHeader
import com.zxn.musicplayer.ui.components.TabActions
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ shared dialog

/** Single-text-field dialog used for creating and renaming playlists. */
@Composable
fun NameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist?") },
        text = { Text("\"$name\" will be deleted. Your songs stay on the device.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ Playlists (Your Library > Playlists)

@Composable
fun PlaylistsScreen(
    playlistVm: PlaylistViewModel,
    player: PlayerViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    val playlists by playlistVm.playlists.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<PlaylistItem?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistItem?>(null) }

    LargeTitleScaffold("Playlists", onBack = onBack) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onCreate).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GenericCover(Icons.Filled.Add, Modifier.size(56.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("New Playlist", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(playlists, key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenPlaylist(p.id) }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistCover(p, Modifier.size(56.dp), RoundedCornerShape(12.dp), sizePx = 128)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.songCount} ${if (p.songCount == 1) "song" else "songs"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, "Options for ${p.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        AppMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            AppMenuItem(text = { Text("Play") }, onClick = {
                                menu = false
                                scope.launch { playlistVm.songsOf(p.id).let { if (it.isNotEmpty()) { playlistVm.markPlayed(p.id); player.playSongs(it, 0, shuffle = false) } } }
                            })
                            AppMenuItem(text = { Text("Shuffle") }, onClick = {
                                menu = false
                                scope.launch { playlistVm.markPlayed(p.id); player.shufflePlay(playlistVm.songsOf(p.id)) }
                            })
                            AppMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameTarget = p })
                            AppMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleteTarget = p })
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { p ->
        NameDialog("Rename playlist", p.name, "Rename", { playlistVm.rename(p.id, it); renameTarget = null }, { renameTarget = null })
    }
    deleteTarget?.let { p ->
        ConfirmDeleteDialog(p.name, { playlistVm.delete(p.id); deleteTarget = null }, { deleteTarget = null })
    }
}

// ------------------------------------------------------------------ Favorites / Recent / Most played

@Composable
fun SmartListScreen(
    kind: String,
    playlistVm: PlaylistViewModel,
    player: PlayerViewModel,
    actions: SongActions,
    playingId: Long?,
    onBack: () -> Unit,
) {
    val favorites by playlistVm.favoriteSongs.collectAsStateWithLifecycle()
    val recent by playlistVm.recentSongs.collectAsStateWithLifecycle()
    val most by playlistVm.mostPlayedSongs.collectAsStateWithLifecycle()
    val (title, songs) = when (kind) {
        "favorites" -> "Favorites" to favorites
        "recent" -> "Recently played" to recent
        else -> "Most played" to most
    }
    DetailScreen(
        title = title,
        subtitle = "${songs.size} songs",
        artUri = songs.firstOrNull()?.uri,
        songs = songs, player = player, playingId = playingId, actions = actions,
        onBack = onBack, onMore = actions::openMenu,
    )
}

// ------------------------------------------------------------------ Playlist detail (reorderable)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistVm: PlaylistViewModel,
    library: LibraryViewModel,
    player: PlayerViewModel,
    actions: SongActions,
    playingId: Long?,
    onBack: () -> Unit,
) {
    val detail by remember(playlistId) { playlistVm.detail(playlistId) }.collectAsStateWithLifecycle(initialValue = null)
    val allSongs by library.allSongs.collectAsStateWithLifecycle()
    val d = detail
    var topMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    val items by playlistVm.playlists.collectAsStateWithLifecycle()
    val item = items.firstOrNull { it.id == playlistId }
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) playlistVm.setCover(playlistId, uri) }
    val listState = rememberLazyListState()
    val reorder = remember(listState) { ReorderState(listState) }
    var local by remember(d?.entries) { mutableStateOf(d?.entries.orEmpty()) }
    var startIndex by remember { mutableIntStateOf(-1) }
    val songs = local.map { it.song }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(d?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { topMenu = true }) { Icon(Icons.Filled.MoreVert, "Playlist options") }
                        AppMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            AppMenuItem(text = { Text("Play next") }, onClick = { topMenu = false; player.playNext(songs) })
                            AppMenuItem(text = { Text("Add to queue") }, onClick = { topMenu = false; player.addToQueue(songs) })
                            AppMenuItem(text = { Text("Rename") }, onClick = { topMenu = false; showRename = true })
                            AppMenuItem(text = { Text("Change cover") }, onClick = {
                                topMenu = false
                                coverPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            if (item?.coverFile != null) AppMenuItem(text = { Text("Remove cover") }, onClick = { topMenu = false; playlistVm.setCover(playlistId, null) })
                            AppMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { topMenu = false; showDelete = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(state = listState, contentPadding = padding) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item != null) PlaylistCover(item, Modifier.size(112.dp), RoundedCornerShape(20.dp))
                        else ArtImage(songs.firstOrNull()?.uri, Modifier.size(112.dp), RoundedCornerShape(20.dp), sizePx = 384)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(d.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${songs.size} ${if (songs.size == 1) "song" else "songs"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(key = "buttons") {
                    Column {
                        PlayShuffleButtons(
                            onPlay = { playlistVm.markPlayed(playlistId); player.playSongs(songs, 0, shuffle = false) },
                            onShuffle = { playlistVm.markPlayed(playlistId); player.shufflePlay(songs) },
                        )
                        OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add songs")
                        }
                    }
                }
                if (local.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "This playlist is empty", "Tap \"Add songs\" or use a song's ⋮ menu.", Modifier.height(240.dp))
                    }
                }
                itemsIndexed(local, key = { _, e -> e.entryId }) { index, e ->
                    val isDragging = reorder.draggingKey == e.entryId
                    ListItem(
                        modifier = Modifier
                            .then(if (isDragging) Modifier.zIndex(1f) else Modifier.animateItem())
                            .graphicsLayer { translationY = reorder.translationFor(e.entryId) }
                            .then(if (isDragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
                            .alpha(if (isDragging) 0.95f else 1f)
                            .clickable { playlistVm.markPlayed(playlistId); player.playSongs(songs, index) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { ArtImage(e.song.uri, Modifier.size(52.dp), sizePx = 128) },
                        headlineContent = {
                            Text(e.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (e.song.id == playingId) MaterialTheme.colorScheme.primary else Color.Unspecified)
                        },
                        supportingContent = { Text(e.song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    actions.openPlaylistItemMenu(e.song) { playlistVm.removeEntry(playlistId, e.entryId) }
                                }) { Icon(Icons.Filled.MoreVert, "More options for ${e.song.title}") }
                                Icon(
                                    Icons.Filled.DragHandle, "Drag to reorder",
                                    Modifier.padding(horizontal = 8.dp).size(28.dp).pointerInput(e.entryId) {
                                        detectDragGestures(
                                            onDragStart = {
                                                if (reorder.start(e.entryId)) startIndex = local.indexOfFirst { it.entryId == e.entryId }
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                reorder.dragBy(amount.y) { from, to ->
                                                    val a = local.indexOfFirst { it.entryId == from }
                                                    val b = local.indexOfFirst { it.entryId == to }
                                                    if (a >= 0 && b >= 0 && a != b) local = local.toMutableList().apply { add(b, removeAt(a)) }
                                                }
                                            },
                                            onDragEnd = {
                                                val key = reorder.draggingKey
                                                reorder.end()
                                                if (key is Long) {
                                                    val end = local.indexOfFirst { it.entryId == key }
                                                    if (startIndex >= 0 && end >= 0 && end != startIndex) {
                                                        playlistVm.moveEntry(playlistId, key, d.entries[end].entryId)
                                                    }
                                                }
                                            },
                                            onDragCancel = { reorder.end(); local = d.entries },
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

    if (showAdd) {
        AddSongsSheet(allSongs, onConfirm = { ids -> playlistVm.addSongs(playlistId, ids); showAdd = false }, onDismiss = { showAdd = false })
    }
    if (showRename && d != null) {
        NameDialog("Rename playlist", d.name, "Rename", { playlistVm.rename(playlistId, it); showRename = false }, { showRename = false })
    }
    if (showDelete && d != null) {
        ConfirmDeleteDialog(d.name, { showDelete = false; onBack(); playlistVm.delete(playlistId) }, { showDelete = false })
    }
}

// ------------------------------------------------------------------ multi-select song picker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(allSongs: List<Song>, onConfirm: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(listOf<Long>()) } // keeps tap order
    val q = query.trim()
    val filtered = remember(q, allSongs) {
        if (q.isEmpty()) allSongs
        else allSongs.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Add songs", style = MaterialTheme.typography.titleLarge)
            Button(enabled = selected.isNotEmpty(), onClick = { onConfirm(selected) }) { Text("Add ${selected.size}") }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(filtered, key = { it.id }) { s ->
                val checked = s.id in selected
                ListItem(
                    modifier = Modifier.clickable { selected = if (checked) selected - s.id else selected + s.id },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { ArtImage(s.uri, Modifier.size(48.dp), RoundedCornerShape(14.dp), sizePx = 128) },
                    headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                )
            }
        }
    }
}
