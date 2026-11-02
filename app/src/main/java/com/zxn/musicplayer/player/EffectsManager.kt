package com.zxn.musicplayer.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(val index: Int, val centerHz: Int, val level: Int) // level in millibels (100 = 1 dB)

data class EffectsState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val presets: List<String> = emptyList(),
    val currentPreset: Int = -1, // -1 = custom
    val bassBoostSupported: Boolean = false,
    val bassBoost: Int = 0,      // 0..1000
    val virtualizerSupported: Boolean = false,
    val virtualizer: Int = 0,    // 0..1000
)

/**
 * Equalizer + bass boost + virtualizer on the player's audio session. The service calls [attach]/[release];
 * the UI reads [state] and calls the setters. Settings persist in SharedPreferences and are re-applied on attach.
 */
class EffectsManager(context: Context) {
    private val prefs = context.getSharedPreferences("effects", Context.MODE_PRIVATE)
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var sessionId = 0

    private val _state = MutableStateFlow(EffectsState())
    val state: StateFlow<EffectsState> = _state.asStateFlow()

    private val enabled get() = prefs.getBoolean(K_ENABLED, false)

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) return // 0 = global mix; never attach there
        if (audioSessionId == sessionId && eq != null) return
        release()
        sessionId = audioSessionId

        try {
            val e = Equalizer(0, audioSessionId)
            val lo = e.bandLevelRange[0].toInt()
            val hi = e.bandLevelRange[1].toInt()
            val savedPreset = prefs.getInt(K_PRESET, -1)
            val savedLevels = prefs.getString(K_LEVELS, null)?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
            if (savedPreset in 0 until e.numberOfPresets.toInt()) {
                e.usePreset(savedPreset.toShort())
            } else if (savedLevels.size == e.numberOfBands.toInt()) {
                savedLevels.forEachIndexed { i, l -> e.setBandLevel(i.toShort(), l.coerceIn(lo, hi).toShort()) }
            }
            eq = e
        } catch (_: Exception) { eq = null }

        try {
            val b = BassBoost(0, audioSessionId)
            if (b.strengthSupported) { b.setStrength(prefs.getInt(K_BASS, 0).toShort()); bass = b } else b.release()
        } catch (_: Exception) { bass = null }
        try {
            val v = Virtualizer(0, audioSessionId)
            if (v.strengthSupported) { v.setStrength(prefs.getInt(K_VIRT, 0).toShort()); virt = v } else v.release()
        } catch (_: Exception) { virt = null }

        applyEnabled()
        publish()
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { virt?.release() }
        eq = null; bass = null; virt = null
        sessionId = 0
        publish()
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(K_ENABLED, on).apply()
        applyEnabled()
        publish()
    }

    fun setBandLevel(index: Int, level: Int) {
        val e = eq ?: return
        runCatching { e.setBandLevel(index.toShort(), level.toShort()) }
        prefs.edit().putInt(K_PRESET, -1).apply()
        saveLevels(e)
        publish()
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        runCatching { e.usePreset(index.toShort()) }
        prefs.edit().putInt(K_PRESET, index).apply()
        saveLevels(e)
        publish()
    }

    fun setBassBoost(strength: Int) {
        prefs.edit().putInt(K_BASS, strength).apply()
        runCatching { bass?.setStrength(strength.toShort()) }
        applyEnabled()
        publish()
    }

    fun setVirtualizer(strength: Int) {
        prefs.edit().putInt(K_VIRT, strength).apply()
        runCatching { virt?.setStrength(strength.toShort()) }
        applyEnabled()
        publish()
    }

    /** Flat EQ, no bass boost, no virtualizer. */
    fun reset() {
        eq?.let { e ->
            for (i in 0 until e.numberOfBands.toInt()) runCatching { e.setBandLevel(i.toShort(), 0) }
            saveLevels(e)
        }
        prefs.edit().putInt(K_PRESET, -1).putInt(K_BASS, 0).putInt(K_VIRT, 0).apply()
        runCatching { bass?.setStrength(0) }
        runCatching { virt?.setStrength(0) }
        applyEnabled()
        publish()
    }

    // ---- internals ----

    private fun saveLevels(e: Equalizer) {
        val levels = (0 until e.numberOfBands.toInt()).joinToString(",") { e.getBandLevel(it.toShort()).toString() }
        prefs.edit().putString(K_LEVELS, levels).apply()
    }

    private fun applyEnabled() {
        val on = enabled
        runCatching { eq?.enabled = on }
        runCatching { bass?.enabled = on && prefs.getInt(K_BASS, 0) > 0 }
        runCatching { virt?.enabled = on && prefs.getInt(K_VIRT, 0) > 0 }
    }

    private fun publish() {
        val e = eq
        _state.value = EffectsState(
            available = e != null,
            enabled = enabled,
            bands = if (e == null) emptyList() else List(e.numberOfBands.toInt()) { i ->
                EqBand(i, e.getCenterFreq(i.toShort()) / 1000, e.getBandLevel(i.toShort()).toInt())
            },
            minLevel = e?.bandLevelRange?.get(0)?.toInt() ?: -1500,
            maxLevel = e?.bandLevelRange?.get(1)?.toInt() ?: 1500,
            presets = if (e == null) emptyList() else List(e.numberOfPresets.toInt()) { e.getPresetName(it.toShort()) },
            currentPreset = prefs.getInt(K_PRESET, -1),
            bassBoostSupported = bass != null,
            bassBoost = prefs.getInt(K_BASS, 0),
            virtualizerSupported = virt != null,
            virtualizer = prefs.getInt(K_VIRT, 0),
        )
    }

    private companion object {
        const val K_ENABLED = "enabled"
        const val K_PRESET = "preset"
        const val K_LEVELS = "levels"
        const val K_BASS = "bass"
        const val K_VIRT = "virt"
    }
}
