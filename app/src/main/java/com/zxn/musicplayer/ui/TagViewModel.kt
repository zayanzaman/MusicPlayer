package com.zxn.musicplayer.ui

import android.app.Application
import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zxn.musicplayer.MusicApp
import com.zxn.musicplayer.data.SongFileOps
import com.zxn.musicplayer.data.tags.ScanState
import com.zxn.musicplayer.data.tags.TagField
import com.zxn.musicplayer.data.tags.TagStatus
import com.zxn.musicplayer.data.tags.TagSuggestion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A system "allow this app to modify these files?" dialog the UI must launch; complete [result] with the outcome. */
class WriteRequest(val sender: IntentSender, val result: CompletableDeferred<Boolean> = CompletableDeferred())

class TagViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as MusicApp).container
    private val repo = container.tagRepository
    private val songs = container.songRepository
    private val prefs = app.getSharedPreferences("tag_prefs", Context.MODE_PRIVATE)

    private fun rows(status: String) =
        repo.observe(status).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pending: StateFlow<List<TagSuggestion>> = rows(TagStatus.PENDING)
    val approved: StateFlow<List<TagSuggestion>> = rows(TagStatus.APPROVED)
    val applied: StateFlow<List<TagSuggestion>> = rows(TagStatus.APPLIED)
    val rejected: StateFlow<List<TagSuggestion>> = rows(TagStatus.REJECTED)

    val counts: StateFlow<Map<String, Int>> =
        repo.counts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val scan: StateFlow<ScanState> = repo.scan

    private val _consent = MutableStateFlow(prefs.getBoolean("consent", false))
    val consent: StateFlow<Boolean> = _consent.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val requestChannel = Channel<WriteRequest>(Channel.UNLIMITED)
    val writeRequests = requestChannel.receiveAsFlow()

    // selection in the review list
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()
    fun toggleSelected(id: Long) { _selected.value = _selected.value.let { if (id in it) it - id else it + id } }
    fun selectAll(ids: List<Long>) { _selected.value = ids.toSet() }
    fun clearSelection() { _selected.value = emptySet() }

    fun giveConsent() { prefs.edit().putBoolean("consent", true).apply(); _consent.value = true }
    fun revokeConsent() { prefs.edit().putBoolean("consent", false).apply(); _consent.value = false; repo.cancelScan() }

    fun startScan() {
        if (!_consent.value) return
        val visible = songs.songs.value
        val known = visible.map { it.id }.toSet() + songs.hiddenSongs.value.map { it.id }
        repo.startScan(container.appScope, visible, known)
    }
    fun cancelScan() = repo.cancelScan()

    // ---- decisions
    fun approve(ids: Collection<Long>) = viewModelScope.launch { repo.approve(ids.toList()); clearSelection() }
    fun reject(ids: Collection<Long>) = viewModelScope.launch { repo.reject(ids.toList()); clearSelection() }
    fun restore(ids: Collection<Long>) = viewModelScope.launch { repo.backToPending(ids.toList()); clearSelection() }
    fun approveAll() = viewModelScope.launch { repo.approveAllPending(); clearSelection() }
    fun rejectAll() = viewModelScope.launch { repo.rejectAllPending(); clearSelection() }
    fun toggleField(id: Long, field: TagField) = viewModelScope.launch { repo.toggleField(id, field) }
    fun resetDecisions() = viewModelScope.launch {
        repo.resetDecisions(); _messages.tryEmit("Rejected and unmatched songs will be checked again on the next scan.")
    }
    fun included(row: TagSuggestion, f: TagField) = repo.included(row, f)
    fun changedFields(row: TagSuggestion) = repo.changedFields(row)
    fun songUri(id: Long) = repo.songUri(id)

    // ---- writing
    fun applyApproved() = viewModelScope.launch { run(repo.approved(), undo = false) }
    fun undo(ids: Collection<Long>) = viewModelScope.launch {
        val set = ids.toSet()
        run(repo.applied().filter { it.songId in set }, undo = true)
    }

    private suspend fun run(rows: List<TagSuggestion>, undo: Boolean) {
        if (_busy.value) return
        if (rows.isEmpty()) { _messages.tryEmit(if (undo) "Nothing to undo." else "No approved changes to apply."); return }
        _busy.value = true
        var ok = 0; var failed = 0; var denied = false
        try {
            for (chunk in rows.chunked(50)) {
                val request = WriteRequest(SongFileOps.writeRequest(getApplication(), chunk.map { repo.songUri(it.songId) }))
                requestChannel.send(request)
                val granted = withTimeoutOrNull(10 * 60_000L) { request.result.await() } ?: false
                if (!granted) { denied = true; break }
                val r = repo.write(chunk, undo)
                ok += r.ok; failed += r.failed
            }
        } finally {
            _busy.value = false
        }
        delay(800) // let MediaStore pick up the rewritten files
        songs.refresh()
        _messages.tryEmit(
            buildString {
                append(if (undo) "Restored $ok song(s)" else "Updated $ok song(s)")
                if (failed > 0) append(", $failed failed")
                if (denied) append(". Permission was declined; the rest are still waiting.")
            },
        )
    }
}
