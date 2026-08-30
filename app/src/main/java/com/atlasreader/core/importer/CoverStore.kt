package com.atlasreader.core.importer

import android.content.Context
import com.atlasreader.core.database.dao.CoverDao
import com.atlasreader.core.database.entity.CoverEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cover images live as files (covers/<contentHash>.jpg) so the SQLite database
 * never carries bitmap bytes; this class owns both the disk layout and the
 * `covers` table rows.
 */
@Singleton
class CoverStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coverDao: CoverDao,
) {

    private val dir: File get() = File(context.filesDir, "covers").apply { mkdirs() }

    /** Persist bytes and upsert the row; returns the absolute path. */
    suspend fun saveAndRegister(documentId: Long, bytes: ByteArray, contentHash: String, nowMs: Long): String =
        withContext(Dispatchers.IO) {
            val file = File(dir, "$contentHash.jpg")
            file.writeBytes(bytes)
            val path = file.absolutePath
            coverDao.upsert(
                CoverEntity(
                    documentId = documentId,
                    path = path,
                    updatedAtMs = nowMs,
                )
            )
            path
        }

    suspend fun register(documentId: Long, path: String, nowMs: Long) =
        coverDao.upsert(CoverEntity(documentId = documentId, path = path, updatedAtMs = nowMs))

    /** Delete the row; the file is removed by [deleteFile] when no document needs it. */
    suspend fun unregister(documentId: Long) = coverDao.deleteForDocument(documentId)

    suspend fun forDocument(documentId: Long) = coverDao.forDocument(documentId)

    /** Best-effort file removal (only call when the cover is not shared). */
    fun deleteFile(path: String) {
        runCatching { File(path).delete() }
    }

    fun deleteOrphanFiles(keep: (String) -> Boolean) {
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (!keep(file.absolutePath)) file.delete()
            }
        }
    }

    fun listAll(): List<String> = dir.listFiles()?.map { it.absolutePath } ?: emptyList()
}
