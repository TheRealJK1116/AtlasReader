package com.atlasreader.core.importer

import android.content.Context
import android.net.Uri
import com.atlasreader.core.common.AtlasError
import com.atlasreader.core.util.FileHash
import com.atlasreader.core.util.FilenameUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a SAF uri into app-private storage, computing the content hash (head
 * + tail sample) *while copying* — one I/O pass for both the copy and the
 * duplicate-detection key. Files land in filesDir/imports/<hash8>/<name>.
 */
@Singleton
class FileCopier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class CopiedFile(val file: File, val contentHash: String, val sizeBytes: Long)

    suspend fun copyToLibrary(uri: Uri, displayName: String, maxBytes: Long = MAX_FILE_BYTES): CopiedFile =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val stream = resolver.openInputStream(uri)
                ?: throw ImportException(AtlasError.Io("Cannot open stream for $displayName"))

            stream.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val hashPrefix = FileHash.sha256(displayName.toByteArray()).take(8)
                val safeName = FilenameUtils.safeFileName(displayName)
                val dir = File(context.filesDir, "imports/$hashPrefix")
                dir.mkdirs()
                val target = uniqueFile(dir, safeName)

                var total = 0L
                val head = java.io.ByteArrayOutputStream(HEAD_SAMPLE)
                val tailRing = TailBuffer(TAIL_SAMPLE)
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        total += read
                        if (head.size() < HEAD_SAMPLE) {
                            head.write(buffer, 0, read.coerceAtMost(HEAD_SAMPLE - head.size()))
                        }
                        tailRing.write(buffer, 0, read)
                    }
                }
                if (total > maxBytes) {
                    target.delete()
                    throw ImportException(AtlasError.Import("File too large (max ${maxBytes / (1024 * 1024)} MB)"))
                }
                digest.update(head.toByteArray())
                tailRing.writeInto(digest)
                val contentHash = digest.digest().joinToString("") { "%02x".format(it) }

                CopiedFile(target, contentHash, total)
            }
        }

    /** Move the copied file to its final content-addressed home. */
    fun moveIntoPlace(copied: CopiedFile): File {
        val finalDir = File(context.filesDir, "imports/${copied.contentHash.take(8)}")
        if (finalDir == copied.file.parentFile) return copied.file
        finalDir.mkdirs()
        val finalFile = uniqueFile(finalDir, copied.file.name)
        if (copied.file.renameTo(finalFile)) return finalFile
        // rename failed (cross-dir): copy+delete
        copied.file.copyTo(finalFile, overwrite = true)
        copied.file.delete()
        return finalFile
    }

    fun deleteCopy(copied: CopiedFile) {
        copied.file.delete()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var i = 1
        while (candidate.exists()) {
            val base = name.substringBeforeLast('.')
            val ext = name.substringAfterLast('.', "")
            candidate = if (ext.isEmpty()) File(dir, "$base ($i)") else File(dir, "$base ($i).$ext")
            i++
        }
        return candidate
    }

    /**
     * Bounded head/tail sampling with a ring buffer: hash(head) + hash(tail)
     * catches both truncated and duplicated content cheaply.
     */
    private class TailBuffer(private val capacity: Int) {
        private val buffer = ByteArray(capacity)
        private var size = 0
        private var start = 0

        fun write(data: ByteArray, len: Int) {
            if (len >= capacity) {
                System.arraycopy(data, len - capacity, buffer, 0, capacity)
                start = 0
                size = capacity
                return
            }
            for (i in 0 until len) {
                buffer[(start + size) % capacity] = data[i]
                if (size < capacity) size++ else start = (start + 1) % capacity
            }
        }

        fun writeInto(digest: MessageDigest) {
            if (size == 0) return
            val contiguous = ByteArray(size)
            for (i in 0 until size) contiguous[i] = buffer[(start + i) % capacity]
            digest.update(contiguous)
        }
    }

    class ImportException(val error: AtlasError) : Exception(error.toString())

    private companion object {
        const val HEAD_SAMPLE = 256 * 1024
        const val TAIL_SAMPLE = 256 * 1024
        const val MAX_FILE_BYTES = 200L * 1024 * 1024
    }
}
