package com.zxn.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.zxn.musicplayer.MusicApp
import com.zxn.musicplayer.player.EffectsState
import kotlinx.coroutines.flow.StateFlow

/** Thin UI handle over the shared [com.zxn.musicplayer.player.EffectsManager]. */
class EffectsViewModel(app: Application) : AndroidViewModel(app) {
    private val fx = (app as MusicApp).container.effects
    val state: StateFlow<EffectsState> = fx.state

    fun setEnabled(on: Boolean) = fx.setEnabled(on)
    fun setBand(index: Int, level: Int) = fx.setBandLevel(index, level)
    fun usePreset(index: Int) = fx.usePreset(index)
    fun setBassBoost(strength: Int) = fx.setBassBoost(strength)
    fun setVirtualizer(strength: Int) = fx.setVirtualizer(strength)
    fun reset() = fx.reset()
}
