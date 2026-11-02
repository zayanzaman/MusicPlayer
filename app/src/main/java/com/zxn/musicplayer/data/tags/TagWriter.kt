package com.zxn.musicplayer.data.tags

import android.content.Context
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File

/** Values to write. null = leave alone, "" = remove the field. */
data class TagValues(
    val title: String? = null, val artist: String? = null, val album: String? = null,
    val albumArtist: String? = null, val year: String? = null, val track: String? = null, val genre: String? = null,
)

/**
 * Writes tags into MP3 / M4A files with the jaudiotagger Android fork.
 * Safe-write strategy: copy the file to cache -> edit the copy -> re-read it to verify -> only then overwrite the original
 * (a backup copy is kept while overwriting and restored if that step fails).
 * The caller must already hold write access to [uri] (MediaStore.createWriteRequest). Do NOT call TagOptionSingleton.setAndroid.
 */
object TagWriter {
    fun supportedExtension(fileName: String): String? =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "mp3"
            "m4a", "mp4" -> "m4a"
            else -> null
        }

    /** Returns null on success or a short error message. */
    fun write(context: Context, uri: Uri, fileName: String, values: TagValues): String? {
        val ext = supportedExtension(fileName) ?: return "Unsupported format"
        val dir = File(context.cacheDir, "tagwork").apply { mkdirs() }
        val work = File(dir, "work.$ext")
        val backup = File(dir, "backup.$ext")
        try {
            val cr = context.contentResolver
            cr.openInputStream(uri)?.use { input -> work.outputStream().use { input.copyTo(it) } } ?: return "Can't open file"
            work.copyTo(backup, overwrite = true)

            val audio = AudioFileIO.read(work)
            val tag = audio.tagOrCreateAndSetDefault
            apply(tag, FieldKey.TITLE, values.title)
            apply(tag, FieldKey.ARTIST, values.artist)
            apply(tag, FieldKey.ALBUM, values.album)
            apply(tag, FieldKey.ALBUM_ARTIST, values.albumArtist)
            apply(tag, FieldKey.YEAR, values.year)
            apply(tag, FieldKey.TRACK, values.track)
            apply(tag, FieldKey.GENRE, values.genre)
            audio.commit()

            // verify the edited copy before touching the original
            val check = AudioFileIO.read(work).tag ?: return "Verification failed"
            if (!matches(check, FieldKey.TITLE, values.title) || !matches(check, FieldKey.ARTIST, values.artist) ||
                !matches(check, FieldKey.ALBUM, values.album)
            ) return "Verification failed"

            try {
                cr.openFileDescriptor(uri, "rwt")?.use { pfd ->
                    java.io.FileOutputStream(pfd.fileDescriptor).use { out -> work.inputStream().use { it.copyTo(out) } }
                } ?: return "Can't write file"
            } catch (e: Exception) {
                runCatching {
                    cr.openFileDescriptor(uri, "rwt")?.use { pfd ->
                        java.io.FileOutputStream(pfd.fileDescriptor).use { out -> backup.inputStream().use { it.copyTo(out) } }
                    }
                }
                return e.message ?: "Write failed"
            }
            return null
        } catch (e: Throwable) {
            return e.message ?: e.javaClass.simpleName
        } finally {
            work.delete(); backup.delete()
        }
    }

    private fun apply(tag: Tag, key: FieldKey, value: String?) {
        if (value == null) return
        val clean = sanitize(value)
        if (clean.isBlank()) tag.deleteField(key) else tag.setField(key, clean)
    }

    /** Tag text may come from the internet: drop control characters and cap the length before it is written to a file. */
    private fun sanitize(v: String) = v.filter { !it.isISOControl() }.trim().take(255)

    private fun matches(tag: Tag, key: FieldKey, expected: String?): Boolean {
        if (expected == null) return true
        val want = sanitize(expected)
        val actual = runCatching { tag.getFirst(key) }.getOrDefault("")
        return if (want.isBlank()) actual.isBlank() else actual == want
    }
}
