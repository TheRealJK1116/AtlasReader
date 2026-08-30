package com.atlasreader.domain.model

import com.atlasreader.core.engine.DocumentFormat

/** UI-facing library row (flattened document ⋈ cover ⋈ progress). */
data class DocumentSummary(
    val id: Long,
    val contentHash: String,
    val fileName: String,
    val displayName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val format: DocumentFormat,
    val title: String,
    val author: String?,
    val description: String?,
    val addedAtMs: Long,
    val openedAtMs: Long?,
    val favorite: Boolean,
    val coverPath: String?,
    val progressPercent: Float,
    val progressUpdatedAtMs: Long?,
    val tags: List<String> = emptyList(),
) {
    val progressLabel: String?
        get() = when {
            progressPercent >= 99.5f -> "Finished"
            progressPercent > 0f -> "${(progressPercent * 100).toInt()}%"
            else -> null
        }
}

/** Immutable reading position persisted between sessions. */
data class ReadingPosition(
    val documentId: Long,
    val resourceToken: String?,
    val charOffset: Int,
    val pageIndex: Int,
    val scrollFraction: Float,
    val percent: Float,
    val updatedAtMs: Long,
    val sessionAccumMs: Long,
)

data class Bookmark(
    val id: Long,
    val documentId: Long,
    val resourceToken: String,
    val charOffset: Int,
    val text: String,
    val createdAtMs: Long,
    val note: String?,
)

data class Highlight(
    val id: Long,
    val documentId: Long,
    val resourceToken: String,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val colorHex: String,
    val note: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class ReaderNote(
    val id: Long,
    val documentId: Long,
    val resourceToken: String,
    val anchorOffset: Int,
    val text: String,
    val linkedHighlightId: Long?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/** One full-text search hit within a document chunk. */
data class SearchMatch(
    val chunkToken: String,
    val position: Int,
    val term: String,
    val snippet: String,
)

data class SearchResultGroup(
    val document: DocumentSummary,
    val matches: List<SearchMatch>,
)

data class ReadingStats(
    val totalReadMs: Long,
    val finishedBooks: Int,
    val startedBooks: Int,
)
