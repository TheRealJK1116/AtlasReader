package com.atlasreader.core.util

import java.security.MessageDigest

/**
 * Hashing utilities. SHA-256 over the first bytes of a file is used for
 * duplicate detection: content-addressing is stable across renames and moves,
 * which simple name comparisons are not.
 */
object FileHash {
    private const val SAMPLE_SIZE = 256L * 1024

    /** Hash a sample of the stream (head + tail) to bound I/O on very large files. */
    fun hashSample(bytes: ByteArray): String = sha256(bytes)

    fun hashFileBytes(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val head = ByteArray(SAMPLE_SIZE.toInt())
            val headRead = input.read(head)
            if (headRead > 0) digest.update(head, 0, headRead)
            // Sample the tail as well so truncated/identical-head files still differ.
            if (file.length() > SAMPLE_SIZE * 2) {
                input.skip(file.length() - SAMPLE_SIZE - headRead)
                val tail = ByteArray(SAMPLE_SIZE.toInt())
                val tailRead = input.read(tail)
                if (tailRead > 0) digest.update(tail, 0, tailRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
