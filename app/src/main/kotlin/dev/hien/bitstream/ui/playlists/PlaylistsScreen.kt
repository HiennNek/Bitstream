package dev.hien.bitstream.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.BitstreamEmptyState
import dev.hien.bitstream.core.designsystem.BitstreamLargeHeader
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.core.model.Track
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun PlaylistsRoute(onTrack: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: PlaylistsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PlaylistsViewModel(container.playlistStore, container.musicRepository)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryTracks by container.musicRepository.tracks.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )
    val scope = rememberCoroutineScope()
    // The detail is ViewModel state rather than a nav destination, so system back
    // must clear the selection instead of leaving the Playlists tab.
    BackHandler(enabled = state.selectedId != null) { viewModel.select(null) }

    PlaylistsScreen(
        state = state,
        libraryTracks = libraryTracks,
        onSelect = viewModel::select,
        onBack = { viewModel.select(null) },
        onCreate = { name -> scope.launch { viewModel.create(name) } },
        onRename = { id, name -> scope.launch { viewModel.rename(id, name) } },
        onDelete = { id -> scope.launch { viewModel.delete(id) } },
        onAddTrack = { trackId ->
            state.selectedId?.let { id -> scope.launch { viewModel.addTrack(id, trackId) } }
        },
        onMove = { from, to ->
            state.selectedId?.let { id -> scope.launch { viewModel.reorder(id, from, to) } }
        },
        onRemove = { trackId ->
            state.selectedId?.let { id -> scope.launch { viewModel.removeTrack(id, trackId) } }
        },
        onTrack = { trackId ->
            val playlistTracks = state.tracks
            val startIndex = playlistTracks.indexOfFirst { it.id == trackId }
            if (playlistTracks.isNotEmpty() && startIndex >= 0) {
                container.playTracks(playlistTracks, startIndex = startIndex)
            } else {
                onTrack(trackId)
            }
        },
    )
}

@Composable
fun PlaylistsScreen(
    state: PlaylistsUiState,
    libraryTracks: List<Track> = emptyList(),
    onSelect: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    onCreate: (String) -> Unit = {},
    onRename: (Long, String) -> Unit = { _, _ -> },
    onDelete: (Long) -> Unit = {},
    onAddTrack: (Long) -> Unit = {},
    onMove: (Int, Int) -> Unit = { _, _ -> },
    onRemove: (Long) -> Unit = {},
    onTrack: (Long) -> Unit = {},
) {
    val selected = state.playlists.firstOrNull { it.id == state.selectedId }
    if (selected == null) {
        PlaylistList(state = state, onSelect = onSelect, onCreate = onCreate)
    } else {
        PlaylistDetail(
            playlist = selected,
            state = state,
            libraryTracks = libraryTracks,
            onBack = onBack,
            onRename = onRename,
            onDelete = onDelete,
            onAddTrack = onAddTrack,
            onMove = onMove,
            onRemove = onRemove,
            onTrack = onTrack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistList(
    state: PlaylistsUiState,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        // The app scaffold already keeps this content above the nav bar and the
        // mini player; adding the system inset again would double the gap.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BitstreamLargeHeader(title = "Playlists") },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "New playlist",
                )
            }
        },
    ) { padding ->
        if (state.playlists.isEmpty()) {
            BitstreamEmptyState(
                title = "No playlists yet",
                message = "Create one to collect tracks.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.xxxl),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(state.playlists, key = { it.id }) { playlist ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(Radii.tile))
                            .clickable { onSelect(playlist.id) },
                        content = {
                            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                onCreate(name)
                showCreate = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetail(
    playlist: PlaylistInfo,
    state: PlaylistsUiState,
    libraryTracks: List<Track>,
    onBack: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onAddTrack: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onTrack: (Long) -> Unit,
) {
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showAddTracks by rememberSaveable { mutableStateOf(false) }
    val tracksById = remember(state.tracks) { state.tracks.associateBy { it.id } }

    Column(modifier = Modifier.fillMaxSize()) {
        BitstreamLargeHeader(title = playlist.name) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            TextButton(onClick = { showAddTracks = true }) { Text("Add tracks") }
            TextButton(onClick = { showRename = true }) { Text("Rename") }
            TextButton(onClick = { showDelete = true }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }

        if (state.entries.isEmpty()) {
            BitstreamEmptyState(
                title = "This playlist is empty.",
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(Spacing.xxxl),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                itemsIndexed(
                    items = state.entries,
                    key = { index, trackId -> "$index-$trackId" },
                ) { index, trackId ->
                    PlaylistEntryRow(
                        index = index,
                        count = state.entries.size,
                        track = tracksById[trackId],
                        onPlay = { onTrack(trackId) },
                        onMove = onMove,
                        onRemove = { onRemove(trackId) },
                    )
                }
            }
        }
    }

    if (showAddTracks) {
        AlertDialog(
            onDismissRequest = { showAddTracks = false },
            title = { Text("Add tracks to ${playlist.name}") },
            text = {
                if (libraryTracks.isEmpty()) {
                    Text("No tracks in library.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(libraryTracks, key = { it.id }) { track ->
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddTrack(track.id) },
                                content = {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = {
                                    Text("+", style = MaterialTheme.typography.titleMedium)
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTracks = false }) { Text("Done") }
            },
        )
    }

    if (showRename) {
        NameDialog(
            title = "Rename playlist",
            initial = playlist.name,
            confirmLabel = "Save",
            onDismiss = { showRename = false },
            onConfirm = { name ->
                onRename(playlist.id, name)
                showRename = false
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("This removes the playlist, not the FLAC files.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(playlist.id)
                        showDelete = false
                        onBack()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistEntryRow(
    index: Int,
    count: Int,
    track: Track?,
    onPlay: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
) {
    val itemHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            // Offset before clip so a dragged row is not cut off by its slot.
            .offset { IntOffset(0, dragY.roundToInt()) }
            .zIndex(if (dragY != 0f) 1f else 0f)
            .clip(RoundedCornerShape(Radii.tile))
            .clickable(onClick = onPlay),
        leadingContent = { Text("${index + 1}") },
        content = {
            Text(
                text = track?.title ?: "Track $index",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (track != null) {
            {
                Text(
                    text = listOf(track.artist, track.album)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Remove from playlist",
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = "Reorder",
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(index, count) {
                            detectDragGestures(
                                onDragStart = { dragY = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragY += amount.y
                                },
                                onDragEnd = {
                                    val shift = (dragY / itemHeightPx).roundToInt()
                                    if (shift != 0) {
                                        onMove(index, (index + shift).coerceIn(0, count - 1))
                                    }
                                    dragY = 0f
                                },
                                onDragCancel = { dragY = 0f },
                            )
                        },
                )
            }
        },
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
