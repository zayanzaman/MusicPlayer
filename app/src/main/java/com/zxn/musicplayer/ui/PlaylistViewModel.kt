package com.zxn.musicplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zxn.musicplayer.MusicApp
import com.zxn.musicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistItem(val id: Long, val name: String, val songCount: Int, val artSongUri: Uri?, val coverFile: java.io.File? = null, val coverVersion: Long = 0)
data class PlaylistEntry(val entryId: Long, val song: Song)
data class PlaylistDetail(val id: Long, val name: String, val entries: List<PlaylistEntry>)

/** Playlists, favorites and play history, resolved against the scanned library. Songs missing from the device are skipped. */
class PlaylistViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as MusicApp).container
    private val repo = container.playlistRepository
    private val songMap: Flow<Map<Long, Song>> = container.songRepository.songs.map { list -> list.associateBy { it.id } }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // When each playlist was last played (set by markPlayed); playlists are listed most recently played first.
    private val playPrefs = app.getSharedPreferences("playlist_prefs", android.content.Context.MODE_PRIVATE)
    private val lastPlayed = kotlinx.coroutines.flow.MutableStateFlow(
        playPrefs.all.mapNotNull { (k, v) -> if (k.startsWith("played_") && v is Long) k.removePrefix("played_").toLongOrNull()?.let { it to v } else null }.toMap()
    )

    fun markPlayed(playlistId: Long) {
        val t = System.currentTimeMillis()
        playPrefs.edit().putLong("played_$playlistId", t).apply()
        lastPlayed.value = lastPlayed.value + (playlistId to t)
    }

    val playlists: StateFlow<List<PlaylistItem>> = combine(repo.allPlaylists, songMap, lastPlayed) { lists, map, played ->
        lists.sortedWith(compareByDescending<com.zxn.musicplayer.data.db.PlaylistWithCount> { played[it.playlist.id] ?: 0L }.thenByDescending { it.playlist.createdAt }).map {
            val cover = com.zxn.musicplayer.data.PlaylistCovers.existing(getApplication<Application>(), it.playlist.id)
            PlaylistItem(it.playlist.id, it.playlist.name, it.songCount, it.firstSongId?.let { id -> map[id]?.uri }, cover, it.playlist.updatedAt + (cover?.lastModified() ?: 0L))
        }
    }.state(emptyList())

    val favoriteIds: StateFlow<Set<Long>> = repo.favoriteIds.map { it.toSet() }.state(emptySet())

    val favoriteSongs: StateFlow<List<Song>> =
        combine(repo.favoriteIds, songMap) { ids, map -> ids.mapNotNull { map[it] } }.state(emptyList())

    val recentSongs: StateFlow<List<Song>> =
        combine(repo.recent(50), songMap) { stats, map -> stats.mapNotNull { map[it.songId] } }.state(emptyList())

    /**
     * On Repeat: songs you actually listened to (30 s+) in the last 90 days, scored so recent plays count more
     * (a play's weight halves every 14 days). Top 15.
     */
    val mostPlayedSongs: StateFlow<List<Song>> =
        combine(container.history.observeSince(System.currentTimeMillis() - 90L * 24 * 3600 * 1000), songMap) { events, map ->
            val now = System.currentTimeMillis()
            events.groupBy { it.songId }
                .mapValues { (_, list) -> list.sumOf { Math.pow(0.5, (now - it.at) / (14.0 * 24 * 3600 * 1000)) } }
                .entries.sortedByDescending { it.value }
                .mapNotNull { map[it.key] }
                .take(15)
        }.state(emptyList())

    fun detail(id: Long): Flow<PlaylistDetail?> =
        combine(repo.playlist(id), repo.entries(id), songMap) { playlist, entries, map ->
            playlist?.let {
                PlaylistDetail(it.id, it.name, entries.mapNotNull { e -> map[e.songId]?.let { s -> PlaylistEntry(e.entryId, s) } })
            }
        }.flowOn(Dispatchers.Default)

    suspend fun songsOf(id: Long): List<Song> = detail(id).first()?.entries?.map { it.song }.orEmpty()

    fun create(name: String, songIds: List<Long> = emptyList()) { viewModelScope.launch { repo.create(name, songIds) } }

    /** Creates a playlist (optionally with a custom cover picked from the gallery) and reports its id. */
    fun createWithCover(name: String, songIds: List<Long>, cover: Uri?, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.create(name, songIds)
            if (cover != null) kotlinx.coroutines.withContext(Dispatchers.IO) { com.zxn.musicplayer.data.PlaylistCovers.save(getApplication<Application>(), id, cover) }
            repo.rename(id, name) // bumps updatedAt so the list re-reads the cover
            onCreated(id)
        }
    }

    fun setCover(id: Long, cover: Uri?) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                if (cover == null) com.zxn.musicplayer.data.PlaylistCovers.delete(getApplication<Application>(), id)
                else com.zxn.musicplayer.data.PlaylistCovers.save(getApplication<Application>(), id, cover)
            }
            repo.touch(id)
        }
    }
    fun rename(id: Long, name: String) { viewModelScope.launch { repo.rename(id, name) } }
    fun delete(id: Long) { viewModelScope.launch { repo.delete(id); com.zxn.musicplayer.data.PlaylistCovers.delete(getApplication<Application>(), id) } }
    fun addSongs(playlistId: Long, songIds: List<Long>, toStart: Boolean = false) {
        viewModelScope.launch { repo.addSongs(playlistId, songIds, toStart) }
    }
    fun removeEntry(playlistId: Long, entryId: Long) { viewModelScope.launch { repo.removeEntry(playlistId, entryId) } }
    fun moveEntry(playlistId: Long, entryId: Long, targetEntryId: Long) {
        viewModelScope.launch { repo.moveEntryTo(playlistId, entryId, targetEntryId) }
    }
    fun toggleFavorite(songId: Long) { viewModelScope.launch { repo.toggleFavorite(songId) } }
}
