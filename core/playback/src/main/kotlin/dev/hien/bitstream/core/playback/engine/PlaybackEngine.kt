package dev.hien.bitstream.core.playback.engine

import dev.hien.bitstream.core.model.AudioFormat
import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.PlaybackState
import dev.hien.bitstream.core.model.PlaybackStatus
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.model.VolumeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Transport and state interface consumed by the Media3 player adapter and UI
 * ViewModels.
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    val notices: StateFlow<List<String>>
        get() = MutableStateFlow(emptyList())

    val queueItems: StateFlow<List<Track>>
        get() = MutableStateFlow(emptyList())

    val currentQueueIndex: StateFlow<Int>
        get() = MutableStateFlow(-1)

    val dacName: StateFlow<String?>
        get() = MutableStateFlow(null)

    fun play()

    fun pause()

    fun seekTo(ms: Long)

    fun next()

    fun previous()

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) = Unit

    fun playIndex(index: Int) = Unit

    fun moveQueueItem(from: Int, to: Int) = Unit

    fun removeQueueItem(index: Int) = Unit

    fun setShuffle(on: Boolean) = Unit

    fun setRepeat(mode: RepeatMode) = Unit

    fun setSoftwareVolume(on: Boolean) = Unit

    fun setHardwareVolume(level: Int) = Unit

    fun stop() = Unit
}

/**
 * Serialized playback state machine coordinating the [Queue], [AudioBackend],
 * gapless pre-enqueueing, format/decode skip notices, and bit-perfect status.
 */
class PlaybackEngine(
    private val backend: AudioBackend,
    scope: CoroutineScope,
) : PlaybackController {
    private val job = SupervisorJob()
    private val engineScope = CoroutineScope(job + Dispatchers.Unconfined)
    private val mutex = Mutex()
    private val queue = Queue<Track>()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _notices = MutableStateFlow<List<String>>(emptyList())
    override val notices: StateFlow<List<String>> = _notices.asStateFlow()

    private val _queueItems = MutableStateFlow<List<Track>>(emptyList())
    override val queueItems: StateFlow<List<Track>> = _queueItems.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(-1)
    override val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _dacName = MutableStateFlow<String?>(null)
    override val dacName: StateFlow<String?> = _dacName.asStateFlow()

    private var softwareVolumeEnabled: Boolean = false
    private var lastHardwareVolume: VolumeState = VolumeState.Fixed
    private var gaplessEnqueuedTrackId: Long? = null
    private var resumeFrame: Long = 0L

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            job.cancel()
        }
        engineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            backend.events.collect { event ->
                mutex.withLock {
                    onBackendEvent(event)
                }
            }
        }
    }

    override fun setQueue(tracks: List<Track>, startIndex: Int) {
        enqueueCommand {
            gaplessEnqueuedTrackId = null
            resumeFrame = 0L
            queue.setItems(tracks, startIndex)
            syncQueueFlows()
            if (tracks.isEmpty()) {
                backend.stop()
                _state.update {
                    it.copy(
                        status = PlaybackStatus.IDLE,
                        track = null,
                        positionMs = 0L,
                        durationMs = 0L,
                        underruns = 0L,
                        queueSize = 0,
                        integrity = Integrity.UNAVAILABLE,
                    )
                }
                return@enqueueCommand
            }
            startCurrentOrSkip(initialAttempt = true)
        }
    }

    override fun play() {
        enqueueCommand {
            val current = queue.current ?: return@enqueueCommand
            when (_state.value.status) {
                PlaybackStatus.READY -> return@enqueueCommand
                PlaybackStatus.PAUSED -> {
                    backend.resume()
                    _state.update { it.copy(status = PlaybackStatus.READY) }
                }
                else -> {
                    openAndStart(current, startFrame = resumeFrame, allowSkipOnFailure = false)
                }
            }
        }
    }

    override fun pause() {
        enqueueCommand {
            if (_state.value.status != PlaybackStatus.READY) return@enqueueCommand
            val track = queue.current
            if (track != null && track.sampleRate > 0) {
                resumeFrame = (_state.value.positionMs * track.sampleRate) / 1_000L
            }
            gaplessEnqueuedTrackId = null
            backend.pause()
            _state.update { it.copy(status = PlaybackStatus.PAUSED) }
        }
    }

    override fun seekTo(ms: Long) {
        enqueueCommand {
            val track = queue.current ?: return@enqueueCommand
            val clampedMs = if (track.durationMs > 0) {
                ms.coerceIn(0L, track.durationMs)
            } else {
                ms.coerceAtLeast(0L)
            }
            val targetFrame = if (track.sampleRate > 0) {
                (clampedMs * track.sampleRate) / 1_000L
            } else {
                0L
            }
            resumeFrame = targetFrame
            gaplessEnqueuedTrackId = null
            _state.update {
                it.copy(
                    positionMs = clampedMs,
                    integrity = if (track.hasMd5) Integrity.PARTIAL else Integrity.UNAVAILABLE,
                )
            }
            if (_state.value.status == PlaybackStatus.READY) {
                backend.start(track.uri, targetFrame)
            }
        }
    }

    override fun next() {
        enqueueCommand {
            if (queue.isEmpty) return@enqueueCommand
            gaplessEnqueuedTrackId = null
            resumeFrame = 0L
            advanceToNextPlayable(ignoreRepeatOne = true)
        }
    }

    override fun previous() {
        enqueueCommand {
            if (queue.isEmpty) return@enqueueCommand
            gaplessEnqueuedTrackId = null
            resumeFrame = 0L
            val target = queue.previous() ?: return@enqueueCommand
            syncQueueFlows()
            openAndStart(target, startFrame = 0L, allowSkipOnFailure = true)
        }
    }

    override fun playIndex(index: Int) {
        enqueueCommand {
            val selected = queue.select(index) ?: return@enqueueCommand
            gaplessEnqueuedTrackId = null
            resumeFrame = 0L
            syncQueueFlows()
            openAndStart(selected, startFrame = 0L, allowSkipOnFailure = true)
        }
    }

    override fun moveQueueItem(from: Int, to: Int) {
        enqueueCommand {
            gaplessEnqueuedTrackId = null
            queue.move(from, to)
            syncQueueFlows()
        }
    }

    override fun removeQueueItem(index: Int) {
        enqueueCommand {
            val wasCurrent = index == queue.currentIndex
            gaplessEnqueuedTrackId = null
            queue.removeAt(index)
            syncQueueFlows()
            if (queue.isEmpty) {
                backend.stop()
                _state.update {
                    it.copy(
                        status = PlaybackStatus.IDLE,
                        track = null,
                        positionMs = 0L,
                        durationMs = 0L,
                        queueSize = 0,
                    )
                }
            } else if (wasCurrent) {
                resumeFrame = 0L
                startCurrentOrSkip(initialAttempt = true)
            } else {
                _state.update { it.copy(queueSize = queue.size) }
            }
        }
    }

    override fun setShuffle(on: Boolean) {
        enqueueCommand {
            gaplessEnqueuedTrackId = null
            queue.setShuffle(on)
            syncQueueFlows()
            _state.update { it.copy(shuffle = queue.shuffle) }
        }
    }

    override fun setRepeat(mode: RepeatMode) {
        enqueueCommand {
            gaplessEnqueuedTrackId = null
            queue.setRepeat(mode)
            _state.update { it.copy(repeat = queue.repeat) }
        }
    }

    override fun setSoftwareVolume(on: Boolean) {
        enqueueCommand {
            softwareVolumeEnabled = on
            backend.setSoftwareVolume(on)
            _state.update { current ->
                val activePath = when {
                    current.status == PlaybackStatus.NO_DAC ||
                        current.path == BitPerfectPath.NO_DAC -> BitPerfectPath.NO_DAC
                    current.path == BitPerfectPath.UNSUPPORTED_FORMAT ->
                        BitPerfectPath.UNSUPPORTED_FORMAT
                    on -> BitPerfectPath.SOFTWARE_VOLUME
                    else -> BitPerfectPath.BIT_PERFECT
                }
                val activeVolume = if (on) {
                    VolumeState.Software
                } else {
                    lastHardwareVolume
                }
                current.copy(path = activePath, volume = activeVolume)
            }
        }
    }

    override fun setHardwareVolume(level: Int) {
        enqueueCommand {
            val clamped = level.coerceIn(0, 100)
            lastHardwareVolume = VolumeState.Hardware(clamped)
            backend.setHardwareVolume(clamped)
            if (!softwareVolumeEnabled) {
                _state.update { it.copy(volume = lastHardwareVolume) }
            }
        }
    }

    override fun stop() {
        enqueueCommand {
            gaplessEnqueuedTrackId = null
            resumeFrame = 0L
            backend.stop()
            _state.update {
                it.copy(
                    status = PlaybackStatus.IDLE,
                    positionMs = 0L,
                )
            }
        }
    }

    fun close() {
        job.cancel()
    }

    private suspend fun startCurrentOrSkip(initialAttempt: Boolean) {
        val current = queue.current ?: return
        val started = openAndStart(
            track = current,
            startFrame = 0L,
            allowSkipOnFailure = queue.size > 1 || !initialAttempt,
        )
        if (!started && queue.size > 1) {
            advanceToNextPlayable(ignoreRepeatOne = true, maxAttempts = queue.size - 1)
        }
    }

    private suspend fun advanceToNextPlayable(
        ignoreRepeatOne: Boolean,
        maxAttempts: Int = queue.size.coerceAtLeast(1),
    ) {
        var attempts = 0
        var skipRepeatOne = ignoreRepeatOne
        while (attempts < maxAttempts) {
            val nextTrack = queue.advance(ignoreRepeatOne = skipRepeatOne)
            syncQueueFlows()
            if (nextTrack == null) {
                backend.stop()
                _state.update {
                    it.copy(
                        status = PlaybackStatus.ENDED,
                        positionMs = it.durationMs,
                    )
                }
                return
            }
            attempts += 1
            val started = openAndStart(
                track = nextTrack,
                startFrame = 0L,
                allowSkipOnFailure = true,
            )
            if (started || _state.value.status == PlaybackStatus.NO_DAC) {
                return
            }
            skipRepeatOne = true
        }
        backend.stop()
        _state.update { it.copy(status = PlaybackStatus.ERROR) }
    }

    private suspend fun openAndStart(
        track: Track,
        startFrame: Long,
        allowSkipOnFailure: Boolean,
    ): Boolean {
        _state.update {
            it.copy(
                status = PlaybackStatus.OPENING,
                track = track,
                positionMs = if (track.sampleRate > 0) {
                    (startFrame * 1_000L) / track.sampleRate
                } else {
                    0L
                },
                durationMs = track.durationMs,
                underruns = 0L,
                queueSize = queue.size,
                shuffle = queue.shuffle,
                repeat = queue.repeat,
                integrity = initialIntegrity(track, startFrame),
            )
        }

        return when (val result = backend.open(track, track.format)) {
            is OpenResult.Ready -> {
                lastHardwareVolume = result.volume
                _dacName.value = result.dacName
                val path = if (softwareVolumeEnabled) {
                    BitPerfectPath.SOFTWARE_VOLUME
                } else {
                    BitPerfectPath.BIT_PERFECT
                }
                val volume = if (softwareVolumeEnabled) {
                    VolumeState.Software
                } else {
                    result.volume
                }
                _state.update {
                    it.copy(
                        status = PlaybackStatus.READY,
                        path = path,
                        volume = volume,
                        track = track,
                        durationMs = track.durationMs,
                        queueSize = queue.size,
                    )
                }
                backend.start(track.uri, startFrame)
                true
            }

            is OpenResult.NoDac -> {
                addNotice(result.reason)
                _state.update {
                    it.copy(
                        status = PlaybackStatus.NO_DAC,
                        path = BitPerfectPath.NO_DAC,
                        track = track,
                        queueSize = queue.size,
                    )
                }
                false
            }

            is OpenResult.UnsupportedFormat -> {
                addNotice("Skipped ${track.title}: ${result.reason}")
                if (!allowSkipOnFailure) {
                    _state.update {
                        it.copy(
                            status = PlaybackStatus.ERROR,
                            path = BitPerfectPath.UNSUPPORTED_FORMAT,
                            track = track,
                            queueSize = queue.size,
                        )
                    }
                }
                false
            }

            is OpenResult.Failed -> {
                val detail = if (result.reason.contains("could not be decoded", ignoreCase = true)) {
                    result.reason
                } else {
                    "${track.title} could not be decoded: ${result.reason}"
                }
                addNotice("Skipped ${track.title}: $detail")
                if (!allowSkipOnFailure) {
                    _state.update {
                        it.copy(
                            status = PlaybackStatus.ERROR,
                            track = track,
                            queueSize = queue.size,
                        )
                    }
                }
                false
            }
        }
    }

    private suspend fun onBackendEvent(event: BackendEvent) {
        when (event) {
            is BackendEvent.Progress -> {
                val currentTrack = queue.current ?: return
                val positionMs = event.positionMs(currentTrack.sampleRate)
                _state.update {
                    it.copy(
                        positionMs = if (currentTrack.durationMs > 0) {
                            positionMs.coerceAtMost(currentTrack.durationMs)
                        } else {
                            positionMs
                        },
                    )
                }
                maybeEnqueueGapless(currentTrack, positionMs)
            }

            is BackendEvent.Underrun -> {
                _state.update { it.copy(underruns = event.count) }
            }

            is BackendEvent.Md5 -> {
                _state.update { it.copy(integrity = event.integrity) }
            }

            is BackendEvent.Ended -> {
                if (event.integrity != Integrity.UNAVAILABLE || _state.value.track?.hasMd5 != true) {
                    _state.update { it.copy(integrity = event.integrity) }
                }
                val preEnqueuedId = gaplessEnqueuedTrackId
                gaplessEnqueuedTrackId = null
                resumeFrame = 0L
                if (preEnqueuedId != null) {
                    val nextTrack = queue.advance()
                    syncQueueFlows()
                    if (nextTrack != null && nextTrack.id == preEnqueuedId) {
                        _state.update {
                            it.copy(
                                status = PlaybackStatus.READY,
                                track = nextTrack,
                                positionMs = 0L,
                                durationMs = nextTrack.durationMs,
                                underruns = 0L,
                                queueSize = queue.size,
                                integrity = initialIntegrity(nextTrack, startFrame = 0L),
                            )
                        }
                        return
                    }
                }
                advanceToNextPlayable(ignoreRepeatOne = false)
            }

            is BackendEvent.Failed -> {
                gaplessEnqueuedTrackId = null
                resumeFrame = 0L
                val currentTrack = queue.current
                val detail = if (event.reason.contains("could not be decoded", ignoreCase = true)) {
                    event.reason
                } else {
                    "${currentTrack?.title ?: "Track"} could not be decoded: ${event.reason}"
                }
                addNotice(detail)
                advanceToNextPlayable(ignoreRepeatOne = true)
            }
        }
    }

    private suspend fun maybeEnqueueGapless(currentTrack: Track, positionMs: Long) {
        if (gaplessEnqueuedTrackId != null) return
        if (currentTrack.durationMs <= 0L) return
        val remainingMs = currentTrack.durationMs - positionMs
        if (remainingMs > GAPLESS_WINDOW_MS) return
        val nextTrack = queue.peekNext() ?: return
        if (!isSameFormat(currentTrack.format, nextTrack.format)) return
        if (backend.enqueueNext(nextTrack, nextTrack.format)) {
            gaplessEnqueuedTrackId = nextTrack.id
        }
    }

    private fun isSameFormat(first: AudioFormat, second: AudioFormat): Boolean =
        first.sampleRate > 0 &&
            first.bitDepth > 0 &&
            first.channels > 0 &&
            first == second

    private fun initialIntegrity(track: Track, startFrame: Long): Integrity =
        when {
            !track.hasMd5 -> Integrity.UNAVAILABLE
            startFrame > 0L -> Integrity.PARTIAL
            else -> Integrity.PARTIAL
        }

    private fun syncQueueFlows() {
        _queueItems.value = queue.items
        _currentQueueIndex.value = queue.currentIndex
    }

    private fun addNotice(message: String) {
        _notices.update { existing ->
            (existing + message).takeLast(MAX_NOTICES)
        }
    }

    private fun enqueueCommand(block: suspend () -> Unit) {
        engineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                block()
            }
        }
    }

    private companion object {
        const val GAPLESS_WINDOW_MS = 10_000L
        const val MAX_NOTICES = 20
    }
}
