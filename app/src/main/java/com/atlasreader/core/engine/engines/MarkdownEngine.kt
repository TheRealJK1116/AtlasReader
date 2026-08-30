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
 * Markdown engine. Splits on ATX headings so each heading starts a chapter;
 * the TOC is derived from heading levels. HTML output comes from flexmark.
 */
@Singleton
class MarkdownEngine @Inject constructor() : DocumentEngine {

    override val format: DocumentFormat = DocumentFormat.MARKDOWN

    override suspend fun extractMetadata(context: Context?, source: DocumentSource): ExtractedMetadata {
        // Cheap scan: only look at the first heading — never parse the whole file.
        val title = try {
            val raw = EngineIo.readText(context, source)
            raw.lineSequence().firstNotNullOfOrNull { line ->
                ATX_HEADING.matchEntire(line.trim())?.groupValues?.get(2)?.trim()
            }?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            AtlasLog.w("MarkdownEngine metadata scan failed", e)
            null
        } ?: FilenameUtils.stripExtension(source.displayName).trim()
        return ExtractedMetadata(title = title)
    }

    override suspend fun parse(context: Context?, source: DocumentSource): ParsedDocument {
        val raw = try {
            EngineIo.readText(context, source)
        } catch (e: DocumentOpenException) {
            throw e
        } catch (e: Exception) {
            throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, e.message.orEmpty(), e)
        }

        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val sections = mutableListOf<Section>()
        var currentHeading: Pair<String, Int>? = null
        val body = StringBuilder()

        fun flush() {
            val text = body.toString()
            if (text.isNotBlank() || currentHeading != null) {
                sections += Section(currentHeading, text)
            }
            body.clear()
        }

        for (line in lines) {
            val heading = ATX_HEADING.matchEntire(line.trim())
            if (heading != null) {
                flush()
                val level = heading.groupValues[1].length
                val label = heading.groupValues[2].trim()
                currentHeading = label to level
            } else {
                if (body.isNotEmpty()) body.append('\n')
                body.append(line)
            }
        }
        flush()
        if (sections.isEmpty() && raw.isNotBlank()) sections += Section(null, raw)

        val chunks = sections.mapIndexed { index, section ->
            val chunkBody = buildString {
                if (section.heading != null) {
                    append("#".repeat(section.headingLevel)).append(' ').append(section.heading).append('\n')
                }
                append(section.body)
            }.trim()
            ProseChunk(
                resourceToken = index.toString(),
                index = index,
                heading = section.heading?.first,
                text = chunkBody,
                html = MarkdownRenderer.toHtml(chunkBody),
            )
        }

        val toc = sections.mapIndexedNotNull { index, section ->
            section.heading?.let { (label, level) -> TocEntry(label, level.coerceIn(1, 3), index) }
        }

        return ParsedDocument(
            format = format,
            metadata = ExtractedMetadata(title = toc.firstOrNull()?.label ?: FilenameUtils.stripExtension(source.displayName).trim()),
            tableOfContents = toc,
            chunks = chunks,
        )
    }

    private data class Section(val heading: Pair<String, Int>?, val body: String) {
        val headingLevel: Int get() = heading?.second ?: 0
    }

    private companion object {
        val ATX_HEADING = Regex("^(#{1,6})\\s+(.+?)\\s*#*$")
    }
}
