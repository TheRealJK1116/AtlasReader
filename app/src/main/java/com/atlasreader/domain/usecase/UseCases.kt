package com.atlasreader.domain.usecase

import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.atlasreader.core.common.AtlasResult
import com.atlasreader.core.database.LibraryFilter
import com.atlasreader.core.database.LibrarySort
import com.atlasreader.core.database.entity.CollectionWithCount
import com.atlasreader.core.engine.ParsedDocument
import com.atlasreader.core.importer.ImportRequest
import com.atlasreader.data.repository.LibraryRepository
import com.atlasreader.data.repository.ReaderRepository
import com.atlasreader.data.repository.SearchRepository
import com.atlasreader.domain.model.Bookmark
import com.atlasreader.domain.model.DocumentSummary
import com.atlasreader.domain.model.Highlight
import com.atlasreader.domain.model.ReaderNote
import com.atlasreader.domain.model.SearchResultGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// Library use cases
// ---------------------------------------------------------------------------

@Singleton
class ObserveLibraryUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(filter: LibraryFilter, sort: LibrarySort): PagingSource<Int, DocumentSummary> =
        repository.library(filter, sort)
}

@Singleton
class ContinueReadingUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(limit: Int = 12): Flow<List<DocumentSummary>> = repository.continueReading(limit)
}

@Singleton
class RecentsUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(limit: Int = 24): Flow<List<DocumentSummary>> = repository.recents(limit)
}

@Singleton
class LibraryCountUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    operator fun invoke(): Flow<Int> = repository.count()
}

@Singleton
class ToggleFavoriteUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(documentId: Long, favorite: Boolean) =
        repository.setFavorite(documentId, favorite)
}

@Singleton
class DeleteDocumentsUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(ids: List<Long>) = repository.deleteDocuments(ids)
}

@Singleton
class CollectionsUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    fun observeAll(): Flow<List<CollectionWithCount>> = repository.collections()

    suspend fun create(name: String): Long = repository.createCollection(name)

    suspend fun delete(id: Long) = repository.deleteCollection(id)

    suspend fun addDocument(collectionId: Long, documentId: Long) =
        repository.addToCollection(collectionId, documentId)

    suspend fun removeDocument(collectionId: Long, documentId: Long) =
        repository.removeFromCollection(collectionId, documentId)

    fun documentsIn(collectionId: Long): Flow<List<DocumentSummary>> =
        repository.documentsInCollection(collectionId)

    suspend fun collectionIdsFor(documentId: Long): List<Long> =
        repository.collectionsForDocument(documentId)
}

@Singleton
class TagsUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {
    fun observeAll(): Flow<List<com.atlasreader.core.database.entity.TagEntity>> = repository.tags()

    suspend fun assign(documentId: Long, name: String) = repository.assignTag(documentId, name)

    suspend fun remove(documentId: Long, tagId: Long) = repository.removeTag(documentId, tagId)

    suspend fun namesFor(documentId: Long): List<String> = repository.tagsForDocument(documentId)

    suspend fun delete(tagId: Long) = repository.deleteTag(tagId)
}

// ---------------------------------------------------------------------------
// Reader use cases
// ---------------------------------------------------------------------------

@Singleton
class OpenDocumentUseCase @Inject constructor(
    private val repository: ReaderRepository,
) {
    suspend operator fun invoke(documentId: Long): AtlasResult<ReaderRepository.OpenedDocument> =
        repository.open(documentId)
}

@Singleton
class ReaderAnnotationsUseCase @Inject constructor(
    private val repository: ReaderRepository,
) {
    fun observeBookmarks(documentId: Long): Flow<List<Bookmark>> = repository.observeBookmarks(documentId)

    suspend fun addBookmark(documentId: Long, token: String, offset: Int, text: String, note: String? = null): Long =
        repository.addBookmark(documentId, token, offset, text, note)

    suspend fun deleteBookmark(bookmark: Bookmark) = repository.deleteBookmark(bookmark)

    fun observeHighlights(documentId: Long): Flow<List<Highlight>> = repository.observeHighlights(documentId)

    suspend fun addHighlight(
        documentId: Long,
        token: String,
        start: Int,
        end: Int,
        text: String,
        colorHex: String,
    ): Long = repository.addHighlight(documentId, token, start, end, text, colorHex)

    suspend fun deleteHighlight(highlight: Highlight) = repository.deleteHighlight(highlight)

    suspend fun recolorHighlight(id: Long, colorHex: String) = repository.recolorHighlight(id, colorHex)

    suspend fun setHighlightNote(id: Long, note: String?) = repository.setHighlightNote(id, note)

    fun observeNotes(documentId: Long): Flow<List<ReaderNote>> = repository.observeNotes(documentId)

    suspend fun addNote(documentId: Long, token: String, offset: Int, text: String, highlightId: Long? = null): Long =
        repository.addNote(documentId, token, offset, text, highlightId)

    suspend fun updateNote(note: ReaderNote) = repository.updateNote(note)

    suspend fun deleteNote(note: ReaderNote) = repository.deleteNote(note)
}

@Singleton
class ReaderPositionUseCase @Inject constructor(
    private val repository: ReaderRepository,
) {
    suspend fun save(position: com.atlasreader.domain.model.ReadingPosition) = repository.savePosition(position)

    suspend fun recordSession(documentId: Long, deltaMs: Long) = repository.recordSessionTime(documentId, deltaMs)

    fun observe(documentId: Long): Flow<com.atlasreader.domain.model.ReadingPosition?> =
        repository.observePosition(documentId)
}

// ---------------------------------------------------------------------------
// Search use cases
// ---------------------------------------------------------------------------

@Singleton
class SearchLibraryUseCase @Inject constructor(
    private val repository: SearchRepository,
) {
    suspend fun metadata(query: String, limit: Int = 12) = repository.metadataSearch(query, limit)

    suspend fun fullText(query: String, limit: Int = 20): List<SearchResultGroup> =
        repository.fullTextSearch(query, limit)

    fun history(limit: Int = 10): Flow<List<String>> = repository.recentHistory(limit)

    suspend fun recordHistory(query: String) = repository.recordHistory(query)

    suspend fun clearHistory() = repository.clearHistory()
}

// ---------------------------------------------------------------------------
// Import use cases
// ---------------------------------------------------------------------------

@Singleton
class ImportFilesUseCase @Inject constructor(
    private val coordinator: com.atlasreader.core.importer.ImportCoordinator,
) {
    operator fun invoke(requests: List<ImportRequest>) = coordinator.import(requests)

    val state get() = coordinator.state

    val autoOpen get() = coordinator.autoOpen
}

@Singleton
class ImportFolderUseCase @Inject constructor(
    private val coordinator: com.atlasreader.core.importer.ImportCoordinator,
) {
    suspend operator fun invoke(uri: android.net.Uri): List<ImportRequest> = coordinator.importFolder(uri)
}
