package com.atlasreader.core.engine

import android.content.Context

/**
 * Plugin contract for document formats. Adding a new format (DOCX, ODT, CBZ,
 * MOBI, AZW3…) means implementing this interface and registering it in
 * [EngineRegistry] — consumers depend only on this abstraction.
 *
 * Implementations must be safe to call from background dispatchers and must
 * not throw across the boundary: failures are reported as [DocumentOpenException]
 * which callers map onto the error taxonomy.
 */
interface DocumentEngine {
    val format: DocumentFormat

    /**
     * Extract bibliographic metadata. Called once during import (and refresh).
     * [context] may be null when [DocumentSource.localPath] is set (JVM tests).
     */
    suspend fun extractMetadata(context: Context?, source: DocumentSource): ExtractedMetadata

    /** Extract a cover bitmap, typically a scaled first page / cover file. */
    suspend fun extractCover(context: Context?, source: DocumentSource): ByteArray? = null

    /** Fully parse the document into [ParsedDocument]. */
    suspend fun parse(context: Context?, source: DocumentSource): ParsedDocument
}

/** Typed failure thrown by engines; converted by callers into [com.atlasreader.core.common.AtlasError]. */
class DocumentOpenException(
    val format: DocumentFormat,
    val reason: Reason,
    detail: String,
    cause: Throwable? = null,
) : Exception("${format.label}: $reason — $detail", cause) {

    enum class Reason {
        /** File missing, unreadable or truncated. */
        CORRUPT,
        /** Recognised but engine cannot handle this variant yet. */
        UNSUPPORTED,
        /** Too large to open safely in memory. */
        TOO_LARGE,
        /** DRM-protected (future). */
        PROTECTED,
        /** Unknown internal failure. */
        INTERNAL,
    }
}
