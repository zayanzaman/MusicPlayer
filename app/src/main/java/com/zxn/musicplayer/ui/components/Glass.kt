package com.zxn.musicplayer.ui.components

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.zxn.musicplayer.player.ArtLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------- bottom inset for content scrolling behind the glass bars

/** Height of the floating glass bars (mini player + tab bar); top-level lists add it as bottom content padding. */
val LocalBottomInset = compositionLocalOf { 0.dp }

// ---------------------------------------------------------------- shimmer

/** One shared animation (0..1) so hundreds of placeholders don't each run their own animator. */
val LocalShimmer = compositionLocalOf<State<Float>?> { null }

@Composable
fun ProvideShimmer(active: Boolean = true, content: @Composable () -> Unit) {
    // The animator only runs while placeholders can be on screen; afterwards nothing ticks every frame.
    val phase: State<Float> = if (active) {
        val t = rememberInfiniteTransition(label = "shimmer")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    } else remember { mutableStateOf(0.4f) }
    CompositionLocalProvider(LocalShimmer provides phase, content = content)
}

/** Placeholder background with a moving highlight. Reads the animation only while drawing (no recomposition). */
@Composable
fun Modifier.shimmer(shape: Shape): Modifier {
    val phase = LocalShimmer.current
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    return clip(shape).drawBehind {
        drawRect(base)
        val p = phase?.value ?: 0.4f
        val w = size.width.coerceAtLeast(1f)
        val x = -w + p * w * 3f
        drawRect(Brush.linearGradient(listOf(Color.Transparent, hi, Color.Transparent), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x + w, w * 0.3f)))
    }
}

@Composable
fun SongRowSkeleton() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).shimmer(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).shimmer(RoundedCornerShape(7.dp)))
            Box(Modifier.fillMaxWidth(0.35f).height(11.dp).shimmer(RoundedCornerShape(6.dp)))
        }
    }
}

/** Shown while the library is first being read. */
@Composable
fun ListSkeleton(count: Int = 12, modifier: Modifier = Modifier) {
    LazyColumn(modifier, userScrollEnabled = false) { items(count) { SongRowSkeleton() } }
}

// ---------------------------------------------------------------- glass

/** Translucent "frosted" surface: tinted, semi-transparent, with a hairline edge and soft top highlight. */
@Composable
fun Modifier.glass(shape: Shape, alpha: Float = 0.80f): Modifier {
    val cs = MaterialTheme.colorScheme
    val tint = cs.surfaceContainer.copy(alpha = alpha)
    val edge = cs.onSurface.copy(alpha = 0.10f)
    val sheen = cs.onSurface.copy(alpha = 0.06f)
    return clip(shape)
        .background(tint)
        .background(Brush.verticalGradient(listOf(sheen, Color.Transparent), endY = 120f))
        .border(BorderStroke(0.6.dp, edge), shape)
}

// ---------------------------------------------------------------- sort / filter menu

data class MenuOption<T>(val value: T, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** Rounded, spaced menu with a title, icons, check on the selected option and an optional "reverse order" switch. */
@Composable
fun <T> SortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String,
    options: List<MenuOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    reversed: Boolean? = null,
    onReversed: ((Boolean) -> Unit)? = null,
) {
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp, shadowElevation = 12.dp,
        modifier = Modifier.width(250.dp),
    ) {
        Text(
            title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
        )
        options.forEach { o ->
            val on = o.value == selected
            Row(
                Modifier.fillMaxWidth()
                    .clickableNoRipple { onSelect(o.value); onDismiss() }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(o.icon, null, Modifier.size(20.dp), tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
                Text(
                    o.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (on) Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (reversed != null && onReversed != null) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Reverse order", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    reversed, onReversed,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface, checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant, uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

// ---------------------------------------------------------------- dynamic color from artwork

private val artColorCache = ConcurrentHashMap<String, Int>()

/** Dominant/vibrant color of the song's artwork (null until computed or if no art). Cheap: samples a 64px thumbnail. */
@Composable
fun rememberArtColor(uri: Uri?): Color? {
    val ctx = LocalContext.current
    val result = produceState<Color?>(initialValue = uri?.toString()?.let { artColorCache[it] }?.let { Color(it) }, uri) {
        if (uri == null) { value = null; return@produceState }
        val key = uri.toString()
        artColorCache[key]?.let { value = Color(it); return@produceState }
        val argb = withContext(Dispatchers.IO) {
            runCatching {
                val bmp = ArtLoader.load(ctx, uri, 96) ?: return@runCatching null
                val p = Palette.from(bmp).maximumColorCount(16).generate()
                (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch)?.rgb
                    ?: p.getDominantColor(0).takeIf { it != 0 }
            }.getOrNull()
        }
        if (argb != null) { artColorCache[key] = argb; value = Color(argb) } else value = null
    }
    return result.value
}


// ---------------------------------------------------------------- tab bar

data class TabItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/**
 * Floating glass tab bar. Tap, or press and slide across the icons to switch tabs; the selection pill glides with
 * your finger. While a playlist is being created ([creating]) the last tab turns into an X that cancels it and
 * sliding is disabled.
 */
@Composable
fun GlassTabBar(
    items: List<TabItem>,
    selectedIndex: Int,
    createIndex: Int,
    creating: Boolean,
    onSelect: (Int) -> Unit,
    onCancelCreate: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val n = items.size
    // The gesture block below lives across recompositions, so it must always call the LATEST callbacks.
    val select by rememberUpdatedState(onSelect)
    val cancel by rememberUpdatedState(onCancelCreate)
    val pos by animateFloatAsState(selectedIndex.toFloat(), spring(dampingRatio = 0.75f, stiffness = 420f), label = "pill")
    val shape = RoundedCornerShape(32.dp)
    val pill = cs.onSurface.copy(alpha = 0.10f)

    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().height(62.dp).glass(shape, 0.90f)
                .drawBehind {
                    val w = size.width / n
                    drawRoundRect(
                        pill, topLeft = androidx.compose.ui.geometry.Offset(pos * w + 6.dp.toPx(), 6.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(w - 12.dp.toPx(), size.height - 12.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()),
                    )
                }
                .pointerInput(creating, n) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat()
                        fun idx(x: Float) = (x / w * n).toInt().coerceIn(0, n - 1)
                        var last = idx(down.position.x)
                        if (creating) {
                            val up = waitForUpOrCancellation()
                            if (up != null && last == createIndex) cancel()
                            return@awaitEachGesture
                        }
                        select(last)
                        // keep following the finger: sliding across the bar switches tabs as it passes them
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val i = idx(change.position.x)
                            if (i != last && i != createIndex) { last = i; select(i) }
                            if (change.positionChanged()) change.consume()
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val on = i == selectedIndex
                val tint by animateColorAsState(if (on) cs.onSurface else cs.onSurfaceVariant, label = "tabTint")
                val rotation by animateFloatAsState(if (i == createIndex && creating) 45f else 0f, spring(dampingRatio = 0.6f), label = "plusRot")
                val enabled = !creating || i == createIndex
                Column(
                    Modifier.weight(1f).fillMaxHeight().graphicsLayer { alpha = if (enabled) 1f else 0.35f },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, item.label, Modifier.size(24.dp).graphicsLayer { rotationZ = rotation }, tint = tint)
                    Spacer(Modifier.height(3.dp))
                    Text(if (i == createIndex && creating) "Cancel" else item.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- consistent menus (every dropdown in the app)

@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismissRequest,
        modifier = modifier.width(230.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp, shadowElevation = 14.dp,
        content = content,
    )
}

@Composable
fun AppMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    androidx.compose.material3.DropdownMenuItem(
        text = text, onClick = onClick, modifier = modifier, leadingIcon = leadingIcon, enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = androidx.compose.material3.MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
