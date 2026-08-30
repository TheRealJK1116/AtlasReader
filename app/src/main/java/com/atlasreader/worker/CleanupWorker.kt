package com.atlasreader.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atlasreader.core.common.AtlasLog
import com.atlasreader.core.database.dao.DocumentDao
import com.atlasreader.core.database.dao.SearchDao
import com.atlasreader.core.importer.CoverStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Periodic hygiene: prunes search history, removes orphan cover files (covers
 * whose row no longer exists) and empty import directories. Scheduled from
 * Settings → Data management and at app start (idempotent, cheap).
 */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coverStore: CoverStore,
    private val documentDao: DocumentDao,
    private val searchDao: SearchDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            searchDao.pruneHistory(keep = 30)

            // Covers referenced by any document (also across hash reuse).
            val referenced = documentDao.allIds().mapNotNull { documentDao.byId(it) }
                .mapNotNull { it.contentHash }
            val keepPrefixes = referenced.map { "covers/$it.jpg" }
            coverStore.deleteOrphanFiles(keepPaths = emptySet()) { path ->
                val name = File(path).name // <hash>.jpg
                keepPrefixes.none { it.endsWith(name) }
            }

            // Remove import staging dirs left from interrupted copies.
            val imports = File(applicationContext.filesDir, "imports")
            imports.listFiles()?.forEach { dir ->
                if (dir.isDirectory && (dir.listFiles()?.isEmpty() ?: true)) dir.delete()
            }

            AtlasLog.i("CleanupWorker finished")
            Result.success()
        } catch (e: Exception) {
            AtlasLog.e("CleanupWorker failed", e)
            Result.failure()
        }
    }
}
