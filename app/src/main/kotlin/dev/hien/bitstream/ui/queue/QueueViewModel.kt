package dev.hien.bitstream.ui.queue

import androidx.lifecycle.ViewModel
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.playback.engine.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class QueueUiState(
    val items: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
)

class QueueViewModel(
    private val controller: PlaybackController,
) : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val _state = MutableStateFlow(
        buildUiState(
            items = controller.queueItems.value,
            currentIndex = controller.currentQueueIndex.value,
            shuffle = controller.state.value.shuffle,
            repeat = controller.state.value.repeat,
        ),
    )
    val state: StateFlow<QueueUiState> = _state.asStateFlow()

    init {
        vmScope.launch {
            combine(
                controller.queueItems,
                controller.currentQueueIndex,
                controller.state,
            ) { items, currentIndex, playbackState ->
                buildUiState(
                    items = items,
                    currentIndex = currentIndex,
                    shuffle = playbackState.shuffle,
                    repeat = playbackState.repeat,
                )
            }.collect { next ->
                _state.value = next
            }
        }
    }

    fun remove(index: Int) {
        controller.removeQueueItem(index)
    }

    fun move(from: Int, to: Int) {
        controller.moveQueueItem(from, to)
    }

    fun play(index: Int) {
        controller.playIndex(index)
    }

    fun toggleShuffle() {
        controller.setShuffle(!_state.value.shuffle)
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        controller.setRepeat(next)
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }

    private fun buildUiState(
        items: List<Track>,
        currentIndex: Int,
        shuffle: Boolean,
        repeat: RepeatMode,
    ): QueueUiState = QueueUiState(
        items = items,
        currentIndex = if (currentIndex in items.indices) currentIndex else -1,
        shuffle = shuffle,
        repeat = repeat,
    )
}
