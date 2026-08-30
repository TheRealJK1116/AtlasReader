package com.atlasreader.core.engine.engines

import android.content.Context
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
import com.atlasreader.core.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTML engine. A single HTML file is treated as one chunk (internal #anchor
 * links work natively in the WebView). Scripts and event handlers are stripped
 * before display; relative resources keep resolving because the WebView is
 * given the real file path as base URL.
 */
@Singleton
class HtmlEngine @Inject constructor() : DocumentEngine {

    override val format: DocumentFormat = DocumentFormat.HTML

    override suspend fun extractMetadata(context: Context?, source: DocumentSource): ExtractedMetadata {
        val raw = readRaw(context, source)
        val doc = Jsoup.parse(raw)
        val title = doc.title().trim().takeIf { it.isNotEmpty() }
        val description = doc.select("meta[name=description]").attr("content").trim().takeIf { it.isNotEmpty() }
        val author = doc.select("meta[name=author]").attr("content").trim().takeIf { it.isNotEmpty() }
        return ExtractedMetadata(
            title = title ?: FilenameUtils.stripExtension(source.displayName).trim(),
            author = author,
            description = description,
            language = doc.select("html").attr("lang").takeIf { it.isNotEmpty() },
        )
    }

    override suspend fun parse(context: Context?, source: DocumentSource): ParsedDocument {
        val raw = readRaw(context, source)
        val doc = Jsoup.parse(raw)
        val body = HtmlUtils.sanitizeForDisplay(doc.body().html())
        val text = HtmlUtils.textFromHtml(body)
        val headings = doc.select("h1, h2, h3, h4").map { it.text().trim() }.filter { it.isNotEmpty() }

        val toc = headings.mapIndexed { i, label ->
            TocEntry(label, (i % 3) + 1, 0)
        }

        return ParsedDocument(
            format = format,
            metadata = extractMetadata(context, source),
            tableOfContents = toc,
            chunks = listOf(
                ProseChunk(
                    resourceToken = "0",
                    index = 0,
                    heading = headings.firstOrNull(),
                    text = text,
                    html = "<div class=\"prose\">$body</div>",
                )
            ),
        )
    }

    private fun readRaw(context: Context?, source: DocumentSource): String =
        try {
            EngineIo.readText(context, source)
        } catch (e: DocumentOpenException) {
            throw e
        } catch (e: Exception) {
            throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, e.message.orEmpty(), e)
        }
}
