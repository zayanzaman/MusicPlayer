package com.zxn.musicplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import java.util.concurrent.ConcurrentHashMap

/** Loads album art for a song uri: system thumbnail (API 29+), else embedded picture. Blocking: call off the main thread. */
object ArtLoader {
    private val noArt: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Everything up to this size shares one cached 384px file (rows, grids, mini player), scaled down on read. */
    private const val BASE = 384

    private fun cacheDir(context: Context) = java.io.File(context.cacheDir, "art")

    /** True when the art for this song has already been resolved (cached image or known to have none). */
    fun isCached(context: Context, songUri: Uri): Boolean {
        val id = songUri.lastPathSegment ?: return false
        val d = cacheDir(context)
        return java.io.File(d, "${id}_$BASE.jpg").exists() || java.io.File(d, "${id}.none").exists()
    }

    fun load(context: Context, songUri: Uri, sizePx: Int = 512): Bitmap? {
        val key = songUri.toString()
        if (key in noArt) return null
        val id = songUri.lastPathSegment?.toLongOrNull()?.toString() ?: return loadUncached(context, songUri, sizePx) // numeric only: the id becomes a cache file name
        val base = if (sizePx <= BASE) BASE else sizePx
        val dir = cacheDir(context)
        val file = java.io.File(dir, "${id}_$base.jpg")
        val none = java.io.File(dir, "$id.none")
        if (none.exists()) { noArt.add(key); return null }

        var bitmap: Bitmap? = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (bitmap == null) {
            bitmap = loadUncached(context, songUri, base)
            if (bitmap == null) {
                noArt.add(key)
                runCatching { dir.mkdirs(); none.createNewFile() }
                return null
            }
            runCatching {
                dir.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }
        }
        return if (sizePx < bitmap.width) Bitmap.createScaledBitmap(bitmap, sizePx, sizePx * bitmap.height / bitmap.width, true) else bitmap
    }

    /** Call when art may have changed (e.g. after tag/cover edits). */
    fun clearCaches(context: Context) {
        noArt.clear()
        java.io.File(context.cacheDir, "art").deleteRecursively()
    }

    private fun loadUncached(context: Context, songUri: Uri, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(songUri, Size(sizePx, sizePx), null)
            } catch (_: Exception) { /* no art, fall through */ }
        }
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, songUri)
            val bytes = mmr.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }
}
