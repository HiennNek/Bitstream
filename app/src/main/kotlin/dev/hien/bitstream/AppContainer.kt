package dev.hien.bitstream

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.work.WorkManager
import dev.hien.bitstream.core.data.MusicRepository
import dev.hien.bitstream.core.data.PlaybackStateStore
import dev.hien.bitstream.core.data.RoomMusicRepository
import dev.hien.bitstream.core.data.db.BitstreamDatabase
import dev.hien.bitstream.core.data.db.ScanFolderEntity
import dev.hien.bitstream.core.data.library.ArtworkCache
import dev.hien.bitstream.core.data.library.ScanWorker
import dev.hien.bitstream.core.model.PlaybackStatus
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.playback.engine.NativeAudioBackend
import dev.hien.bitstream.core.playback.engine.PlaybackEngine
import dev.hien.bitstream.core.playback.usb.AndroidUsbDeviceSource
import dev.hien.bitstream.core.playback.usb.DataStoreLastDeviceStore
import dev.hien.bitstream.core.playback.usb.UacDescriptorParser
import dev.hien.bitstream.core.playback.usb.UsbDeviceManager
import dev.hien.bitstream.ui.playlists.PlaylistStore
import dev.hien.bitstream.ui.playlists.RoomPlaylistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Process-wide singletons, created lazily by [BitstreamApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: BitstreamDatabase = BitstreamDatabase.create(appContext)

    val musicRepository: MusicRepository = RoomMusicRepository(
        trackDao = database.trackDao(),
        albumDao = database.albumDao(),
        artistDao = database.artistDao(),
    )

    val playlistStore: PlaylistStore = RoomPlaylistStore(database.playlistDao())

    val playbackStateStore: PlaybackStateStore = PlaybackStateStore(database.playbackStateDao())

    val artworkCache = ArtworkCache(appContext)

    val usbDeviceManager: UsbDeviceManager by lazy {
        UsbDeviceManager(
            source = AndroidUsbDeviceSource(appContext),
            parser = UacDescriptorParser(),
            store = DataStoreLastDeviceStore(appContext),
        )
    }

    val audioBackend: NativeAudioBackend by lazy {
        NativeAudioBackend(
            context = appContext,
            usbDeviceManager = usbDeviceManager,
            scope = appScope,
        )
    }

    val playbackEngine: PlaybackEngine by lazy {
        PlaybackEngine(
            backend = audioBackend,
            scope = appScope,
        ).also { engine ->
            appScope.launch {
                engine.state
                    .map { it.status }
                    .distinctUntilChanged()
                    .collect { status ->
                        if (status == PlaybackStatus.OPENING ||
                            status == PlaybackStatus.READY
                        ) {
                            PlaybackService.ensureStarted(appContext)
                        }
                    }
            }
        }
    }

    val scanning: Flow<Boolean> =
        WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWorkFlow(ScanWorker.UNIQUE_WORK)
            .map { infos -> infos.any { !it.state.isFinished } }

    suspend fun addFolder(treeUri: String, displayName: String) {
        database.scanFolderDao().upsert(
            ScanFolderEntity(
                treeUri = treeUri,
                displayName = displayName,
                lastScan = 0,
            ),
        )
    }

    fun scanFolder(treeUri: String) {
        ScanWorker.enqueue(appContext, treeUri)
    }

    fun scanAll() {
        ScanWorker.enqueue(appContext)
    }

    fun playTrack(trackId: Long) {
        appScope.launch {
            val allTracks = musicRepository.tracks.first()
            if (allTracks.isEmpty()) return@launch
            val startIndex = allTracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
            usbDeviceManager.refresh()
            PlaybackService.ensureStarted(appContext)
            playbackEngine.setQueue(allTracks, startIndex)
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        appScope.launch {
            usbDeviceManager.refresh()
            PlaybackService.ensureStarted(appContext)
            playbackEngine.setQueue(tracks, startIndex.coerceIn(tracks.indices))
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer is not provided")
}
