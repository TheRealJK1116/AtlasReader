package com.atlasreader.core.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.atlasreader.core.common.AtlasError
import com.atlasreader.core.common.AtlasLog
import com.atlasreader.core.common.DispatcherProvider
import com.atlasreader.core.common.TimeProvider
import com.atlasreader.core.database.dao.DocumentDao
import com.atlasreader.core.database.entity.DocumentEntity
import com.atlasreader.core.datastore.AppSettings
import com.atlasreader.core.engine.DocumentSource
import com.atlasreader.core.engine.EngineRegistry
import com.atlasreader.core.engine.ExtractedMetadata
import com.atlasreader.core.indexer.SearchIndexer
import com.atlasreader.core.util.FilenameUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the whole import pipeline for every source (SAF picker, share
 * menu, folder import, drag & drop):
 *
 *  validate → copy (hashing in-flight) → duplicate check → metadata extraction
 *  → cover extraction → DB insert → background parse + full-text index
 *
 * Single-worker sequential queue keeps DB writes serialised; UI observes
 * [state]. Failures never abort the batch — they are reported per file and the
 * pipeline continues (requirement: "handle failures gracefully").
 */
@Singleton
class ImportCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engineRegistry: EngineRegistry,
    private val fileCopier: FileCopier,
    private val coverStore: CoverStore,
    private val documentDao: DocumentDao,
    private val searchIndexer: SearchIndexer,
    private val settings: AppSettings,
    private val dispatchers: DispatcherProvider,
    private val time: TimeProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val queue = Channel<ImportRequest>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val workerLock = Any()
    private var workerJob: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _autoOpen = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    val autoOpen: SharedFlow<Long> = _autoOpen.asSharedFlow()

    fun import(requests: List<ImportRequest>) {
        if (requests.isEmpty()) return
        val current = _state.value
        _state.update {
            it.copy(
                running = true,
                total = it.total + requests.size,
                currentFile = it.currentFile ?: requests.first().displayName,
            )
        }
        pending.addAndGet(requests.size)
        requests.forEach { queue.trySend(it) }
        ensureWorker()
    }

    private fun ensureWorker() {
        synchronized(workerLock) {
            if (workerJob?.isActive == true) return
            workerJob = scope.launch {
                var imported = 0
                var duplicates = 0
                var failed = 0
                for (request in queue) {
                    _state.update {
                        it.copy(done = it.done + 1, currentFile = request.displayName ?: "Importing…")
                    }
                    when (val outcome = importOne(request)) {
                        is ImportOutcome.Imported -> {
                            imported++
                            if (runCatching { settings.importAutoOpen.first() }.getOrDefault(true)) {
                                _autoOpen.tryEmit(outcome.documentId)
                            }
                        }
                        is ImportOutcome.Duplicate -> duplicates++
                        is ImportOutcome.Unsupported, is ImportOutcome.Failed -> failed++
                    }
                    _state.update {
                        it.copy(imported = imported, duplicates = duplicates, failed = failed)
                    }
                    if (pending.decrementAndGet() <= 0) {
                        _state.value = ImportState()
                    }
                }
            }
        }
    }

    private suspend fun importOne(request: ImportRequest): ImportOutcome {
        val displayName = request.displayName?.takeIf { it.isNotBlank() }
            ?: queryDisplayName(request.uri)
            ?: "document"

        val engine = engineRegistry.engineForUri(displayName, request.mimeType)
            ?: return ImportOutcome.Unsupported(displayName, "Unsupported file format")

        return try {
            val copied = fileCopier.copyToLibrary(request.uri, displayName)

            val existing = documentDao.byHash(copied.contentHash)
            if (existing != null) {
                fileCopier.deleteCopy(copied)
                return ImportOutcome.Duplicate(existing.id)
            }

            val file = fileCopier.moveIntoPlace(copied)
            val source = DocumentSource(
                uri = request.uri,
                displayName = displayName,
                localPath = file.absolutePath,
            )

            val metadata = try {
                engine.extractMetadata(context, source)
            } catch (e: Exception) {
                AtlasLog.w("Metadata extraction failed for $displayName", e)
                ExtractedMetadata()
            }
            val title = metadata.title ?: FilenameUtils.stripExtension(displayName).trim()
            val now = time.epochMillis()

            val documentId = documentDao.insert(
                DocumentEntity(
                    contentHash = copied.contentHash,
                    fileName = file.name,
                    displayName = displayName,
                    filePath = file.absolutePath,
                    fileSizeBytes = copied.sizeBytes,
                    format = engine.format.name,
                    title = title,
                    author = metadata.author,
                    description = metadata.description,
                    language = metadata.language,
                    publisher = metadata.publisher,
                    publishedDate = metadata.publishedDate,
                    sourceUri = request.uri.toString(),
                    addedAtMs = now,
                )
            )

            if (documentId <= 0L) {
                // A racing import inserted the same hash — resolve against DB.
                val winner = documentDao.byHash(copied.contentHash)
                file.delete()
                return if (winner != null) ImportOutcome.Duplicate(winner.id)
                else ImportOutcome.Failed(displayName, AtlasError.Import("Insert failed"))
            }

            try {
                val coverBytes = engine.extractCover(context, source)
                if (coverBytes != null && coverBytes.isNotEmpty()) {
                    coverStore.saveAndRegister(documentId, coverBytes, copied.contentHash, now)
                }
            } catch (e: Exception) {
                AtlasLog.w("Cover extraction failed for $displayName", e)
            }

            // Background parse + index; never blocks import completion.
            scope.launch {
                try {
                    val parsed = engine.parse(context, source)
                    searchIndexer.indexDocument(documentId, parsed, title, metadata.author)
                } catch (e: Exception) {
                    AtlasLog.w("Indexing failed for document $documentId", e)
                }
            }

            ImportOutcome.Imported(documentId)
        } catch (e: FileCopier.ImportException) {
            ImportOutcome.Failed(displayName, e.error)
        } catch (e: Exception) {
            AtlasLog.e("Import failed: $displayName", e)
            ImportOutcome.Failed(displayName, AtlasError.Import(e.message ?: "Unknown import error"))
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    /** Folder import: recursively enumerate supported files under a SAF tree. */
    suspend fun importFolder(uri: Uri, maxDepth: Int = 4): List<ImportRequest> =
        kotlinx.coroutines.withContext(dispatchers.io) {
            val requests = mutableListOf<ImportRequest>()
            collectTree(uri, 0, maxDepth, requests)
            requests
        }

    private fun collectTree(
        uri: Uri,
        depth: Int,
        maxDepth: Int,
        requests: MutableList<ImportRequest>,
    ) {
        if (depth > maxDepth) return
        val tree = runCatching {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
        }.getOrNull() ?: return
        for (file in tree.listFiles()) {
            if (file.isDirectory) {
                collectTree(file.uri, depth + 1, maxDepth, requests)
            } else {
                val name = file.name ?: continue
                if (engineRegistry.engineForUri(name, file.type) != null) {
                    requests += ImportRequest(uri = file.uri, displayName = name, mimeType = file.type, folderLabel = name)
                }
            }
        }
    }
}
