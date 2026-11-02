package com.zxn.musicplayer.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Drag-to-reorder helper for a LazyColumn. Items must use stable keys. While dragging, the caller keeps a
 * local copy of the list and swaps on [dragBy]'s callback; the final move is committed on drop.
 */
class ReorderState(private val listState: LazyListState) {
    var draggingKey: Any? by mutableStateOf(null)
        private set
    private var centerY by mutableFloatStateOf(0f)

    fun start(key: Any): Boolean {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        draggingKey = key
        centerY = info.offset + info.size / 2f
        return true
    }

    /** Moves the dragged row by [dy] px; calls [onSwap] (draggedKey, otherKey) when it crosses another row. */
    fun dragBy(dy: Float, onSwap: (Any, Any) -> Unit) {
        val key = draggingKey ?: return
        centerY += dy
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != key && centerY >= it.offset && centerY <= it.offset + it.size
        } ?: return
        onSwap(key, target.key)
    }

    fun end() { draggingKey = null }

    /** Vertical offset to apply to the dragged row so it follows the finger. */
    fun translationFor(key: Any): Float {
        if (key != draggingKey) return 0f
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return centerY - (info.offset + info.size / 2f)
    }
}
