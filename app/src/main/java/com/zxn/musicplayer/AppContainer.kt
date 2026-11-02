package com.zxn.musicplayer

import android.content.Context
import com.zxn.musicplayer.data.PlaylistRepository
import com.zxn.musicplayer.data.SongRepository
import com.zxn.musicplayer.data.db.AppDatabase
import com.zxn.musicplayer.data.tags.TagDatabase
import com.zxn.musicplayer.data.tags.TagRepository
import com.zxn.musicplayer.player.EffectsManager
import com.zxn.musicplayer.player.QueueStore
import com.zxn.musicplayer.player.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (no Hilt). Access via (application as MusicApp).container. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database = AppDatabase.create(context)
    val songRepository = SongRepository(context)
    val playlistRepository = PlaylistRepository(database)
    val queueStore = QueueStore(context)
    val effects = EffectsManager(context)
    val sleepTimer = SleepTimer(appScope)
    val history = com.zxn.musicplayer.data.db.HistoryDatabase.create(context).dao()
    val artPreloader = com.zxn.musicplayer.player.ArtPreloader(context.applicationContext, appScope)
    val tagRepository = TagRepository(context.applicationContext, TagDatabase.create(context))
}
