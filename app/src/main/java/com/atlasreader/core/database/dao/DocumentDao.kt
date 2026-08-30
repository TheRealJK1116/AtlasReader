package com.atlasreader.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.atlasreader.core.database.entity.DocumentEntity
import com.atlasreader.core.database.entity.DocumentRow
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE contentHash = :hash LIMIT 1")
    suspend fun byHash(hash: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<DocumentEntity>

    @Query("SELECT COUNT(*) FROM documents")
    fun observeCount(): Flow<Int>

    @Query("SELECT id FROM documents")
    suspend fun allIds(): List<Long>

    @Query("SELECT * FROM documents ORDER BY addedAtMs DESC LIMIT 1")
    suspend fun latestAdded(): DocumentEntity?

    @Query("UPDATE documents SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE documents SET openedAtMs = :openedAtMs WHERE id = :id")
    suspend fun touchOpen(id: Long, openedAtMs: Long)

    @Query("UPDATE documents SET lastPositionJson = :json, title = COALESCE(title, :titleFallback) WHERE id = :id")
    suspend fun updatePosition(id: Long, json: String?, titleFallback: String?)

    /**
     * Flattened library query (documents ⋈ covers ⋈ reading_progress). Room
     * appends LIMIT/OFFSET for paging. Build the SQL with [LibraryQueryBuilder].
     */
    @RawQuery(observedEntities = [DocumentEntity::class])
    fun observeLibrary(query: SupportSQLiteQuery): PagingSource<Int, DocumentRow>

    /** Title/author/file-name lookup for metadata search and suggestions. */
    @Query(
        """
        SELECT * FROM documents
        WHERE title LIKE '%' || :q || '%' OR author LIKE '%' || :q || '%' OR fileName LIKE '%' || :q || '%'
        ORDER BY openedAtMs DESC LIMIT :limit
        """
    )
    suspend fun searchMetadata(q: String, limit: Int): List<DocumentEntity>

    /** Continue-reading: in-progress documents, most recently active first. */
    @Query(
        """
        SELECT d.*, c.path AS coverPath,
               p.resourceToken AS progressResourceToken, p.charOffset AS progressCharOffset,
               p.pageIndex AS progressPageIndex, p.scrollFraction AS progressScrollFraction,
               p.percent AS progressPercent, p.updatedAtMs AS progressUpdatedAtMs,
               p.sessionAccumMs AS progressSessionAccumMs
        FROM documents d
        LEFT JOIN covers c ON c.documentId = d.id
        LEFT JOIN reading_progress p ON p.documentId = d.id
        WHERE p.percent IS NOT NULL AND p.percent > 0 AND p.percent < 99.5
        ORDER BY p.updatedAtMs DESC LIMIT :limit
        """
    )
    fun observeContinueReading(limit: Int): Flow<List<DocumentRow>>

    /** Recents: most recently opened documents (never-opened excluded). */
    @Query(
        """
        SELECT d.*, c.path AS coverPath,
               p.resourceToken AS progressResourceToken, p.charOffset AS progressCharOffset,
               p.pageIndex AS progressPageIndex, p.scrollFraction AS progressScrollFraction,
               p.percent AS progressPercent, p.updatedAtMs AS progressUpdatedAtMs,
               p.sessionAccumMs AS progressSessionAccumMs
        FROM documents d
        LEFT JOIN covers c ON c.documentId = d.id
        LEFT JOIN reading_progress p ON p.documentId = d.id
        WHERE d.openedAtMs IS NOT NULL
        ORDER BY d.openedAtMs DESC LIMIT :limit
        """
    )
    fun observeRecents(limit: Int): Flow<List<DocumentRow>>

    /** Reading statistics aggregate. */
    @Query(
        """
        SELECT COALESCE(SUM(percent >= 99.5), 0) AS finished,
               COALESCE(SUM(sessionAccumMs), 0) AS totalMs,
               COALESCE(SUM(percent >= 1.0), 0) AS started
        FROM documents d LEFT JOIN reading_progress p ON p.documentId = d.id
        """
    )
    fun observeReadingStats(): Flow<ReadingStatsRow>
}

data class ReadingStatsRow(
    val finished: Int,
    val totalMs: Long,
    val started: Int,
)
