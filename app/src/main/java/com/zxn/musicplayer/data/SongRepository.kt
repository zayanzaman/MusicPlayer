package com.zxn.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Scans MediaStore for local music. Call [refresh] after permission is granted and on app resume. */
class SongRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
    private var raw: List<Song> = emptyList()
    private val hiddenIds = MutableStateFlow(loadHidden())

    /** Visible songs only (hidden ones are filtered out everywhere in the app). */
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _hiddenSongs = MutableStateFlow<List<Song>>(emptyList())
    val hiddenSongs: StateFlow<List<Song>> = _hiddenSongs.asStateFlow()

    /** False until the first scan finishes, so the UI can tell "loading" from "no music". */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun refresh() {
        val result = withContext(Dispatchers.IO) { query() }
        raw = result
        publish()
        _loaded.value = true
    }

    fun hide(songId: Long) { hiddenIds.value = hiddenIds.value + songId; saveHidden(); publish() }
    fun unhide(songId: Long) { hiddenIds.value = hiddenIds.value - songId; saveHidden(); publish() }

    private fun publish() {
        val hidden = hiddenIds.value
        _songs.value = raw.filter { it.id !in hidden }
        _hiddenSongs.value = raw.filter { it.id in hidden }
    }

    private fun loadHidden(): Set<Long> =
        prefs.getStringSet("hidden", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty()

    private fun saveHidden() {
        prefs.edit().putStringSet("hidden", hiddenIds.value.map { it.toString() }.toSet()).apply()
    }

    private fun query(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(MIN_DURATION_MS.toString())
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val out = ArrayList<Song>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iArtistId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                out += Song(
                    id = id,
                    title = c.getString(iTitle).clean("Unknown title"),
                    artist = c.getString(iArtist).clean("Unknown artist"),
                    album = c.getString(iAlbum).clean("Unknown album"),
                    albumId = c.getLong(iAlbumId),
                    artistId = c.getLong(iArtistId),
                    durationMs = c.getLong(iDur),
                    track = c.getInt(iTrack) % 1000, // MediaStore encodes disc*1000 + track
                    year = c.getInt(iYear),
                    dateAddedSec = c.getLong(iAdded),
                    uri = ContentUris.withAppendedId(collection, id),
                )
            }
        }
        return out
    }

    private fun String?.clean(fallback: String): String =
        if (isNullOrBlank() || this == MediaStore.UNKNOWN_STRING) fallback else this

    companion object {
        /** Hides tiny clips (notification sounds etc.). Make this a setting later if wanted. */
        const val MIN_DURATION_MS = 10_000L
    }
}
