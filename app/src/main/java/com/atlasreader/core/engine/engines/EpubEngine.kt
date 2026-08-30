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
import com.atlasreader.core.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPUB engine (EPUB 2 + EPUB 3). The archive is extracted once next to the
 * imported file so the reader WebView can resolve images/CSS and cross-chapter
 * links against real file paths. Metadata comes from the OPF package document;
 * TOC is read from the EPUB3 nav document or the EPUB2 NCX.
 */
@Singleton
class EpubEngine @Inject constructor() : DocumentEngine {

    override val format: DocumentFormat = DocumentFormat.EPUB

    private val extractionLocks = mutableMapOf<String, Object>()

    override suspend fun extractMetadata(context: Context?, source: DocumentSource): ExtractedMetadata {
        val (opf, _) = openPackage(context, source) ?: return ExtractedMetadata()
        val meta = opf.select("metadata").firstOrNull()
        return ExtractedMetadata(
            title = meta?.select("dc\\:title, title").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() },
            author = meta?.select("dc\\:creator, creator").joinToString(", ") { it.text().trim() }.takeIf { it.isNotEmpty() },
            language = meta?.select("dc\\:language, language").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() },
            publisher = meta?.select("dc\\:publisher, publisher").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() },
            publishedDate = meta?.select("dc\\:date, date").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() },
            description = meta?.select("dc\\:description, description").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() },
            coverBytes = extractCoverBytes(context, source),
        )
    }

    override suspend fun extractCover(context: Context?, source: DocumentSource): ByteArray? =
        extractCoverBytes(context, source)

    private fun extractCoverBytes(context: Context?, source: DocumentSource): ByteArray? {
        val packageInfo = openPackage(context, source) ?: return null
        val (opf, zip) = packageInfo
        return try {
            val coverHref = resolveCoverHref(opf, zip)
            coverHref?.let { href ->
                val entry = zip.getEntry(href) ?: return null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (e: Exception) {
            AtlasLog.w("EpubEngine cover extraction failed", e)
            null
        }
    }

    private fun resolveCoverHref(opf: Document, zip: ZipFile): String? {
        val baseDir = opfBaseDir(opf)
        // EPUB3: manifest item with properties containing cover-image
        val byProperty = opf.select("manifest item[properties~=cover-image]").firstOrNull()?.attr("href")
        // EPUB2: <meta name="cover" content="id"/>
        val byMeta = opf.select("metadata meta[name=cover]").firstOrNull()?.attr("content")
            ?.let { id -> opf.select("manifest item[id=$id]").firstOrNull()?.attr("href") }
        val href = byProperty ?: byMeta
        if (href != null && href.isNotBlank()) {
            return normalizeEntryPath(baseDir, href)?.takeIf { zip.getEntry(it) != null }
        }
        // Fallback: first image named cover.*
        val firstCover = zip.entries().asSequence()
            .map { it.name }
            .filter { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) }
            .firstOrNull { COVER_FILENAME.matches(File(it).name) }
        return firstCover
    }

    override suspend fun parse(context: Context?, source: DocumentSource): ParsedDocument {
        val file = source.file
            ?: throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "EPUB requires a local file")
        val zip = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, e.message.orEmpty(), e)
        }
        zip.use { z ->
            val container = z.getEntry("META-INF/container.xml")
                ?: throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "Missing META-INF/container.xml")
            val containerDoc = Jsoup.parse(z.getInputStream(container).bufferedReader().use { it.readText() })
            val rootfilePath = containerDoc.select("rootfile[full-path]").firstOrNull()?.attr("full-path")
                ?: throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "container.xml has no rootfile")
            val opfEntry = z.getEntry(rootfilePath)
                ?: throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "OPF not found: $rootfilePath")
            val opf = Jsoup.parse(z.getInputStream(opfEntry).bufferedReader().use { it.readText() })
            val baseDir = rootfilePath.substringBeforeLast('/', "")

            // Manifest: id → resolved entry path
            val manifest = mutableMapOf<String, String>()
            for (item in opf.select("manifest item")) {
                val id = item.attr("id")
                val href = item.attr("href")
                if (id.isNotBlank() && href.isNotBlank()) {
                    manifest[id] = normalizeEntryPath(baseDir, href) ?: continue
                }
            }

            // Spine: ordered content files
            val spine = opf.select("spine itemref")
            if (spine.isEmpty()) {
                throw DocumentOpenException(format, DocumentOpenException.Reason.UNSUPPORTED, "Empty spine")
            }
            val spineEntries = spine.mapNotNull { item ->
                val idref = item.attr("idref")
                val path = manifest[idref] ?: return@mapNotNull null
                idref to path
            }

            val extractedDir = ensureExtracted(file)
            val chunks = mutableListOf<ProseChunk>()
            spineEntries.forEachIndexed { index, (idref, entryPath) ->
                val html = readEntryText(z, entryPath, extractedDir)
                    ?: throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "Spine file missing: $entryPath")
                val sanitized = HtmlUtils.sanitizeForDisplay(html)
                val text = HtmlUtils.textFromHtml(sanitized)
                val heading = headingOf(sanitized)
                val chapterFile = File(extractedDir, entryPath)
                chunks += ProseChunk(
                    resourceToken = "spine_${index}_$idref",
                    index = index,
                    heading = heading,
                    text = text,
                    html = "<div class=\"prose\">$sanitized</div>",
                    baseUrl = chapterFile.absolutePath.takeIf { it.isNotEmpty() },
                )
            }

            val metadata = extractMetadata(context, source)
            val toc = buildToc(opf, z, spineEntries, baseDir, chunks.size)

            return ParsedDocument(
                format = format,
                metadata = metadata.copy(title = metadata.title ?: FilenameUtils.stripExtension(source.displayName).trim()),
                tableOfContents = toc,
                chunks = chunks,
            )
        }
    }

    private fun buildToc(
        opf: Document,
        zip: ZipFile,
        spineEntries: List<Pair<String, String>>,
        baseDir: String,
        chunkCount: Int,
    ): List<TocEntry> {
        val spinePaths = spineEntries.map { it.second }

        // EPUB3 nav document
        val navItem = opf.select("manifest item[properties~=nav]").firstOrNull()
        if (navItem != null) {
            val path = normalizeEntryPath(baseDir, navItem.attr("href"))
            if (path != null) {
                val navDoc = zip.getEntry(path)?.let { entry ->
                    try { Jsoup.parse(zip.getInputStream(entry).bufferedReader().use { it.readText() }) } catch (e: Exception) { null }
                }
                if (navDoc != null) {
                    val toc = mutableListOf<TocEntry>()
                    collectNavItems(navDoc.select("nav[epub\\:type=toc] ol, nav > ol").firstOrNull() ?: navDoc.select("ol").firstOrNull(), spinePaths, 1, toc)
                    if (toc.isNotEmpty()) return toc
                }
            }
        }

        // EPUB2 NCX
        val ncxItem = opf.select("manifest item[media-type=application/x-dtbncx+xml]").firstOrNull()
        val ncxPath = ncxItem?.attr("href")?.let { normalizeEntryPath(baseDir, it) }
        if (ncxPath != null && zip.getEntry(ncxPath) != null) {
            val ncxDoc = try { Jsoup.parse(zip.getInputStream(zip.getEntry(ncxPath)).bufferedReader().use { it.readText() }) } catch (e: Exception) { null }
            if (ncxDoc != null) {
                val toc = mutableListOf<TocEntry>()
                collectNcxItems(ncxDoc.select("navMap > navPoint").firstOrNull(), spinePaths, 1, toc)
                return toc
            }
        }
        return emptyList()
    }

    private fun collectNavItems(ol: Element?, spinePaths: List<String>, level: Int, out: MutableList<TocEntry>) {
        if (ol == null) return
        for (li in ol.select(":scope > li")) {
            val a = li.select(":scope > a").firstOrNull()
            if (a != null) {
                val label = a.text().trim()
                val href = a.attr("href")
                val chunkIndex = chunkIndexForHref(href, spinePaths, out.size)
                if (label.isNotEmpty() && chunkIndex != null) {
                    out += TocEntry(label, level.coerceAtMost(4), chunkIndex)
                }
            }
            collectNavItems(li.select(":scope > ol").firstOrNull(), spinePaths, level + 1, out)
        }
    }

    private fun collectNcxItems(navPoint: Element?, spinePaths: List<String>, level: Int, out: MutableList<TocEntry>) {
        if (navPoint == null) return
        for (np in navPoint.select(":scope > navPoint")) {
            val label = np.select(":scope > navLabel > text").firstOrNull()?.text()?.trim()
            val href = np.select(":scope > content").firstOrNull()?.attr("src")
            val chunkIndex = href?.let { chunkIndexForHref(it, spinePaths, out.size) }
            if (!label.isNullOrEmpty() && chunkIndex != null) {
                out += TocEntry(label, level.coerceAtMost(4), chunkIndex)
            }
            collectNcxItems(np, spinePaths, level + 1, out)
        }
    }

    private fun chunkIndexForHref(href: String, spinePaths: List<String>, fallback: Int): Int? {
        val path = href.substringBefore('#')
        if (path.isBlank()) return fallback
        return spinePaths.indexOfFirst { it.endsWith(path) || path.endsWith(it.substringAfterLast('/')) }
            .takeIf { it >= 0 }
    }

    private fun headingOf(html: String): String? {
        val doc = Jsoup.parseBodyFragment(html)
        doc.select("h1, h2, h3").firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return doc.title().trim().takeIf { it.isNotEmpty() }
    }

    // ------------------------------------------------------------- package IO

    private fun openPackage(context: Context?, source: DocumentSource): Pair<Document, ZipFile>? {
        val file = source.file
            ?: throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "EPUB requires a local file")
        return try {
            val zip = ZipFile(file)
            val container = zip.getEntry("META-INF/container.xml")
                ?: zip.close().let { throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "Missing META-INF/container.xml") }
            val doc = Jsoup.parse(zip.getInputStream(container).bufferedReader().use { it.readText() })
            val rootfile = doc.select("rootfile[full-path]").firstOrNull()?.attr("full-path")
                ?: zip.close().let { throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "container.xml has no rootfile") }
            val opf = zip.getEntry(rootfile)?.let {
                Jsoup.parse(zip.getInputStream(it).bufferedReader().use { r -> r.readText() })
            } ?: zip.close().let { throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "OPF missing: $rootfile") }
            opf to zip
        } catch (e: DocumentOpenException) {
            throw e
        } catch (e: Exception) {
            AtlasLog.w("EpubEngine openPackage failed", e)
            throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, e.message.orEmpty(), e)
        }
    }

    private fun opfBaseDir(opf: Document): String {
        // best-effort: manifest hrefs resolve against the OPF's directory
        val firstHref = opf.select("manifest item").firstOrNull()?.attr("href") ?: return ""
        val lastSlash = firstHref.lastIndexOf('/')
        return if (lastSlash > 0) firstHref.substring(0, lastSlash) else ""
    }

    private fun normalizeEntryPath(baseDir: String, href: String): String? {
        val clean = href.trim().replace('\\', '/')
        if (clean.startsWith("/") || clean.contains("..")) return null
        return if (baseDir.isEmpty()) clean else "$baseDir/$clean"
    }

    private fun readEntryText(zip: ZipFile, entryPath: String, extractedDir: File): String? {
        // Prefer the extracted copy (same bytes, real file path for base URL).
        val extracted = File(extractedDir, entryPath)
        if (extracted.isFile) return extracted.readText()
        val entry = zip.getEntry(entryPath) ?: return null
        return zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }

    /** Extract the archive next to the epub (once). Thread-safe via a per-file lock. */
    private fun ensureExtracted(epub: File): File {
        val dir = File(epub.parentFile, "${epub.name}_extracted")
        if (dir.isDirectory && dir.listFiles()?.isNotEmpty() == true) return dir
        val lock = synchronized(extractionLocks) { extractionLocks.getOrPut(epub.absolutePath) { Object() } }
        synchronized(lock) {
            if (dir.isDirectory && dir.listFiles()?.isNotEmpty() == true) return dir
            dir.mkdirs()
            ZipFile(epub).use { zip ->
                for (entry in zip.entries()) {
                    val name = entry.name
                    if (name.startsWith("/") || name.split('/').any { it == ".." }) continue
                    if (entry.isDirectory) continue
                    val target = File(dir, name).normalize()
                    if (!target.path.startsWith(dir.path)) continue
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }
            }
            return dir
        }
    }

    private companion object {
        val COVER_FILENAME = Regex("(?i)^cover\\.(jpe?g|png)$")
    }
}
