package dev.hien.bitstream.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.ui.nowplaying.formatSourceRateDepth

/**
 * Modal bottom sheet presenting the playback queue with drag-to-reorder,
 * swipe-to-remove, tap-to-play, format badges, and an empty state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    onDismiss: () -> Unit,
    onMove: ((from: Int, to: Int) -> Unit)? = null,
    onRemove: ((index: Int) -> Unit)? = null,
    onPlay: ((index: Int) -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val viewModel: QueueViewModel = viewModel(
        factory = viewModelFactory {
            initializer { QueueViewModel(container.playbackEngine) }
        },
    )
    val state by viewModel.state.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("queue_sheet"),
    ) {
        QueuePane(
            state = state,
            onMove = onMove ?: viewModel::move,
            onRemove = onRemove ?: viewModel::remove,
            onPlay = onPlay ?: viewModel::play,
            onToggleShuffle = viewModel::toggleShuffle,
            onCycleRepeat = viewModel::cycleRepeat,
            onClose = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 640.dp),
        )
    }
}

/**
 * Reusable queue content rendered inside [QueueSheet] on compact screens or a
 * supporting pane on medium/expanded screens.
 */
@Composable
fun QueuePane(
    state: QueueUiState,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onPlay: (index: Int) -> Unit,
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("queue_pane"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "${state.items.size} tracks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.shuffle,
                    onClick = onToggleShuffle,
                    label = { Text("Shuffle") },
                )
                FilterChip(
                    selected = state.repeat != RepeatMode.OFF,
                    onClick = onCycleRepeat,
                    label = {
                        Text(
                            when (state.repeat) {
                                RepeatMode.OFF -> "Repeat Off"
                                RepeatMode.ALL -> "Repeat All"
                                RepeatMode.ONE -> "Repeat 1"
                            },
                        )
                    },
                )
                if (onClose != null) {
                    TextButton(onClick = onClose) {
                        Text("Done")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .testTag("queue_empty"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing queued",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = state.items,
                    key = { index, track -> "${track.id}-$index" },
                ) { index, track ->
                    QueueRow(
                        track = track,
                        index = index,
                        totalCount = state.items.size,
                        isCurrent = index == state.currentIndex,
                        onPlay = { onPlay(index) },
                        onMove = onMove,
                        onRemove = { onRemove(index) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueRow(
    track: Track,
    index: Int,
    totalCount: Int,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart ||
                value == SwipeToDismissBoxValue.StartToEnd
            ) {
                onRemove()
                true
            } else {
                false
            }
        },
    )

    var dragAccumulatorPx by remember(index) { mutableFloatStateOf(0f) }
    val rowStepPx = 120f

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "Remove",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        val containerColor = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }

        ListItem(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onPlay)
                .testTag("queue_row_${track.id}"),
            colors = ListItemDefaults.colors(containerColor = containerColor),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .pointerInput(index, totalCount) {
                            detectVerticalDragGestures(
                                onDragEnd = { dragAccumulatorPx = 0f },
                                onDragCancel = { dragAccumulatorPx = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAccumulatorPx += dragAmount
                                    if (dragAccumulatorPx >= rowStepPx && index < totalCount - 1) {
                                        dragAccumulatorPx = 0f
                                        onMove(index, index + 1)
                                    } else if (dragAccumulatorPx <= -rowStepPx && index > 0) {
                                        dragAccumulatorPx = 0f
                                        onMove(index, index - 1)
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isCurrent) "▶" else "≡",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            headlineContent = {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                )
            },
            supportingContent = {
                Text(
                    text = track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                    ) {
                        Text(
                            text = formatSourceRateDepth(track.bitDepth, track.sampleRate),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Text(
                            text = "×",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}
