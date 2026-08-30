package com.atlasreader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.atlasreader.core.database.dao.AnnotationDao
import com.atlasreader.core.database.dao.CollectionDao
import com.atlasreader.core.database.dao.CoverDao
import com.atlasreader.core.database.dao.DocumentDao
import com.atlasreader.core.database.dao.ProgressDao
import com.atlasreader.core.database.dao.SearchDao
import com.atlasreader.core.database.dao.TagDao
import com.atlasreader.core.database.dao.UserPreferencesDao
import com.atlasreader.core.database.entity.BookmarkEntity
import com.atlasreader.core.database.entity.ChunkPreviewEntity
import com.atlasreader.core.database.entity.CollectionDocumentCrossRef
import com.atlasreader.core.database.entity.CollectionEntity
import com.atlasreader.core.database.entity.CoverEntity
import com.atlasreader.core.database.entity.DocumentEntity
import com.atlasreader.core.database.entity.DocumentTagCrossRef
import com.atlasreader.core.database.entity.HighlightEntity
import com.atlasreader.core.database.entity.NoteEntity
import com.atlasreader.core.database.entity.ReadingProgressEntity
import com.atlasreader.core.database.entity.SearchHistoryEntity
import com.atlasreader.core.database.entity.TagEntity
import com.atlasreader.core.database.entity.UserPreferenceEntity

/**
 * Room schema v1. Tables:
 *
 *  - documents             the library (metadata + file pointers, content-addressed)
 *  - covers                cover image file pointers (bytes live on disk)
 *  - collections/tags + cross-refs   organisational layers (many-to-many)
 *  - reading_progress      per-document position + session time
 *  - bookmarks/highlights/notes      reader annotations
 *  - search_history        suggestion history
 *  - user_preferences      key/value store (library UI state, stats aggregates)
 *  - search_index          contentless FTS4 virtual table (created in [CALLBACK])
 *
 * Migration path: bump [VERSION], add a MIGRATION_1_2 object, register in
 * AtlasDatabaseModule. Schema snapshots are exported to app/schemas.
 */
@Database(
    entities = [
        DocumentEntity::class,
        CoverEntity::class,
        CollectionEntity::class,
        CollectionDocumentCrossRef::class,
        TagEntity::class,
        DocumentTagCrossRef::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        SearchHistoryEntity::class,
        ChunkPreviewEntity::class,
        UserPreferenceEntity::class,
    ],
    version = AtlasDatabase.VERSION,
    exportSchema = true,
)
abstract class AtlasDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun collectionDao(): CollectionDao
    abstract fun tagDao(): TagDao
    abstract fun coverDao(): CoverDao
    abstract fun progressDao(): ProgressDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun searchDao(): SearchDao
    abstract fun userPreferencesDao(): UserPreferencesDao

    companion object {
        const val NAME = "atlas_reader.db"
        const val VERSION = 1

        /** Contentless FTS4: rows are (documentId, chunkToken, position, term). */
        const val FTS_SCHEMA = (
            "CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts4(" +
                "documentId, chunkToken, position, term, " +
                "tokenize=unicode61 \"remove_diacritics 2\")"
            )

        val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(FTS_SCHEMA)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                // Room leaves FK enforcement off by default; we rely on CASCADE.
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}
