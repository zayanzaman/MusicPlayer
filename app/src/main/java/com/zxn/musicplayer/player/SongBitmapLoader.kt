package com.zxn.musicplayer.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Gives the media notification / lock screen artwork for local songs (artworkUri = the song's content uri). */
@OptIn(UnstableApi::class)
class SongBitmapLoader(private val context: Context) : BitmapLoader {
    private val fallback = DataSourceBitmapLoader(context)
    private val executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            return executor.submit(Callable<Bitmap> {
                ArtLoader.load(context, uri) ?: throw IOException("No artwork for $uri")
            })
        }
        return fallback.loadBitmap(uri)
    }
}
