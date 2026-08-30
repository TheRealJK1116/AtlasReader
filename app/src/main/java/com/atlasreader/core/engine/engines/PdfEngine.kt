package com.atlasreader.core.engine.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.atlasreader.core.common.AtlasLog
import com.atlasreader.core.engine.DocumentEngine
import com.atlasreader.core.engine.DocumentFormat
import com.atlasreader.core.engine.DocumentOpenException
import com.atlasreader.core.engine.DocumentSource
import com.atlasreader.core.engine.ExtractedMetadata
import com.atlasreader.core.engine.PageProvider
import com.atlasreader.core.engine.ParsedDocument
import com.atlasreader.core.util.FilenameUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDF engine using the platform [PdfRenderer]. Fixed-layout: the document is a
 * sequence of bitmaps rendered on demand at display resolution, cached by the
 * pager. Metadata comes from PdfDocument.Info.
 *
 * Note: PdfRenderer does not expose a text layer, so PDF full-text search is
 * not available in v1 (roadmap: text-layer extraction with PdfBox/PDF.js).
 */
@Singleton
class PdfEngine @Inject constructor() : DocumentEngine {

    override val format: DocumentFormat = DocumentFormat.PDF

    override suspend fun extractMetadata(context: Context?, source: DocumentSource): ExtractedMetadata {
        val descriptor = openDescriptor(source)
        try {
            descriptor.use { pfd ->
                val info = PdfDocument(pfd).use { it.info }
                return ExtractedMetadata(
                    title = info.title?.trim()?.takeIf { it.isNotEmpty() }
                        ?: FilenameUtils.stripExtension(source.displayName).trim(),
                    author = info.author?.trim()?.takeIf { it.isNotEmpty() },
                    description = info.subject?.trim()?.takeIf { it.isNotEmpty() },
                    publishedDate = info.creationDate?.takeIf { it.isNotEmpty() },
                )
            }
        } catch (e: Exception) {
            throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, e.message.orEmpty(), e)
        }
    }

    override suspend fun extractCover(context: Context?, source: DocumentSource): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val descriptor = openDescriptor(source)
                descriptor.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount == 0) return@use null
                        renderer.openPage(0).use { page ->
                            val scale = COVER_WIDTH_PX.toFloat() / page.width.toFloat()
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val out = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            bitmap.recycle()
                            out.toByteArray()
                        }
                    }
                }
            } catch (e: Exception) {
                AtlasLog.w("PdfEngine cover extraction failed", e)
                null
            }
        }

    override suspend fun parse(context: Context?, source: DocumentSource): ParsedDocument {
        val metadata = extractMetadata(context, source)
        return ParsedDocument(
            format = format,
            metadata = metadata,
            tableOfContents = emptyList(),
            chunks = emptyList(),
            pageProvider = PdfPageProvider(source, format),
        )
    }

    private fun openDescriptor(source: DocumentSource): ParcelFileDescriptor {
        val file = source.file
            ?: throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "PDF requires a local file")
        if (!file.exists() || file.length() == 0L) {
            throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "PDF file missing or empty")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private companion object {
        const val COVER_WIDTH_PX = 480
    }
}

/**
 * Lazy, mutex-guarded [PdfRenderer] facade. A single renderer instance is
 * reused across pager requests; [close] must be called when the reader closes
 * (leaks the fd + native memory otherwise).
 */
class PdfPageProvider(
    private val source: DocumentSource,
    private val format: DocumentFormat,
) : PageProvider {

    private val mutex = Mutex()
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null

    override suspend fun pageCount(): Int = mutex.withLock {
        (renderer ?: openRenderer()).pageCount
    }

    private suspend fun openRenderer(): PdfRenderer {
        val file = source.file
            ?: throw DocumentOpenException(format, DocumentOpenException.Reason.CORRUPT, "PDF requires a local file")
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor = pfd
        return PdfRenderer(pfd).also { renderer = it }
    }

    override suspend fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int, density: Float): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val r = renderer ?: openRenderer()
                    if (pageIndex !in 0 until r.pageCount) return@withContext null
                    r.openPage(pageIndex).use { page ->
                        val scale = widthPx.toFloat() / page.width.toFloat()
                        val w = (page.width * scale).toInt().coerceIn(1, MAX_BITMAP_DIMENSION)
                        val h = (page.height * scale).toInt().coerceIn(1, MAX_BITMAP_DIMENSION)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            } catch (e: Exception) {
                AtlasLog.e("PdfPageProvider render failed (page $pageIndex)", e)
                null
            }
        }

    suspend fun close() {
        mutex.withLock {
            try { renderer?.close() } catch (_: Exception) {}
            try { descriptor?.close() } catch (_: Exception) {}
            renderer = null
            descriptor = null
        }
    }

    private companion object {
        const val MAX_BITMAP_DIMENSION = 4096
    }
}
