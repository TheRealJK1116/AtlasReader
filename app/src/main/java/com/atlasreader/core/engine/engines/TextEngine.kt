package com.atlasreader.core.engine.engines

import android.content.Context
import com.atlasreader.core.common.AtlasLog
import com.atlasreader.core.engine.DocumentEngine
import com.atlasreader.core.engine.DocumentFormat
import com.atlasreader.core.engine.DocumentOpenException
import com.atlasreader.core.engine.DocumentSource
import com.atlasreader.core.engine.EngineIo
import com.atlasreader.core.engine.ExtractedMetadata
import com.atlasreader.core.engine.ParsedDocument
import com.atlasreader.core.engine.ProseChunk
import com.atlasreader.core.engine.TocEntry
import com.atlasreader.core.util.FilenameUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain-text engine. Chunks the file into paragraph groups of ~1500 chars so
 * the WebView only re-lays out ~16 KB of text per chapter; detects
 * CHAPTER/PART-style headings for a minimal TOC. Charset-fallbacks through
 * UTF-8 → windows-1252.
 */
@Singleton
class TextEngine @Inject constructor() : DocumentEngine {

    override val format: DocumentFormat = DocumentFormat.TXT

    override suspend fun extractMetadata(context: Context?, source: DocumentSource): ExtractedMetadata =
        ExtractedMetadata(title = FilenameUtils.stripExtension(source.displayName).trim())

    override suspend fun parse(context: Context?, source: DocumentSource): ParsedDocument {
        val raw = try {
            EngineIo.readText(context, source)
        } catch (e: DocumentOpenException) {
            throw e
        } catch (e: Exception) {
            AtlasLog.w("TextEngine parse failed", e)
            throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, e.message.orEmpty(), e)
        }

        val paragraphs = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n\n")
        val chunks = buildChunks(paragraphs)
        val toc = chunks.mapNotNull { chunk ->
            chunk.heading?.let { TocEntry(it, 1, chunk.index) }
        }
        return ParsedDocument(
            format = format,
            metadata = extractMetadata(context, source),
            tableOfContents = toc,
            chunks = chunks,
        )
    }

    private fun buildChunks(paragraphs: List<String>): List<ProseChunk> {
        val chunks = mutableListOf<ProseChunk>()
        val buffer = StringBuilder()
        var bufferHeading: String? = null
        var index = 0

        fun flush() {
            if (buffer.isBlank()) return
            val text = buffer.toString().trimEnd()
            val html = buildString {
                append("<div class=\"prose\">")
                text.split("\n").forEachIndexed { i, line ->
                    if (i > 0) append("<br/>")
                    append(line.escapeHtml().ifBlank { "&nbsp;" })
                }
                append("</div>")
            }
            chunks += ProseChunk(
                resourceToken = index.toString(),
                index = index,
                heading = bufferHeading,
                text = text,
                html = html,
            )
            index++
            buffer.clear()
            bufferHeading = null
        }

        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue

            val heading = detectHeading(trimmed)
            val candidate = trimmed
            if (buffer.isNotEmpty() && buffer.length + candidate.length > CHUNK_TARGET_CHARS && bufferHeading == null) {
                flush()
            }
            if (heading != null && buffer.isNotEmpty()) flush()
            if (heading != null) bufferHeading = heading
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(candidate)
            if (buffer.length >= CHUNK_MAX_CHARS) flush()
        }
        flush()
        return chunks
    }

    private fun detectHeading(line: String): String? {
        if (line.length > 80) return null
        val match = HEADING_PATTERN.find(line) ?: return null
        return line.trim()
    }

    private companion object {
        val HEADING_PATTERN = Regex("(?i)^\\s*(chapter|part|book|section|act)\\s+([0-9ivxlcdm]+|\\w+)([.:]\\s.*)?$")
        const val CHUNK_TARGET_CHARS = 1500
        const val CHUNK_MAX_CHARS = 4000
    }
}

/** HTML-escaping used by text engines. */
internal fun String.escapeHtml(): String = buildString(length) {
    for (ch in this@escapeHtml) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(ch)
        }
    }
}
