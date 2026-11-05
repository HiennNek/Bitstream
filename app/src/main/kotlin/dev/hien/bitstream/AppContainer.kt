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
import dev.hien.bitstream.core.model.RepeatMode
import dev.hien.bitstream.core.model.Track
import dev.hien.bitstream.core.playback.engine.NativeAudioBackend
import dev.hien.bitstream.core.playback.engine.PlaybackEngine
import dev.hien.bitstream.core.playback.usb.AndroidUsbDeviceSource
import dev.hien.bitstream.core.playback.usb.DataStoreLastDeviceStore
import dev.hien.bitstream.core.playback.usb.UacDescriptorParser
import dev.hien.bitstream.core.playback.usb.UsbDeviceManager
import dev.hien.bitstream.ui.playlists.PlaylistStore
import dev.hien.bitstream.ui.playlists.RoomPlaylistStore
import dev.hien.bitstream.ui.settings.AndroidSettingsStore
import dev.hien.bitstream.ui.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        favoriteDao = database.favoriteDao(),
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
                val saved = playbackStateStore.load()
                if (saved != null && engine.queueItems.value.isEmpty()) {
                    val savedIds = playbackStateStore.queueIds(saved)
                    if (savedIds.isNotEmpty()) {
                        val tracksById = musicRepository.tracks.first().associateBy { it.id }
                        val restoredTracks = savedIds.mapNotNull { tracksById[it] }
                        if (restoredTracks.isNotEmpty()) {
                            val repeatMode = runCatching {
                                RepeatMode.valueOf(saved.repeat)
                            }.getOrDefault(RepeatMode.OFF)
                            engine.restoreQueue(
                                tracks = restoredTracks,
                                startIndex = saved.index.coerceIn(restoredTracks.indices),
                                positionMs = saved.positionMs,
                                shuffle = saved.shuffle,
                                repeat = repeatMode,
                            )
                        }
                    }
                }
            }
            appScope.launch {
                var lastSavedStructure: String? = null
                var lastSaveTimeMs = 0L
                combine(
                    engine.queueItems,
                    engine.currentQueueIndex,
                    engine.state,
                ) { items, index, state ->
                    Triple(items, index, state)
                }.collect { (items, index, state) ->
                    if (items.isNotEmpty() && index >= 0) {
                        val structureKey = "${items.map { it.id }}|$index|${state.status}|${state.shuffle}|${state.repeat}"
                        val now = System.currentTimeMillis()
                        val structureChanged = structureKey != lastSavedStructure
                        val periodicDue = now - lastSaveTimeMs >= 5_000L
                        if (structureChanged || periodicDue) {
                            lastSavedStructure = structureKey
                            lastSaveTimeMs = now
                            playbackStateStore.save(
                                queueIds = items.map { it.id },
                                index = index,
                                positionMs = state.positionMs,
                                shuffle = state.shuffle,
                                repeat = state.repeat,
                            )
                        }
                    }
                }
            }
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

    val settingsStore: SettingsStore by lazy {
        AndroidSettingsStore(
            context = appContext,
            scanFolderDao = database.scanFolderDao(),
            playbackController = playbackEngine,
            scope = appScope,
        )
    }

    init {
        appScope.launch {
            if (database.scanFolderDao().observeFolders().first().isNotEmpty()) {
                scanAll()
            }
        }
    }

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

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        appScope.launch {
            usbDeviceManager.refresh()
            PlaybackService.ensureStarted(appContext)
            if (shuffle != null) {
                playbackEngine.setShuffle(shuffle)
            }
            playbackEngine.setQueue(tracks, startIndex.coerceIn(tracks.indices))
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer is not provided")
}
