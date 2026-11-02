package com.zxn.musicplayer.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zxn.musicplayer.data.Song
import java.util.UUID

object MediaIds {
    /** Unique per queue entry: "<songId>:<8 random chars>". */
    fun newId(songId: Long) = "$songId:${UUID.randomUUID().toString().take(8)}"
    fun songId(mediaId: String): Long? = mediaId.substringBefore(':').toLongOrNull()
}

/** artworkUri is the song's own content uri; [SongBitmapLoader]/Coil extract art from it. */
fun Song.toMediaItem(mediaId: String = MediaIds.newId(id)): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(uri)
                .setDurationMs(durationMs)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()
