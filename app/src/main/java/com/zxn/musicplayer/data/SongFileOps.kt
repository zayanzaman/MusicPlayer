package com.zxn.musicplayer.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings

/** MediaStore file operations (Android 11+). Rename/delete need a system permission dialog first. */
object SongFileOps {

    data class Details(
        val fileName: String?,
        val sizeBytes: Long?,
        val mimeType: String?,
        val folder: String?,
        val bitrateKbps: Int?,
    )

    fun loadDetails(context: Context, uri: Uri): Details {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.Audio.AudioColumns.BITRATE,
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                return Details(
                    fileName = c.getString(0),
                    sizeBytes = c.getLong(1),
                    mimeType = c.getString(2),
                    folder = c.getString(3),
                    bitrateKbps = c.getInt(4).takeIf { it > 0 }?.let { it / 1000 },
                )
            }
        }
        return Details(null, null, null, null, null)
    }

    /** System dialog: "Allow <app> to modify this audio file?" Launch the returned sender, then call [rename] on OK. */
    fun writeRequest(context: Context, uris: List<Uri>): IntentSender =
        MediaStore.createWriteRequest(context.contentResolver, uris).intentSender

    /** System dialog: "Allow <app> to delete this audio file?" The system deletes the file itself on OK. */
    fun deleteRequest(context: Context, uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender

    /**
     * Renames the file (keeping its extension) and updates the title in MediaStore.
     * Note: this does not rewrite tags embedded inside the file; a media rescan may restore an embedded title.
     */
    fun rename(context: Context, uri: Uri, newTitle: String): Boolean {
        val resolver = context.contentResolver
        val current = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return false
        val extension = current.substringAfterLast('.', "")
        val clean = newTitle.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
        if (clean.isEmpty()) return false
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, if (extension.isEmpty()) clean else "$clean.$extension")
            put(MediaStore.Audio.Media.TITLE, clean)
        }
        return runCatching { resolver.update(uri, values, null, null) > 0 }.getOrDefault(false)
    }

    // ---- ringtone ----
    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun setRingtone(context: Context, uri: Uri): Boolean =
        runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
            true
        }.getOrDefault(false)
}
