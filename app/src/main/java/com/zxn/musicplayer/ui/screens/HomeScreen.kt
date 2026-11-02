package com.zxn.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zxn.musicplayer.ui.LibraryViewModel
import com.zxn.musicplayer.ui.PlayerViewModel
import com.zxn.musicplayer.ui.PlaylistItem
import com.zxn.musicplayer.ui.PlaylistViewModel
import com.zxn.musicplayer.ui.components.ArtImage
import com.zxn.musicplayer.ui.components.LargeTitleScaffold
import com.zxn.musicplayer.ui.components.ListSkeleton
import com.zxn.musicplayer.ui.components.TabActions
import com.zxn.musicplayer.ui.components.shimmer

// ---------------------------------------------------------------- shared cover pieces

/** Solid generic cover (used for fixed playlists and playlists without a cover). */
@Composable
fun GenericCover(
    icon: ImageVector, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp),
    gradient: Pair<Color, Color>? = null,
) {
    val cs = MaterialTheme.colorScheme
    val brush = if (gradient != null) Brush.linearGradient(listOf(gradient.first, gradient.second))
    else Brush.linearGradient(listOf(cs.surfaceContainerHighest, cs.surfaceContainerHigh))
    Box(modifier.clip(shape).background(brush), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.fillMaxSize(0.38f), tint = if (gradient != null) Color.White else cs.onSurfaceVariant)
    }
}

// Fixed playlist colors (app gradient style)
val LikedGradient = Color(0xFFFA2D48) to Color(0xFFFF6A7F)
val RecentGradient = Color(0xFFFF8A00) to Color(0xFFFFB347)
val RepeatGradient = Color(0xFF7B4DFF) to Color(0xFFB085FF)

/** Custom cover if the user picked one, else the first song's art, else a generic tile. */
@Composable
fun PlaylistCover(p: PlaylistItem, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp), sizePx: Int = 384) {
    val ctx = LocalContext.current
    when {
        p.coverFile != null -> {
            val request = remember(p.id, p.coverVersion) {
                ImageRequest.Builder(ctx).data(p.coverFile).memoryCacheKey("plcover_${p.id}_${p.coverVersion}").diskCacheKey("plcover_${p.id}_${p.coverVersion}").build()
            }
            AsyncImage(request, null, modifier.clip(shape), contentScale = ContentScale.Crop)
        }
        p.artSongUri != null -> ArtImage(p.artSongUri, modifier, shape, sizePx)
        else -> GenericCover(Icons.AutoMirrored.Filled.QueueMusic, modifier, shape)
    }
}

/** Square cover + title + subtitle, used in every horizontal row. */
@Composable
fun CoverCard(
    title: String, subtitle: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, width: Dp = 140.dp, circle: Boolean = false,
    art: @Composable (Modifier, Shape) -> Unit,
) {
    val shape: Shape = if (circle) CircleShape else RoundedCornerShape(14.dp)
    Column(modifier.width(width).clickable(onClick = onClick), horizontalAlignment = if (circle) Alignment.CenterHorizontally else Alignment.Start) {
        art(Modifier.size(width).aspectRatio(1f), shape)
        Spacer(Modifier.height(8.dp))
        Text(
            title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (circle) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth(),
        )
        if (subtitle.isNotEmpty()) Text(
            subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (circle) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp))
}

private val rowPadding = PaddingValues(horizontal = 16.dp)

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(
    library: LibraryViewModel,
    playlistVm: PlaylistViewModel,
    player: PlayerViewModel,
    ready: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmart: (String) -> Unit,
    onCreate: () -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
) {
    val loaded by library.loaded.collectAsStateWithLifecycle()
    val all by library.allSongs.collectAsStateWithLifecycle()
    val playlists by playlistVm.playlists.collectAsStateWithLifecycle()
    val favorites by playlistVm.favoriteSongs.collectAsStateWithLifecycle()
    val recent by playlistVm.recentSongs.collectAsStateWithLifecycle()
    val most by playlistVm.mostPlayedSongs.collectAsStateWithLifecycle()

    // artists / albums ordered by what you listened to most recently, then the rest
    val artists = remember(recent, all) {
        val byArtist = all.groupBy { it.artistId }
        val order = recent.map { it.artistId }.distinct()
        (order + byArtist.keys.filter { it !in order }).take(20).mapNotNull { id -> byArtist[id]?.first() }
    }
    val albums = remember(recent, all) {
        val byAlbum = all.groupBy { it.albumId }
        val order = recent.map { it.albumId }.distinct()
        (order + byAlbum.keys.filter { it !in order }).take(20).mapNotNull { id -> byAlbum[id]?.first() }
    }
    val added = remember(all) { all.sortedByDescending { it.dateAddedSec }.take(20) }
    val recents = remember(recent) { recent.take(20) }

    LargeTitleScaffold("Home", actions = { TabActions(onSearch, onSettings) }) { padding ->
        when {
            !loaded || !ready -> ListSkeleton(modifier = Modifier.padding(padding))
            else -> LazyColumn(contentPadding = padding) {
                item { SectionTitle("Your Playlists") }
                item {
                    LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            CoverCard("Liked Songs", "${favorites.size} songs", { onOpenSmart("favorites") }) { m, s ->
                                GenericCover(Icons.Filled.Favorite, m, s, gradient = LikedGradient)
                            }
                        }
                        item {
                            CoverCard("Recently Played", "${recent.size} songs", { onOpenSmart("recent") }) { m, s ->
                                GenericCover(Icons.Filled.History, m, s, gradient = RecentGradient)
                            }
                        }
                        item {
                            CoverCard("On Repeat", "${most.size} songs", { onOpenSmart("most") }) { m, s ->
                                GenericCover(Icons.Filled.Repeat, m, s, gradient = RepeatGradient)
                            }
                        }
                        items(playlists, key = { it.id }) { p ->
                            CoverCard(p.name, "${p.songCount} ${if (p.songCount == 1) "song" else "songs"}", { onOpenPlaylist(p.id) }) { m, s ->
                                PlaylistCover(p, m, s)
                            }
                        }
                        item {
                            CoverCard("New Playlist", "", onCreate) { m, s -> GenericCover(Icons.Filled.Add, m, s) }
                        }
                    }
                }

                if (recents.isNotEmpty()) {
                    item { SectionTitle("Recents") }
                    item {
                        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(recents, key = { _, s -> s.id }) { i, s ->
                                CoverCard(s.title, s.artist, { player.playSongs(recents, i) }) { m, sh -> ArtImage(s.uri, m, sh, 384) }
                            }
                        }
                    }
                }
                if (artists.isNotEmpty()) {
                    item { SectionTitle("Artists") }
                    item {
                        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(artists, key = { it.artistId }) { s ->
                                CoverCard(s.artist, "", { onArtist(s.artistId) }, circle = true, width = 120.dp) { m, sh -> ArtImage(s.uri, m, sh, 384) }
                            }
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { SectionTitle("Albums") }
                    item {
                        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(albums, key = { it.albumId }) { s ->
                                CoverCard(s.album, s.artist, { onAlbum(s.albumId) }) { m, sh -> ArtImage(s.uri, m, sh, 384) }
                            }
                        }
                    }
                }
                if (added.isNotEmpty()) {
                    item { SectionTitle("Recently Added") }
                    item {
                        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(added, key = { _, s -> s.id }) { i, s ->
                                CoverCard(s.title, s.artist, { player.playSongs(added, i) }) { m, sh -> ArtImage(s.uri, m, sh, 384) }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Your Library (sub-navigation hub)

@Composable
fun LibraryHubScreen(
    library: LibraryViewModel,
    playlistVm: PlaylistViewModel,
    onLiked: () -> Unit,
    onPlaylists: () -> Unit,
    onArtists: () -> Unit,
    onAlbums: () -> Unit,
) {
    val favorites by playlistVm.favoriteSongs.collectAsStateWithLifecycle()
    val playlists by playlistVm.playlists.collectAsStateWithLifecycle()
    val artists by library.artists.collectAsStateWithLifecycle()
    val albums by library.albums.collectAsStateWithLifecycle()

    LargeTitleScaffold("Your Library") { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = com.zxn.musicplayer.ui.components.LocalBottomInset.current),
          ) {
            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                HubItem(Icons.Filled.Favorite, "Liked Songs", "${favorites.size}", onLiked)
                HubItem(Icons.AutoMirrored.Filled.QueueMusic, "Playlists", "${playlists.size}", onPlaylists)
                HubItem(Icons.Filled.Person, "Artists", "${artists.size}", onArtists)
                HubItem(Icons.Filled.Album, "Albums", "${albums.size}", onAlbums)
            }
          }
        }
    }
}

@Composable
private fun HubItem(icon: ImageVector, title: String, count: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(count, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
