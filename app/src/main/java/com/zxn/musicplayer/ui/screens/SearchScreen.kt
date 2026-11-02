package com.zxn.musicplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxn.musicplayer.data.Song
import com.zxn.musicplayer.ui.LibraryViewModel
import com.zxn.musicplayer.ui.PlayerViewModel
import com.zxn.musicplayer.ui.PlaylistViewModel
import com.zxn.musicplayer.ui.components.ArtImage
import com.zxn.musicplayer.ui.components.EmptyState
import com.zxn.musicplayer.ui.components.SectionHeader
import com.zxn.musicplayer.ui.components.SongRow

@Composable
fun SearchScreen(
    library: LibraryViewModel,
    player: PlayerViewModel,
    playingId: Long?,
    onBack: () -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    playlistVm: PlaylistViewModel,
    onPlaylist: (Long) -> Unit,
    onMore: (Song) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val songs by library.allSongs.collectAsStateWithLifecycle()
    val albums by library.albums.collectAsStateWithLifecycle()
    val artists by library.artists.collectAsStateWithLifecycle()
    val playlists by playlistVm.playlists.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    val q = query.trim()
    val songHits = remember(q, songs) {
        if (q.isEmpty()) emptyList()
        else songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }.take(100)
    }
    val albumHits = remember(q, albums) {
        if (q.isEmpty()) emptyList() else albums.filter { it.name.contains(q, true) || it.artist.contains(q, true) }.take(20)
    }
    val playlistHits = remember(q, playlists) {
        if (q.isEmpty()) emptyList() else playlists.filter { it.name.contains(q, true) }.take(20)
    }
    val artistHits = remember(q, artists) {
        if (q.isEmpty()) emptyList() else artists.filter { it.name.contains(q, true) }.take(20)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text("Search your library") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                )
            }
        },
    ) { padding ->
        if (q.isEmpty()) {
            EmptyState(Icons.Filled.Search, "Search your music", "Find songs, albums, artists and playlists.", Modifier.padding(padding).imePadding())
        } else if (songHits.isEmpty() && albumHits.isEmpty() && artistHits.isEmpty() && playlistHits.isEmpty()) {
            EmptyState(Icons.Filled.Search, "No results", "Nothing matches \"$q\".", Modifier.padding(padding).imePadding())
        } else {
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = padding) {
                if (playlistHits.isNotEmpty()) {
                    item { SectionHeader("Playlists") }
                    items(playlistHits, key = { "playlist-${it.id}" }) { p ->
                        ListItem(
                            modifier = Modifier.clickable { onPlaylist(p.id) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { ArtImage(p.artSongUri, Modifier.size(52.dp), RoundedCornerShape(14.dp), sizePx = 128) },
                            headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${p.songCount} songs") },
                        )
                    }
                }
                if (artistHits.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    items(artistHits, key = { "artist-${it.id}" }) { artist ->
                        ListItem(
                            modifier = Modifier.clickable { onArtist(artist.id) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(Icons.Filled.Person, null) },
                            headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${artist.songCount} songs") },
                        )
                    }
                }
                if (albumHits.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(albumHits, key = { "album-${it.id}" }) { album ->
                        ListItem(
                            modifier = Modifier.clickable { onAlbum(album.id) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { ArtImage(album.artSongUri, Modifier.size(52.dp), RoundedCornerShape(14.dp), sizePx = 128) },
                            headlineContent = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                if (songHits.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    items(songHits, key = { "song-${it.id}" }) { s ->
                        SongRow(s, s.id == playingId, onClick = { player.playSongs(songHits, songHits.indexOf(s)) }, onMore = { onMore(s) })
                    }
                }
            }
        }
    }
}
