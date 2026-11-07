package dev.hien.bitstream.core.playback.session

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.PlaybackState
import dev.hien.bitstream.core.model.PlaybackStatus
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.model.VolumeState
import dev.hien.bitstream.core.playback.engine.PlaybackController
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Media3 [SimpleBasePlayer] shell backed by [PlaybackController].
 *
 * All PCM decoding and USB transport stay in the native engine; this adapter
 * exists solely so `MediaSession` and the system media notification reflect
 * the engine's queue, transport state, format line, and hardware volume.
 */
@OptIn(UnstableApi::class)
class BitstreamPlayer(
    private val controller: PlaybackController,
    looper: Looper = Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {
    private val mainHandler = Handler(looper)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    // getState() runs on every 100 ms position tick. Rebuilding the whole
    // playlist (a MediaItem + metadata per queued track) each time is O(queue)
    // main-thread work, so reuse it until the queue or the format path changes.
    private var cachedTracks: List<Track>? = null
    private var cachedPath: BitPerfectPath? = null
    private var cachedPlayerState: Int = Int.MIN_VALUE
    private var cachedMediaItems: List<MediaItemData> = emptyList()

    init {
        scope.launch {
            combine(
                controller.state,
                controller.queueItems,
                controller.currentQueueIndex,
            ) { state, items, index ->
                Triple(state, items, index)
            }.drop(1).collect {
                if (Looper.myLooper() == looper) {
                    invalidateState()
                } else {
                    mainHandler.post { invalidateState() }
                }
            }
        }
    }

    override fun getState(): State {
        val state = controller.state.value
        val playerState = when (state.status) {
            PlaybackStatus.IDLE,
            PlaybackStatus.NO_DAC,
            PlaybackStatus.ERROR,
            -> Player.STATE_IDLE

            PlaybackStatus.OPENING -> Player.STATE_BUFFERING
            PlaybackStatus.READY,
            PlaybackStatus.PAUSED,
            -> Player.STATE_READY

            PlaybackStatus.ENDED -> Player.STATE_ENDED
        }
        val playWhenReady = state.status == PlaybackStatus.READY ||
            state.status == PlaybackStatus.OPENING

        val items = controller.queueItems.value.ifEmpty {
            listOfNotNull(state.track)
        }

        val mediaItems = if (items === cachedTracks &&
            state.path == cachedPath &&
            playerState == cachedPlayerState
        ) {
            cachedMediaItems
        } else {
            cachedTracks = items
            cachedPath = state.path
            cachedPlayerState = playerState
            buildMediaItems(items, playerState, state).also { cachedMediaItems = it }
        }

        val currentIndex = controller.currentQueueIndex.value.takeIf { it in mediaItems.indices }
            ?: if (mediaItems.isNotEmpty()) 0 else C.INDEX_UNSET

        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_RELEASE,
            )
            .apply {
                if (state.volume is VolumeState.Hardware) {
                    add(Player.COMMAND_GET_VOLUME)
                    add(Player.COMMAND_SET_VOLUME)
                    add(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
                    add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                }
            }
            .build()

        val currentTrackMeta = state.track?.let { buildMediaMetadata(it, state) }
            ?: MediaMetadata.EMPTY
        val volumeFloat = when (val vol = state.volume) {
            is VolumeState.Hardware -> (vol.level / 100f).coerceIn(0f, 1f)
            else -> 1f
        }
        val deviceInfo = when (state.volume) {
            is VolumeState.Hardware -> DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                .setMinVolume(0)
                .setMaxVolume(100)
                .build()
            else -> DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                .setMinVolume(0)
                .setMaxVolume(0)
                .build()
        }
        val deviceVolume = when (val vol = state.volume) {
            is VolumeState.Hardware -> vol.level.coerceIn(0, 100)
            else -> 0
        }

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playerState)
            .setPlaylist(mediaItems)
            .setPlaylistMetadata(currentTrackMeta)
            .setCurrentMediaItemIndex(currentIndex)
            .setContentPositionMs(state.positionMs)
            .setAudioAttributes(AudioAttributes.DEFAULT)
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(deviceVolume)
            .setVolume(volumeFloat)
            .setShuffleModeEnabled(state.shuffle)
            .setRepeatMode(
                when (state.repeat) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                },
            )
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            controller.play()
        } else {
            controller.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        controller.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> controller.next()

            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> controller.previous()

            Player.COMMAND_SEEK_TO_MEDIA_ITEM -> {
                val currentIdx = controller.currentQueueIndex.value
                if (mediaItemIndex >= 0 && mediaItemIndex != currentIdx) {
                    controller.playIndex(mediaItemIndex)
                } else if (positionMs != C.TIME_UNSET) {
                    controller.seekTo(positionMs.coerceAtLeast(0L))
                }
            }

            else -> {
                val targetMs = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0L)
                controller.seekTo(targetMs)
            }
        }
        return Futures.immediateVoidFuture()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in SimpleBasePlayer")
    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        val level = (volume.coerceIn(0f, 1f) * 100f).roundToInt()
        controller.setHardwareVolume(level)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        controller.setHardwareVolume(deviceVolume.coerceIn(0, 100))
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        val current = (controller.state.value.volume as? VolumeState.Hardware)?.level ?: return Futures.immediateVoidFuture()
        controller.setHardwareVolume((current + 5).coerceAtMost(100))
        return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        val current = (controller.state.value.volume as? VolumeState.Hardware)?.level ?: return Futures.immediateVoidFuture()
        controller.setHardwareVolume((current - 5).coerceAtLeast(0))
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        controller.setShuffle(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        val mode = when (repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        controller.setRepeat(mode)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    private fun buildMediaItems(
        items: List<Track>,
        playerState: Int,
        state: PlaybackState,
    ): List<MediaItemData> = if (items.isNotEmpty()) {
        items.map { track ->
            val metadata = buildMediaMetadata(track, state)
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.uri)
                .setMediaMetadata(metadata)
                .build()
            MediaItemData.Builder(track.id)
                .setMediaItem(mediaItem)
                .setMediaMetadata(metadata)
                .setDurationUs(
                    if (track.durationMs > 0L) track.durationMs * 1_000L else C.TIME_UNSET,
                )
                .setIsSeekable(true)
                .build()
        }
    } else if (playerState == Player.STATE_READY || playerState == Player.STATE_BUFFERING) {
        // SimpleBasePlayer requires a non-empty playlist in STATE_READY / STATE_BUFFERING.
        listOf(
            MediaItemData.Builder(PLACEHOLDER_ITEM_UID)
                .setIsSeekable(true)
                .build(),
        )
    } else {
        emptyList()
    }

    private fun buildMediaMetadata(track: Track, state: PlaybackState): MediaMetadata {
        val formatSubtitle = formatNotificationLine(track, state)
        val artistLine = track.artist.ifBlank { track.albumArtist }
        return MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(
                if (artistLine.isNotBlank()) {
                    "$artistLine · $formatSubtitle"
                } else {
                    formatSubtitle
                },
            )
            .setAlbumTitle(track.album)
            .setAlbumArtist(track.albumArtist)
            .setSubtitle(formatSubtitle)
            .setDescription(formatSubtitle)
            .apply {
                track.artworkUri?.takeIf { it.isNotBlank() }?.let { art ->
                    setArtworkUri(Uri.parse(art))
                }
            }
            .build()
    }

    companion object {
        private const val PLACEHOLDER_ITEM_UID = -1L

        fun formatNotificationLine(track: Track, state: PlaybackState): String {
            val rateKhz = if (track.sampleRate % 1_000 == 0) {
                (track.sampleRate / 1_000).toString()
            } else {
                String.format(Locale.US, "%.1f", track.sampleRate / 1_000.0)
            }
            val pathLabel = when (state.path) {
                BitPerfectPath.BIT_PERFECT -> "Bit-perfect"
                BitPerfectPath.SOFTWARE_VOLUME -> "Software volume"
                BitPerfectPath.UNSUPPORTED_FORMAT -> "Format mismatch"
                BitPerfectPath.NO_DAC -> "No DAC"
            }
            return if (track.bitDepth > 0 && track.sampleRate > 0) {
                "FLAC ${track.bitDepth}/$rateKhz · $pathLabel"
            } else {
                "FLAC · $pathLabel"
            }
        }
    }
}
