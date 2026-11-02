package com.zxn.musicplayer.player

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zxn.musicplayer.AppContainer
import com.zxn.musicplayer.MainActivity
import com.zxn.musicplayer.MusicApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the ExoPlayer + MediaSession. Provides background playback, the media notification,
 * lock-screen / Bluetooth / headset controls, queue persistence and play-count tracking.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private var countedMediaId: String? = null

    // "Real listen" tracking for On Repeat: counts once a song has actually played 30 s (or half of a short song).
    private var qualifyJob: kotlinx.coroutines.Job? = null
    private var playedMs = 0L
    private var playStartedAt = 0L
    private var qualified = false

    override fun onCreate() {
        super.onCreate()
        container = (application as MusicApp).container

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones unplug
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.repeatMode = container.queueStore.repeatMode
        player.addListener(PlayerListener())
        container.effects.attach(player.audioSessionId)
        container.sleepTimer.bind(player)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setBitmapLoader(SongBitmapLoader(this))
            .build()

        restoreQueue(player)
        scope.launch(Dispatchers.IO) { runCatching { container.history.prune(System.currentTimeMillis() - 120L * 24 * 3600 * 1000) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Keep playing when the app is swiped away; shut down if nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.player?.let { saveQueue(it) }
        container.sleepTimer.bind(null)
        container.effects.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ---- queue persistence -------------------------------------------------------------

    private fun saveQueue(player: Player) {
        val ids = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
        container.queueStore.saveQueue(ids, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0))
    }

    /** Restores the last queue (paused) so the app and notification resume where you left off. */
    private fun restoreQueue(player: Player) {
        val saved = container.queueStore.loadQueue() ?: return
        scope.launch {
            val repo = container.songRepository
            if (!repo.loaded.value && hasAudioPermission()) runCatching { repo.refresh() }
            if (player.mediaItemCount != 0) return@launch // user already started something

            val byId = repo.songs.value.associateBy { it.id }
            val items = ArrayList<MediaItem>()
            var newIndex = 0
            var indexSurvived = false
            saved.mediaIds.forEachIndexed { i, mediaId ->
                val song = MediaIds.songId(mediaId)?.let { byId[it] } ?: return@forEachIndexed
                if (i < saved.index) newIndex++
                if (i == saved.index) indexSurvived = true
                items += song.toMediaItem(mediaId)
            }
            if (items.isEmpty()) return@launch
            player.setMediaItems(
                items,
                newIndex.coerceAtMost(items.lastIndex),
                if (indexSurvived) saved.positionMs else 0L,
            )
            player.prepare() // stays paused
        }
    }

    private fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    // ---- play counts -------------------------------------------------------------------

    private fun countCurrent(player: Player) {
        val id = player.currentMediaItem?.mediaId ?: return
        if (id == countedMediaId) return
        countedMediaId = id
        MediaIds.songId(id)?.let { songId ->
            scope.launch(Dispatchers.IO) { container.playlistRepository.recordPlay(songId) }
        }
    }

    private fun trackListening(player: Player, transitioned: Boolean) {
        if (transitioned) {
            qualifyJob?.cancel(); playedMs = 0; playStartedAt = 0; qualified = false
        }
        if (qualified) return
        if (player.isPlaying) {
            if (playStartedAt != 0L) return // already timing
            playStartedAt = android.os.SystemClock.elapsedRealtime()
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 60_000L
            val needed = minOf(30_000L, maxOf(5_000L, dur / 2))
            val songId = player.currentMediaItem?.mediaId?.let { MediaIds.songId(it) } ?: return
            qualifyJob?.cancel()
            qualifyJob = scope.launch {
                kotlinx.coroutines.delay((needed - playedMs).coerceAtLeast(0))
                qualified = true
                withContext(Dispatchers.IO) { container.history.add(com.zxn.musicplayer.data.db.PlayEvent(songId = songId, at = System.currentTimeMillis())) }
            }
        } else if (playStartedAt != 0L) {
            playedMs += android.os.SystemClock.elapsedRealtime() - playStartedAt
            playStartedAt = 0
            qualifyJob?.cancel()
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            container.effects.attach(audioSessionId)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) container.sleepTimer.onEndOfTrackReached()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                )
            ) saveQueue(player)

            if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) container.queueStore.repeatMode = player.repeatMode

            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) countedMediaId = null
            if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                trackListening(player, events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION))
            }
            if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION) && player.isPlaying) {
                countCurrent(player)
            }
        }
    }
}
