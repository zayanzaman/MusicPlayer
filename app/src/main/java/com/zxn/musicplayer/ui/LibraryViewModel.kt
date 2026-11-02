package com.zxn.musicplayer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zxn.musicplayer.MusicApp
import com.zxn.musicplayer.data.Album
import com.zxn.musicplayer.data.Artist
import com.zxn.musicplayer.data.Song
import com.zxn.musicplayer.data.toAlbums
import com.zxn.musicplayer.data.toArtists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SongSort(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), RECENT("Recently added"), DURATION("Duration"),
}

/** Library data for the UI: songs (sorted), albums, artists. */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MusicApp).container.songRepository
    private val prefs = app.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    val loaded: StateFlow<Boolean> = repo.loaded
    /** Unsorted, as scanned. Detail/search screens derive from this. */
    val allSongs: StateFlow<List<Song>> = repo.songs

    private val _sort = MutableStateFlow(
        runCatching { SongSort.valueOf(prefs.getString("song_sort", SongSort.TITLE.name)!!) }.getOrDefault(SongSort.TITLE)
    )
    val sort: StateFlow<SongSort> = _sort.asStateFlow()

    fun setSort(sort: SongSort) {
        _sort.value = sort
        prefs.edit().putString("song_sort", sort.name).apply()
    }

    private val _reversed = MutableStateFlow(prefs.getBoolean("song_sort_rev", false))
    val reversed: StateFlow<Boolean> = _reversed.asStateFlow()
    fun setReversed(value: Boolean) {
        _reversed.value = value
        prefs.edit().putBoolean("song_sort_rev", value).apply()
    }

    val sortedSongs: StateFlow<List<Song>> = combine(repo.songs, _sort, _reversed) { songs, sort, rev ->
        val sorted = when (sort) {
            SongSort.TITLE -> songs.sortedBy { it.title.lowercase() }
            SongSort.ARTIST -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            SongSort.ALBUM -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.track }, { it.title.lowercase() }))
            SongSort.RECENT -> songs.sortedByDescending { it.dateAddedSec }
            SongSort.DURATION -> songs.sortedByDescending { it.durationMs }
        }
        if (rev) sorted.reversed() else sorted
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = repo.songs.map { it.toAlbums() }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<Artist>> = repo.songs.map { it.toArtists() }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hiddenSongs: StateFlow<List<Song>> = repo.hiddenSongs
    fun hide(songId: Long) = repo.hide(songId)
    fun unhide(songId: Long) = repo.unhide(songId)

    fun refresh() { viewModelScope.launch { repo.refresh() } }
    suspend fun refreshNow() = repo.refresh()
}
