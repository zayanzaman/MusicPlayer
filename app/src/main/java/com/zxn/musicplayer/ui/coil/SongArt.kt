package com.zxn.musicplayer.ui.coil

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import com.zxn.musicplayer.player.ArtLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Art decoding gets its own small pool so fast scrolling can't flood the IO threads or MediaProvider. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val artDispatcher = Dispatchers.IO.limitedParallelism(3)

/** Coil model: "the artwork of this song file". */
data class SongArt(val uri: Uri, val sizePx: Int = 256)

class SongArtKeyer : Keyer<SongArt> {
    override fun key(data: SongArt, options: Options): String = "${data.uri}#${data.sizePx}"
}

class SongArtFetcher(private val data: SongArt, private val context: Context) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bitmap = withContext(artDispatcher) { ArtLoader.load(context, data.uri, data.sizePx) } ?: return null
        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<SongArt> {
        override fun create(data: SongArt, options: Options, imageLoader: ImageLoader): Fetcher =
            SongArtFetcher(data, context)
    }
}
