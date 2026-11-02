package com.zxn.musicplayer.player

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SleepTimerState(val active: Boolean = false, val remainingMs: Long = 0L, val endOfTrack: Boolean = false)

/**
 * Pauses playback after a delay (volume fades over the last 10 s) or at the end of the current song.
 * Lives in the app container so it survives the UI; the service binds its player.
 */
class SleepTimer(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var job: Job? = null

    fun bind(player: ExoPlayer?) {
        if (player == null) cancel()
        this.player = player
    }

    fun startMinutes(minutes: Int) {
        reset()
        val total = minutes * 60_000L
        job = scope.launch {
            var remaining = total
            while (remaining > 0) {
                if (remaining % 1_000L == 0L) _state.value = SleepTimerState(active = true, remainingMs = remaining)
                if (remaining <= FADE_MS) player?.volume = (remaining.toFloat() / FADE_MS).coerceIn(0f, 1f)
                delay(TICK_MS)
                remaining -= TICK_MS
            }
            player?.pause()
            player?.volume = 1f
            _state.value = SleepTimerState()
        }
    }

    fun startEndOfTrack() {
        reset()
        player?.pauseAtEndOfMediaItems = true
        _state.value = SleepTimerState(active = true, endOfTrack = true)
    }

    /** Called by the service when ExoPlayer paused because [startEndOfTrack] asked it to. */
    fun onEndOfTrackReached() {
        if (_state.value.endOfTrack) {
            player?.pauseAtEndOfMediaItems = false
            _state.value = SleepTimerState()
        }
    }

    fun cancel() {
        reset()
        _state.value = SleepTimerState()
    }

    private fun reset() {
        job?.cancel()
        job = null
        player?.pauseAtEndOfMediaItems = false
        player?.volume = 1f
    }

    private companion object {
        const val FADE_MS = 10_000L
        const val TICK_MS = 250L
    }
}
