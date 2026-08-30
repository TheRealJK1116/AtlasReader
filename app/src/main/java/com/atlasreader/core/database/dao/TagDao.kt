package com.atlasreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atlasreader.core.database.entity.DocumentTagCrossRef
import com.atlasreader.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT id FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun idByName(name: String): Long?

    /** Get-or-create a tag by name (normalised lowercase). */
    suspend fun ensure(name: String, nowMs: Long): Long {
        idByName(name)?.let { return it }
        return insert(TagEntity(name = name, createdAtMs = nowMs))
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToDocument(ref: DocumentTagCrossRef)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId AND tagId = :tagId")
    suspend fun removeFromDocument(documentId: Long, tagId: Long)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId")
    suspend fun removeAllForDocument(documentId: Long)

    @Query(
        """
        SELECT t.name FROM tags t JOIN document_tags dt ON dt.tagId = t.id
        WHERE dt.documentId = :documentId ORDER BY t.name COLLATE NOCASE
        """
    )
    suspend fun namesForDocument(documentId: Long): List<String>

    @Query("SELECT COUNT(*) FROM document_tags WHERE tagId = :tagId")
    suspend fun countDocuments(tagId: Long): Int

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)
}
