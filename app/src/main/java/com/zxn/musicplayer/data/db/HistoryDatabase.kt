package com.zxn.musicplayer.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One row per "real" listen (a song played for 30 s, or half of a short song). Powers the On Repeat playlist. */
@Entity(tableName = "play_events")
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val at: Long,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun add(event: PlayEvent)

    @Query("SELECT * FROM play_events WHERE at >= :since")
    fun observeSince(since: Long): Flow<List<PlayEvent>>

    @Query("DELETE FROM play_events WHERE at < :before")
    suspend fun prune(before: Long)
}

/** Separate database so the listening history can never affect playlists. */
@Database(entities = [PlayEvent::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun dao(): HistoryDao

    companion object {
        fun create(context: Context): HistoryDatabase =
            Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, "history.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
