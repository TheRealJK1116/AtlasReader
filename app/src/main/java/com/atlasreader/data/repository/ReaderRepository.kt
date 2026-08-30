package com.atlasreader.data.repository

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.atlasreader.core.common.AtlasError
import com.atlasreader.core.common.AtlasResult
import com.atlasreader.core.common.DispatcherProvider
import com.atlasreader.core.common.TimeProvider
import com.atlasreader.core.database.dao.AnnotationDao
import com.atlasreader.core.database.dao.DocumentDao
import com.atlasreader.core.database.dao.ProgressDao
import com.atlasreader.core.database.entity.BookmarkEntity
import com.atlasreader.core.database.entity.HighlightEntity
import com.atlasreader.core.database.entity.NoteEntity
import com.atlasreader.core.database.entity.ReadingProgressEntity
import com.atlasreader.core.engine.DocumentFormat
import com.atlasreader.core.engine.DocumentOpenException
import com.atlasreader.core.engine.DocumentSource
import com.atlasreader.core.engine.EngineRegistry
import com.atlasreader.core.engine.ParsedDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reader-facing persistence: opening documents (engine + bounded memory cache),
 * reading position/session time, and all annotation CRUD.
 */
@Singleton
class ReaderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engineRegistry: EngineRegistry,
    private val documentDao: DocumentDao,
    private val progressDao: ProgressDao,
    private val annotationDao: AnnotationDao,
    private val dispatchers: DispatcherProvider,
    private val time: TimeProvider,
) {

    /** Small bounded cache for continuous documents; PDFs are always re-parsed. */
    private val cache = LruCache<String, ParsedDocument>(MAX_CACHED_DOCUMENTS)

    data class OpenedDocument(
        val documentId: Long,
        val title: String?,
        val parsed: ParsedDocument,
        val position: ReadingPosition?,
    )

    suspend fun open(documentId: Long): AtlasResult<OpenedDocument> {
        val entity = documentDao.byId(documentId)
            ?: return AtlasResult.Failure(AtlasError.NotFound)
        val format = runCatching { DocumentFormat.valueOf(entity.format) }
            .getOrNull() ?: return AtlasResult.Failure(AtlasError.Engine(entity.format, "Unknown format"))
        val engine = engineRegistry.engineFor(format)
            ?: return AtlasResult.Failure(AtlasError.Engine(format.label, "No engine registered"))

        return try {
            val source = DocumentSource(
                uri = runCatching { Uri.parse(entity.sourceUri ?: "") }.getOrDefault(Uri.EMPTY),
                displayName = entity.displayName,
                localPath = entity.filePath,
            )
            val parsed = if (format.kind == com.atlasreader.core.engine.DocumentKind.FIXED_LAYOUT) {
                engine.parse(context, source)
            } else {
                cache.get(entity.contentHash) ?: engine.parse(context, source).also {
                    cache.put(entity.contentHash, it)
                }
            }
            val position = progressDao.forDocument(documentId)?.toPosition()
            documentDao.touchOpen(documentId, time.epochMillis())
            AtlasResult.Success(OpenedDocument(entity.id, entity.title, parsed, position))
        } catch (e: DocumentOpenException) {
            AtlasResult.Failure(AtlasError.Engine(format.label, e.reason.toString()))
        } catch (e: Exception) {
            AtlasResult.Failure(AtlasError.Engine(format.label, e.message ?: "Open failed"))
        }
    }

    suspend fun evictFromCache(contentHash: String) = cache.remove(contentHash)

    // ------------------------------------------------------------ progress

    suspend fun savePosition(position: ReadingPosition) {
        progressDao.upsert(
            ReadingProgressEntity(
                documentId = position.documentId,
                resourceToken = position.resourceToken,
                charOffset = position.charOffset,
                pageIndex = position.pageIndex,
                scrollFraction = position.scrollFraction,
                percent = position.percent,
                updatedAtMs = position.updatedAtMs,
                sessionStartMs = time.epochMillis(),
                sessionAccumMs = position.sessionAccumMs,
            )
        )
    }

    suspend fun recordSessionTime(documentId: Long, deltaMs: Long) {
        if (deltaMs <= 0) return
        progressDao.accumulateSession(documentId, deltaMs)
    }

    fun observePosition(documentId: Long): Flow<ReadingPosition?> =
        progressDao.observeForDocument(documentId).map { it?.toPosition() }

    // ---------------------------------------------------------- annotations

    fun observeBookmarks(documentId: Long): Flow<List<Bookmark>> =
        annotationDao.observeBookmarks(documentId).map { list -> list.map { it.toDomain() } }

    suspend fun addBookmark(documentId: Long, token: String, offset: Int, text: String, note: String? = null): Long =
        annotationDao.insertBookmark(
            BookmarkEntity(
                documentId = documentId,
                resourceToken = token,
                charOffset = offset,
                text = text,
                note = note,
                createdAtMs = time.epochMillis(),
            )
        )

    suspend fun deleteBookmark(bookmark: Bookmark) {
        annotationDao.deleteBookmark(
            BookmarkEntity(
                id = bookmark.id,
                documentId = bookmark.documentId,
                resourceToken = bookmark.resourceToken,
                charOffset = bookmark.charOffset,
                text = bookmark.text,
                note = bookmark.note,
                createdAtMs = bookmark.createdAtMs,
            )
        )
    }

    fun observeHighlights(documentId: Long): Flow<List<Highlight>> =
        annotationDao.observeHighlights(documentId).map { list -> list.map { it.toDomain() } }

    suspend fun addHighlight(
        documentId: Long,
        token: String,
        startOffset: Int,
        endOffset: Int,
        text: String,
        colorHex: String,
    ): Long {
        val now = time.epochMillis()
        return annotationDao.insertHighlight(
            HighlightEntity(
                documentId = documentId,
                resourceToken = token,
                startOffset = startOffset,
                endOffset = endOffset,
                text = text,
                colorHex = colorHex,
                createdAtMs = now,
                updatedAtMs = now,
            )
        )
    }

    suspend fun deleteHighlight(highlight: Highlight) {
        annotationDao.deleteHighlight(
            HighlightEntity(
                id = highlight.id,
                documentId = highlight.documentId,
                resourceToken = highlight.resourceToken,
                startOffset = highlight.startOffset,
                endOffset = highlight.endOffset,
                text = highlight.text,
                colorHex = highlight.colorHex,
                note = highlight.note,
                createdAtMs = highlight.createdAtMs,
                updatedAtMs = highlight.updatedAtMs,
            )
        )
    }

    suspend fun recolorHighlight(id: Long, colorHex: String) =
        annotationDao.recolorHighlight(id, colorHex, time.epochMillis())

    suspend fun setHighlightNote(id: Long, note: String?) =
        annotationDao.setHighlightNote(id, note, time.epochMillis())

    fun observeNotes(documentId: Long): Flow<List<ReaderNote>> =
        annotationDao.observeNotes(documentId).map { list -> list.map { it.toDomain() } }

    suspend fun addNote(documentId: Long, token: String, anchorOffset: Int, text: String, linkedHighlightId: Long? = null): Long {
        val now = time.epochMillis()
        return annotationDao.insertNote(
            NoteEntity(
                documentId = documentId,
                resourceToken = token,
                anchorOffset = anchorOffset,
                text = text,
                linkedHighlightId = linkedHighlightId,
                createdAtMs = now,
                updatedAtMs = now,
            )
        )
    }

    suspend fun updateNote(note: ReaderNote) {
        annotationDao.updateNote(
            NoteEntity(
                id = note.id,
                documentId = note.documentId,
                resourceToken = note.resourceToken,
                anchorOffset = note.anchorOffset,
                text = note.text,
                linkedHighlightId = note.linkedHighlightId,
                createdAtMs = note.createdAtMs,
                updatedAtMs = time.epochMillis(),
            )
        )
    }

    suspend fun deleteNote(note: ReaderNote) {
        annotationDao.deleteNote(
            NoteEntity(
                id = note.id,
                documentId = note.documentId,
                resourceToken = note.resourceToken,
                anchorOffset = note.anchorOffset,
                text = note.text,
                linkedHighlightId = note.linkedHighlightId,
                createdAtMs = note.createdAtMs,
                updatedAtMs = note.updatedAtMs,
            )
        )
    }

    private companion object {
        const val MAX_CACHED_DOCUMENTS = 3
    }
}
