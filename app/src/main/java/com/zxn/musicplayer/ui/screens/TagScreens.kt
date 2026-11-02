package com.zxn.musicplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import com.zxn.musicplayer.ui.components.AppMenu
import com.zxn.musicplayer.ui.components.AppMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxn.musicplayer.data.tags.TagField
import com.zxn.musicplayer.data.tags.TagStatus
import com.zxn.musicplayer.data.tags.TagSuggestion
import com.zxn.musicplayer.ui.TagViewModel
import com.zxn.musicplayer.ui.components.ArtImage
import com.zxn.musicplayer.ui.components.EmptyState

/** Which list a [TagListScreen] shows. Route argument values. */
enum class TagMode(val route: String, val title: String) {
    REVIEW("review", "Review suggestions"),
    APPROVED("approved", "Approved"),
    APPLIED("applied", "Applied changes"),
    REJECTED("rejected", "Rejected"),
}

// ------------------------------------------------------------------ Hub

@Composable
fun TagsHubScreen(vm: TagViewModel, onBack: () -> Unit, onOpen: (TagMode) -> Unit) {
    val counts by vm.counts.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val consent by vm.consent.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var askConsent by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    fun c(s: String) = counts[s] ?: 0

    SubScreen(
        "Update tags", onBack,
        actions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            AppMenu(menu, { menu = false }) {
                AppMenuItem(text = { Text("Re-check rejected & unmatched") }, onClick = { menu = false; confirmReset = true })
                if (consent) AppMenuItem(text = { Text("Withdraw online-lookup consent") }, onClick = { menu = false; vm.revokeConsent() })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Complete & organize song tags", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Looks up each song on MusicBrainz using its current title/artist/file name and suggests fixes for " +
                                "title, artist, album, album artist, year, track number and genre. Nothing changes until you approve it. " +
                                "Works with MP3 and M4A files. Songs that are already fine or that you've decided on won't show up again.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (scan.running) {
                            LinearProgressIndicator(
                                progress = { if (scan.total == 0) 0f else scan.done.toFloat() / scan.total },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("Scanning ${scan.done + 1} of ${scan.total}: ${scan.current}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                            Text("Keep the app open. Progress is saved, so you can stop any time.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = vm::cancelScan) { Text("Stop scan") }
                        } else {
                            Button(onClick = { if (consent) vm.startScan() else askConsent = true }) {
                                Icon(Icons.Filled.Search, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                                Text(if (counts.isEmpty()) "Scan my library" else "Scan new & changed songs")
                            }
                            scan.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item { HubRow("To review", c(TagStatus.PENDING), Icons.Filled.AutoFixHigh, c(TagStatus.PENDING) > 0) { onOpen(TagMode.REVIEW) } }
            item {
                HubRow("Approved, waiting to apply", c(TagStatus.APPROVED), Icons.Filled.Done, c(TagStatus.APPROVED) > 0,
                    trailing = {
                        if (c(TagStatus.APPROVED) > 0) FilledTonalButton(onClick = vm::applyApproved, enabled = !busy) {
                            Text(if (busy) "Writing…" else "Apply")
                        }
                    }) { onOpen(TagMode.APPROVED) }
            }
            item { HubRow("Applied (can be undone)", c(TagStatus.APPLIED), Icons.Filled.CheckCircle, c(TagStatus.APPLIED) > 0) { onOpen(TagMode.APPLIED) } }
            item { HubRow("Rejected", c(TagStatus.REJECTED), Icons.Filled.Close, c(TagStatus.REJECTED) > 0) { onOpen(TagMode.REJECTED) } }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { HubRow("Already complete", c(TagStatus.NO_CHANGE), Icons.Filled.DoneAll, false) {} }
            item { HubRow("No confident match", c(TagStatus.NOT_FOUND), Icons.Filled.Search, false) {} }
            if (c(TagStatus.UNSUPPORTED) > 0) item { HubRow("Other formats (not supported yet)", c(TagStatus.UNSUPPORTED), Icons.Filled.Close, false) {} }
            if (c(TagStatus.FAILED) > 0) item { HubRow("Failed (will retry on next scan)", c(TagStatus.FAILED), Icons.Filled.Close, false) {} }
        }
    }

    if (askConsent) AlertDialog(
        onDismissRequest = { askConsent = false },
        title = { Text("Send song info to MusicBrainz?") },
        text = {
            Text(
                "To find correct tags, this feature sends each song's title and artist (or its file name when tags are missing) " +
                    "to the free MusicBrainz database (musicbrainz.org) over the internet. No audio, account or device information is sent. " +
                    "This only happens while a scan is running, and you can withdraw consent any time from this screen's menu.",
            )
        },
        confirmButton = { TextButton(onClick = { askConsent = false; vm.giveConsent(); vm.startScan() }) { Text("Agree & scan") } },
        dismissButton = { TextButton(onClick = { askConsent = false }) { Text("Cancel") } },
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Re-check songs?") },
        text = { Text("Forgets your rejections and the \"no match\" results so those songs are looked at again on the next scan.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetDecisions() }) { Text("Re-check") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable
private fun HubRow(
    title: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, clickable: Boolean,
    trailing: @Composable () -> Unit = {}, onClick: () -> Unit,
) {
    ListItem(
        modifier = if (clickable) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text("$count song${if (count == 1) "" else "s"}") },
        trailingContent = trailing,
    )
}

// ------------------------------------------------------------------ Lists (review / approved / applied / rejected)

@Composable
fun TagListScreen(vm: TagViewModel, mode: TagMode, onBack: () -> Unit) {
    val rows by when (mode) {
        TagMode.REVIEW -> vm.pending
        TagMode.APPROVED -> vm.approved
        TagMode.APPLIED -> vm.applied
        TagMode.REJECTED -> vm.rejected
    }.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(mode) { vm.clearSelection() }

    val ids = rows.map { it.songId }
    val target = if (selected.isEmpty()) ids else ids.filter { it in selected } // actions apply to selection, else everything
    val label = if (selected.isEmpty()) "all ${ids.size}" else "${target.size} selected"

    SubScreen(
        mode.title, onBack,
        actions = {
            if (rows.isNotEmpty()) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                AppMenu(menu, { menu = false }) {
                    AppMenuItem(text = { Text("Select all") }, onClick = { menu = false; vm.selectAll(ids) })
                    AppMenuItem(text = { Text("Clear selection") }, onClick = { menu = false; vm.clearSelection() })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (rows.isEmpty()) {
                EmptyState(Icons.Filled.DoneAll, "Nothing here", when (mode) {
                    TagMode.REVIEW -> "No suggestions waiting. Run a scan from the Update tags screen."
                    TagMode.APPROVED -> "Approved changes appear here until you apply them."
                    TagMode.APPLIED -> "Changes written to your files appear here, where you can undo them."
                    TagMode.REJECTED -> "Rejected suggestions appear here in case you change your mind."
                })
            } else {
                // bulk action bar: acts on the selection, or on everything when nothing is selected
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (mode) {
                        TagMode.REVIEW -> {
                            Button(onClick = { confirm = "approve" }, modifier = Modifier.weight(1f)) { Text("Approve $label") }
                            OutlinedButton(onClick = { confirm = "reject" }, modifier = Modifier.weight(1f)) { Text("Reject $label") }
                        }
                        TagMode.APPROVED -> {
                            Button(onClick = vm::applyApproved, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text(if (busy) "Writing…" else "Apply all ${ids.size}")
                            }
                            OutlinedButton(onClick = { vm.restore(target) }, modifier = Modifier.weight(1f)) { Text("Back to review") }
                        }
                        TagMode.APPLIED -> OutlinedButton(onClick = { confirm = "undo" }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Undo $label")
                        }
                        TagMode.REJECTED -> OutlinedButton(onClick = { vm.restore(target) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Restore $label")
                        }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(rows, key = { it.songId }) { row ->
                        SuggestionCard(vm, row, mode, row.songId in selected)
                    }
                }
            }
        }
    }

    confirm?.let { action ->
        val (title, text) = when (action) {
            "approve" -> "Approve $label?" to "They'll move to \"Approved\" and are written to the files when you press Apply."
            "reject" -> "Reject $label?" to "They won't be suggested again unless the file changes or you re-check rejected songs."
            else -> "Undo $label?" to "Restores the previous tags in the files."
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) }, text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (action) { "approve" -> vm.approve(target); "reject" -> vm.reject(target); else -> vm.undo(target) }
                }) { Text("Yes") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SuggestionCard(vm: TagViewModel, row: TagSuggestion, mode: TagMode, isSelected: Boolean) {
    val editable = mode == TagMode.REVIEW || mode == TagMode.APPROVED
    Card(
        Modifier.fillMaxWidth().clickable { vm.toggleSelected(row.songId) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Checkbox(isSelected, { vm.toggleSelected(row.songId) })
                ArtImage(vm.songUri(row.songId), Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.curTitle ?: row.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(row.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (mode == TagMode.REVIEW) {
                    IconButton(onClick = { vm.reject(listOf(row.songId)) }) { Icon(Icons.Filled.Close, "Reject") }
                    IconButton(onClick = { vm.approve(listOf(row.songId)) }) { Icon(Icons.Filled.Done, "Approve", tint = MaterialTheme.colorScheme.primary) }
                }
            }
            FieldDiff(vm, row, TagField.TITLE, row.curTitle, row.newTitle, editable)
            FieldDiff(vm, row, TagField.ARTIST, row.curArtist, row.newArtist, editable)
            FieldDiff(vm, row, TagField.ALBUM, row.curAlbum, row.newAlbum, editable)
            FieldDiff(vm, row, TagField.ALBUM_ARTIST, row.curAlbumArtist, row.newAlbumArtist, editable)
            FieldDiff(vm, row, TagField.YEAR, row.curYear?.toString(), row.newYear?.toString(), editable)
            FieldDiff(vm, row, TagField.TRACK, row.curTrack?.toString(), row.newTrack?.toString(), editable)
            FieldDiff(vm, row, TagField.GENRE, row.curGenre, row.newGenre, editable)
            row.error?.let { Text("Last attempt failed: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun FieldDiff(vm: TagViewModel, row: TagSuggestion, field: TagField, old: String?, new: String?, editable: Boolean) {
    if (new == null) return
    val on = vm.included(row, field)
    Row(
        Modifier.fillMaxWidth().then(if (editable) Modifier.clickable { vm.toggleField(row.songId, field) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editable) Checkbox(on, { vm.toggleField(row.songId, field) }, Modifier.size(36.dp))
        Column(Modifier.weight(1f).padding(start = if (editable) 4.dp else 0.dp)) {
            Text(field.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${old?.takeIf { it.isNotBlank() } ?: "(empty)"}  →  $new",
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
