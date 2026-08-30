package com.atlasreader.core.engine

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Format → engine lookup. The registry is the single extension point for new
 * formats: add an engine, register it here, done.
 */
@Singleton
class EngineRegistry @Inject constructor(
    private val textEngine: TextEngine,
    private val markdownEngine: MarkdownEngine,
    private val rtfEngine: RtfEngine,
    private val htmlEngine: HtmlEngine,
    private val epubEngine: EpubEngine,
    private val pdfEngine: PdfEngine,
) {

    private val byFormat: Map<DocumentFormat, DocumentEngine> = listOf(
        pdfEngine, epubEngine, markdownEngine, rtfEngine, htmlEngine, textEngine
    ).associateBy { it.format }

    fun engineFor(format: DocumentFormat): DocumentEngine? = byFormat[format]

    fun engineForExtension(extension: String?): DocumentEngine? =
        DocumentFormat.fromExtension(extension)?.let { byFormat[it] }

    fun engineForUri(displayName: String, mimeType: String?): DocumentEngine? {
        DocumentFormat.fromMimeType(mimeType)?.let { return byFormat[it] }
        return engineForExtension(com.atlasreader.core.util.FilenameUtils.extension(displayName))
    }

    val supportedExtensions: Set<String> = byFormat.keys.flatMap { it.extensions }.toSet()

    val supportedFormats: List<DocumentFormat> = byFormat.keys.toList()
}
