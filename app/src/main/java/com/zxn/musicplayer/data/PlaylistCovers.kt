package com.zxn.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** Custom playlist cover images, stored as files (no database change): filesDir/covers/playlist_<id>.jpg */
object PlaylistCovers {
    private fun dir(context: Context) = File(context.filesDir, "covers").apply { mkdirs() }
    fun file(context: Context, playlistId: Long) = File(dir(context), "playlist_$playlistId.jpg")

    /** Returns the cover file if the playlist has a custom cover. */
    fun existing(context: Context, playlistId: Long): File? = file(context, playlistId).takeIf { it.exists() }

    /** Decodes the picked image, center-crops to a square (max 800px) and saves it. */
    fun save(context: Context, playlistId: Long, source: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 800 && bounds.outHeight / (sample * 2) >= 800) sample *= 2
        val raw = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val side = minOf(raw.width, raw.height)
        val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side)
        val out = if (side > 800) Bitmap.createScaledBitmap(square, 800, 800, true) else square
        file(context, playlistId).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    }.getOrDefault(false)

    fun delete(context: Context, playlistId: Long) { file(context, playlistId).delete() }
}
