package com.atlasreader.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlasreader.core.database.AtlasDatabase
import com.atlasreader.core.database.dao.DocumentDao
import com.atlasreader.core.database.entity.DocumentEntity
import com.atlasreader.core.database.entity.DocumentRow
import com.atlasreader.core.database.entity.IndexToken
import com.atlasreader.core.database.entity.SearchHitRow
import com.atlasreader.core.indexer.TextTokenizer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In-memory Room round-trip covering the FTS4 index (created via callback),
 * contentless-token insertion, MATCH querying through @RawQuery, and the
 * flattened library projection.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRoundTripTest {

    private lateinit var db: AtlasDatabase
    private lateinit var documentDao: DocumentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AtlasDatabase::class.java)
            .addCallback(AtlasDatabase.CALLBACK)
            .allowMainThreadQueries()
            .build()
        documentDao = db.documentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryDocument() = runBlocking {
        val id = documentDao.insert(
            DocumentEntity(
                contentHash = "abc123",
                fileName = "book.epub",
                displayName = "book.epub",
                filePath = "/tmp/book.epub",
                fileSizeBytes = 1024,
                format = "EPUB",
                title = "The Test Book",
                author = "Jane Doe",
                addedAtMs = 1L,
            )
        )
        assertTrue(id > 0)
        val loaded = documentDao.byId(id)
        assertEquals("The Test Book", loaded?.title)
        assertEquals(id, documentDao.byHash("abc123")?.id)
    }

    @Test
    fun fullTextIndexRoundTrip() = runBlocking {
        val id = documentDao.insert(
            DocumentEntity(
                contentHash = "hash1",
                fileName = "doc.md",
                displayName = "doc.md",
                filePath = "/tmp/doc.md",
                fileSizeBytes = 10,
                format = "MARKDOWN",
                title = "Sample",
                addedAtMs = 1L,
            )
        )

        // Insert tokens directly (mirrors SearchIndexer behaviour).
        val writable = db.openHelper.writableDatabase
        writable.beginTransaction()
        try {
            TextTokenizer.tokens("The quick brown fox jumps over the lazy dog")
                .forEach { token ->
                    writable.execSQL(
                        "INSERT INTO search_index (documentId, chunkToken, position, term) VALUES (?, ?, ?, ?)",
                        arrayOf(id.toString(), "0", token.position, token.term),
                    )
                }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }

        val hits: List<SearchHitRow> = db.searchDao().searchIndex(
            androidx.sqlite.db.SupportSQLiteQuery(
                "SELECT documentId, chunkToken, position, term FROM search_index WHERE search_index MATCH ?",
                arrayOf("\"fox\"*"),
            )
        )
        assertEquals(1, hits.size)
        assertEquals(id, hits[0].documentId)
        assertEquals("fox", hits[0].term)
    }

    @Test
    fun libraryProjectionIncludesProgressAndCover() = runBlocking {
        val id = documentDao.insert(
            DocumentEntity(
                contentHash = "h2",
                fileName = "a.txt",
                displayName = "a.txt",
                filePath = "/tmp/a.txt",
                fileSizeBytes = 5,
                format = "TXT",
                title = "Alpha",
                addedAtMs = 2L,
            )
        )
        db.progressDao().upsert(
            com.atlasreader.core.database.entity.ReadingProgressEntity(
                documentId = id,
                resourceToken = "0",
                scrollFraction = 0.5f,
                percent = 0.4f,
                updatedAtMs = 3L,
            )
        )

        val sql = com.atlasreader.core.database.LibraryQueryBuilder.build(
            com.atlasreader.core.database.LibraryFilter(),
            com.atlasreader.core.database.LibrarySort.PROGRESS,
        )
        // Verify the generated SQL executes against the real schema (the
        // PagingSource path is exercised via @RawQuery in the UI tests).
        val rawRows = db.openHelper.readableDatabase.rawQuery(
            sql.sql,
            sql.args.map { it.toString() }.toTypedArray(),
        )
        rawRows.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Alpha", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(0.4f, cursor.getFloat(cursor.getColumnIndexOrThrow("progressPercent")), 0.001f)
        }
    }
}
