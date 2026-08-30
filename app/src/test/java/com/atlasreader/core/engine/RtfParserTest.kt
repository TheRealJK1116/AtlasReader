package com.atlasreader.core.engine

import com.atlasreader.core.engine.engines.RtfParser
import com.atlasreader.core.engine.engines.RtfToHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RtfParserTest {

    private val sample = """
        {\rtf1\ansi\deff0 {\fonttbl {\f0 Times New Roman;}}
        {\colortbl ;\red255\green0\blue0;\red0\green0\blue255;}
        {\info {\title A Test Book}{\author Jane Doe}}
        Hello \b world\b0. This is \i italic\i0 text with \cf1 red\cf0 color.
        \par
        Second paragraph: caf\'e9 with \u233? unicode, \emdash dashes \u8211? en dash.
        }
    """.trimIndent()

    @Test
    fun `parses metadata and skips font and info tables`() {
        val doc = RtfParser.parse(sample.toByteArray())
        assertEquals("A Test Book", doc.title)
        assertEquals("Jane Doe", doc.author)
        assertEquals(2, doc.colorTable.size)
        assertEquals("#ff0000", doc.colorTable[1])
    }

    @Test
    fun `parses text into styled paragraphs`() {
        val doc = RtfParser.parse(sample.toByteArray())
        assertEquals(2, doc.blocks.size)

        val first = doc.blocks[0]
        assertEquals("Hello world. This is italic text with red color.", first.text)

        val boldSpan = first.spans.first { it.style.bold }
        assertEquals("world", first.text.substring(boldSpan.start, boldSpan.end))

        val italicSpan = first.spans.first { it.style.italic }
        assertEquals("italic", first.text.substring(italicSpan.start, italicSpan.end))

        val colorSpan = first.spans.first { it.style.colorIndex == 1 }
        assertEquals("red", first.text.substring(colorSpan.start, colorSpan.end))
    }

    @Test
    fun `handles unicode escapes and hex escapes`() {
        val doc = RtfParser.parse(sample.toByteArray())
        val second = doc.blocks[1]
        assertTrue(second.text.contains("café"))
        assertTrue(second.text.contains("café".substring(0, 3)))
        assertTrue(second.text.contains("\u2014")) // em dash
        assertTrue(second.text.contains("\u2013")) // en dash from \u8211
    }

    @Test
    fun `html renderer escapes text and preserves styles`() {
        val doc = RtfParser.parse(sample.toByteArray())
        val html = RtfToHtml.render(doc)
        assertTrue(html.contains("<b>"))
        assertTrue(html.contains("&amp;") == false) // no raw ampersand in text here
        assertTrue(html.startsWith("<div class=\"prose\">"))
        assertTrue(html.endsWith("</div>"))
    }

    @Test
    fun `malformed input does not throw`() {
        val doc = RtfParser.parse("{\\rtf1 broken\\par\\par\\par".toByteArray())
        // Parser must survive unbalanced braces without exceptions.
        assertTrue(doc.blocks.isNotEmpty() || doc.text.isEmpty())
    }

    @Test
    fun `empty input yields empty document`() {
        val doc = RtfParser.parse(ByteArray(0))
        assertEquals("", doc.text)
    }
}
