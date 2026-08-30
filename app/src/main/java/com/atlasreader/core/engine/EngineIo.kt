package com.atlasreader.core.engine

import android.content.Context
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * I/O helpers shared by engines: prefer the imported local file (fast path,
 * testable on the JVM with `context = null`), fall back to streaming the SAF
 * uri when a context is available.
 */
internal object EngineIo {

    fun open(context: Context?, source: DocumentSource): InputStream =
        source.file?.inputStream()
            ?: context?.contentResolver?.openInputStream(source.uri)
            ?: throw DocumentOpenException(
                source.extension?.let { DocumentFormat.fromExtension(it) } ?: DocumentFormat.TXT,
                DocumentOpenException.Reason.CORRUPT,
                "Cannot open input stream for ${source.displayName}"
            )

    fun readBytes(context: Context?, source: DocumentSource, maxBytes: Long = Long.MAX_VALUE): ByteArray =
        open(context, source).use { input ->
            val buffer = input.readBytes()
            if (buffer.size.toLong() > maxBytes) {
                throw DocumentOpenException(
                    DocumentFormat.TXT, DocumentOpenException.Reason.TOO_LARGE,
                    "File exceeds size limit ${maxBytes}"
                )
            }
            buffer
        }

    /**
     * Read text with charset fallback: UTF-8 first, then windows-1252 (very
     * common for legacy .txt/.rtf), always replacing malformed bytes.
     */
    fun readText(context: Context?, source: DocumentSource, maxBytes: Long = 64L * 1024 * 1024): String {
        val bytes = readBytes(context, source, maxBytes)
        return decodeText(bytes)
    }

    fun decodeText(bytes: ByteArray): String {
        val utf8 = tryDecode(bytes, StandardCharsets.UTF_8)
        if (bytes.isEmpty() || utf8.indexOf('\uFFFD') < 0) return utf8
        // Contains replacement chars: try windows-1252 before accepting garbage.
        return tryDecode(bytes, Charset.forName("windows-1252"))
    }

    private fun tryDecode(bytes: ByteArray, charset: Charset): String =
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
}
