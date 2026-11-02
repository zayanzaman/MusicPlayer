package com.zxn.musicplayer.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zxn.musicplayer.ui.screens.AlbumDetailScreen
import com.zxn.musicplayer.ui.screens.EqualizerScreen
import com.zxn.musicplayer.ui.screens.HiddenSongsScreen
import com.zxn.musicplayer.ui.screens.SettingsScreen
import com.zxn.musicplayer.ui.screens.AlbumsScreen
import com.zxn.musicplayer.ui.screens.ArtistDetailScreen
import com.zxn.musicplayer.ui.screens.ArtistsScreen
import com.zxn.musicplayer.ui.screens.NowPlayingScreen
import com.zxn.musicplayer.ui.screens.PlaylistDetailScreen
import com.zxn.musicplayer.ui.screens.PlaylistsScreen
import com.zxn.musicplayer.ui.screens.SmartListScreen
import com.zxn.musicplayer.ui.screens.SearchScreen
import com.zxn.musicplayer.ui.components.GlassTabBar
import com.zxn.musicplayer.ui.theme.MonoScope
import com.zxn.musicplayer.ui.components.TabItem
import com.zxn.musicplayer.MusicApp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.zxn.musicplayer.ui.screens.HomeScreen
import com.zxn.musicplayer.ui.screens.LibraryHubScreen
import com.zxn.musicplayer.ui.screens.CreatePlaylistScreen
import com.zxn.musicplayer.ui.screens.CreateDraft
import com.zxn.musicplayer.ui.components.LocalBottomInset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zxn.musicplayer.ui.screens.TagListScreen
import com.zxn.musicplayer.ui.screens.TagMode
import com.zxn.musicplayer.ui.screens.TagsHubScreen
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.zxn.musicplayer.ui.screens.SongsScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val CREATE = "create"
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
    const val SEARCH = "search"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val PLAYLIST = "playlist/{id}"
    const val SMART = "smart/{kind}"
    const val SETTINGS = "settings"
    const val HIDDEN = "hidden"
    const val EQUALIZER = "equalizer"
    const val TAGS = "tags"
    const val TAGS_LIST = "tags/{mode}"
    const val NOW_PLAYING = "nowplaying"
}

private val TABS = listOf(
    TabItem(Routes.HOME, "Home", Icons.Filled.Home),
    TabItem(Routes.SONGS, "Songs", Icons.Filled.MusicNote),
    TabItem(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic),
    TabItem(Routes.CREATE, "Create", Icons.Filled.Add),
)
private const val CREATE_INDEX = 3

/** Screens that show the floating tab bar (the four tabs and the Your Library sub-screens). */
private val BAR_ROUTES = setOf(Routes.HOME, Routes.SONGS, Routes.LIBRARY, Routes.CREATE, Routes.PLAYLISTS, Routes.ARTISTS, Routes.ALBUMS)

private fun tabIndexFor(route: String?): Int = when (route) {
    Routes.SONGS -> 1
    Routes.LIBRARY, Routes.PLAYLISTS, Routes.ARTISTS, Routes.ALBUMS -> 2
    Routes.CREATE -> CREATE_INDEX
    else -> 0
}

@Composable
fun AppRoot(player: PlayerViewModel, library: LibraryViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val state by player.state.collectAsStateWithLifecycle()
    val playlistVm: PlaylistViewModel = viewModel()
    val effectsVm: EffectsViewModel = viewModel()
    val tagVm: TagViewModel = viewModel()

    // One launcher for the system "allow changing these files" dialogs requested by the tag feature.
    var pendingWrite by remember { mutableStateOf<WriteRequest?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        pendingWrite?.result?.complete(r.resultCode == Activity.RESULT_OK)
        pendingWrite = null
    }
    LaunchedEffect(Unit) {
        tagVm.writeRequests.collect { req ->
            pendingWrite = req
            writeLauncher.launch(IntentSenderRequest.Builder(req.sender).build())
        }
    }
    val actions = rememberSongActions(player, library, playlistVm)

    val isTopLevel = route == null || route in BAR_ROUTES

    // Art for every song is resolved once at launch, so lists don't stall on covers while scrolling.
    val allSongs by library.allSongs.collectAsStateWithLifecycle()
    val preloader = (LocalContext.current.applicationContext as MusicApp).container.artPreloader
    val preloaded by preloader.ready.collectAsStateWithLifecycle()
    LaunchedEffect(allSongs) { preloader.start(allSongs) }
    val artReady = preloaded || allSongs.isEmpty()

    // Create-playlist pipeline state lives here so the tab bar's X can cancel it.
    val draft = remember { CreateDraft() }
    var confirmDiscard by remember { mutableStateOf(false) }
    val creating = route == Routes.CREATE
    fun cancelCreate() { draft.reset(); nav.popBackStack() }
    BackHandler(enabled = creating) { if (draft.isDirty) confirmDiscard = true else cancelCreate() }
    fun startCreate() { if (route != Routes.CREATE) { draft.reset(); nav.navigate(Routes.CREATE) { launchSingleTop = true } } }
    // Now Playing is a sheet-like overlay (not a nav destination) so the app stays visible behind it while you drag it away.
    var playerOpen by remember { mutableStateOf(false) }
    val inNowPlaying = playerOpen

    // If the queue is cleared while the full player is open, close it.
    LaunchedEffect(state.hasMedia, inNowPlaying) {
        if (inNowPlaying && !state.hasMedia) playerOpen = false
    }

    fun openNowPlaying() { playerOpen = true }
    BackHandler(enabled = playerOpen) { playerOpen = false }

    // Bars float over the content (glass), so top-level lists scroll underneath them. Other screens are simply
    // padded by the bar height; that padding animates so route changes don't jump.
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    val contentBottom by animateDpAsState(if (isTopLevel || inNowPlaying) 0.dp else barHeight, label = "contentBottom")

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomInset provides (if (isTopLevel) barHeight else 0.dp)) {
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = contentBottom)) {
            composable(Routes.HOME) {
                HomeScreen(
                    library, playlistVm, player, artReady,
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenPlaylist = { nav.navigate("playlist/$it") },
                    onOpenSmart = { nav.navigate("smart/$it") },
                    onCreate = ::startCreate,
                    onAlbum = { nav.navigate("album/$it") },
                    onArtist = { nav.navigate("artist/$it") },
                )
            }
            composable(Routes.SONGS) {
                SongsScreen(
                    library, player, state.songId, artReady,
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onMore = actions::openMenu,
                )
            }
            composable(Routes.LIBRARY) {
                LibraryHubScreen(
                    library, playlistVm,
                    onLiked = { nav.navigate("smart/favorites") },
                    onPlaylists = { nav.navigate(Routes.PLAYLISTS) },
                    onArtists = { nav.navigate(Routes.ARTISTS) },
                    onAlbums = { nav.navigate(Routes.ALBUMS) },
                )
            }
            composable(Routes.CREATE) {
                CreatePlaylistScreen(draft, playlistVm, library, onCreated = { id ->
                    draft.reset()
                    nav.navigate("playlist/$id") { popUpTo(Routes.CREATE) { inclusive = true } }
                })
            }
            composable(Routes.ALBUMS) { AlbumsScreen(library, onAlbum = { nav.navigate("album/$it") }, onBack = { nav.popBackStack() }) }
            composable(Routes.ARTISTS) { ArtistsScreen(library, onArtist = { nav.navigate("artist/$it") }, onBack = { nav.popBackStack() }) }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    playlistVm, player,
                    onBack = { nav.popBackStack() },
                    onOpenPlaylist = { nav.navigate("playlist/$it") },
                    onCreate = ::startCreate,
                )
            }
            composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PlaylistDetailScreen(
                    entry.arguments?.getLong("id") ?: -1L, playlistVm, library, player, actions, state.songId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SMART, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { entry ->
                SmartListScreen(
                    entry.arguments?.getString("kind") ?: "favorites", playlistVm, player, actions, state.songId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    library, player, state.songId,
                    onBack = { nav.popBackStack() },
                    onAlbum = { nav.navigate("album/$it") },
                    onArtist = { nav.navigate("artist/$it") },
                    playlistVm = playlistVm,
                    onPlaylist = { nav.navigate("playlist/$it") },
                    onMore = actions::openMenu,
                )
            }
            composable(Routes.ALBUM, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                AlbumDetailScreen(
                    entry.arguments?.getLong("id") ?: -1L, library, player, state.songId, actions,
                    onBack = { nav.popBackStack() }, onMore = actions::openMenu,
                )
            }
            composable(Routes.ARTIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ArtistDetailScreen(
                    entry.arguments?.getLong("id") ?: -1L, library, player, state.songId, actions,
                    onBack = { nav.popBackStack() }, onMore = actions::openMenu,
                )
            }
            composable(Routes.SETTINGS) {
                MonoScope {
                    SettingsScreen(
                        library, onBack = { nav.popBackStack() },
                        onEqualizer = { nav.navigate(Routes.EQUALIZER) },
                        onHidden = { nav.navigate(Routes.HIDDEN) },
                        onTags = { nav.navigate(Routes.TAGS) },
                    )
                }
            }
            composable(Routes.TAGS) {
                TagsHubScreen(tagVm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("tags/${it.route}") })
            }
            composable(Routes.TAGS_LIST, arguments = listOf(navArgument("mode") { type = NavType.StringType })) { entry ->
                val mode = TagMode.entries.firstOrNull { it.route == entry.arguments?.getString("mode") } ?: TagMode.REVIEW
                TagListScreen(tagVm, mode, onBack = { nav.popBackStack() })
            }
            composable(Routes.HIDDEN) { MonoScope { HiddenSongsScreen(library, onBack = { nav.popBackStack() }) } }
            composable(Routes.EQUALIZER) { MonoScope { EqualizerScreen(effectsVm, onBack = { nav.popBackStack() }) } }
        }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = playerOpen && state.hasMedia,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it },
        ) {
            NowPlayingScreen(
                player, playlistVm, actions,
                onOpenEqualizer = { playerOpen = false; nav.navigate(Routes.EQUALIZER) },
                onBack = { playerOpen = false },
            )
        }

        if (!inNowPlaying) {
            Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeight = with(density) { it.height.toDp() } }) {
                if (state.hasMedia) MiniPlayer(player, state, onOpen = ::openNowPlaying)
                if (isTopLevel) {
                    GlassTabBar(
                        items = TABS,
                        selectedIndex = tabIndexFor(route),
                        createIndex = CREATE_INDEX,
                        creating = creating,
                        onSelect = { i ->
                            val current = tabIndexFor(route)
                            when {
                                i == CREATE_INDEX -> startCreate()
                                i == current -> if (i == 2) nav.popBackStack(Routes.LIBRARY, inclusive = false)
                                else -> nav.navigate(TABS[i].route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onCancelCreate = { if (draft.isDirty) confirmDiscard = true else cancelCreate() },
                    )
                } else {
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }

    if (confirmDiscard) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard new playlist?") },
        text = { Text("Your name, cover and selected songs will be lost.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmDiscard = false; cancelCreate() }) { Text("Discard") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
    )

    SongActionsHost(actions)
}
