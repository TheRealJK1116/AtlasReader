package com.atlasreader.core.indexer

import java.text.Normalizer

data class Token(val position: Int, val term: String)

/**
 * Unicode-aware tokenizer shared by the indexer and the search query builder.
 * Terms are NFKC-folded, lowercased and diacritic-stripped so "café" matches
 * "cafe". CJK segmentation is a known limitation (documented).
 */
object TextTokenizer {

    fun tokens(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c.isLetterOrDigit()) {
                val start = i
                while (i < n && text[i].isLetterOrDigit()) i++
                val term = normalizeTerm(text.substring(start, i))
                if (term.isNotEmpty()) tokens += Token(start, term)
            } else {
                i++
            }
        }
        return tokens
    }

    /** Normalise a single word (used for query terms too). */
    fun normalizeTerm(word: String): String {
        val nfkd = Normalizer.normalize(word, Normalizer.Form.NFKD)
        val sb = StringBuilder(nfkd.length)
        for (ch in nfkd) {
            if (ch.isLetterOrDigit()) sb.append(ch)
        }
        return sb.toString().lowercase()
    }

    /** Split a user query into normalised terms. */
    fun queryTerms(query: String): List<String> =
        query.split(Regex("\\s+")).map { normalizeTerm(it) }.filter { it.isNotEmpty() }
}
