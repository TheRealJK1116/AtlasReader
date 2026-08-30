package com.atlasreader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.atlasreader.core.database.entity.BookmarkEntity
import com.atlasreader.core.database.entity.HighlightEntity
import com.atlasreader.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {

    // ------------------------------------------------------------ bookmarks

    @Insert
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY createdAtMs DESC")
    fun observeBookmarks(documentId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId AND resourceToken = :token ORDER BY charOffset")
    suspend fun bookmarksInChunk(documentId: Long, token: String): List<BookmarkEntity>

    // ------------------------------------------------------------ highlights

    @Insert
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Update
    suspend fun updateHighlight(highlight: HighlightEntity)

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)

    @Query("SELECT * FROM highlights WHERE documentId = :documentId ORDER BY createdAtMs DESC")
    fun observeHighlights(documentId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE documentId = :documentId AND resourceToken = :token ORDER BY startOffset")
    suspend fun highlightsInChunk(documentId: Long, token: String): List<HighlightEntity>

    @Query("UPDATE highlights SET colorHex = :colorHex, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun recolorHighlight(id: Long, colorHex: String, nowMs: Long)

    @Query("UPDATE highlights SET note = :note, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun setHighlightNote(id: Long, note: String?, nowMs: Long)

    // ---------------------------------------------------------------- notes

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE documentId = :documentId ORDER BY createdAtMs DESC")
    fun observeNotes(documentId: Long): Flow<List<NoteEntity>>

    // ------------------------------------------------------------- cleanup

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId")
    suspend fun deleteBookmarksForDocument(documentId: Long)

    @Query("DELETE FROM highlights WHERE documentId = :documentId")
    suspend fun deleteHighlightsForDocument(documentId: Long)

    @Query("DELETE FROM notes WHERE documentId = :documentId")
    suspend fun deleteNotesForDocument(documentId: Long)
}
