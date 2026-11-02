package com.zxn.musicplayer.data.tags

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Which tag fields a suggestion can change. Used as bit flags in [TagSuggestion.excludedMask]. */
enum class TagField(val bit: Int, val label: String) {
    TITLE(1, "Title"), ARTIST(2, "Artist"), ALBUM(4, "Album"), ALBUM_ARTIST(8, "Album artist"),
    YEAR(16, "Year"), TRACK(32, "Track no."), GENRE(64, "Genre")
}

object TagStatus {
    const val PENDING = "PENDING"         // proposal waiting for the user
    const val APPROVED = "APPROVED"       // approved, not yet written to the file
    const val REJECTED = "REJECTED"       // user said no; not shown again unless the file changes / decisions reset
    const val APPLIED = "APPLIED"         // written into the file (can be undone)
    const val NO_CHANGE = "NO_CHANGE"     // tags already good; skipped on later scans
    const val NOT_FOUND = "NOT_FOUND"     // no confident match online; skipped on later scans
    const val UNSUPPORTED = "UNSUPPORTED" // format we can't write (only MP3 + M4A)
    const val FAILED = "FAILED"           // network/IO error; retried on next scan
}

/**
 * One row per song. cur* = tags found in the file at scan time (also the data used to undo),
 * new* = proposed values (null = leave that field alone).
 * [fingerprint] = "size:dateModified"; when it changes the file was edited elsewhere and the song is re-scanned.
 */
@Entity(tableName = "tag_suggestions", indices = [Index("status")])
data class TagSuggestion(
    @PrimaryKey val songId: Long,
    val fingerprint: String,
    val status: String,
    val fileName: String = "",
    val curTitle: String? = null, val curArtist: String? = null, val curAlbum: String? = null,
    val curAlbumArtist: String? = null, val curYear: Int? = null, val curTrack: Int? = null, val curGenre: String? = null,
    val newTitle: String? = null, val newArtist: String? = null, val newAlbum: String? = null,
    val newAlbumArtist: String? = null, val newYear: Int? = null, val newTrack: Int? = null, val newGenre: String? = null,
    val excludedMask: Int = 0,
    val mbid: String? = null,
    val confidence: Int = 0,
    val error: String? = null,
    val updatedAt: Long = 0,
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tag_suggestions")
    suspend fun all(): List<TagSuggestion>

    @Query("SELECT * FROM tag_suggestions WHERE status = :status ORDER BY curArtist, curTitle")
    fun observeByStatus(status: String): Flow<List<TagSuggestion>>

    @Query("SELECT status, COUNT(*) AS count FROM tag_suggestions GROUP BY status")
    fun observeCounts(): Flow<List<StatusCount>>

    @Query("SELECT * FROM tag_suggestions WHERE songId = :id")
    suspend fun get(id: Long): TagSuggestion?

    @Query("SELECT * FROM tag_suggestions WHERE status = :status")
    suspend fun byStatus(status: String): List<TagSuggestion>

    @Query("SELECT * FROM tag_suggestions WHERE songId IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<TagSuggestion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: TagSuggestion)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(rows: List<TagSuggestion>)

    @Query("UPDATE tag_suggestions SET status = :status, updatedAt = :now WHERE songId IN (:ids)")
    suspend fun setStatus(ids: List<Long>, status: String, now: Long)

    @Query("UPDATE tag_suggestions SET status = :to, updatedAt = :now WHERE status = :from")
    suspend fun moveAll(from: String, to: String, now: Long)

    @Query("UPDATE tag_suggestions SET excludedMask = :mask WHERE songId = :id")
    suspend fun setMask(id: Long, mask: Int)

    @Query("DELETE FROM tag_suggestions WHERE status IN (:statuses)")
    suspend fun deleteWithStatus(statuses: List<String>)

    @Query("DELETE FROM tag_suggestions WHERE songId IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM tag_suggestions")
    suspend fun clear()
}

data class StatusCount(val status: String, val count: Int)

/** Separate database so tag work can never affect playlists/favorites. */
@Database(entities = [TagSuggestion::class], version = 1, exportSchema = false)
abstract class TagDatabase : RoomDatabase() {
    abstract fun dao(): TagDao

    companion object {
        fun create(context: Context): TagDatabase =
            Room.databaseBuilder(context.applicationContext, TagDatabase::class.java, "tags.db")
                .fallbackToDestructiveMigration() // it's only a cache of suggestions
                .build()
    }
}
