package com.atlasreader.core.indexer

import org.junit.Assert.assertEquals
import org.junit.Test

class TextTokenizerTest {

    @Test
    fun `tokenises with positions`() {
        val tokens = TextTokenizer.tokens("Hello, World! foo_bar")
        assertEquals(listOf("hello", "world", "foo", "bar"), tokens.map { it.term })
        assertEquals(0, tokens[0].position)
        assertEquals(7, tokens[1].position)
        assertEquals(14, tokens[2].position)
    }

    @Test
    fun `strips diacritics and case-folds`() {
        val tokens = TextTokenizer.tokens("Café déjà vu")
        assertEquals(listOf("cafe", "deja", "vu"), tokens.map { it.term })
    }

    @Test
    fun `handles unicode scripts without crashing`() {
        val tokens = TextTokenizer.tokens("Привет мир こんにちは")
        assertEquals(3, tokens.size)
    }

    @Test
    fun `query terms are normalised`() {
        assertEquals(listOf("cafe", "deja"), TextTokenizer.queryTerms("Café   déjà"))
        assertEquals(emptyList<String>(), TextTokenizer.queryTerms("   "))
    }
}
