package com.atlasreader.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atlasreader.core.common.AtlasLog
import com.atlasreader.core.database.dao.DocumentDao
import com.atlasreader.core.engine.DocumentFormat
import com.atlasreader.core.engine.DocumentSource
import com.atlasreader.core.engine.EngineRegistry
import com.atlasreader.core.indexer.SearchIndexer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Rebuilds the full-text index for every library document. Triggered from
 * Settings → Data management. Long-running by design; runs on the background
 * worker thread and yields periodically to the system.
 */
@HiltWorker
class ReindexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val documentDao: DocumentDao,
    private val engineRegistry: EngineRegistry,
    private val searchIndexer: SearchIndexer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val documents = documentDao.byIds(documentDao.allIds())
            var indexed = 0
            for (doc in documents) {
                val format = runCatching { DocumentFormat.valueOf(doc.format) }.getOrNull()
                val engine = format?.let { engineRegistry.engineFor(it) }
                if (engine == null || format == DocumentFormat.PDF) continue
                try {
                    val source = DocumentSource(
                        uri = android.net.Uri.parse(doc.sourceUri ?: ""),
                        displayName = doc.displayName,
                        localPath = doc.filePath,
                    )
                    val parsed = engine.parse(applicationContext, source)
                    searchIndexer.indexDocument(doc.id, parsed, doc.title, doc.author)
                    indexed++
                } catch (e: Exception) {
                    AtlasLog.w("Reindex failed for doc ${doc.id}", e)
                }
                if (isStopped) return Result.retry()
            }
            AtlasLog.i("Reindexed $indexed documents")
            Result.success()
        } catch (e: Exception) {
            AtlasLog.e("ReindexWorker failed", e)
            Result.failure()
        }
    }
}
