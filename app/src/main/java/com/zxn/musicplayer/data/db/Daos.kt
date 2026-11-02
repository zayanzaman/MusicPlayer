package com.zxn.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_songs WHERE playlistId = p.id) AS songCount, " +
            "(SELECT songId FROM playlist_songs WHERE playlistId = p.id ORDER BY position LIMIT 1) AS firstSongId " +
            "FROM playlists p ORDER BY p.name COLLATE NOCASE"
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position")
    fun observeEntries(id: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position")
    suspend fun getEntries(id: Long): List<PlaylistSongEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertEntries(entries: List<PlaylistSongEntity>)

    @Update
    suspend fun updateEntries(entries: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE entryId = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Transaction
    suspend fun addSongs(playlistId: Long, songIds: List<Long>, now: Long) {
        var pos = maxPosition(playlistId)
        insertEntries(songIds.map { PlaylistSongEntity(playlistId = playlistId, songId = it, position = ++pos, addedAt = now) })
        touch(playlistId, now)
    }

    /** "Add to top": new songs go first, existing songs shift down. */
    @Transaction
    suspend fun addSongsToStart(playlistId: Long, songIds: List<Long>, now: Long) {
        val n = songIds.size
        if (n == 0) return
        updateEntries(getEntries(playlistId).map { it.copy(position = it.position + n) })
        insertEntries(songIds.mapIndexed { i, id -> PlaylistSongEntity(playlistId = playlistId, songId = id, position = i, addedAt = now) })
        touch(playlistId, now)
    }

    /** Moves [entryId] to where [targetEntryId] currently is (safe even if some songs are missing from the UI list). */
    @Transaction
    suspend fun moveEntryTo(playlistId: Long, entryId: Long, targetEntryId: Long, now: Long) {
        val list = getEntries(playlistId).toMutableList()
        val from = list.indexOfFirst { it.entryId == entryId }
        val to = list.indexOfFirst { it.entryId == targetEntryId }
        if (from < 0 || to < 0 || from == to) return
        list.add(to, list.removeAt(from))
        updateEntries(list.mapIndexed { i, e -> e.copy(position = i) })
        touch(playlistId, now)
    }

    /** Moves an entry from index [from] to index [to] and rewrites positions. */
    @Transaction
    suspend fun moveEntry(playlistId: Long, from: Int, to: Int, now: Long) {
        val list = getEntries(playlistId).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        updateEntries(list.mapIndexed { i, e -> e.copy(position = i) })
        touch(playlistId, now)
    }

    @Transaction
    suspend fun removeEntryAndCompact(playlistId: Long, entryId: Long, now: Long) {
        deleteEntry(entryId)
        updateEntries(getEntries(playlistId).mapIndexed { i, e -> e.copy(position = i) })
        touch(playlistId, now)
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)

    @Query("SELECT COUNT(*) FROM favorites WHERE songId = :songId")
    suspend fun isFavorite(songId: Long): Int

    @Transaction
    suspend fun toggle(songId: Long, now: Long) {
        if (isFavorite(songId) > 0) remove(songId) else add(FavoriteEntity(songId, now))
    }
}

@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(stats: SongStatsEntity)

    @Query("UPDATE song_stats SET playCount = playCount + 1, lastPlayed = :now WHERE songId = :songId")
    suspend fun increment(songId: Long, now: Long)

    // No SQLite upsert: Android 8.0 ships SQLite 3.18, which predates it.
    @Transaction
    suspend fun recordPlay(songId: Long, now: Long) {
        insertIfMissing(SongStatsEntity(songId, 0, 0))
        increment(songId, now)
    }

    @Query("SELECT * FROM song_stats WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SongStatsEntity>>

    @Query("SELECT * FROM song_stats WHERE playCount > 0 ORDER BY playCount DESC, lastPlayed DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<SongStatsEntity>>
}
