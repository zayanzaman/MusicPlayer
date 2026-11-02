package com.zxn.musicplayer.data.tags

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MbRelease(
    val id: String, val title: String, val date: String?, val official: Boolean,
    val isAlbum: Boolean, val artist: String?, val track: Int?,
)

data class MbRecording(
    val id: String, val score: Int, val title: String, val artist: String, val lengthMs: Long?,
    val releases: List<MbRelease>, val genre: String?,
)

class MbException(message: String, val retryable: Boolean) : IOException(message)

/**
 * Minimal MusicBrainz web-service client (https://musicbrainz.org/doc/MusicBrainz_API).
 * Rules we follow: identifying User-Agent (REQUIRED), at most 1 request/second, back off on 429/503.
 * Parsing is defensive: any missing field is simply treated as unknown.
 */
class MusicBrainzClient(
    private val userAgent: String = "MusicPlayer/1.0.0 ( https://github.com/zayanzaman )",
) {
    private val gate = Mutex()
    private var lastCall = 0L

    private val blockedGenres = setOf("seen live", "favorites", "favourite", "favorite", "love", "male vocalists", "female vocalists")

    suspend fun searchRecordings(title: String, artist: String?, limit: Int = 10): List<MbRecording> {
        val q = buildString {
            append("recording:\"").append(escape(title)).append('"')
            if (!artist.isNullOrBlank()) append(" AND artist:\"").append(escape(artist)).append('"')
        }
        val url = "https://musicbrainz.org/ws/2/recording?query=${URLEncoder.encode(q, "UTF-8")}&fmt=json&limit=$limit"
        val json = get(url)
        val arr = json.optJSONArray("recordings") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { runCatching { parseRecording(arr.getJSONObject(it)) }.getOrNull() }
    }

    // Strips quotes, backslashes and control characters, and caps the length, so a file's tags can't alter the query.
    private fun escape(s: String) = s.filter { !it.isISOControl() }.replace("\\", " ").replace("\"", " ").trim().take(200)

    private fun parseRecording(o: JSONObject): MbRecording {
        val releases = o.optJSONArray("releases").toList().mapNotNull { r -> runCatching { parseRelease(r) }.getOrNull() }
        val tags = o.optJSONArray("tags").toList()
            .map { it.optString("name") to it.optInt("count", 0) }
            .filter { it.first.isNotBlank() && it.first.lowercase() !in blockedGenres && it.second >= 2 }
            .maxByOrNull { it.second }
        return MbRecording(
            id = o.getString("id"),
            score = o.optInt("score", 0),
            title = o.getString("title"),
            artist = credit(o.optJSONArray("artist-credit")),
            lengthMs = if (o.has("length") && !o.isNull("length")) o.optLong("length") else null,
            releases = releases,
            genre = tags?.first?.let { fixGenreCase(it) },
        )
    }

    private fun fixGenreCase(s: String) = s.split(' ').joinToString(" ") { w ->
        w.split('-').joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun parseRelease(r: JSONObject): MbRelease {
        val group = r.optJSONObject("release-group")
        val secondary = group?.optJSONArray("secondary-types")
        val track = r.optJSONArray("media").toList().firstNotNullOfOrNull { m ->
            m.optJSONArray("track").toList().firstNotNullOfOrNull { t -> t.optString("number").toIntOrNull() }
        }
        return MbRelease(
            id = r.getString("id"),
            title = r.optString("title"),
            date = r.optString("date").ifBlank { null },
            official = r.optString("status").equals("Official", ignoreCase = true),
            isAlbum = group?.optString("primary-type").equals("Album", ignoreCase = true) && (secondary == null || secondary.length() == 0),
            artist = r.optJSONArray("artist-credit")?.let { credit(it) }?.ifBlank { null },
            track = track,
        )
    }

    private fun credit(a: JSONArray?): String {
        if (a == null) return ""
        val sb = StringBuilder()
        for (i in 0 until a.length()) {
            val c = a.optJSONObject(i) ?: continue
            val name = c.optString("name").ifBlank { c.optJSONObject("artist")?.optString("name").orEmpty() }
            sb.append(name).append(c.optString("joinphrase"))
        }
        return sb.toString().trim()
    }

    private fun JSONArray?.toList(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private suspend fun get(url: String): JSONObject = gate.withLock {
        var attempt = 0
        while (true) {
            val wait = 1100 - (System.currentTimeMillis() - lastCall)
            if (wait > 0) delay(wait)
            try {
                val (code, body) = withContext(Dispatchers.IO) { request(url) }
                lastCall = System.currentTimeMillis()
                when {
                    code == 200 -> return@withLock JSONObject(body)
                    (code == 429 || code == 503) && attempt < 3 -> { attempt++; delay(3000L * attempt) }
                    code == 429 || code == 503 -> throw MbException("MusicBrainz is busy (HTTP $code). Try again later.", true)
                    code in 500..599 -> throw MbException("MusicBrainz server error (HTTP $code)", true)
                    else -> throw MbException("MusicBrainz request failed (HTTP $code)", false)
                }
            } catch (e: MbException) {
                throw e
            } catch (e: IOException) {
                lastCall = System.currentTimeMillis()
                throw MbException("No connection to MusicBrainz", true)
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    /** Reads at most 4 MB, so a misbehaving server can't exhaust memory. */
    private fun readLimited(stream: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > 4 * 1024 * 1024) throw IOException("Response too large")
        }
        return out.toString("UTF-8")
    }

    private fun request(url: String): Pair<Int, String> {
        val parsed = URL(url)
        require(parsed.protocol == "https" && parsed.host == "musicbrainz.org") { "Blocked request" }
        val c = parsed.openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.requestMethod = "GET"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.use { readLimited(it) } ?: "")
        } finally {
            c.disconnect()
        }
    }
}
