package com.atlasreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atlasreader.core.database.entity.CollectionDocumentCrossRef
import com.atlasreader.core.database.entity.CollectionEntity
import com.atlasreader.core.database.entity.DocumentRow
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: CollectionEntity): Long

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query(
        """
        SELECT c.*,
               (SELECT COUNT(*) FROM collection_documents cd WHERE cd.collectionId = c.id) AS documentCount
        FROM collections c ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeWithCounts(): Flow<List<CollectionWithCount>>

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addDocument(ref: CollectionDocumentCrossRef)

    @Query("DELETE FROM collection_documents WHERE collectionId = :collectionId AND documentId = :documentId")
    suspend fun removeDocument(collectionId: Long, documentId: Long)

    @Query("DELETE FROM collection_documents WHERE documentId = :documentId")
    suspend fun removeAllForDocument(documentId: Long)

    @Query("SELECT collectionId FROM collection_documents WHERE documentId = :documentId")
    suspend fun collectionIdsForDocument(documentId: Long): List<Long>

    @Query(
        """
        SELECT d.*, c.path AS coverPath,
               p.resourceToken AS progressResourceToken, p.charOffset AS progressCharOffset,
               p.pageIndex AS progressPageIndex, p.scrollFraction AS progressScrollFraction,
               p.percent AS progressPercent, p.updatedAtMs AS progressUpdatedAtMs,
               p.sessionAccumMs AS progressSessionAccumMs
        FROM collection_documents cd
        JOIN documents d ON d.id = cd.documentId
        LEFT JOIN covers c ON c.documentId = d.id
        LEFT JOIN reading_progress p ON p.documentId = d.id
        WHERE cd.collectionId = :collectionId
        ORDER BY d.title IS NULL, d.title COLLATE NOCASE
        """
    )
    fun observeDocumentsIn(collectionId: Long): Flow<List<DocumentRow>>
}

data class CollectionWithCount(
    val id: Long,
    val name: String,
    val createdAtMs: Long,
    val documentCount: Int,
)
