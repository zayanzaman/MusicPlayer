package com.zxn.musicplayer.player

import android.content.Context
import com.zxn.musicplayer.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Resolves every song's cover once at app start (into ArtLoader's disk cache), so lists never wait on art later.
 * On later launches almost everything is already cached and this finishes instantly. The UI shows placeholder rows
 * until [ready]; [ready] also flips after a short timeout so a first run on a huge library never blocks the app.
 */
class ArtPreloader(private val context: Context, private val scope: CoroutineScope) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private var started = false

    fun start(songs: List<Song>) {
        if (started || songs.isEmpty()) return
        started = true
        scope.launch(Dispatchers.Default) {
            // release the UI after at most 2.5 s; the work continues in the background
            launch { delay(2500); _ready.value = true }
            val pending = songs.filter { !ArtLoader.isCached(context, it.uri) }
            if (pending.isEmpty()) { _ready.value = true; _progress.value = 1f; return@launch }
            val done = AtomicInteger(0)
            val queue = java.util.concurrent.ConcurrentLinkedQueue(pending)
            // newest songs first = what people see first
            List(3) {
                async(Dispatchers.IO) {
                    while (true) {
                        val song = queue.poll() ?: break
                        runCatching { ArtLoader.load(context, song.uri, 384) }
                        _progress.value = done.incrementAndGet().toFloat() / pending.size
                    }
                }
            }.awaitAll()
            _progress.value = 1f
            _ready.value = true
        }
    }
}
