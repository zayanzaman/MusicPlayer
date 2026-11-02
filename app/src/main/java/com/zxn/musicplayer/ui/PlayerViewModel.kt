package com.zxn.musicplayer.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.zxn.musicplayer.MusicApp
import com.zxn.musicplayer.data.Song
import com.zxn.musicplayer.player.MediaIds
import com.zxn.musicplayer.player.PlaybackService
import com.zxn.musicplayer.player.SleepTimerState
import com.zxn.musicplayer.player.toMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class QueueEntry(
    val index: Int,
    val mediaId: String,
    val songId: Long?,
    val title: String,
    val artist: String,
    val artUri: Uri?,
)

data class PlayerState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasMedia: Boolean = false,
    val songId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artUri: Uri? = null,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val currentIndex: Int = 0,
    val queue: List<QueueEntry> = emptyList(),
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

/**
 * UI-side handle to the playback service (via MediaController). All playback and queue
 * operations go through here. Shuffle is "physical": it reorders the upcoming queue and
 * remembers the original order so it can be undone (see [toggleShuffle]).
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = (app as MusicApp).container.queueStore
    private val sleepTimer = (app as MusicApp).container.sleepTimer

    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state
    fun startSleepTimer(minutes: Int) = sleepTimer.startMinutes(minutes)
    fun startSleepTimerEndOfTrack() = sleepTimer.startEndOfTrack()
    fun cancelSleepTimer() = sleepTimer.cancel()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState(shuffle = store.shuffleOn))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Updated ~4x/second while playing; kept separate so the rest of the UI doesn't recompose. */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh(rebuildQueue = true)
        }, ContextCompat.getMainExecutor(app))

        viewModelScope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) _positionMs.value = c.currentPosition.coerceAtLeast(0L)
                delay(250)
            }
        }
    }

    // ---- state mirroring ---------------------------------------------------------------

    private fun refresh(rebuildQueue: Boolean) {
        val c = controller ?: return
        val item = c.currentMediaItem
        val md = item?.mediaMetadata
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: md?.durationMs ?: 0L
        val queue = if (rebuildQueue) buildQueue(c) else null
        _state.update { old ->
            old.copy(
                connected = true,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                hasMedia = item != null,
                songId = item?.mediaId?.let { MediaIds.songId(it) },
                title = md?.title?.toString().orEmpty(),
                artist = md?.artist?.toString().orEmpty(),
                album = md?.albumTitle?.toString().orEmpty(),
                artUri = md?.artworkUri,
                durationMs = duration,
                shuffle = store.shuffleOn,
                repeatMode = c.repeatMode,
                speed = c.playbackParameters.speed,
                pitch = c.playbackParameters.pitch,
                currentIndex = c.currentMediaItemIndex,
                hasNext = c.hasNextMediaItem(),
                hasPrevious = c.hasPreviousMediaItem(),
                queue = queue ?: old.queue,
            )
        }
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
    }

    private fun buildQueue(c: MediaController): List<QueueEntry> = List(c.mediaItemCount) { i ->
        val item = c.getMediaItemAt(i)
        QueueEntry(
            index = i,
            mediaId = item.mediaId,
            songId = MediaIds.songId(item.mediaId),
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            artUri = item.mediaMetadata.artworkUri,
        )
    }

    private fun items(c: MediaController): List<MediaItem> = List(c.mediaItemCount) { c.getMediaItemAt(it) }

    // ---- transport ---------------------------------------------------------------------

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() } // restarts the song if past 3s, else goes back
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs); _positionMs.value = positionMs }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** [pitch] null keeps the current pitch; pass the same value as speed for "tape" style, or 1f to keep pitch natural. */
    fun setSpeed(speed: Float, pitch: Float? = null) {
        val c = controller ?: return
        c.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4f), pitch ?: c.playbackParameters.pitch)
    }

    fun setPitch(pitch: Float) {
        val c = controller ?: return
        c.playbackParameters = PlaybackParameters(c.playbackParameters.speed, pitch.coerceIn(0.5f, 2f))
    }

    // ---- starting playback -------------------------------------------------------------

    /**
     * Replaces the queue with [songs] and starts at [startIndex].
     * [shuffle] = null keeps the current shuffle setting; true/false forces it.
     */
    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        val shuffleOn = shuffle ?: store.shuffleOn
        val all = songs.map { it.toMediaItem() }
        val start = startIndex.coerceIn(0, all.lastIndex)

        val queue: List<MediaItem>
        val queueStart: Int
        if (shuffleOn) {
            queue = listOf(all[start]) + all.filterIndexed { i, _ -> i != start }.shuffled()
            queueStart = 0
            store.originalIds = all.map { it.mediaId }
        } else {
            queue = all
            queueStart = start
            store.originalIds = emptyList()
        }
        store.shuffleOn = shuffleOn
        c.setMediaItems(queue, queueStart, 0L)
        c.prepare()
        c.play()
    }

    fun shufflePlay(songs: List<Song>) {
        if (songs.isNotEmpty()) playSongs(songs, Random.nextInt(songs.size), shuffle = true)
    }

    // ---- queue: add / reorder / remove -------------------------------------------------

    /** "Add to first": inserts right after the current song. Starts playback if the queue is empty. */
    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) { playSongs(songs); return }
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
    }

    /** "Add to last": appends to the end of the queue. Starts playback if the queue is empty. */
    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) { playSongs(songs); return }
        c.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) c.moveMediaItem(from, to)
    }

    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun skipToQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) { c.seekToDefaultPosition(index); c.play() }
    }

    /** Removes everything after the current song. */
    fun clearUpcoming() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
    }

    /** Empties the whole queue and stops playback. */
    fun clearQueue() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        store.originalIds = emptyList()
    }

    /** Used after a song is deleted from the device. */
    fun removeSongsFromQueue(songIds: Set<Long>) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            val id = MediaIds.songId(c.getMediaItemAt(i).mediaId)
            if (id != null && id in songIds) c.removeMediaItem(i)
        }
    }

    // ---- shuffle -----------------------------------------------------------------------

    /**
     * ON: shuffles everything after the current song and remembers the original order.
     * OFF: puts the queue back into the remembered order around the current song (songs added
     * while shuffled go to the end). Playback is never interrupted.
     */
    fun toggleShuffle() {
        val c = controller
        val turnOn = !store.shuffleOn
        store.shuffleOn = turnOn
        if (c != null && c.mediaItemCount > 1) {
            if (turnOn) shuffleUpcoming(c) else restoreOriginalOrder(c)
        } else if (!turnOn) {
            store.originalIds = emptyList()
        }
        _state.update { it.copy(shuffle = turnOn) }
    }

    private fun shuffleUpcoming(c: MediaController) {
        val all = items(c)
        val cur = c.currentMediaItemIndex
        store.originalIds = all.map { it.mediaId }
        val upcoming = all.subList(cur + 1, all.size).shuffled()
        if (upcoming.isNotEmpty()) c.replaceMediaItems(cur + 1, all.size, upcoming)
    }

    private fun restoreOriginalOrder(c: MediaController) {
        val all = items(c)
        val cur = c.currentMediaItemIndex
        val order = store.originalIds.withIndex().associate { (i, id) -> id to i }
        store.originalIds = emptyList()
        val curPos = order[all[cur].mediaId] ?: return
        val others = all.filterIndexed { i, _ -> i != cur }
        val (known, unknown) = others.partition { it.mediaId in order }
        val sorted = known.sortedBy { order.getValue(it.mediaId) }
        val before = sorted.filter { order.getValue(it.mediaId) < curPos }
        val after = sorted.filter { order.getValue(it.mediaId) > curPos } + unknown
        // Replace the later range first so the current item's index stays valid.
        if (cur + 1 < all.size || after.isNotEmpty()) c.replaceMediaItems(cur + 1, all.size, after)
        if (cur > 0 || before.isNotEmpty()) c.replaceMediaItems(0, cur, before)
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) } // does not stop playback; the service lives on
        super.onCleared()
    }
}
