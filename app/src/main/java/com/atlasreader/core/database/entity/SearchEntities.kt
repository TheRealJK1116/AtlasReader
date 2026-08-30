package com.atlasreader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Search suggestion history (user-typed queries). Kept small; pruned by the
 * search repository.
 */
@Entity(
    tableName = "search_history",
    indices = [Index(value = ["createdAtMs"])],
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val createdAtMs: Long,
)

/**
 * Key/value preferences persisted in SQLite so they participate in backups and
 * are available to workers. Runtime UI settings (theme etc.) live in DataStore;
 * this table holds library UI state (view mode, sort, filters) and reading
 * statistics aggregates.
 */
@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** One token of the full-text index (contentless FTS4 row). */
data class IndexToken(
    val documentId: Long,
    val chunkToken: String,
    val position: Int,
    val term: String,
)

/** Column projection of the flattened library list query (documents ⋈ covers ⋈ progress). */
data class DocumentRow(
    val id: Long,
    val contentHash: String,
    val fileName: String,
    val displayName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val format: String,
    val title: String?,
    val author: String?,
    val description: String?,
    val language: String?,
    val publisher: String?,
    val publishedDate: String?,
    val sourceUri: String?,
    val addedAtMs: Long,
    val openedAtMs: Long?,
    val favorite: Boolean,
    val lastPositionJson: String?,
    // covers ⋈
    val coverPath: String?,
    // reading_progress ⋈
    val progressResourceToken: String?,
    val progressCharOffset: Int?,
    val progressPageIndex: Int?,
    val progressScrollFraction: Float?,
    val progressPercent: Float?,
    val progressUpdatedAtMs: Long?,
    val progressSessionAccumMs: Long?,
)

/** Row returned by full-text search: a token hit inside a document chunk. */
data class SearchHitRow(
    val documentId: Long,
    val chunkToken: String,
    val position: Int,
    val term: String,
)

/**
 * Short plain-text prefix of each indexed chunk, used to build search result
 * snippets without re-parsing documents.
 */
@Entity(
    tableName = "chunk_previews",
    primaryKeys = ["documentId", "chunkToken"],
)
data class ChunkPreviewEntity(
    val documentId: Long,
    val chunkToken: String,
    val preview: String,
)
