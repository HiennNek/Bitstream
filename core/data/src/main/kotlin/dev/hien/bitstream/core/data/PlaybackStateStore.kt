package dev.hien.bitstream.core.data

import dev.hien.bitstream.core.data.db.PlaybackStateDao
import dev.hien.bitstream.core.data.db.PlaybackStateEntity
import dev.hien.bitstream.core.model.RepeatMode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the queue so playback can be restored after process death. The queue
 * itself is a JSON array of track IDs; the rest of the row is small and fixed.
 */
class PlaybackStateStore(
    private val dao: PlaybackStateDao,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun save(
        queueIds: List<Long>,
        index: Int,
        positionMs: Long,
        shuffle: Boolean,
        repeat: RepeatMode,
    ) {
        dao.save(
            PlaybackStateEntity(
                id = 0,
                queueJson = json.encodeToString(queueIds),
                index = index,
                positionMs = positionMs,
                shuffle = shuffle,
                repeat = repeat.name,
            ),
        )
    }

    suspend fun load(): PlaybackStateEntity? = dao.load()

    fun queueIds(state: PlaybackStateEntity): List<Long> =
        runCatching { json.decodeFromString<List<Long>>(state.queueJson) }
            .getOrDefault(emptyList())
}
