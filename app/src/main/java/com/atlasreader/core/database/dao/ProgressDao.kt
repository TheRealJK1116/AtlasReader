package com.atlasreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.atlasreader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)

    @Update
    suspend fun update(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE documentId = :documentId LIMIT 1")
    suspend fun forDocument(documentId: Long): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE documentId = :documentId LIMIT 1")
    fun observeForDocument(documentId: Long): Flow<ReadingProgressEntity?>

    @Query("DELETE FROM reading_progress WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: Long)

    /** Session bookkeeping for reading statistics (adds elapsed ms). */
    @Query("UPDATE reading_progress SET sessionAccumMs = sessionAccumMs + :deltaMs WHERE documentId = :documentId")
    suspend fun accumulateSession(documentId: Long, deltaMs: Long)
}
