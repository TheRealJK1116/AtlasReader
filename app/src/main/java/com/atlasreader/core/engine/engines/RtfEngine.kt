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
 * RTF engine: parses with [RtfParser], chunks at paragraph boundaries, renders
 * styled HTML. Metadata comes from the `info` destination (title/author).
 */
@Singleton
class RtfEngine @Inject constructor() : DocumentEngine {

    override val format: DocumentFormat = DocumentFormat.RTF

    override suspend fun extractMetadata(context: Context?, source: DocumentSource): ExtractedMetadata {
        val doc = parseRtf(context, source)
        return ExtractedMetadata(
            title = doc.title.ifBlank { FilenameUtils.stripExtension(source.displayName).trim() },
            author = doc.author.ifBlank { null },
        )
    }

    override suspend fun parse(context: Context?, source: DocumentSource): ParsedDocument {
        val doc = parseRtf(context, source)
        val chunks = buildChunks(doc)
        val fullHtml = RtfToHtml.render(doc)

        return ParsedDocument(
            format = format,
            metadata = ExtractedMetadata(
                title = doc.title.ifBlank { FilenameUtils.stripExtension(source.displayName).trim() },
                author = doc.author.ifBlank { null },
            ),
            tableOfContents = emptyList(),
            chunks = chunks,
        ).let { parsed ->
            if (chunks.size == 1) {
                // Single chunk: reuse the full render (cheap path for small docs).
                parsed.copy(chunks = listOf(parsed.chunks.first().copy(html = fullHtml)))
            } else {
                parsed
            }
        }
    }

    private fun parseRtf(context: Context?, source: DocumentSource): RtfParser.RtfDocument {
        val bytes = try {
            EngineIo.readBytes(context, source, maxBytes = 64L * 1024 * 1024)
        } catch (e: DocumentOpenException) {
            throw e
        } catch (e: Exception) {
            throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, e.message.orEmpty(), e)
        }
        return try {
            RtfParser.parse(bytes)
        } catch (e: Exception) {
            AtlasLog.w("RtfParser failed", e)
            throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, e.message.orEmpty(), e)
        }
    }

    private fun buildChunks(doc: RtfParser.RtfDocument): List<ProseChunk> {
        val chunks = mutableListOf<ProseChunk>()
        val blockBuffer = mutableListOf<RtfParser.RtfBlock>()
        var chars = 0
        var index = 0

        fun flush() {
            if (blockBuffer.isEmpty()) return
            val html = RtfToHtml.render(RtfParser.RtfDocument(blockBuffer.toList(), doc.colorTable, "", ""))
            val text = blockBuffer.joinToString("\n") { it.text }
            chunks += ProseChunk(
                resourceToken = index.toString(),
                index = index,
                heading = null,
                text = text,
                html = html,
            )
            index++
            blockBuffer.clear()
            chars = 0
        }

        for (block in doc.blocks) {
            blockBuffer += block
            chars += block.text.length
            if (chars >= 1500) flush()
        }
        flush()
        return chunks
    }
}
