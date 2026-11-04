package dev.hien.bitstream.core.playback.engine

import dev.hien.bitstream.core.model.RepeatMode
import kotlin.random.Random

/**
 * Ordered playback queue with repeat off/all/one and seeded shuffle history so
 * [previous] returns to the tracks actually visited before walking backward
 * through the play order.
 */
class Queue<T>(
    items: List<T> = emptyList(),
    startIndex: Int = 0,
    repeat: RepeatMode = RepeatMode.OFF,
    shuffle: Boolean = false,
    private val seed: Long = DEFAULT_SEED,
) {
    private val mutableItems = items.toMutableList()
    private val history = ArrayDeque<Int>()
    private var playOrder: List<Int> = emptyList()

    var repeat: RepeatMode = repeat
        private set

    var shuffle: Boolean = shuffle
        private set

    var currentIndex: Int = if (mutableItems.isEmpty()) {
        -1
    } else {
        startIndex.coerceIn(0, mutableItems.lastIndex)
    }
        private set

    val items: List<T>
        get() = mutableItems.toList()

    val size: Int
        get() = mutableItems.size

    val isEmpty: Boolean
        get() = mutableItems.isEmpty()

    val current: T?
        get() = mutableItems.getOrNull(currentIndex)

    init {
        rebuildOrder()
    }

    fun setItems(newItems: List<T>, startIndex: Int = 0) {
        mutableItems.clear()
        mutableItems.addAll(newItems)
        history.clear()
        currentIndex = if (mutableItems.isEmpty()) {
            -1
        } else {
            startIndex.coerceIn(0, mutableItems.lastIndex)
        }
        rebuildOrder()
    }

    fun setRepeat(mode: RepeatMode) {
        repeat = mode
    }

    fun setShuffle(enabled: Boolean, shuffleSeed: Long = seed) {
        if (shuffle == enabled && playOrder.size == mutableItems.size) return
        shuffle = enabled
        history.clear()
        rebuildOrder(shuffleSeed)
    }

    fun select(index: Int): T? {
        if (index !in mutableItems.indices) return null
        if (currentIndex in mutableItems.indices && currentIndex != index) {
            history.addLast(currentIndex)
        }
        currentIndex = index
        if (shuffle) {
            rebuildOrder()
        }
        return current
    }

    fun peekNext(ignoreRepeatOne: Boolean = false): T? {
        val nextIndex = nextIndex(ignoreRepeatOne) ?: return null
        return mutableItems.getOrNull(nextIndex)
    }

    fun advance(ignoreRepeatOne: Boolean = false): T? {
        val nextIndex = nextIndex(ignoreRepeatOne) ?: return null
        if (currentIndex in mutableItems.indices && nextIndex != currentIndex) {
            history.addLast(currentIndex)
        }
        currentIndex = nextIndex
        return current
    }

    fun previous(): T? {
        if (mutableItems.isEmpty() || currentIndex !in mutableItems.indices) return null
        if (history.isNotEmpty()) {
            currentIndex = history.removeLast()
            return current
        }
        val order = activeOrder()
        val position = order.indexOf(currentIndex).takeIf { it >= 0 } ?: 0
        val previousIndex = when {
            position > 0 -> order[position - 1]
            repeat == RepeatMode.ALL -> order.last()
            else -> order.first()
        }
        currentIndex = previousIndex
        return current
    }

    fun removeAt(index: Int): T? {
        if (index !in mutableItems.indices) return null
        val removed = mutableItems.removeAt(index)
        history.clear()
        currentIndex = when {
            mutableItems.isEmpty() -> -1
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> currentIndex.coerceAtMost(mutableItems.lastIndex)
            else -> currentIndex
        }
        rebuildOrder()
        return removed
    }

    fun move(from: Int, to: Int) {
        if (from !in mutableItems.indices || to !in mutableItems.indices || from == to) return
        val item = mutableItems.removeAt(from)
        mutableItems.add(to, item)
        history.clear()
        currentIndex = when {
            from == currentIndex -> to
            from < currentIndex && to >= currentIndex -> currentIndex - 1
            from > currentIndex && to <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        rebuildOrder()
    }

    private fun nextIndex(ignoreRepeatOne: Boolean): Int? {
        if (mutableItems.isEmpty() || currentIndex !in mutableItems.indices) return null
        if (!ignoreRepeatOne && repeat == RepeatMode.ONE) {
            return currentIndex
        }
        val order = activeOrder()
        val position = order.indexOf(currentIndex).takeIf { it >= 0 } ?: 0
        return when {
            position + 1 < order.size -> order[position + 1]
            repeat == RepeatMode.ALL -> order.firstOrNull()
            else -> null
        }
    }

    private fun activeOrder(): List<Int> {
        if (playOrder.size != mutableItems.size) {
            rebuildOrder()
        }
        return playOrder
    }

    private fun rebuildOrder(shuffleSeed: Long = seed) {
        if (mutableItems.isEmpty()) {
            playOrder = emptyList()
            return
        }
        val indices = mutableItems.indices.toList()
        if (!shuffle) {
            playOrder = indices
            return
        }
        val current = currentIndex.takeIf { it in mutableItems.indices }
        val remaining = indices.filter { it != current }.shuffled(Random(shuffleSeed))
        playOrder = if (current != null) {
            listOf(current) + remaining
        } else {
            remaining
        }
    }

    private companion object {
        const val DEFAULT_SEED = 0xB1757EA0L
    }
}
