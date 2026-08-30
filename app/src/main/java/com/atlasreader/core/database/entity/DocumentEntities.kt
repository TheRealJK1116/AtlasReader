package com.atlasreader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A library document. `contentHash` is the SHA-256 of a file sample — the
 * unique duplicate-detection key (stable across renames/moves, unlike names).
 * `filePath` points at the imported private copy (SAF sources become stale,
 * hence copies). `openedAtMs` drives Recents; `favorite` is a cheap filter.
 */
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["addedAtMs"]),
        Index(value = ["openedAtMs"]),
        Index(value = ["title"]),
        Index(value = ["author"]),
        Index(value = ["favorite"]),
        Index(value = ["format"]),
    ],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentHash: String,
    val fileName: String,
    val displayName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val format: String,
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val sourceUri: String? = null,
    val addedAtMs: Long,
    val openedAtMs: Long? = null,
    val favorite: Boolean = false,
    val lastPositionJson: String? = null,
)

/**
 * Cover image stored as a file (covers/<contentHash>.jpg) — the database only
 * records where and how big. Keeps the documents table lean at 50k+ rows and
 * lets covers be regenerated or evicted without DB surgery.
 */
@Entity(
    tableName = "covers",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CoverEntity(
    @PrimaryKey val documentId: Long,
    val path: String,
    val width: Int = 0,
    val height: Int = 0,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long,
)

@Entity(
    tableName = "collection_documents",
    primaryKeys = ["collectionId", "documentId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class CollectionDocumentCrossRef(
    val collectionId: Long,
    val documentId: Long,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long,
)

@Entity(
    tableName = "document_tags",
    primaryKeys = ["documentId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class DocumentTagCrossRef(
    val documentId: Long,
    val tagId: Long,
)
