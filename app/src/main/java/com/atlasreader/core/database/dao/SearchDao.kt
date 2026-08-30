package com.atlasreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.atlasreader.core.database.entity.ChunkPreviewEntity
import com.atlasreader.core.database.entity.SearchHistoryEntity
import com.atlasreader.core.database.entity.SearchHitRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {

    // ------------------------------------------------------------ history

    @Insert
    suspend fun insertHistory(entry: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY createdAtMs DESC LIMIT :limit")
    fun observeRecentHistory(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE id IN (SELECT id FROM search_history ORDER BY createdAtMs DESC LIMIT -1 OFFSET :keep)")
    suspend fun pruneHistory(keep: Int)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    // ------------------------------------------------------------ FTS index

    /** Match against the contentless FTS4 `search_index` virtual table. */
    @RawQuery
    suspend fun searchIndex(query: SupportSQLiteQuery): List<SearchHitRow>

    // ------------------------------------------------------------ previews

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreviews(previews: List<ChunkPreviewEntity>)

    @Query("SELECT * FROM chunk_previews WHERE documentId = :documentId AND chunkToken = :chunkToken LIMIT 1")
    suspend fun preview(documentId: Long, chunkToken: String): ChunkPreviewEntity?

    @Query("DELETE FROM chunk_previews WHERE documentId = :documentId")
    suspend fun deletePreviewsForDocument(documentId: Long)
}
