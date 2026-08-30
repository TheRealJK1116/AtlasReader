package com.atlasreader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per document. The reading position is a *pointer into the parsed
 * document*: the chunk whose text we are in ([resourceToken]), the character
 * offset within it, and the scroll fraction for WebView restore. PDF uses
 * [pageIndex]. [percent] is the derived overall progress used for sorting and
 * "Continue reading". Session timestamps accumulate reading time for stats.
 */
@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["updatedAtMs"])],
)
data class ReadingProgressEntity(
    @PrimaryKey val documentId: Long,
    val resourceToken: String? = null,
    val charOffset: Int = 0,
    val pageIndex: Int = 0,
    val scrollFraction: Float = 0f,
    val percent: Float = 0f,
    val updatedAtMs: Long,
    val sessionStartMs: Long? = null,
    val sessionAccumMs: Long = 0L,
)

/** A saved position in the text, optionally annotated. */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["documentId"])],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val resourceToken: String,
    val charOffset: Int,
    val text: String,
    val createdAtMs: Long,
    val note: String? = null,
)

/** A coloured selection over [text] at [startOffset, endOffset) of a chunk. */
@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["documentId"])],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val resourceToken: String,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val colorHex: String,
    val note: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/** A note pinned to a chunk position; may be linked to a highlight. */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = HighlightEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedHighlightId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["documentId"]), Index(value = ["linkedHighlightId"])],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val resourceToken: String,
    val anchorOffset: Int,
    val text: String,
    val linkedHighlightId: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
