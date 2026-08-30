package com.atlasreader.core.engine

/** Rough classification of how a document is consumed. */
enum class DocumentKind { TEXTUAL, FIXED_LAYOUT }

/**
 * Canonical format registry. Extending the app with DOCX/ODT/MOBI etc. means
 * adding an enum value (or a plugin entry) plus an engine — no existing engine
 * or consumer changes.
 */
enum class DocumentFormat(
    val label: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
    val kind: DocumentKind,
) {
    EPUB("EPUB", setOf("epub"), setOf("application/epub+zip", "application/x-ebook"), DocumentKind.TEXTUAL),
    PDF("PDF", setOf("pdf"), setOf("application/pdf"), DocumentKind.FIXED_LAYOUT),
    MARKDOWN("Markdown", setOf("md", "markdown"), setOf("text/markdown", "text/x-markdown"), DocumentKind.TEXTUAL),
    RTF("RTF", setOf("rtf"), setOf("text/rtf", "application/rtf"), DocumentKind.TEXTUAL),
    HTML("HTML", setOf("html", "htm", "xhtml"), setOf("text/html", "application/xhtml+xml"), DocumentKind.TEXTUAL),
    TXT("Plain text", setOf("txt", "text", "log"), setOf("text/plain"), DocumentKind.TEXTUAL);

    companion object {
        fun fromExtension(ext: String?): DocumentFormat? =
            entries.firstOrNull { ext != null && ext.lowercase() in it.extensions }

        fun fromMimeType(mime: String?): DocumentFormat? =
            entries.firstOrNull { mime != null && mime.lowercase() in it.mimeTypes }
    }
}
