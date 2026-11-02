package com.zxn.musicplayer.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import com.zxn.musicplayer.ui.components.AppMenu
import com.zxn.musicplayer.ui.components.AppMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxn.musicplayer.data.Song
import com.zxn.musicplayer.ui.LibraryViewModel
import com.zxn.musicplayer.ui.PlayerViewModel
import com.zxn.musicplayer.ui.SongActions
import com.zxn.musicplayer.ui.SongSort
import com.zxn.musicplayer.ui.components.ArtImage
import com.zxn.musicplayer.ui.components.EmptyState
import com.zxn.musicplayer.ui.components.ListSkeleton
import com.zxn.musicplayer.ui.components.MenuOption
import com.zxn.musicplayer.ui.components.SortMenu
import com.zxn.musicplayer.ui.components.LargeTitleScaffold
import com.zxn.musicplayer.ui.components.PlayShuffleButtons
import com.zxn.musicplayer.ui.components.TabActions
import com.zxn.musicplayer.ui.components.SongRow

// ---------------------------------------------------------------- Songs

@Composable
fun SongsScreen(
    library: LibraryViewModel,
    player: PlayerViewModel,
    playingId: Long?,
    ready: Boolean,
    onSearch: () -> Unit,
    onMore: (Song) -> Unit,
) {
    val songs by library.sortedSongs.collectAsStateWithLifecycle()
    val loaded by library.loaded.collectAsStateWithLifecycle()
    val sort by library.sort.collectAsStateWithLifecycle()
    val reversed by library.reversed.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }

    LargeTitleScaffold(
        title = "Songs",
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                SortMenu(
                    expanded = sortMenu, onDismiss = { sortMenu = false }, title = "Sort songs by",
                    options = listOf(
                        MenuOption(SongSort.TITLE, "Title", Icons.Filled.MusicNote),
                        MenuOption(SongSort.ARTIST, "Artist", Icons.Filled.Person),
                        MenuOption(SongSort.ALBUM, "Album", Icons.Filled.Album),
                        MenuOption(SongSort.RECENT, "Recently added", Icons.Filled.Schedule),
                        MenuOption(SongSort.DURATION, "Duration", Icons.Filled.Timer),
                    ),
                    selected = sort, onSelect = library::setSort,
                    reversed = reversed, onReversed = library::setReversed,
                )
            }
        },
    ) { padding ->
        when {
            !loaded || !ready -> ListSkeleton(modifier = Modifier.padding(padding))
            songs.isEmpty() -> EmptyState(
                Icons.Filled.MusicNote, "No music found",
                "Add audio files to this device and they'll show up here.", Modifier.padding(padding),
            )
            else -> LazyColumn(contentPadding = padding) {
                item(contentType = "buttons") {
                    PlayShuffleButtons(
                        onPlay = { player.playSongs(songs, 0, shuffle = false) },
                        onShuffle = { player.shufflePlay(songs) },
                    )
                }
                itemsIndexed(songs, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { i, s ->
                    SongRow(s, s.id == playingId, onClick = { player.playSongs(songs, i) }, onMore = { onMore(s) }, modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).softEntrance())
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Albums

@Composable
fun AlbumsScreen(library: LibraryViewModel, onAlbum: (Long) -> Unit, onBack: () -> Unit) {
    val albums by library.albums.collectAsStateWithLifecycle()
    val loaded by library.loaded.collectAsStateWithLifecycle()

    LargeTitleScaffold("Albums", onBack = onBack) { padding ->
        when {
            !loaded -> ListSkeleton(modifier = Modifier.padding(padding))
            albums.isEmpty() -> EmptyState(Icons.Filled.Album, "No albums", "Albums appear once you have music on this device.", Modifier.padding(padding))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(albums, key = { it.id }, contentType = { "album" }) { album ->
                    Column(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).clickable { onAlbum(album.id) }) {
                        ArtImage(album.artSongUri, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(24.dp), sizePx = 384)
                        Spacer(Modifier.size(8.dp))
                        Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${album.artist} · ${album.songCount} ${if (album.songCount == 1) "song" else "songs"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    library: LibraryViewModel,
    player: PlayerViewModel,
    playingId: Long?,
    actions: SongActions,
    onBack: () -> Unit,
    onMore: (Song) -> Unit,
) {
    val all by library.allSongs.collectAsStateWithLifecycle()
    val songs = remember(all, albumId) {
        all.filter { it.albumId == albumId }.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
    }
    val first = songs.firstOrNull()
    DetailScreen(
        title = first?.album ?: "Album",
        subtitle = if (first == null) "" else "${songs.map { it.artist }.distinct().singleOrNull() ?: "Various artists"} · ${songs.size} songs",
        artUri = first?.uri,
        songs = songs, player = player, playingId = playingId, actions = actions, onBack = onBack, onMore = onMore,
    )
}

// ---------------------------------------------------------------- Artists

@Composable
fun ArtistsScreen(library: LibraryViewModel, onArtist: (Long) -> Unit, onBack: () -> Unit) {
    val artists by library.artists.collectAsStateWithLifecycle()
    val loaded by library.loaded.collectAsStateWithLifecycle()

    LargeTitleScaffold("Artists", onBack = onBack) { padding ->
        when {
            !loaded -> ListSkeleton(modifier = Modifier.padding(padding))
            artists.isEmpty() -> EmptyState(Icons.Filled.Person, "No artists", "Artists appear once you have music on this device.", Modifier.padding(padding))
            else -> LazyColumn(contentPadding = padding) {
                items(artists, key = { it.id }, contentType = { "artist" }) { artist ->
                    ListItem(
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null).clickable { onArtist(artist.id) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        },
                        headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text("${artist.songCount} songs · ${artist.albumCount} ${if (artist.albumCount == 1) "album" else "albums"}")
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artistId: Long,
    library: LibraryViewModel,
    player: PlayerViewModel,
    playingId: Long?,
    actions: SongActions,
    onBack: () -> Unit,
    onMore: (Song) -> Unit,
) {
    val all by library.allSongs.collectAsStateWithLifecycle()
    val songs = remember(all, artistId) {
        all.filter { it.artistId == artistId }
            .sortedWith(compareBy({ it.album.lowercase() }, { it.track }, { it.title.lowercase() }))
    }
    val first = songs.firstOrNull()
    DetailScreen(
        title = first?.artist ?: "Artist",
        subtitle = "${songs.size} songs · ${songs.map { it.albumId }.distinct().size} albums",
        artUri = first?.uri,
        songs = songs, player = player, playingId = playingId, actions = actions, onBack = onBack, onMore = onMore,
    )
}

// ---------------------------------------------------------------- shared detail layout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    title: String,
    subtitle: String,
    artUri: Uri?,
    songs: List<Song>,
    player: PlayerViewModel,
    playingId: Long?,
    actions: SongActions,
    onBack: () -> Unit,
    onMore: (Song) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (songs.isNotEmpty()) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                        AppMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            AppMenuItem(text = { Text("Play next") }, onClick = { menu = false; player.playNext(songs) })
                            AppMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; player.addToQueue(songs) })
                            AppMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; actions.openAddToPlaylist(songs.map { it.id }) })
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (songs.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "Nothing here", "These songs are no longer on this device.", Modifier.padding(padding))
        } else {
            LazyColumn(contentPadding = padding) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtImage(artUri, Modifier.size(112.dp), RoundedCornerShape(28.dp), sizePx = 384)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    PlayShuffleButtons(
                        onPlay = { player.playSongs(songs, 0, shuffle = false) },
                        onShuffle = { player.shufflePlay(songs) },
                    )
                }
                itemsIndexed(songs, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { i, s ->
                    SongRow(s, s.id == playingId, onClick = { player.playSongs(songs, i) }, onMore = { onMore(s) }, modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                }
            }
        }
    }
}


/** Rows glide in (short fade + small rise) as they scroll into view. One cheap animation per row, drawn in the graphics layer. */
private fun Modifier.softEntrance(): Modifier = composed {
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    graphicsLayer {
        val v = p.value
        alpha = v
        translationY = (1f - v) * 18.dp.toPx()
    }
}
