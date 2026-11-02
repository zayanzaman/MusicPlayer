package com.zxn.musicplayer.data.tags

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import com.zxn.musicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class ScanState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val message: String? = null, // last result / error, shown on the hub
)

data class ApplyResult(val ok: Int, val failed: Int)

/**
 * Scans songs against MusicBrainz, stores suggestions, and writes approved ones into the files.
 * Songs whose file hasn't changed since they were last handled (no change needed / rejected / applied / not found)
 * are skipped on later scans, so they never reappear.
 */
class TagRepository(
    private val context: Context,
    private val db: TagDatabase,
    private val client: MusicBrainzClient = MusicBrainzClient(),
) {
    private val dao = db.dao()

    val counts: Flow<Map<String, Int>> = dao.observeCounts().map { list -> list.associate { it.status to it.count } }
    fun observe(status: String): Flow<List<TagSuggestion>> = dao.observeByStatus(status)

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan

    fun songUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    // ---------------------------------------------------------------- scanning

    private class FileInfo(val name: String, val size: Long, val modified: Long, val path: String?) {
        val fingerprint get() = "$size:$modified"
    }

    private fun fileInfo(ids: List<Long>): Map<Long, FileInfo> {
        val out = HashMap<Long, FileInfo>()
        ids.chunked(500).forEach { chunk ->
            val sel = "${MediaStore.Audio.Media._ID} IN (${chunk.joinToString(",")})"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATA,
                ),
                sel, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out[c.getLong(0)] = FileInfo(c.getString(1) ?: "", c.getLong(2), c.getLong(3), c.getString(4))
                }
            }
        }
        return out
    }

    private class CurrentTags(
        val title: String?, val artist: String?, val album: String?, val albumArtist: String?,
        val year: Int?, val track: Int?, val genre: String?,
    )

    private fun readTags(song: Song): CurrentTags {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, song.uri)
            fun s(k: Int) = r.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
            CurrentTags(
                title = s(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = s(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = s(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = s(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                year = s(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull()?.takeIf { it > 0 },
                track = s(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 },
                genre = s(MediaMetadataRetriever.METADATA_KEY_GENRE),
            )
        } catch (e: Exception) {
            CurrentTags(song.title, song.artist, song.album, null, song.year.takeIf { it > 0 }, song.track.takeIf { it > 0 }, null)
        } finally {
            runCatching { r.release() }
        }
    }

    private var scanJob: kotlinx.coroutines.Job? = null

    /** Starts a scan in [scope] (the app scope, so it keeps running when leaving the screen). */
    fun startScan(scope: kotlinx.coroutines.CoroutineScope, songs: List<Song>, knownIds: Set<Long>) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch { scan(songs, knownIds) }
    }

    fun cancelScan() { scanJob?.cancel() }

    /**
     * Scans songs that are new or whose file changed. Rows are saved as we go, so cancelling (or the app closing)
     * keeps all progress and the next scan continues with the rest.
     */
    suspend fun scan(songs: List<Song>, knownIds: Set<Long>) {
        if (_scan.value.running) return
        _scan.value = ScanState(running = true, message = "Preparing…")
        try {
            val existing = dao.all().associateBy { it.songId }
            val gone = existing.keys.filter { it !in knownIds }
            if (gone.isNotEmpty()) gone.chunked(500).forEach { dao.delete(it) }

            val info = withContext(Dispatchers.IO) { fileInfo(songs.map { it.id }) }
            val todo = songs.filter { s ->
                val i = info[s.id] ?: return@filter false
                val e = existing[s.id]
                e == null || e.fingerprint != i.fingerprint || e.status == TagStatus.FAILED
            }
            var found = 0; var unchanged = 0; var notFound = 0; var failed = 0; var consecutiveNetFails = 0
            val buffer = ArrayList<TagSuggestion>()
            suspend fun flush() { if (buffer.isNotEmpty()) { dao.putAll(buffer.toList()); buffer.clear() } }

            for ((index, song) in todo.withIndex()) {
                currentCoroutineContext().ensureActive()
                _scan.value = _scan.value.copy(done = index, total = todo.size, current = song.title)
                val fi = info.getValue(song.id)
                val row = try {
                    withContext(Dispatchers.IO) { analyze(song, fi) }
                } catch (e: MbException) {
                    consecutiveNetFails++
                    if (consecutiveNetFails >= 3) {
                        flush()
                        _scan.value = ScanState(message = "Stopped: ${e.message}. Progress is saved.")
                        return
                    }
                    TagSuggestion(song.id, fi.fingerprint, TagStatus.FAILED, fi.name, error = e.message, updatedAt = now())
                }
                if (row.status != TagStatus.FAILED) consecutiveNetFails = 0
                when (row.status) {
                    TagStatus.PENDING -> found++
                    TagStatus.NO_CHANGE -> unchanged++
                    TagStatus.NOT_FOUND, TagStatus.UNSUPPORTED -> notFound++
                    TagStatus.FAILED -> failed++
                }
                buffer += row
                if (buffer.size >= 10) flush()
            }
            flush()
            _scan.value = ScanState(
                message = when {
                    todo.isEmpty() -> "Nothing new to scan."
                    else -> "Scan finished: $found suggestions, $unchanged already fine, $notFound not matched" +
                        if (failed > 0) ", $failed failed (will retry)" else ""
                },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            _scan.value = ScanState(message = "Scan stopped. Progress is saved.")
            throw e
        } catch (e: Exception) {
            _scan.value = ScanState(message = "Scan failed: ${e.message}")
        }
    }

    private fun now() = System.currentTimeMillis()

    private suspend fun analyze(song: Song, fi: FileInfo): TagSuggestion {
        val base = TagSuggestion(song.id, fi.fingerprint, TagStatus.NO_CHANGE, fi.name, updatedAt = now())
        if (TagWriter.supportedExtension(fi.name) == null) return base.copy(status = TagStatus.UNSUPPORTED)

        val cur = readTags(song)
        val withCur = base.copy(
            curTitle = cur.title, curArtist = cur.artist, curAlbum = cur.album, curAlbumArtist = cur.albumArtist,
            curYear = cur.year, curTrack = cur.track, curGenre = cur.genre,
        )
        // Already well tagged (title, artist, album, year, track all present): skip the online lookup entirely.
        val complete = listOf(cur.title, cur.artist, cur.album).none { TagCleaner.isMissing(it) } && cur.year != null && cur.track != null
        if (complete) return withCur.copy(status = TagStatus.NO_CHANGE)
        val guess = TagCleaner.parseFileName(fi.name)
        val qTitle = (if (TagCleaner.isMissing(cur.title)) guess.title else TagCleaner.cleanTitle(cur.title!!)).ifBlank { guess.title }
        val qArtist = if (!TagCleaner.isMissing(cur.artist)) TagCleaner.cleanArtist(cur.artist!!) else guess.artist
        if (qTitle.isBlank()) return withCur.copy(status = TagStatus.NOT_FOUND)

        val recs = client.searchRecordings(qTitle, qArtist)
        val (rec, rel) = pick(qTitle, qArtist, song.durationMs, cur.album, recs) ?: return withCur.copy(status = TagStatus.NOT_FOUND)

        val newTitle = rec.title.takeIf { it != cur.title }
        val newArtist = rec.artist.takeIf { it.isNotBlank() && it != cur.artist }
        val newAlbum = rel?.title?.takeIf { it.isNotBlank() && it != cur.album }
        val newAlbumArtist = if (TagCleaner.isMissing(cur.albumArtist)) (rel?.artist ?: rec.artist).takeIf { it.isNotBlank() } else null
        val newYear = if (cur.year == null) rel?.date?.take(4)?.toIntOrNull() else null
        val newTrack = if (cur.track == null) rel?.track else null
        val newGenre = if (TagCleaner.isMissing(cur.genre)) rec.genre else null

        val changed = listOf(newTitle, newArtist, newAlbum, newAlbumArtist, newYear, newTrack, newGenre).any { it != null }
        return withCur.copy(
            status = if (changed) TagStatus.PENDING else TagStatus.NO_CHANGE,
            newTitle = newTitle, newArtist = newArtist, newAlbum = newAlbum, newAlbumArtist = newAlbumArtist,
            newYear = newYear, newTrack = newTrack, newGenre = newGenre,
            mbid = rec.id, confidence = rec.score,
        )
    }

    private fun pick(
        qTitle: String, qArtist: String?, durationMs: Long, curAlbum: String?, recs: List<MbRecording>,
    ): Pair<MbRecording, MbRelease?>? {
        var best: MbRecording? = null
        var bestRank = -1.0
        for (r in recs) {
            val ts = TagCleaner.similarity(qTitle, r.title)
            val artistSim = if (qArtist == null) 0.0 else {
                val n = TagCleaner.normalize(qArtist); val m = TagCleaner.normalize(r.artist)
                if (n.isNotEmpty() && (m.contains(n) || n.contains(m))) maxOf(0.8, TagCleaner.similarity(qArtist, r.artist))
                else TagCleaner.similarity(qArtist, r.artist)
            }
            val durOk = r.lengthMs == null || durationMs <= 0 || abs(r.lengthMs - durationMs) <= 15_000
            val accepted = durOk && ts >= 0.85 &&
                (if (qArtist != null) artistSim >= 0.6 else (r.score >= 95 && ts >= 0.9))
            if (!accepted) continue
            val rank = ts + artistSim + r.score / 100.0
            if (rank > bestRank) { bestRank = rank; best = r }
        }
        val rec = best ?: return null
        val sameAlbum = rec.releases.firstOrNull { !TagCleaner.isMissing(curAlbum) && TagCleaner.similarity(curAlbum!!, it.title) >= 0.85 }
        val release = sameAlbum ?: rec.releases.sortedWith(
            compareByDescending<MbRelease> { it.official }
                .thenByDescending { it.isAlbum }
                .thenBy { it.date ?: "9999" },
        ).firstOrNull()
        return rec to release
    }

    // ---------------------------------------------------------------- decisions

    suspend fun approve(ids: List<Long>) = ids.chunked(500).forEach { dao.setStatus(it, TagStatus.APPROVED, now()) }
    suspend fun reject(ids: List<Long>) = ids.chunked(500).forEach { dao.setStatus(it, TagStatus.REJECTED, now()) }
    suspend fun backToPending(ids: List<Long>) = ids.chunked(500).forEach { dao.setStatus(it, TagStatus.PENDING, now()) }
    suspend fun approveAllPending() = dao.moveAll(TagStatus.PENDING, TagStatus.APPROVED, now())
    suspend fun rejectAllPending() = dao.moveAll(TagStatus.PENDING, TagStatus.REJECTED, now())

    suspend fun toggleField(id: Long, field: TagField) {
        val row = dao.get(id) ?: return
        dao.setMask(id, row.excludedMask xor field.bit)
    }

    /** Forget decisions so rejected / unmatched songs get looked at again on the next scan. */
    suspend fun resetDecisions() = dao.deleteWithStatus(listOf(TagStatus.REJECTED, TagStatus.NOT_FOUND, TagStatus.FAILED))
    suspend fun resetEverything() = dao.clear()

    suspend fun approved(): List<TagSuggestion> = dao.byStatus(TagStatus.APPROVED)
    suspend fun applied(): List<TagSuggestion> = dao.byStatus(TagStatus.APPLIED)

    // ---------------------------------------------------------------- applying / undo

    fun included(row: TagSuggestion, f: TagField) = (row.excludedMask and f.bit) == 0

    /** Fields that will actually change for this row (proposed and not excluded). */
    fun changedFields(row: TagSuggestion): List<TagField> = buildList {
        if (row.newTitle != null && included(row, TagField.TITLE)) add(TagField.TITLE)
        if (row.newArtist != null && included(row, TagField.ARTIST)) add(TagField.ARTIST)
        if (row.newAlbum != null && included(row, TagField.ALBUM)) add(TagField.ALBUM)
        if (row.newAlbumArtist != null && included(row, TagField.ALBUM_ARTIST)) add(TagField.ALBUM_ARTIST)
        if (row.newYear != null && included(row, TagField.YEAR)) add(TagField.YEAR)
        if (row.newTrack != null && included(row, TagField.TRACK)) add(TagField.TRACK)
        if (row.newGenre != null && included(row, TagField.GENRE)) add(TagField.GENRE)
    }

    private fun forward(row: TagSuggestion): TagValues {
        val f = changedFields(row)
        return TagValues(
            title = row.newTitle.takeIf { TagField.TITLE in f },
            artist = row.newArtist.takeIf { TagField.ARTIST in f },
            album = row.newAlbum.takeIf { TagField.ALBUM in f },
            albumArtist = row.newAlbumArtist.takeIf { TagField.ALBUM_ARTIST in f },
            year = row.newYear?.toString().takeIf { TagField.YEAR in f },
            track = row.newTrack?.toString().takeIf { TagField.TRACK in f },
            genre = row.newGenre.takeIf { TagField.GENRE in f },
        )
    }

    private fun backward(row: TagSuggestion): TagValues {
        val f = changedFields(row)
        return TagValues(
            title = (row.curTitle ?: "").takeIf { TagField.TITLE in f },
            artist = (row.curArtist ?: "").takeIf { TagField.ARTIST in f },
            album = (row.curAlbum ?: "").takeIf { TagField.ALBUM in f },
            albumArtist = (row.curAlbumArtist ?: "").takeIf { TagField.ALBUM_ARTIST in f },
            year = (row.curYear?.toString() ?: "").takeIf { TagField.YEAR in f },
            track = (row.curTrack?.toString() ?: "").takeIf { TagField.TRACK in f },
            genre = (row.curGenre ?: "").takeIf { TagField.GENRE in f },
        )
    }

    /** Writes the given rows into their files. The caller must already hold write access (system dialog). */
    suspend fun write(rows: List<TagSuggestion>, undo: Boolean): ApplyResult = withContext(Dispatchers.IO) {
        var ok = 0; var failed = 0
        val info = fileInfo(rows.map { it.songId })
        for (row in rows) {
            currentCoroutineContext().ensureActive()
            val err = TagWriter.write(context, songUri(row.songId), row.fileName, if (undo) backward(row) else forward(row))
            if (err == null) {
                ok++
                val fp = fileInfo(listOf(row.songId))[row.songId]?.fingerprint ?: row.fingerprint
                dao.put(row.copy(status = if (undo) TagStatus.REJECTED else TagStatus.APPLIED, fingerprint = fp, error = null, updatedAt = now()))
                info[row.songId]?.path?.let { MediaScannerConnection.scanFile(context, arrayOf(it), null, null) }
            } else {
                failed++
                dao.put(row.copy(error = err, updatedAt = now()))
            }
        }
        ApplyResult(ok, failed)
    }
}
