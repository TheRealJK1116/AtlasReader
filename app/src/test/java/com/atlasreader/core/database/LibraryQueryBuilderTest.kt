package com.atlasreader.core.database

import com.atlasreader.core.engine.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQueryBuilderTest {

    private fun placeholderCount(sql: String): Int = sql.count { it == '?' }

    @Test
    fun `empty filter produces valid query`() {
        val built = LibraryQueryBuilder.build(LibraryFilter(), LibrarySort.TITLE)
        assertTrue(built.sql.contains("WHERE 1=1"))
        assertTrue(built.sql.contains("ORDER BY d.title IS NULL, d.title COLLATE NOCASE ASC"))
        assertEquals(0, built.args.size)
        assertEquals(placeholderCount(built.sql), built.args.size)
    }

    @Test
    fun `formats filter uses IN clause with one arg per format`() {
        val built = LibraryQueryBuilder.build(
            LibraryFilter(formats = setOf(DocumentFormat.EPUB, DocumentFormat.PDF)),
            LibrarySort.DATE_ADDED,
        )
        assertTrue(built.sql.contains("d.format IN (?,?)"))
        assertEquals(listOf("EPUB", "PDF"), built.args)
    }

    @Test
    fun `collection and tag filters use EXISTS`() {
        val built = LibraryQueryBuilder.build(
            LibraryFilter(collectionId = 7, tagId = 3),
            LibrarySort.DATE_ADDED,
        )
        assertTrue(built.sql.contains("EXISTS (SELECT 1 FROM collection_documents"))
        assertTrue(built.sql.contains("EXISTS (SELECT 1 FROM document_tags"))
        assertEquals(listOf<Any>(7, 3), built.args)
    }

    @Test
    fun `status filters map to progress ranges`() {
        val reading = LibraryQueryBuilder.build(
            LibraryFilter(status = ReadingStatusFilter.READING),
            LibrarySort.PROGRESS,
        )
        assertTrue(reading.sql.contains("p.percent > 0 AND p.percent < 99.5"))
        assertTrue(reading.sql.contains("ORDER BY COALESCE(p.percent, 0) DESC"))

        val finished = LibraryQueryBuilder.build(
            LibraryFilter(status = ReadingStatusFilter.FINISHED),
            LibrarySort.DATE_OPENED,
        )
        assertTrue(finished.sql.contains("p.percent >= 99.5"))
    }

    @Test
    fun `text query is escaped against LIKE injection`() {
        val built = LibraryQueryBuilder.build(LibraryFilter(query = "100%_wild\\card"), LibrarySort.TITLE)
        assertTrue(built.sql.contains("ESCAPE '\\'"))
        // The user's % and _ must be escaped, not treated as wildcards.
        assertEquals(3, built.args.size)
        assertTrue((built.args[0] as String).contains("100\\%\\_wild\\\\card"))
        assertEquals(placeholderCount(built.sql), built.args.size)
    }

    @Test
    fun `favorites filter adds column predicate`() {
        val built = LibraryQueryBuilder.build(LibraryFilter(favoritesOnly = true), LibrarySort.SIZE)
        assertTrue(built.sql.contains("d.favorite = 1"))
        assertTrue(built.sql.contains("ORDER BY d.fileSizeBytes DESC"))
    }
}
