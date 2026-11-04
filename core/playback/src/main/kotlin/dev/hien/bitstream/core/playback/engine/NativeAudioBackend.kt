package dev.hien.bitstream.core.playback.engine

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.hien.bitstream.core.model.AudioFormat
import dev.hien.bitstream.core.model.Integrity
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.model.VolumeState
import dev.hien.bitstream.core.playback.native.NativeAudio
import dev.hien.bitstream.core.playback.usb.DacState
import dev.hien.bitstream.core.playback.usb.UacFormat
import dev.hien.bitstream.core.playback.usb.UsbDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [AudioBackend] implementation backed by [UsbDeviceManager] and the JNI
 * [NativeAudio] bridge. Polls [NativeAudio.nativeStats] every 100 ms while a
 * stream is active and derives playback position from `framesWritten`.
 */
class NativeAudioBackend(
    private val context: Context,
    private val usbDeviceManager: UsbDeviceManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : AudioBackend {
    private val _events = MutableSharedFlow<BackendEvent>(extraBufferCapacity = 32)
    override val events: Flow<BackendEvent> = _events.asSharedFlow()

    private val _ringFillPercent = MutableStateFlow(0)
    val ringFillPercent: StateFlow<Int> = _ringFillPercent.asStateFlow()

    private val _lastNativeError = MutableStateFlow(0)
    val lastNativeError: StateFlow<Int> = _lastNativeError.asStateFlow()

    private var nativeHandle: Long = 0L
    private var openedDeviceFd: Int = -1
    private var openedSampleRate: Int = 0
    private var openedChannels: Int = 0
    private var openedSubslotBytes: Int = 0

    private var currentTrack: Track? = null
    private var currentFormat: AudioFormat? = null
    private var currentUacFormat: UacFormat? = null
    private val enqueuedTracks = ArrayDeque<Pair<Track, AudioFormat>>()
    private var startFrameOffset: Long = 0L
    private var lastFramesWritten: Long = 0L
    private var lastUnderruns: Long = 0L
    private var lastTrackIndex: Long = 0L
    private var lastIntegrity: Integrity = Integrity.UNAVAILABLE
    private var softwareVolumeEnabled: Boolean = false
    private var softwareVolumeLevel: Int = 100
    private var hardwareVolumeLevel: Int = 100
    private var pollJob: Job? = null
    private var wasDacConnected: Boolean = false
    private var lastDacName: String? = null

    init {
        usbDeviceManager.onBeforeClose = ::closeNativeHandle
        scope.launch {
            usbDeviceManager.state.collect { dacState ->
                when (dacState) {
                    is DacState.Connected -> {
                        val name = dacState.handle.name
                        val hadPrevious = lastDacName != null
                        lastDacName = name
                        if (!wasDacConnected && hadPrevious) {
                            _events.emit(BackendEvent.Reattached(name))
                        }
                        wasDacConnected = true
                    }

                    DacState.NoDevice -> {
                        if (wasDacConnected) {
                            wasDacConnected = false
                            closeNativeHandle()
                            _events.emit(BackendEvent.Detached(lastDacName))
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    override suspend fun open(track: Track, format: AudioFormat): OpenResult {
        val dacState = usbDeviceManager.state.value
        if (dacState !is DacState.Connected) {
            closeNativeHandle()
            val reason = when (dacState) {
                is DacState.PermissionNeeded ->
                    "Grant USB permission for ${dacState.name} to play."
                is DacState.Error -> dacState.reason
                is DacState.Connecting -> "Connecting to USB DAC…"
                DacState.NoDevice -> "Connect a USB DAC to start bit-perfect playback."
            }
            return OpenResult.NoDac(reason)
        }

        if (format.channels != STEREO_CHANNELS) {
            return OpenResult.UnsupportedFormat(
                reason = "${track.title} has ${format.channels} channels; only stereo is supported.",
            )
        }

        val device = dacState.device
        val matchingFormat = device.formats.firstOrNull { candidate ->
            candidate.channels == format.channels &&
                candidate.subslotBytes * 8 >= format.bitDepth &&
                (candidate.sampleRates.isEmpty() || format.sampleRate in candidate.sampleRates)
        }
        if (matchingFormat == null) {
            val supportedRates = device.formats
                .flatMap { it.sampleRates }
                .distinct()
                .sorted()
            return OpenResult.UnsupportedFormat(
                reason = "${format.sampleRate} Hz / ${format.bitDepth}-bit is not supported by ${dacState.handle.name}.",
                supportedRates = supportedRates,
            )
        }

        val sameInterfaceAndSubslot = nativeHandle != 0L &&
            openedDeviceFd == dacState.fileDescriptor &&
            openedChannels == format.channels &&
            openedSubslotBytes == matchingFormat.subslotBytes

        if (sameInterfaceAndSubslot && openedSampleRate != format.sampleRate) {
            val switched = NativeAudio.nativeSetSampleRate(nativeHandle, format.sampleRate)
            if (switched == 0) {
                openedSampleRate = format.sampleRate
            } else {
                closeNativeHandle()
            }
        }

        val canReuseHandle = nativeHandle != 0L &&
            openedDeviceFd == dacState.fileDescriptor &&
            openedSampleRate == format.sampleRate &&
            openedChannels == format.channels &&
            openedSubslotBytes == matchingFormat.subslotBytes

        if (!canReuseHandle) {
            closeNativeHandle()
            val handle = NativeAudio.nativeOpen(
                fd = dacState.fileDescriptor,
                endpoint = device.endpointAddress,
                interfaceNumber = device.streamingInterface,
                altSetting = device.altSetting,
                subslotBytes = matchingFormat.subslotBytes,
                channels = format.channels,
                sampleRate = format.sampleRate,
                syncType = device.syncType.ordinal,
                feedbackEndpoint = device.feedbackEndpoint ?: 0,
                clockSourceId = device.clockSourceId ?: 0,
            )
            if (handle == 0L) {
                return OpenResult.Failed(
                    "Could not open USB audio stream on ${dacState.handle.name}.",
                )
            }
            nativeHandle = handle
            openedDeviceFd = dacState.fileDescriptor
            openedSampleRate = format.sampleRate
            openedChannels = format.channels
            openedSubslotBytes = matchingFormat.subslotBytes
        }

        applyNativeSoftwareVolume()
        currentTrack = track
        currentFormat = format
        currentUacFormat = matchingFormat

        val volume = when {
            softwareVolumeEnabled -> VolumeState.Software(softwareVolumeLevel)
            device.volume != null -> VolumeState.Hardware(hardwareVolumeLevel)
            else -> VolumeState.Fixed
        }
        return OpenResult.Ready(
            volume = volume,
            dacName = dacState.handle.name,
        )
    }

    override suspend fun start(uri: String, startFrame: Long) {
        pollJob?.cancel()
        val handle = nativeHandle
        val format = currentFormat
        val uacFormat = currentUacFormat
        val track = currentTrack
        if (handle == 0L || format == null || uacFormat == null) {
            _events.tryEmit(BackendEvent.Failed("USB audio stream is not open."))
            return
        }

        val pfd = openDescriptor(uri)
        if (pfd == null) {
            _events.tryEmit(
                BackendEvent.Failed("${track?.title ?: uri} could not be decoded (file unavailable)."),
            )
            return
        }

        val result = try {
            NativeAudio.nativePlayFile(
                handle = handle,
                fd = pfd.fd,
                startFrame = startFrame,
                sampleRate = format.sampleRate,
                bitDepth = format.bitDepth,
                channels = format.channels,
                subslotBytes = uacFormat.subslotBytes,
            )
        } finally {
            runCatching { pfd.close() }
        }

        if (result != 0) {
            _events.tryEmit(
                BackendEvent.Failed(
                    reason = "${track?.title ?: uri} could not be decoded (code $result).",
                    error = PlaybackError.fromNativeCode(result),
                ),
            )
            return
        }

        enqueuedTracks.clear()
        startFrameOffset = startFrame
        lastFramesWritten = 0L
        lastUnderruns = 0L
        lastTrackIndex = 0L
        lastIntegrity = Integrity.UNAVAILABLE
        startPolling(handle, format.sampleRate)
    }

    override suspend fun enqueueNext(track: Track, format: AudioFormat): Boolean {
        val handle = nativeHandle
        val uacFormat = currentUacFormat
        if (handle == 0L || uacFormat == null || currentFormat != format) {
            return false
        }
        val pfd = openDescriptor(track.uri) ?: return false
        val result = try {
            NativeAudio.nativeEnqueueNext(
                handle = handle,
                fd = pfd.fd,
                sampleRate = format.sampleRate,
                bitDepth = format.bitDepth,
                channels = format.channels,
                subslotBytes = uacFormat.subslotBytes,
            )
        } finally {
            runCatching { pfd.close() }
        }
        if (result == 0) {
            enqueuedTracks.addLast(track to format)
            return true
        }
        return false
    }

    override suspend fun pause() {
        pollJob?.cancel()
        pollJob = null
        enqueuedTracks.clear()
        _ringFillPercent.value = 0
        if (nativeHandle != 0L) {
            NativeAudio.nativeStop(nativeHandle)
        }
    }

    override suspend fun resume() {
        val track = currentTrack ?: return
        val resumeAt = startFrameOffset + lastFramesWritten
        start(track.uri, resumeAt)
    }

    override suspend fun stop() {
        pollJob?.cancel()
        pollJob = null
        enqueuedTracks.clear()
        startFrameOffset = 0L
        lastFramesWritten = 0L
        lastTrackIndex = 0L
        _ringFillPercent.value = 0
        if (nativeHandle != 0L) {
            NativeAudio.nativeStop(nativeHandle)
        }
    }

    override suspend fun close() {
        closeNativeHandle()
    }

    override fun setSoftwareVolume(enabled: Boolean) {
        softwareVolumeEnabled = enabled
        applyNativeSoftwareVolume()
    }

    override fun setSoftwareVolumeLevel(level: Int) {
        softwareVolumeLevel = level.coerceIn(0, 100)
        applyNativeSoftwareVolume()
    }

    override fun setHardwareVolume(level: Int) {
        hardwareVolumeLevel = level.coerceIn(0, 100)
        usbDeviceManager.setHardwareVolume(hardwareVolumeLevel)
    }

    private fun applyNativeSoftwareVolume() {
        val handle = nativeHandle
        if (handle == 0L) return
        val gainQ30 = if (!softwareVolumeEnabled) {
            -1
        } else {
            ((softwareVolumeLevel / 100.0) * Q30_UNITY).toInt().coerceIn(0, Q30_UNITY)
        }
        NativeAudio.nativeSetSoftwareVolume(handle, gainQ30)
    }

    private fun startPolling(handle: Long, sampleRate: Int) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && nativeHandle == handle) {
                val stats = NativeAudio.nativeStats(handle)
                if (stats.size >= 5) {
                    val state = stats[0]
                    val underruns = stats[1]
                    val framesWritten = stats[2]
                    val md5State = stats[3]
                    val errorCode = stats[4]
                    val trackIndex = if (stats.size >= 6) stats[5] else 0L
                    val ringFill = if (stats.size >= 7) stats[6].toInt() else 0

                    _ringFillPercent.value = ringFill
                    _lastNativeError.value = errorCode.toInt()

                    while (lastTrackIndex < trackIndex) {
                        val finishedIntegrity = if (lastIntegrity != Integrity.UNAVAILABLE) {
                            lastIntegrity
                        } else {
                            mapIntegrity(md5State)
                        }
                        _events.emit(BackendEvent.Md5(finishedIntegrity))
                        _events.emit(
                            BackendEvent.Ended(
                                integrity = finishedIntegrity,
                                framesWritten = startFrameOffset + lastFramesWritten,
                            ),
                        )
                        val next = enqueuedTracks.removeFirstOrNull()
                        if (next != null) {
                            currentTrack = next.first
                            currentFormat = next.second
                        }
                        startFrameOffset = 0L
                        lastFramesWritten = 0L
                        lastIntegrity = Integrity.UNAVAILABLE
                        lastTrackIndex += 1
                    }

                    lastFramesWritten = framesWritten
                    if (underruns != lastUnderruns) {
                        lastUnderruns = underruns
                        _events.emit(BackendEvent.Underrun(underruns))
                    }

                    if (md5State == MD5_VERIFIED || md5State == MD5_FAILED) {
                        val verifiedIntegrity = mapIntegrity(md5State)
                        if (verifiedIntegrity != lastIntegrity) {
                            lastIntegrity = verifiedIntegrity
                            _events.emit(BackendEvent.Md5(verifiedIntegrity))
                        }
                    }

                    _events.emit(
                        BackendEvent.Progress(
                            framesWritten = startFrameOffset + framesWritten,
                            ringFrames = 0L,
                            sampleRate = sampleRate,
                        ),
                    )

                    when (state) {
                        STATE_ENDED -> {
                            _ringFillPercent.value = 0
                            val integrity = mapIntegrity(md5State)
                            _events.emit(BackendEvent.Md5(integrity))
                            _events.emit(
                                BackendEvent.Ended(
                                    integrity = integrity,
                                    framesWritten = startFrameOffset + framesWritten,
                                ),
                            )
                            break
                        }

                        STATE_ERROR -> {
                            _ringFillPercent.value = 0
                            val mapped = PlaybackError.fromNativeCode(errorCode.toInt())
                            if (mapped == PlaybackError.NO_DEVICE) {
                                closeNativeHandle()
                                _events.emit(BackendEvent.Detached(lastDacName))
                            } else {
                                val title = currentTrack?.title ?: "Track"
                                val reason = mapped.userMessage(
                                    dacName = lastDacName,
                                    detail = "$title could not be decoded (error $errorCode).",
                                )
                                _events.emit(
                                    BackendEvent.Failed(
                                        reason = reason,
                                        error = mapped,
                                    ),
                                )
                            }
                            break
                        }
                    }
                }
                delay(pollIntervalMs)
            }
        }
    }

    private fun mapIntegrity(md5State: Long): Integrity {
        val track = currentTrack
        return when (md5State) {
            MD5_VERIFIED -> Integrity.VERIFIED
            MD5_FAILED -> Integrity.FAILED
            else -> when {
                track?.hasMd5 != true -> Integrity.UNAVAILABLE
                startFrameOffset > 0L -> Integrity.PARTIAL
                else -> Integrity.PARTIAL
            }
        }
    }

    private fun openDescriptor(uri: String): ParcelFileDescriptor? =
        runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
        }.getOrNull()

    private fun closeNativeHandle() {
        pollJob?.cancel()
        pollJob = null
        enqueuedTracks.clear()
        _ringFillPercent.value = 0
        val handle = nativeHandle
        nativeHandle = 0L
        openedDeviceFd = -1
        openedSampleRate = 0
        openedChannels = 0
        openedSubslotBytes = 0
        if (handle != 0L) {
            NativeAudio.nativeStop(handle)
            NativeAudio.nativeClose(handle)
        }
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 100L
        const val STEREO_CHANNELS = 2
        const val STATE_ENDED = 2L
        const val STATE_ERROR = 3L
        const val MD5_VERIFIED = 1L
        const val MD5_FAILED = 2L
        const val Q30_UNITY = 1 shl 30
    }
}
