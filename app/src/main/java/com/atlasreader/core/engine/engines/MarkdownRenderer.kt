package com.atlasreader.core.engine.engines

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser

/**
 * Markdown → HTML using flexmark (core + tables + strikethrough). The renderer
 * is a pure JVM function, so it is unit-testable without Android.
 */
object MarkdownRenderer {
    private val options = Parser.builder()
        .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create()))
        .build()

    private val renderer = HtmlRenderer.builder().build()

    fun toHtml(markdown: String): String = renderer.render(options.parse(markdown))
}
