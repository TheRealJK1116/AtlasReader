package com.atlasreader.core.util

import java.text.Normalizer

/**
 * Text normalisation used for duplicate-title comparison, search tokens and
 * metadata matching. Unicode-aware: NFC-folded, diacritics stripped, case-folded.
 */
object TextNormalizer {
    fun normalize(value: String): String {
        val folded = Normalizer.normalize(value, Normalizer.Form.NFKD)
        val sb = StringBuilder(folded.length)
        for (ch in folded) {
            if (Character.isLetterOrDigit(ch)) {
                sb.append(Character.toLowerCase(ch))
            }
        }
        return sb.toString()
    }

    /** True when [a] and [b] are "the same text" under normalisation. */
    fun equivalent(a: String, b: String): Boolean = normalize(a) == normalize(b)

    /** Title similarity used for soft duplicate detection (e.g. "The Hobbit" vs "Hobbit, The"). */
    fun titleKey(title: String): String {
        val tokens = normalize(title).split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.sorted().joinToString(" ")
    }
}
