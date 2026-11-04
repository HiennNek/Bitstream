package dev.hien.bitstream.core.playback.engine

import dev.hien.bitstream.core.model.AudioFormat
import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.PlaybackStatus
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.model.VolumeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEngineTest {
    @Test
    fun `auto-advance skips an unsupported rate with a notice`() = runTest {
        val backend = FakeBackend(supportedRates = setOf(44_100, 48_000))
        val engine = PlaybackEngine(backend, this)
        engine.setQueue(listOf(track(1, 44_100), track(2, 384_000), track(3, 48_000)), 0)
        backend.finishCurrent()
        assertEquals(3L, engine.state.first().track?.id)
        assertEquals(1, engine.notices.value.size)
    }

    @Test
    fun `gapless is requested only when format matches`() = runTest {
        val backend = FakeBackend(supportedRates = setOf(44_100))
        val engine = PlaybackEngine(backend, this)
        engine.setQueue(listOf(track(1, 44_100, 24), track(2, 44_100, 16)), 0)
        backend.advanceToNearEnd()
        assertTrue(backend.enqueued.isEmpty()) // depth differs
    }

    @Test
    fun `gapless is enqueued when rate depth and channels match within 10s of end`() = runTest {
        val backend = FakeBackend(supportedRates = setOf(96_000))
        val engine = PlaybackEngine(backend, this)
        engine.setQueue(listOf(track(1, 96_000, 24), track(2, 96_000, 24)), 0)
        backend.advanceToNearEnd()
        assertEquals(listOf(2L), backend.enqueued.map { it.id })

        backend.finishCurrent()
        assertEquals(2L, engine.state.first().track?.id)
        assertEquals(PlaybackStatus.READY, engine.state.first().status)
    }

    @Test
    fun `queue persists position and repeat mode`() = runTest {
        val queue = Queue(listOf(1L, 2L, 3L), startIndex = 1, repeat = RepeatMode.ALL)
        queue.advance()
        queue.advance()
        queue.advance()
        assertEquals(2L, queue.current)
    }

    @Test
    fun `decode failure advances and reports`() = runTest {
        val backend = FakeBackend(failOn = setOf(2L))
        val engine = PlaybackEngine(backend, this)
        engine.setQueue(listOf(track(1), track(2), track(3)), 0)
        backend.finishCurrent()
        assertEquals(3L, engine.state.first().track?.id)
        assertTrue(engine.notices.value.any { it.contains("could not be decoded") })
    }

    @Test
    fun `position derives from framesWritten minus ringFrames`() = runTest {
        val backend = FakeBackend(supportedRates = setOf(48_000))
        val engine = PlaybackEngine(backend, this)
        engine.setQueue(listOf(track(1, 48_000, 24, durationMs = 120_000L)), 0)
        backend.emit(
            BackendEvent.Progress(
                framesWritten = 48_000L * 15L,
                ringFrames = 48_000L * 2L,
                sampleRate = 48_000,
            ),
        )
        assertEquals(13_000L, engine.state.first().positionMs)
    }

    @Test
    fun `software volume demotes path without demoting verified md5`() = runTest {
        val backend = FakeBackend(supportedRates = setOf(44_100))
        val engine = PlaybackEngine(backend, this)
        engine.setQueue(listOf(track(1, 44_100, 16)), 0)
        assertEquals(BitPerfectPath.BIT_PERFECT, engine.state.first().path)

        engine.setSoftwareVolume(true)
        engine.setSoftwareVolumeLevel(50)
        backend.emit(BackendEvent.Md5(Integrity.VERIFIED))

        val current = engine.state.first()
        assertEquals(BitPerfectPath.SOFTWARE_VOLUME, current.path)
        assertEquals(VolumeState.Software(50), current.volume)
        assertEquals(50, backend.softwareVolumeLevel)
        assertEquals(Integrity.VERIFIED, current.integrity)
    }

    @Test
    fun `disabling gapless suppresses pre-enqueue near track end`() = runTest {
        val backend = FakeBackend(supportedRates = setOf(96_000))
        val engine = PlaybackEngine(backend, this)
        engine.setGapless(false)
        engine.setQueue(listOf(track(1, 96_000, 24), track(2, 96_000, 24)), 0)
        backend.advanceToNearEnd()
        assertTrue(backend.enqueued.isEmpty())
    }

    @Test
    fun `seeded shuffle history returns to previously visited track`() {
        val queue = Queue(
            items = listOf(10L, 20L, 30L, 40L, 50L),
            startIndex = 2,
            repeat = RepeatMode.OFF,
            shuffle = true,
            seed = 42L,
        )
        assertEquals(30L, queue.current)
        val second = queue.advance()
        val third = queue.advance()
        assertTrue(second != null && third != null)
        assertEquals(second, queue.previous())
        assertEquals(30L, queue.previous())
    }

    @Test
    fun `notices keep only the last 20 entries newest last`() = runTest {
        val failingIds = (1L..25L).toSet()
        val backend = FakeBackend(failOn = failingIds)
        val engine = PlaybackEngine(backend, this)
        val tracks = (1L..25L).map { track(it) } + track(26L)
        engine.setQueue(tracks, 0)

        assertEquals(26L, engine.state.first().track?.id)
        assertEquals(20, engine.notices.value.size)
        assertTrue(engine.notices.value.first().contains("Track 6"))
        assertTrue(engine.notices.value.last().contains("Track 25"))
    }
}

internal class FakeBackend(
    private val supportedRates: Set<Int> = setOf(44_100, 48_000, 96_000, 192_000),
    private val failOn: Set<Long> = emptySet(),
    private var dacConnected: Boolean = true,
) : AudioBackend {
    private val eventFlow = MutableSharedFlow<BackendEvent>(extraBufferCapacity = 16)
    val enqueued = mutableListOf<Track>()
    var currentTrack: Track? = null
        private set
    var currentFormat: AudioFormat? = null
        private set
    var softwareVolume: Boolean = false
        private set
    var softwareVolumeLevel: Int = 100
        private set
    var lastStartFrame: Long = 0L
        private set

    override val events: Flow<BackendEvent> = eventFlow

    override suspend fun open(track: Track, format: AudioFormat): OpenResult {
        if (!dacConnected) {
            return OpenResult.NoDac("No USB DAC connected")
        }
        if (track.id in failOn) {
            return OpenResult.Failed("${track.title} could not be decoded")
        }
        if (format.sampleRate !in supportedRates) {
            return OpenResult.UnsupportedFormat(
                reason = "${format.sampleRate} Hz is not supported",
                supportedRates = supportedRates.sorted(),
            )
        }
        currentTrack = track
        currentFormat = format
        return OpenResult.Ready()
    }

    override suspend fun start(uri: String, startFrame: Long) {
        lastStartFrame = startFrame
    }

    override suspend fun enqueueNext(track: Track, format: AudioFormat): Boolean {
        if (track.id in failOn || format.sampleRate !in supportedRates) {
            return false
        }
        enqueued += track
        return true
    }

    override suspend fun pause() = Unit

    override suspend fun resume() = Unit

    override suspend fun stop() = Unit

    override suspend fun close() = Unit

    override fun setSoftwareVolume(enabled: Boolean) {
        softwareVolume = enabled
    }

    override fun setSoftwareVolumeLevel(level: Int) {
        softwareVolumeLevel = level
    }

    fun finishCurrent(integrity: Integrity = Integrity.VERIFIED) {
        val track = currentTrack
        val frames = if (track != null) {
            (track.durationMs * track.sampleRate) / 1_000L
        } else {
            0L
        }
        eventFlow.tryEmit(BackendEvent.Md5(integrity))
        eventFlow.tryEmit(BackendEvent.Ended(integrity = integrity, framesWritten = frames))
    }

    fun advanceToNearEnd() {
        val track = currentTrack ?: return
        val remainingMs = 5_000L
        val targetMs = (track.durationMs - remainingMs).coerceAtLeast(0L)
        val framesWritten = (targetMs * track.sampleRate) / 1_000L
        eventFlow.tryEmit(
            BackendEvent.Progress(
                framesWritten = framesWritten,
                ringFrames = 0L,
                sampleRate = track.sampleRate,
            ),
        )
    }

    fun emit(event: BackendEvent) {
        eventFlow.tryEmit(event)
    }

    fun detach() {
        dacConnected = false
        eventFlow.tryEmit(BackendEvent.Detached("Fake DAC"))
    }

    fun reattach() {
        dacConnected = true
        eventFlow.tryEmit(BackendEvent.Reattached("Fake DAC"))
    }
}

internal fun track(
    id: Long,
    sampleRate: Int = 44_100,
    bitDepth: Int = 16,
    channels: Int = 2,
    durationMs: Long = 60_000L,
    hasMd5: Boolean = true,
): Track = Track(
    id = id,
    uri = "content://bitstream/track/$id",
    title = "Track $id",
    artist = "Artist",
    album = "Album",
    durationMs = durationMs,
    sampleRate = sampleRate,
    bitDepth = bitDepth,
    channels = channels,
    hasMd5 = hasMd5,
)
