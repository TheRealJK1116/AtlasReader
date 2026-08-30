package com.atlasreader.core.engine

import com.atlasreader.core.engine.engines.MarkdownEngine
import com.atlasreader.core.engine.engines.TextEngine
import com.atlasreader.core.util.FileHash
import com.atlasreader.core.util.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineUnitTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun source(text: String, name: String = "test.md"): DocumentSource {
        val file = temp.newFile(name)
        file.writeText(text)
        return DocumentSource(displayName = name, localPath = file.absolutePath)
    }

    // ----------------------------------------------------------- markdown

    @Test
    fun `markdown splits chunks on headings and builds toc`() {
        val md = """
            # My Great Book

            Introduction paragraph.

            ## Chapter One

            Some content with *emphasis* and a [link](https://example.com).

            ## Chapter Two

            More content.
        """.trimIndent()

        val parsed = MarkdownEngine().parse(null, source(md))

        assertEquals(3, parsed.chunks.size)
        assertEquals("My Great Book", parsed.chunks[0].heading)
        assertEquals("Chapter One", parsed.chunks[1].heading)
        assertEquals("Chapter Two", parsed.chunks[2].heading)

        assertEquals(3, parsed.tableOfContents.size)
        assertEquals(1, parsed.tableOfContents[0].level)
        assertEquals(2, parsed.tableOfContents[1].level)

        val html = parsed.chunks[1].html.orEmpty()
        assertTrue(html.contains("<h2>"))
        assertTrue(html.contains("<em>"))
    }

    @Test
    fun `markdown metadata uses first heading without full parse`() {
        val parsed = MarkdownEngine().extractMetadata(null, source("# The Title\n\nBody."))
        assertEquals("The Title", parsed.title)
    }

    @Test
    fun `markdown falls back to filename title`() {
        val parsed = MarkdownEngine().extractMetadata(null, source("Just text, no headings.", "notes.md"))
        assertEquals("notes", parsed.title)
    }

    // ------------------------------------------------------------ plain text

    @Test
    fun `text engine chunks at chapter headings`() {
        val text = "Chapter 1\n\nThe quick brown fox.\n\nChapter 2\n\nThe lazy dog."
        val parsed = TextEngine().parse(null, source(text, "book.txt"))

        assertEquals(2, parsed.chunks.size)
        assertEquals("Chapter 1", parsed.chunks[0].heading)
        assertTrue(parsed.chunks[0].text.contains("quick brown fox"))
        assertEquals(1, parsed.chunks[1].index)
        assertEquals(2, parsed.tableOfContents.size)
    }

    @Test
    fun `text engine escapes html and preserves blank lines`() {
        val text = "Line one\n\nLine with <tag> & 'quotes'\n\nLine three"
        val parsed = TextEngine().parse(null, source(text, "a.txt"))
        val html = parsed.chunks.single().html.orEmpty()
        assertTrue(html.contains("&lt;tag&gt;"))
        assertTrue(html.contains("&amp;"))
        assertTrue(!html.contains("<tag>"))
        assertTrue(parsed.chunks.single().text.contains("<tag> & 'quotes'"))
    }

    @Test
    fun `text engine splits large files into bounded chunks`() {
        val big = buildString {
            repeat(60) { i ->
                append("Paragraph $i: ")
                append("word ".repeat(40))
                append("\n\n")
            }
        }
        val parsed = TextEngine().parse(null, source(big, "big.txt"))
        assertTrue("expected multiple chunks, got ${parsed.chunks.size}", parsed.chunks.size > 3)
        assertTrue(parsed.chunks.all { it.text.length <= 4200 })
    }

    // ----------------------------------------------------------------- hash

    @Test
    fun `content hash is deterministic and sample-based`() {
        val file = temp.newFile("data.bin")
        val payload = ByteArray(1024 * 1024) { (it % 251).toByte() }
        file.writeBytes(payload)
        val first = FileHash.hashFileBytes(file)
        val second = FileHash.hashFileBytes(file)
        assertEquals(first, second)

        val other = temp.newFile("data2.bin")
        other.writeBytes(ByteArray(1024 * 1024) { (it % 250).toByte() })
        assertTrue(first != FileHash.hashFileBytes(other))
    }

    // ------------------------------------------------------------ normalize

    @Test
    fun `normalizer folds diacritics and case`() {
        assertTrue(TextNormalizer.equivalent("Café", "cafe"))
        assertTrue(TextNormalizer.equivalent("NAÏVE", "naive"))
        assertEquals("helloworld", TextNormalizer.normalize("Hello, World!"))
    }

    @Test
    fun `title key is order-insensitive`() {
        assertEquals(
            TextNormalizer.titleKey("The Hobbit"),
            TextNormalizer.titleKey("Hobbit, The"),
        )
    }
}
