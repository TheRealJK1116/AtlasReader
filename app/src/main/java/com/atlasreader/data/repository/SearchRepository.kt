package com.atlasreader.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.atlasreader.core.common.DispatcherProvider
import com.atlasreader.core.common.TimeProvider
import com.atlasreader.core.database.dao.DocumentDao
import com.atlasreader.core.database.dao.SearchDao
import com.atlasreader.core.database.entity.SearchHistoryEntity
import com.atlasreader.core.indexer.TextTokenizer
import com.atlasreader.domain.model.DocumentSummary
import com.atlasreader.domain.model.SearchMatch
import com.atlasreader.domain.model.SearchResultGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise-ish search: FTS4 full-text across indexed content + metadata
 * (title/author) lookups + suggestion history. Snippets are windowed from the
 * `chunk_previews` table so no document re-parsing happens at query time.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val searchDao: SearchDao,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    /** Metadata-only search (title/author/fileName), fast for suggestions. */
    suspend fun metadataSearch(query: String, limit: Int = 12): List<DocumentSummary> =
        withContext(dispatchers.io) {
            documentDao.searchMetadata(query.trim(), limit).map { it.toSummary() }
        }

    /** Full-text search across indexed content. */
    suspend fun fullTextSearch(query: String, limit: Int = 20): List<SearchResultGroup> =
        withContext(dispatchers.io) {
            val terms = TextTokenizer.queryTerms(query)
            if (terms.isEmpty()) return@withContext emptyList()

            // Prefix matching per term, ANDed: every term must appear.
            val matchQuery = terms.joinToString(" AND ") { term ->
                if (term.length == 1) "\"$term\"" else "\"$term\"*"
            }
            val hits = try {
                searchDao.searchIndex(
                    SimpleSQLiteQuery(
                        FTS_SELECT, arrayOf(matchQuery)
                    )
                )
            } catch (e: Exception) {
                // Malformed MATCH syntax (e.g. quoted single chars) — fall back to OR.
                val orQuery = terms.joinToString(" OR ") { "\"$it\"*" }
                searchDao.searchIndex(SimpleSQLiteQuery(FTS_SELECT, arrayOf(orQuery)))
            }
            if (hits.isEmpty()) return@withContext emptyList()

            val byDocument = hits.groupBy { it.documentId }
            val ranked = byDocument.entries
                .sortedByDescending { (_, docHits) -> docHits.size }
                .take(limit)

            val documents = documentDao.byIds(ranked.map { it.key }).associateBy { it.id }
            ranked.mapNotNull { (docId, docHits) ->
                val doc = documents[docId] ?: return@mapNotNull null
                val matches = docHits.take(MAX_MATCHES_PER_DOC).map { hit ->
                    val preview = searchDao.preview(docId, hit.chunkToken)?.preview
                    SearchMatch(
                        chunkToken = hit.chunkToken,
                        position = hit.position,
                        term = hit.term,
                        snippet = buildSnippet(preview, hit.position, hit.term),
                    )
                }
                SearchResultGroup(doc.toSummary(), matches)
            }
        }

    // -------------------------------------------------------------- history

    fun recentHistory(limit: Int = 10): Flow<List<String>> =
        searchDao.observeRecentHistory(limit).map { list -> list.map { it.query } }

    suspend fun recordHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchDao.insertHistory(SearchHistoryEntity(query = trimmed, createdAtMs = time.epochMillis()))
        searchDao.pruneHistory(keep = 30)
    }

    suspend fun clearHistory() = searchDao.clearHistory()

    private fun buildSnippet(preview: String?, position: Int, term: String): String {
        if (preview.isNullOrBlank()) return ""
        val window = 60
        val start = (position - window).coerceAtLeast(0)
        val end = (position + term.length + window).coerceAtMost(preview.length)
        val snippet = preview.substring(start, end).replace(Regex("\\s+"), " ").trim()
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < preview.length) "…" else ""
        return "$prefix$snippet$suffix"
    }

    private companion object {
        const val MAX_MATCHES_PER_DOC = 20
        const val FTS_SELECT =
            "SELECT documentId, chunkToken, position, term FROM search_index WHERE search_index MATCH ?"
    }
}
