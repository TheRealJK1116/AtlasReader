package com.atlasreader.core.engine

import android.graphics.Bitmap

/**
 * One scrollable unit of a document, as consumed by the continuous reader.
 *
 * @property resourceToken stable address of the chunk inside the document
 *   (spine item id for EPUB, chunk ordinal otherwise). Persisted with
 *   bookmarks/highlights/notes and the reading position.
 * @property index ordinal in [ParsedDocument.chunks].
 * @property heading human-readable chunk title (null for untitled chunks).
 * @property text plain text of the chunk. This is the *anchor space* for
 *   highlights/notes: character offsets are measured against it, so they are
 *   stable regardless of how the chunk is rendered.
 * @property html HTML payload rendered in the reader WebView. Must satisfy
 *   `HtmlUtils.textFromHtml(html) == text` so annotation locating works.
 * @property baseUrl resolved against for relative links/images (EPUB chapter
 *   files on disk); null for engine-rendered chunks.
 */
data class ProseChunk(
    val resourceToken: String,
    val index: Int,
    val heading: String?,
    val text: String,
    val html: String? = null,
    val baseUrl: String? = null,
)

/** A table-of-contents entry; anchors point into a chunk. */
data class TocEntry(
    val label: String,
    val level: Int,
    val chunkIndex: Int,
)

/**
 * Renders pages of a fixed-layout document (PDF). Implementations must be
 * reusable: the reader releases the provider when the document closes.
 */
interface PageProvider {
    suspend fun pageCount(): Int
    suspend fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int, density: Float): Bitmap?
}

/** Fully parsed document returned by a [DocumentEngine]. */
data class ParsedDocument(
    val format: DocumentFormat,
    val metadata: ExtractedMetadata,
    val tableOfContents: List<TocEntry>,
    val chunks: List<ProseChunk>,
    /** Non-null for fixed-layout formats; the reader switches to paging mode. */
    val pageProvider: PageProvider? = null,
)
