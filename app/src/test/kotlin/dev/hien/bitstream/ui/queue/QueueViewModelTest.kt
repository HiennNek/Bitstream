package dev.hien.bitstream.ui.queue

import dev.hien.bitstream.core.model.PlaybackState
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.playback.engine.PlaybackController
import dev.hien.bitstream.core.playback.engine.Queue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueViewModelTest {
    @Test
    fun `remove keeps current index valid`() = runTest {
        val vm = QueueViewModel(FakeEngine(queue = listOf(1, 2, 3), index = 2))
        vm.remove(0)
        assertEquals(1, vm.state.first().currentIndex)
        assertEquals(listOf(2L, 3L), vm.state.first().items.map { it.id })
    }

    @Test
    fun `move updates item order and tracks current item`() = runTest {
        val vm = QueueViewModel(FakeEngine(queue = listOf(1, 2, 3), index = 0))
        vm.move(0, 2)
        assertEquals(2, vm.state.first().currentIndex)
        assertEquals(listOf(2L, 3L, 1L), vm.state.first().items.map { it.id })
    }

    @Test
    fun `play updates current index`() = runTest {
        val vm = QueueViewModel(FakeEngine(queue = listOf(1, 2, 3), index = 0))
        vm.play(1)
        assertEquals(1, vm.state.first().currentIndex)
    }

    @Test
    fun `toggleShuffle and cycleRepeat update state`() = runTest {
        val vm = QueueViewModel(FakeEngine(queue = listOf(1, 2, 3), index = 0))
        vm.toggleShuffle()
        assertTrue(vm.state.first().shuffle)
        vm.cycleRepeat()
        assertEquals(RepeatMode.ALL, vm.state.first().repeat)
    }
}

private class FakeEngine(
    queue: List<Int>,
    index: Int,
) : PlaybackController {
    private val backingQueue = Queue<Track>().apply {
        setItems(
            queue.map { id ->
                Track(
                    id = id.toLong(),
                    uri = "content://bitstream/track/$id",
                    title = "Track $id",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 180_000L,
                    sampleRate = 96_000,
                    bitDepth = 24,
                    channels = 2,
                    hasMd5 = true,
                )
            },
            startIndex = index,
        )
    }

    private val _state = MutableStateFlow(
        PlaybackState(
            track = backingQueue.current,
            queueSize = backingQueue.size,
        ),
    )
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queueItems = MutableStateFlow(backingQueue.items)
    override val queueItems: StateFlow<List<Track>> = _queueItems.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(backingQueue.currentIndex)
    override val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(ms: Long) = Unit
    override fun next() = Unit
    override fun previous() = Unit

    override fun playIndex(index: Int) {
        backingQueue.select(index)
        sync()
    }

    override fun moveQueueItem(from: Int, to: Int) {
        backingQueue.move(from, to)
        sync()
    }

    override fun removeQueueItem(index: Int) {
        backingQueue.removeAt(index)
        sync()
    }

    override fun setShuffle(on: Boolean) {
        backingQueue.setShuffle(on)
        sync()
    }

    override fun setRepeat(mode: RepeatMode) {
        backingQueue.setRepeat(mode)
        sync()
    }

    private fun sync() {
        _queueItems.value = backingQueue.items
        _currentQueueIndex.value = backingQueue.currentIndex
        _state.update {
            it.copy(
                track = backingQueue.current,
                queueSize = backingQueue.size,
                shuffle = backingQueue.shuffle,
                repeat = backingQueue.repeat,
            )
        }
    }
}
