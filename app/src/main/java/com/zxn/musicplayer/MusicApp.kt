package com.zxn.musicplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.zxn.musicplayer.ui.coil.SongArtFetcher
import com.zxn.musicplayer.ui.coil.SongArtKeyer

class MusicApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(SongArtKeyer())
                add(SongArtFetcher.Factory(this@MusicApp))
            }
            .crossfade(120)
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .respectCacheHeaders(false)
            .build()
}
