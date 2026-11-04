package dev.hien.bitstream

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.work.WorkManager
import dev.hien.bitstream.core.data.MusicRepository
import dev.hien.bitstream.core.data.RoomMusicRepository
import dev.hien.bitstream.core.data.db.BitstreamDatabase
import dev.hien.bitstream.core.data.db.ScanFolderEntity
import dev.hien.bitstream.core.data.library.ArtworkCache
import dev.hien.bitstream.core.data.library.ScanWorker
import dev.hien.bitstream.ui.playlists.PlaylistStore
import dev.hien.bitstream.ui.playlists.RoomPlaylistStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Process-wide singletons, created lazily by [BitstreamApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = BitstreamDatabase.create(appContext)

    val musicRepository: MusicRepository = RoomMusicRepository(
        trackDao = database.trackDao(),
        albumDao = database.albumDao(),
        artistDao = database.artistDao(),
    )

    val playlistStore: PlaylistStore = RoomPlaylistStore(database.playlistDao())

    val artworkCache = ArtworkCache(appContext)

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
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer is not provided")
}
