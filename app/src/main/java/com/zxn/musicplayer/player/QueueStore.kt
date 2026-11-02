package com.zxn.musicplayer.player

import android.content.Context

/**
 * Small SharedPreferences-backed store shared by the service (writes the live queue) and the
 * ViewModel (writes shuffle state). Both run in the same process.
 *
 * Queue entries are identified by *mediaId* ("songId:randomSuffix"), unique per queue entry so the
 * same song can appear twice.
 */
class QueueStore(context: Context) {
    private val prefs = context.getSharedPreferences("queue_state", Context.MODE_PRIVATE)

    data class Saved(val mediaIds: List<String>, val index: Int, val positionMs: Long)

    fun saveQueue(mediaIds: List<String>, index: Int, positionMs: Long) {
        prefs.edit()
            .putString(K_IDS, mediaIds.joinToString(","))
            .putInt(K_INDEX, index)
            .putLong(K_POS, positionMs)
            .apply()
    }

    fun loadQueue(): Saved? {
        val ids = prefs.getString(K_IDS, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        if (ids.isEmpty()) return null
        return Saved(ids, prefs.getInt(K_INDEX, 0), prefs.getLong(K_POS, 0L))
    }

    /** Shuffle is "physical": upcoming items are reordered, and the original order is kept to undo it. */
    var shuffleOn: Boolean
        get() = prefs.getBoolean(K_SHUFFLE, false)
        set(v) = prefs.edit().putBoolean(K_SHUFFLE, v).apply()

    var originalIds: List<String>
        get() = prefs.getString(K_ORIG, "")!!.split(',').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString(K_ORIG, v.joinToString(",")).apply()

    var repeatMode: Int
        get() = prefs.getInt(K_REPEAT, 0)
        set(v) = prefs.edit().putInt(K_REPEAT, v).apply()

    private companion object {
        const val K_IDS = "ids"
        const val K_INDEX = "index"
        const val K_POS = "pos"
        const val K_SHUFFLE = "shuffle"
        const val K_ORIG = "orig"
        const val K_REPEAT = "repeat"
    }
}
