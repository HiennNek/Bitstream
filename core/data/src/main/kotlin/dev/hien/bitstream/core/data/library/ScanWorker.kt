package dev.hien.bitstream.core.data.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.hien.bitstream.core.data.RoomMusicRepository
import dev.hien.bitstream.core.data.db.BitstreamDatabase
import dev.hien.bitstream.core.data.db.ScanFolderEntity
import dev.hien.bitstream.core.data.metadata.FlacMetadataReader
import dev.hien.bitstream.core.data.toEntity
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Long scan that keeps running when the app leaves the foreground. It is
 * enqueued as the unique work `library-scan`; a new request replaces a queued
 * one so the library is never scanned twice at once.
 */
class ScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = BitstreamDatabase.create(applicationContext)
        return try {
            val explicit = inputData.getString(KEY_TREE_URI)
            val folders = if (explicit != null) {
                listOf(explicit)
            } else {
                database.scanFolderDao().observeFolders().first().map { it.treeUri }
            }
            if (folders.isEmpty()) return Result.success()

            val tree = DocumentFileTree(applicationContext)
            val artwork = ArtworkCache(applicationContext)
            val scanner = LibraryScanner(
                tree = tree,
                reader = FlacMetadataReader(),
                storeArtwork = { key, bytes -> artwork.store(key, bytes) },
            )

            var indexed = 0
            for (treeUri in folders) {
                val root = tree.root(treeUri) ?: continue
                val result = scanner.scan(listOf(root)) { count ->
                    setProgressAsync(workDataOf(KEY_PROGRESS to count))
                }

                database.trackDao().upsertAll(result.tracks.map { it.toEntity() })
                indexed += result.tracks.size
                database.scanFolderDao().upsert(
                    ScanFolderEntity(
                        treeUri = treeUri,
                        displayName = root.name,
                        lastScan = System.currentTimeMillis(),
                    ),
                )
            }

            RoomMusicRepository(
                trackDao = database.trackDao(),
                albumDao = database.albumDao(),
                artistDao = database.artistDao(),
            ).rebuildDerived()

            Result.success(workDataOf(KEY_TRACK_COUNT to indexed))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.failure()
        } finally {
            database.close()
        }
    }

    companion object {
        const val UNIQUE_WORK = "library-scan"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_PROGRESS = "progress"
        const val KEY_TRACK_COUNT = "track_count"

        /** Enqueue a scan of one folder, or of every saved folder when [treeUri] is null. */
        fun enqueue(context: Context, treeUri: String? = null): UUID {
            val request = OneTimeWorkRequestBuilder<ScanWorker>()
                .apply {
                    if (treeUri != null) {
                        setInputData(workDataOf(KEY_TREE_URI to treeUri))
                    }
                }
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)

            return request.id
        }
    }
}
