package com.atlasreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atlasreader.core.database.entity.CoverEntity

@Dao
interface CoverDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cover: CoverEntity)

    @Query("SELECT * FROM covers WHERE documentId = :documentId LIMIT 1")
    suspend fun forDocument(documentId: Long): CoverEntity?

    @Query("DELETE FROM covers WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: Long)
}
