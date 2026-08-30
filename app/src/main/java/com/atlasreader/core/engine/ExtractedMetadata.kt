package com.atlasreader.core.engine

/** Metadata extracted from a document by an engine, prior to DB persistence. */
data class ExtractedMetadata(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    /** Encoded JPEG/PNG cover bitmap, or null when the source has none. */
    val coverBytes: ByteArray? = null,
)
