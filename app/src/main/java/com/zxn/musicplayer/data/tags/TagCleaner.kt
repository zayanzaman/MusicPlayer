package com.zxn.musicplayer.data.tags

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/** Offline text helpers: clean junk out of names, parse "Artist - Title" file names, fuzzy-compare strings. */
object TagCleaner {

    private val junkBrackets = Regex(
        """\s*[\(\[\{]\s*(official\s*(music\s*)?(video|audio|lyric\s*video|visualizer)|lyrics?(\s*video)?|audio|video|hd|hq|4k|""" +
            """official|music\s*video|visualizer|remaster(ed)?(\s*\d{4})?|explicit|clean|full\s*song|with\s*lyrics|""" +
            """free\s*download|download|www\.[^\)\]\}]*|[^\)\]\}]*\.(com|net|org|info|co)[^\)\]\}]*)\s*[\)\]\}]""",
        RegexOption.IGNORE_CASE,
    )
    private val trailingJunk = Regex("""\s*[-–|]\s*(official\s*(music\s*)?(video|audio)|lyrics?|topic)\s*$""", RegexOption.IGNORE_CASE)
    private val trackPrefix = Regex("""^\s*(\d{1,3})\s*[-._)]\s*""")
    private val featPart = Regex("""\s*[\(\[]?\s*(feat\.?|ft\.?|featuring)\s+[^\)\]]*[\)\]]?""", RegexOption.IGNORE_CASE)

    fun isMissing(s: String?): Boolean {
        val t = s?.trim()?.lowercase() ?: return true
        return t.isEmpty() || t == "<unknown>" || t == "unknown" || t == "unknown artist" || t == "unknown album" || t == "null"
    }

    fun cleanTitle(raw: String): String {
        var s = raw.replace('_', ' ')
        var prev: String
        do { prev = s; s = junkBrackets.replace(s, "") } while (s != prev)
        s = trailingJunk.replace(s, "")
        return s.replace(Regex("\\s+"), " ").trim().trim('-', '–', '|').trim()
    }

    fun cleanArtist(raw: String): String =
        raw.replace('_', ' ').replace(Regex("(?i)\\s*-\\s*topic$"), "").replace(Regex("\\s+"), " ").trim()

    data class Guess(val artist: String?, val title: String, val track: Int?)

    /** Best guess for artist/title from a file name like "03 - Artist - Title.mp3". */
    fun parseFileName(fileName: String): Guess {
        var base = fileName.substringBeforeLast('.', fileName).replace('_', ' ').trim()
        var track: Int? = null
        trackPrefix.find(base)?.let { m ->
            // only treat as a track number when something follows
            if (base.length > m.value.length) { track = m.groupValues[1].toIntOrNull(); base = base.substring(m.value.length) }
        }
        val parts = base.split(" - ", " – ")
        return if (parts.size >= 2) Guess(cleanArtist(parts[0]).ifBlank { null }, cleanTitle(parts.drop(1).joinToString(" - ")), track)
        else Guess(null, cleanTitle(base), track)
    }

    /** Lowercase, accent-free, letters/digits only; "feat." parts removed. Used for comparing, never for display. */
    fun normalize(s: String): String {
        val noFeat = featPart.replace(s, "")
        val d = Normalizer.normalize(noFeat, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return d.lowercase().replace("&", "and").filter { it.isLetterOrDigit() }
    }

    /** 0.0..1.0 similarity (Levenshtein ratio on normalized text). */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a); val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val d = levenshtein(x, y)
        return 1.0 - d.toDouble() / max(x.length, y.length)
    }

    /** True when two strings are equal ignoring case/accents/punctuation. */
    fun sameText(a: String?, b: String?): Boolean = a != null && b != null && normalize(a) == normalize(b)

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
