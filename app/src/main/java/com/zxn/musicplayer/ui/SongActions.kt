package com.zxn.musicplayer.ui

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zxn.musicplayer.data.Song
import com.zxn.musicplayer.data.SongFileOps
import com.zxn.musicplayer.data.formatDuration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxn.musicplayer.ui.components.ArtImage
import com.zxn.musicplayer.ui.screens.NameDialog
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State + logic behind the three-dot song menu. One instance lives at the app root; any screen
 * calls [openMenu]. Add-to-playlist, hide and tag/cover editing are
 * shown as disabled "Coming soon" rows until implemented.
 */
class SongActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: PlayerViewModel,
    private val library: LibraryViewModel,
    private val playlists: PlaylistViewModel,
) {
    val favoriteIds: StateFlow<Set<Long>> get() = playlists.favoriteIds
    val playlistItems: StateFlow<List<PlaylistItem>> get() = playlists.playlists

    /** Non-null = the "Add to playlist" sheet is open for these song ids. */
    var addToPlaylistIds by mutableStateOf<List<Long>?>(null)
    /** Non-null = the "New playlist" name dialog is open; the playlist is created with these songs. */
    var createPlaylistIds by mutableStateOf<List<Long>?>(null)
    /** Set when the menu is opened from inside a playlist; adds a "Remove from playlist" row. */
    var menuRemove by mutableStateOf<(() -> Unit)?>(null)

    var menuSong by mutableStateOf<Song?>(null)
    var infoSong by mutableStateOf<Song?>(null)
    var renameSong by mutableStateOf<Song?>(null)

    /** Set by [rememberSongActions]; launches a system permission dialog. */
    var launchSender: ((IntentSender) -> Unit)? = null

    private sealed interface Pending {
        data class Rename(val song: Song, val title: String) : Pending
        data class Delete(val song: Song) : Pending
    }
    private var pending: Pending? = null

    fun openMenu(song: Song) { menuRemove = null; menuSong = song }
    fun openPlaylistItemMenu(song: Song, onRemove: () -> Unit) { menuRemove = onRemove; menuSong = song }

    fun openAddToPlaylist(songIds: List<Long>) { if (songIds.isNotEmpty()) addToPlaylistIds = songIds }
    fun toggleFavorite(song: Song) { playlists.toggleFavorite(song.id) }

    fun addToPlaylist(playlistId: Long, name: String, songIds: List<Long>, toStart: Boolean) {
        playlists.addSongs(playlistId, songIds, toStart)
        toast("Added ${songIds.size} to $name")
    }

    fun createPlaylist(name: String, songIds: List<Long>) {
        playlists.create(name, songIds)
        toast(if (songIds.isEmpty()) "Playlist created" else "Created \"$name\"")
    }

    fun playNext(song: Song) { player.playNext(listOf(song)); toast("Will play next") }
    fun addToQueue(song: Song) { player.addToQueue(listOf(song)); toast("Added to queue") }

    fun hideSong(song: Song) {
        library.hide(song.id)
        player.removeSongsFromQueue(setOf(song.id))
        toast("Hidden. Unhide in Settings > Hidden songs")
    }

    fun requestRename(song: Song, newTitle: String) {
        pending = Pending.Rename(song, newTitle)
        launchSender?.invoke(SongFileOps.writeRequest(context, listOf(song.uri)))
    }

    fun requestDelete(song: Song) {
        pending = Pending.Delete(song)
        launchSender?.invoke(SongFileOps.deleteRequest(context, listOf(song.uri)))
    }

    fun setAsRingtone(song: Song) {
        if (!SongFileOps.canWriteSettings(context)) {
            toast("Allow \"Modify system settings\" for this app, then try again")
            context.startActivity(SongFileOps.writeSettingsIntent(context))
            return
        }
        toast(if (SongFileOps.setRingtone(context, song.uri)) "Ringtone set" else "Couldn't set ringtone")
    }

    fun onPermissionResult(granted: Boolean) {
        val p = pending
        pending = null
        if (!granted || p == null) return
        when (p) {
            is Pending.Rename -> scope.launch {
                val ok = withContext(Dispatchers.IO) { SongFileOps.rename(context, p.song.uri, p.title) }
                toast(if (ok) "Renamed" else "Couldn't rename")
                library.refreshNow()
            }
            is Pending.Delete -> {
                player.removeSongsFromQueue(setOf(p.song.id))
                scope.launch { library.refreshNow() }
                toast("Deleted from device")
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
fun rememberSongActions(player: PlayerViewModel, library: LibraryViewModel, playlists: PlaylistViewModel): SongActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = remember { SongActions(context, scope, player, library, playlists) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        actions.onPermissionResult(it.resultCode == Activity.RESULT_OK)
    }
    actions.launchSender = { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
    return actions
}

/** Renders the options sheet and dialogs for [actions]. Place once at the app root. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsHost(actions: SongActions) {
    val favorites by actions.favoriteIds.collectAsStateWithLifecycle()
    val playlistItems by actions.playlistItems.collectAsStateWithLifecycle()
    actions.menuSong?.let { song ->
        val close = { actions.menuSong = null }
        ModalBottomSheet(onDismissRequest = { close() }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(song.uri, Modifier.size(56.dp), RoundedCornerShape(16.dp), sizePx = 128)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                OptionItem(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next") { close(); actions.playNext(song) }
                OptionItem(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") { close(); actions.addToQueue(song) }
                OptionItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist") { close(); actions.openAddToPlaylist(listOf(song.id)) }
                actions.menuRemove?.let { remove ->
                    OptionItem(Icons.Filled.RemoveCircleOutline, "Remove from this playlist") { close(); remove() }
                }
                val isFav = song.id in favorites
                OptionItem(
                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (isFav) "Remove from favorites" else "Add to favorites",
                ) { close(); actions.toggleFavorite(song) }
                OptionItem(Icons.Filled.Info, "Song info") { close(); actions.infoSong = song }
                OptionItem(Icons.Filled.Edit, "Rename") { close(); actions.renameSong = song }
                OptionItem(Icons.Filled.Image, "Edit tags / change cover", enabled = false, supporting = "Coming soon") {}
                OptionItem(Icons.Filled.RingVolume, "Set as ringtone") { close(); actions.setAsRingtone(song) }
                OptionItem(Icons.Filled.VisibilityOff, "Hide song") { close(); actions.hideSong(song) }
                OptionItem(Icons.Filled.Delete, "Delete from device", danger = true) { close(); actions.requestDelete(song) }
            }
        }
    }
    actions.infoSong?.let { song -> SongInfoDialog(song) { actions.infoSong = null } }
    actions.renameSong?.let { song ->
        var text by remember(song) { mutableStateOf(song.title) }
        AlertDialog(
            onDismissRequest = { actions.renameSong = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { actions.renameSong = null; actions.requestRename(song, text) }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { actions.renameSong = null }) { Text("Cancel") } },
        )
    }
    actions.addToPlaylistIds?.let { ids ->
        AddToPlaylistSheet(
            playlists = playlistItems,
            onPick = { p, toStart -> actions.addToPlaylist(p.id, p.name, ids, toStart); actions.addToPlaylistIds = null },
            onNew = { actions.createPlaylistIds = ids; actions.addToPlaylistIds = null },
            onDismiss = { actions.addToPlaylistIds = null },
        )
    }
    actions.createPlaylistIds?.let { ids ->
        NameDialog(
            "New playlist", "", "Create",
            onConfirm = { name -> actions.createPlaylist(name, ids); actions.createPlaylistIds = null },
            onDismiss = { actions.createPlaylistIds = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(
    playlists: List<PlaylistItem>,
    onPick: (PlaylistItem, Boolean) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var toStart by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Add to playlist", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !toStart, onClick = { toStart = false }, label = { Text("At the end") })
            FilterChip(selected = toStart, onClick = { toStart = true }, label = { Text("At the top") })
        }
        ListItem(
            modifier = Modifier.clickable(onClick = onNew),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Filled.Add, null) },
            headlineContent = { Text("New playlist") },
        )
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(playlists, key = { it.id }) { p ->
                ListItem(
                    modifier = Modifier.clickable { onPick(p, toStart) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { ArtImage(p.artSongUri, Modifier.size(48.dp), RoundedCornerShape(14.dp), sizePx = 128) },
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${p.songCount} ${if (p.songCount == 1) "song" else "songs"}") },
                )
            }
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    supporting: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else LocalContentColor.current
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.5f),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null, tint = tint) },
        headlineContent = { Text(label, color = if (danger) tint else Color.Unspecified) },
        supportingContent = if (supporting != null) ({ Text(supporting) }) else null,
    )
}

@Composable
private fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val details by produceState<SongFileOps.Details?>(null, song) {
        value = withContext(Dispatchers.IO) { SongFileOps.loadDetails(context, song.uri) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Song info") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoLine("Title", song.title)
                InfoLine("Artist", song.artist)
                InfoLine("Album", song.album)
                InfoLine("Duration", formatDuration(song.durationMs))
                if (song.year > 0) InfoLine("Year", song.year.toString())
                if (song.track > 0) InfoLine("Track", song.track.toString())
                details?.let { d ->
                    d.fileName?.let { InfoLine("File", it) }
                    d.folder?.let { InfoLine("Folder", it) }
                    d.mimeType?.let { InfoLine("Format", it) }
                    d.bitrateKbps?.let { InfoLine("Bitrate", "$it kbps") }
                    d.sizeBytes?.let { InfoLine("Size", Formatter.formatShortFileSize(context, it)) }
                }
            }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
