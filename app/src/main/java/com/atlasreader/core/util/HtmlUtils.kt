package com.atlasreader.core.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTML helpers used by the engine layer. Jsoup is used in limited, safe modes
 * (no remote resources are ever fetched — parsing is offline only).
 */
object HtmlUtils {

    private val BLOCK_TAGS = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "ul", "ol",
        "blockquote", "pre", "table", "tr", "section", "article", "header",
        "footer", "aside", "nav", "figure", "figcaption", "br", "hr", "dt", "dd", "dl"
    )

    /**
     * Extracts readable plain text from an HTML fragment, inserting newlines at
     * block boundaries so paragraphs survive (textContent alone collapses them).
     */
    fun textFromHtml(html: String): String {
        val doc: Document = Jsoup.parse(html)
        val sb = StringBuilder()
        appendText(doc.body(), sb)
        // Collapse 3+ newlines to at most two blank lines, trim per line.
        return sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun appendText(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> sb.append(node.text())
            is Element -> {
                val tag = node.tagName().lowercase()
                val block = tag in BLOCK_TAGS
                if (block && sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                for (child in node.childNodes()) appendText(child, sb)
                if (block) {
                    while (sb.isNotEmpty() && sb.last() == ' ') sb.deleteCharAt(sb.length - 1)
                    if (sb.isEmpty() || sb.last() != '\n') sb.append('\n')
                }
            }
            else -> for (child in node.childNodes()) appendText(child, sb)
        }
    }

    /** Extracts the <title> of an HTML document, trimmed. */
    fun extractTitle(html: String): String? =
        Jsoup.parse(html).title().trim().takeIf { it.isNotEmpty() }

    /** Removes <script>/<style> and any on* attributes — basic hygiene before display. */
    fun sanitizeForDisplay(html: String): String {
        val doc = Jsoup.parseBodyFragment(html)
        doc.select("script, style, iframe, object, embed, form, input, button, link, meta").remove()
        doc.select("*").forEach { el -> el.attributes().asList().toList().forEach { attr ->
            if (attr.key.startsWith("on")) el.removeAttr(attr.key)
        } }
        return doc.body().html()
    }
}
