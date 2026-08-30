package com.atlasreader.core.database

import com.atlasreader.core.engine.DocumentFormat

/** Library sort keys. */
enum class LibrarySort { TITLE, AUTHOR, DATE_ADDED, DATE_OPENED, PROGRESS, SIZE }

/** Reading-status filter for the library. */
enum class ReadingStatusFilter { ANY, UNREAD, READING, FINISHED }

/** Composition of every library filter dimension. */
data class LibraryFilter(
    val query: String? = null,
    val formats: Set<DocumentFormat> = emptySet(),
    val collectionId: Long? = null,
    val tagId: Long? = null,
    val favoritesOnly: Boolean = false,
    val status: ReadingStatusFilter = ReadingStatusFilter.ANY,
)

data class BuiltQuery(val sql: String, val args: List<Any>)

/**
 * Builds the flattened library SELECT (documents ⋈ covers ⋈ reading_progress)
 * for every combination of [LibraryFilter] and [LibrarySort]. Pure function —
 * unit tested exhaustively. Uses the ESCAPE clause so user text cannot break
 * LIKE patterns.
 */
object LibraryQueryBuilder {

    private val PROJECTION = """
        SELECT d.id, d.contentHash, d.fileName, d.displayName, d.filePath, d.fileSizeBytes,
               d.format, d.title, d.author, d.description, d.language, d.publisher,
               d.publishedDate, d.sourceUri, d.addedAtMs, d.openedAtMs, d.favorite,
               d.lastPositionJson,
               c.path AS coverPath,
               p.resourceToken AS progressResourceToken, p.charOffset AS progressCharOffset,
               p.pageIndex AS progressPageIndex, p.scrollFraction AS progressScrollFraction,
               p.percent AS progressPercent, p.updatedAtMs AS progressUpdatedAtMs,
               p.sessionAccumMs AS progressSessionAccumMs
    """.trimIndent()

    private val FROM = """
        FROM documents d
        LEFT JOIN covers c ON c.documentId = d.id
        LEFT JOIN reading_progress p ON p.documentId = d.id
    """.trimIndent()

    fun build(filter: LibraryFilter, sort: LibrarySort): BuiltQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        filter.query?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            val q = "%" + escapeLike(raw) + "%"
            where += "(d.title LIKE ? ESCAPE '\\' OR d.author LIKE ? ESCAPE '\\' OR d.fileName LIKE ? ESCAPE '\\')"
            args += q; args += q; args += q
        }
        if (filter.formats.isNotEmpty()) {
            where += "d.format IN (${filter.formats.joinToString(",") { "?" }})"
            args += filter.formats.map { it.name }
        }
        filter.collectionId?.let { id ->
            where += "EXISTS (SELECT 1 FROM collection_documents cd WHERE cd.documentId = d.id AND cd.collectionId = ?)"
            args += id
        }
        filter.tagId?.let { id ->
            where += "EXISTS (SELECT 1 FROM document_tags dt WHERE dt.documentId = d.id AND dt.tagId = ?)"
            args += id
        }
        if (filter.favoritesOnly) where += "d.favorite = 1"
        when (filter.status) {
            ReadingStatusFilter.ANY -> {}
            ReadingStatusFilter.UNREAD -> where += "(p.percent IS NULL OR p.percent = 0)"
            ReadingStatusFilter.READING -> where += "(p.percent IS NOT NULL AND p.percent > 0 AND p.percent < 99.5)"
            ReadingStatusFilter.FINISHED -> where += "(p.percent IS NOT NULL AND p.percent >= 99.5)"
        }

        val orderBy = when (sort) {
            LibrarySort.TITLE -> "d.title IS NULL, d.title COLLATE NOCASE ASC"
            LibrarySort.AUTHOR -> "d.author IS NULL, d.author COLLATE NOCASE ASC, d.title COLLATE NOCASE ASC"
            LibrarySort.DATE_ADDED -> "d.addedAtMs DESC"
            LibrarySort.DATE_OPENED -> "d.openedAtMs IS NULL, d.openedAtMs DESC"
            LibrarySort.PROGRESS -> "COALESCE(p.percent, 0) DESC, d.title COLLATE NOCASE ASC"
            LibrarySort.SIZE -> "d.fileSizeBytes DESC"
        }

        val sql = buildString {
            append(PROJECTION).append(' ')
            append(FROM).append(' ')
            append("WHERE ").append(where.ifEmpty { listOf("1=1") }.joinToString(" AND ")).append(' ')
            append("ORDER BY ").append(orderBy)
        }
        return BuiltQuery(sql, args)
    }

    fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
