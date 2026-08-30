package com.atlasreader.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import androidx.sqlite.db.SimpleSQLiteQuery
import com.atlasreader.core.common.DispatcherProvider
import com.atlasreader.core.common.TimeProvider
import com.atlasreader.core.database.LibraryFilter
import com.atlasreader.core.database.LibrarySort
import com.atlasreader.core.database.LibraryQueryBuilder
import com.atlasreader.core.database.dao.CollectionDao
import com.atlasreader.core.database.dao.DocumentDao
import com.atlasreader.core.database.dao.TagDao
import com.atlasreader.core.database.entity.CollectionDocumentCrossRef
import com.atlasreader.core.database.entity.CollectionEntity
import com.atlasreader.core.database.entity.CollectionWithCount
import com.atlasreader.core.database.entity.DocumentEntity
import com.atlasreader.core.database.entity.DocumentTagCrossRef
import com.atlasreader.core.database.entity.TagEntity
import com.atlasreader.core.importer.CoverStore
import com.atlasreader.core.indexer.SearchIndexer
import com.atlasreader.domain.model.DocumentSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Library data: paged browsing, recents, continue-reading, favourites, bulk
 * deletion, collections and tags. Pure persistence concerns live in the DAOs;
 * file lifecycle (imports/ covers / index cleanup) is orchestrated here.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val collectionDao: CollectionDao,
    private val tagDao: TagDao,
    private val coverStore: CoverStore,
    private val searchIndexer: SearchIndexer,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    // ------------------------------------------------------------- browsing

    @OptIn(ExperimentalPagingApi::class)
    fun library(filter: LibraryFilter, sort: LibrarySort): PagingSource<Int, DocumentSummary> {
        val built = LibraryQueryBuilder.build(filter, sort)
        val source = documentDao.observeLibrary(
            SimpleSQLiteQuery(built.sql, built.args.toTypedArray())
        )
        // Room hands us a PagingSource<...DocumentRow>; PagingData.map cannot be applied to a
        // PagingSource, so map each loaded page to the domain model with a thin wrapper.
        return object : PagingSource<Int, DocumentSummary>() {
            override fun getRefreshKey(state: PagingState<Int, DocumentSummary>): Int? = null

            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DocumentSummary> {
                return when (val result = source.load(params)) {
                    is LoadResult.Page -> LoadResult.Page(
                        data = result.data.map { it.toSummary() },
                        prevKey = result.prevKey,
                        nextKey = result.nextKey,
                        itemsBefore = result.itemsBefore,
                        itemsAfter = result.itemsAfter,
                    )
                    is LoadResult.Error -> LoadResult.Error(result.throwable)
                    is LoadResult.Invalid -> LoadResult.Invalid()
                }
            }
        }
    }

    fun continueReading(limit: Int = 12): Flow<List<DocumentSummary>> =
        documentDao.observeContinueReading(limit).map { rows -> rows.map { it.toSummary() } }

    fun recents(limit: Int = 24): Flow<List<DocumentSummary>> =
        documentDao.observeRecents(limit).map { rows -> rows.map { it.toSummary() } }

    fun count(): Flow<Int> = documentDao.observeCount()

    suspend fun document(id: Long): DocumentEntity? = documentDao.byId(id)

    // ------------------------------------------------------------ favourites

    suspend fun setFavorite(documentId: Long, favorite: Boolean) =
        documentDao.setFavorite(documentId, favorite)

    // ---------------------------------------------------------- bulk deletion

    suspend fun deleteDocuments(ids: List<Long>) = withContext(dispatchers.io) {
        val documents = documentDao.byIds(ids)
        for (doc in documents) {
            runCatching { File(doc.filePath).delete() }
            coverStore.forDocument(doc.id)?.let { cover ->
                coverStore.unregister(doc.id)
                coverStore.deleteFile(cover.path)
            }
            searchIndexer.deleteDocument(doc.id)
        }
        documentDao.deleteByIds(ids)
    }

    // ------------------------------------------------------------ collections

    fun collections(): Flow<List<CollectionWithCount>> = collectionDao.observeWithCounts()

    suspend fun createCollection(name: String): Long =
        collectionDao.insert(CollectionEntity(name = name, createdAtMs = time.epochMillis()))

    suspend fun deleteCollection(id: Long) = collectionDao.delete(id)

    suspend fun addToCollection(collectionId: Long, documentId: Long) =
        collectionDao.addDocument(CollectionDocumentCrossRef(collectionId, documentId))

    suspend fun removeFromCollection(collectionId: Long, documentId: Long) =
        collectionDao.removeDocument(collectionId, documentId)

    fun documentsInCollection(collectionId: Long): Flow<List<DocumentSummary>> =
        collectionDao.observeDocumentsIn(collectionId).map { rows -> rows.map { it.toSummary() } }

    suspend fun collectionsForDocument(documentId: Long): List<Long> =
        collectionDao.collectionIdsForDocument(documentId)

    // ------------------------------------------------------------------ tags

    fun tags(): Flow<List<TagEntity>> = tagDao.observeAll()

    suspend fun assignTag(documentId: Long, name: String) {
        val tagId = tagDao.ensure(name.trim(), time.epochMillis())
        tagDao.addToDocument(DocumentTagCrossRef(documentId, tagId))
    }

    suspend fun removeTag(documentId: Long, tagId: Long) =
        tagDao.removeFromDocument(documentId, tagId)

    suspend fun tagsForDocument(documentId: Long): List<String> =
        tagDao.namesForDocument(documentId)

    suspend fun deleteTag(tagId: Long) = tagDao.delete(tagId)
}
