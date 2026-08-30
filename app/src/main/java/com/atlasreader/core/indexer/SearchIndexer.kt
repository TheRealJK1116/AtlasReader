package com.atlasreader.core.indexer

import com.atlasreader.core.common.AtlasLog
import com.atlasreader.core.common.DispatcherProvider
import com.atlasreader.core.database.AtlasDatabase
import com.atlasreader.core.database.dao.SearchDao
import com.atlasreader.core.database.entity.ChunkPreviewEntity
import com.atlasreader.core.database.entity.IndexToken
import com.atlasreader.core.engine.ParsedDocument
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Incremental full-text indexer. Writes token rows into the contentless FTS4
 * table in batched transactions. Runs on background dispatchers; idempotent
 * (re-indexing a document first deletes its previous rows).
 */
@Singleton
class SearchIndexer @Inject constructor(
    private val db: AtlasDatabase,
    private val searchDao: SearchDao,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun indexDocument(documentId: Long, document: ParsedDocument, title: String?, author: String?) {
        val tokens = mutableListOf<IndexToken>()
        if (!title.isNullOrBlank()) tokens += tokenize(documentId, META_CHUNK, title)
        if (!author.isNullOrBlank()) tokens += tokenize(documentId, META_CHUNK, author)
        for (chunk in document.chunks) {
            tokens += tokenize(documentId, chunk.resourceToken, chunk.text)
        }
        val previews = document.chunks.map { chunk ->
            ChunkPreviewEntity(documentId, chunk.resourceToken, chunk.text.take(PREVIEW_CHARS))
        }

        deleteDocument(documentId)
        withContext(dispatchers.io) {
            val writable = db.openHelper.writableDatabase
            writable.beginTransaction()
            try {
                if (tokens.isNotEmpty()) {
                    tokens.chunked(BATCH_SIZE).forEach { batch ->
                        batch.forEach { token ->
                            writable.execSQL(
                                INSERT_SQL,
                                arrayOf(token.documentId.toString(), token.chunkToken, token.position, token.term)
                            )
                        }
                    }
                }
                writable.execSQL(
                    "DELETE FROM chunk_previews WHERE documentId = ?",
                    arrayOf(documentId.toString())
                )
                if (previews.isNotEmpty()) searchDao.upsertPreviews(previews)
                writable.setTransactionSuccessful()
            } finally {
                writable.endTransaction()
            }
        }
        AtlasLog.d("Indexed ${tokens.size} tokens for document $documentId")
    }

    suspend fun deleteDocument(documentId: Long) {
        withContext(dispatchers.io) {
            val writable = db.openHelper.writableDatabase
            writable.execSQL(
                "DELETE FROM search_index WHERE documentId = ?",
                arrayOf(documentId.toString())
            )
            writable.execSQL(
                "DELETE FROM chunk_previews WHERE documentId = ?",
                arrayOf(documentId.toString())
            )
        }
    }

    suspend fun clearIndex() {
        withContext(dispatchers.io) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM search_index")
            db.openHelper.writableDatabase.execSQL("DELETE FROM chunk_previews")
        }
    }

    private fun tokenize(documentId: Long, chunkToken: String, text: String): List<IndexToken> =
        TextTokenizer.tokens(text).map { IndexToken(documentId, chunkToken, it.position, it.term) }

    companion object {
        const val META_CHUNK = "meta"
        const val PREVIEW_CHARS = 500
        private const val BATCH_SIZE = 1000
        private const val INSERT_SQL =
            "INSERT INTO search_index (documentId, chunkToken, position, term) VALUES (?, ?, ?, ?)"
    }
}
