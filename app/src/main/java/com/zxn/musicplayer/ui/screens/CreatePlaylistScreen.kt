package com.zxn.musicplayer.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zxn.musicplayer.ui.LibraryViewModel
import com.zxn.musicplayer.ui.PlaylistViewModel
import com.zxn.musicplayer.ui.components.ArtImage
import com.zxn.musicplayer.ui.components.LocalBottomInset

/** State of the playlist being created. Hoisted so the nav bar's X button can tell whether to ask before discarding. */
@Stable
class CreateDraft {
    var name by mutableStateOf("")
    var cover by mutableStateOf<Uri?>(null)
    var step by mutableStateOf(0)
    val selected = mutableStateListOf<Long>()
    val isDirty get() = name.isNotBlank() || cover != null || selected.isNotEmpty()
    fun reset() { name = ""; cover = null; step = 0; selected.clear() }
}

/**
 * Create-playlist pipeline: step 1 = cover + name, step 2 = pick songs, then create. Cancel with the X in the tab bar.
 */
@Composable
fun CreatePlaylistScreen(
    draft: CreateDraft,
    playlistVm: PlaylistViewModel,
    library: LibraryViewModel,
    onCreated: (Long) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) draft.cover = uri }
    BackHandler(enabled = draft.step == 1) { draft.step = 0 }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AnimatedContent(
            targetState = draft.step, label = "createStep", modifier = Modifier.weight(1f),
            transitionSpec = {
                if (targetState > initialState) slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                else slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
            },
        ) { step ->
            if (step == 0) StepNameCover(draft, onPickCover = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, onNext = { draft.step = 1 })
            else StepSongs(draft, library, onBack = { draft.step = 0 }, onCreate = {
                playlistVm.createWithCover(draft.name.trim(), draft.selected.toList(), draft.cover) { id -> onCreated(id) }
            })
        }
    }
}

@Composable
private fun StepNameCover(draft: CreateDraft, onPickCover: () -> Unit, onNext: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth > 600.dp && maxWidth > maxHeight
        val coverSize = if (wide) 200.dp else 210.dp

        val cover: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(coverSize).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).clickable(onClick = onPickCover), contentAlignment = Alignment.Center) {
                    val c = draft.cover
                    if (c != null) AsyncImage(c, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(64.dp), tint = cs.onSurfaceVariant)
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp).clip(CircleShape).background(cs.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.AddPhotoAlternate, "Choose cover", tint = cs.onSurface) }
                }
                Row {
                    TextButton(onClick = onPickCover) { Text(if (draft.cover == null) "Add cover" else "Change cover") }
                    if (draft.cover != null) TextButton(onClick = { draft.cover = null }) { Text("Remove") }
                }
            }
        }
        val fields: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                TextField(
                    value = draft.name, onValueChange = { draft.name = it.take(80) },
                    placeholder = { Text("Playlist name") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onNext, enabled = draft.name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("Next: add songs", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = LocalBottomInset.current + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (wide) 12.dp else 24.dp))
            Text("New Playlist", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(if (wide) 16.dp else 28.dp))
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    cover()
                    Spacer(Modifier.width(28.dp))
                    fields(Modifier.weight(1f))
                }
            } else {
                cover()
                Spacer(Modifier.height(12.dp))
                fields(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StepSongs(draft: CreateDraft, library: LibraryViewModel, onBack: () -> Unit, onCreate: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val all by library.allSongs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val songs = remember(all, query) {
        val sorted = all.sortedBy { it.title.lowercase() }
        if (query.isBlank()) sorted else sorted.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Add songs", style = MaterialTheme.typography.titleLarge)
                Text(draft.name, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(songs, key = { it.id }) { s ->
                val on = s.id in draft.selected
                Row(
                    Modifier.fillMaxWidth().clickable { if (on) draft.selected.remove(s.id) else draft.selected.add(s.id) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtImage(s.uri, Modifier.size(48.dp), RoundedCornerShape(10.dp), sizePx = 128)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (on) cs.primary else cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = cs.onPrimary) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).padding(bottom = LocalBottomInset.current)) {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Text(
                    if (draft.selected.isEmpty()) "Create playlist" else "Create playlist · ${draft.selected.size} ${if (draft.selected.size == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
    }
}
