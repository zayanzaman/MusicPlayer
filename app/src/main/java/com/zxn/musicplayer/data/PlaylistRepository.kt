package com.zxn.musicplayer.data

import com.zxn.musicplayer.data.db.AppDatabase
import com.zxn.musicplayer.data.db.PlaylistEntity
import com.zxn.musicplayer.data.db.PlaylistSongEntity
import com.zxn.musicplayer.data.db.PlaylistWithCount
import com.zxn.musicplayer.data.db.SongStatsEntity
import kotlinx.coroutines.flow.Flow

/** Playlists, favorites and play stats. Songs are referenced by MediaStore id only. */
class PlaylistRepository(db: AppDatabase) {
    private val playlists = db.playlistDao()
    private val favorites = db.favoriteDao()
    private val stats = db.statsDao()

    private fun now() = System.currentTimeMillis()

    // Playlists
    val allPlaylists: Flow<List<PlaylistWithCount>> = playlists.observePlaylists()
    fun playlist(id: Long): Flow<PlaylistEntity?> = playlists.observePlaylist(id)
    fun entries(id: Long): Flow<List<PlaylistSongEntity>> = playlists.observeEntries(id)

    suspend fun create(name: String, initialSongIds: List<Long> = emptyList()): Long {
        val t = now()
        val id = playlists.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = t, updatedAt = t))
        if (initialSongIds.isNotEmpty()) playlists.addSongs(id, initialSongIds, t)
        return id
    }

    suspend fun touch(id: Long) = playlists.touch(id, now())
    suspend fun rename(id: Long, name: String) = playlists.rename(id, name.trim(), now())
    suspend fun delete(id: Long) = playlists.delete(id)
    suspend fun addSongs(playlistId: Long, songIds: List<Long>, toStart: Boolean = false) =
        if (toStart) playlists.addSongsToStart(playlistId, songIds, now()) else playlists.addSongs(playlistId, songIds, now())
    suspend fun removeEntry(playlistId: Long, entryId: Long) = playlists.removeEntryAndCompact(playlistId, entryId, now())
    suspend fun moveEntryTo(playlistId: Long, entryId: Long, targetEntryId: Long) =
        playlists.moveEntryTo(playlistId, entryId, targetEntryId, now())
    suspend fun moveEntry(playlistId: Long, from: Int, to: Int) = playlists.moveEntry(playlistId, from, to, now())

    // Favorites
    val favoriteIds: Flow<List<Long>> = favorites.observeIds()
    suspend fun toggleFavorite(songId: Long) = favorites.toggle(songId, now())

    // Stats
    suspend fun recordPlay(songId: Long) = stats.recordPlay(songId, now())
    fun recent(limit: Int = 50): Flow<List<SongStatsEntity>> = stats.observeRecent(limit)
    fun mostPlayed(limit: Int = 50): Flow<List<SongStatsEntity>> = stats.observeMostPlayed(limit)
}
