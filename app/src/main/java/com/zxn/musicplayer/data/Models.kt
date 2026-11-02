package com.zxn.musicplayer.data

import android.net.Uri

/** A local audio file from MediaStore. [id] is the MediaStore _ID and is what playlists/favorites store. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val durationMs: Long,
    val track: Int,
    val year: Int,
    val dateAddedSec: Long,
    val uri: Uri,
)

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val artSongUri: Uri, // art is loaded from a song in the album
)

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

fun List<Song>.toAlbums(): List<Album> =
    groupBy { it.albumId }.map { (id, songs) ->
        val first = songs.first()
        Album(id, first.album, songs.map { it.artist }.distinct().singleOrNull() ?: "Various artists",
            songs.size, first.uri)
    }.sortedBy { it.name.lowercase() }

fun List<Song>.toArtists(): List<Artist> =
    groupBy { it.artistId }.map { (id, songs) ->
        Artist(id, songs.first().artist, songs.size, songs.map { it.albumId }.distinct().size)
    }.sortedBy { it.name.lowercase() }

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
