package com.atlasreader.core.engine.engines

import java.nio.charset.Charset

/**
 * Hand-rolled RTF parser — deliberately dependency-free so it can be unit
 * tested on the JVM. Handles the control words Atlas Reader needs to render:
 *
 *  - groups `{ }` with scoped formatting state
 *  - `\b \i \ul \cfN \highlightN \fsN` character formatting
 *  - `\par \page \line \tab` layout controls
 *  - `\'hh` cp1252 escapes, signed `\uN` unicode with fallback-char skipping
 *  - skipped destinations: `fonttbl, stylesheet, info, pict, object, header,
 *    footer, footnote, listtable, listoverridetable, generator, \* groups`
 *  - captured destinations: `title`, `author` (for metadata)
 *  - the `colortbl` color table (for `\cfN` rendering)
 *
 * Output is a flat list of styled paragraphs; an HTML renderer is included.
 */
class RtfParser private constructor(private val text: String) {

    private var pos = 0
    private var style = Style()
    private val stack = ArrayDeque<Style>()
    private var skipNextGroup = false
    private val colorTable = mutableListOf<String>()
    private var title = ""
    private var author = ""
    private var parsed = false

    private lateinit var result: RtfDocument

    fun parse(): RtfDocument {
        if (!parsed) {
            val sink = Sink()
            parseContent(sink, emit = true)
            sink.paragraphBreak(force = true)
            result = RtfDocument(
                blocks = sink.blocks,
                colorTable = colorTable,
                title = title.trim(),
                author = author.trim(),
            )
            parsed = true
        }
        return result
    }

    // ------------------------------------------------------------------ tokens

    private fun parseContent(sink: Sink?, emit: Boolean) {
        while (pos < text.length) {
            when (val ch = text[pos]) {
                '\\' -> { pos++; parseControl(sink, emit) }
                '{' -> {
                    pos++
                    val dest = peekDestination()
                    when {
                        skipNextGroup -> { skipNextGroup = false; parseContent(null, emit = false) }
                        dest == "*" -> parseContent(null, emit = false)
                        dest == "colortbl" -> parseColorTable()
                        dest == "title" -> { val s = Sink(); parseContent(s, emit = true); title = s.plainText() }
                        dest == "author" -> { val s = Sink(); parseContent(s, emit = true); author = s.plainText() }
                        dest != null && dest in SKIP_DESTINATIONS -> parseContent(null, emit = false)
                        else -> {
                            stack.addLast(style.copy())
                            parseContent(sink, emit)
                        }
                    }
                }
                '}' -> { pos++; if (stack.isNotEmpty()) style = stack.removeLast(); return }
                '\r', '\n' -> pos++
                else -> {
                    pos++
                    if (emit) sink?.append(ch, style)
                }
            }
        }
    }

    private fun parseControl(sink: Sink?, emit: Boolean) {
        if (pos >= text.length) return
        val ch = text[pos]

        // \'hh hex escape (cp1252)
        if (ch == '\'') {
            if (pos + 2 < text.length) {
                val hex = text.substring(pos + 1, pos + 3)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    val decoded = String(byteArrayOf(code.toByte()), CP1252)
                    if (emit && sink != null) sink.append(decoded.first(), style)
                }
                pos += 3
                return
            }
            pos++
            return
        }

        // control symbol (single non-letter char)
        if (!ch.isLetter()) {
            pos++
            when (ch) {
                '\\', '{', '}' -> if (emit) sink?.append(ch, style)
                '~' -> if (emit) sink?.append('\u00A0', style)
                '-' -> if (emit) sink?.append('-', style)
                '_' -> if (emit) sink?.append('\u2011', style)
                '*' -> skipNextGroup = true
            }
            return
        }

        // control word: letters, optional signed number, optional space delimiter
        val start = pos
        while (pos < text.length && text[pos].isLetter()) pos++
        val word = text.substring(start, pos)

        var param = 0
        var hasParam = false
        if (pos < text.length && (text[pos] == '-' || text[pos].isDigit())) {
            var sign = 1
            if (text[pos] == '-') { sign = -1; pos++ }
            val numStart = pos
            while (pos < text.length && text[pos].isDigit()) pos++
            if (pos > numStart) {
                param = text.substring(numStart, pos).toIntOrNull()?.times(sign) ?: 0
                hasParam = true
            }
        }
        if (pos < text.length && text[pos] == ' ') pos++ // consume delimiter

        apply(word, param, hasParam, sink, emit)
    }

    private fun apply(word: String, param: Int, hasParam: Boolean, sink: Sink?, emit: Boolean) {
        when (word) {
            "b" -> style.bold = !hasParam || param != 0
            "b0" -> style.bold = false
            "i" -> style.italic = !hasParam || param != 0
            "i0" -> style.italic = false
            "ul", "uld" -> style.underline = true
            "ul0", "ulnone" -> style.underline = false
            "cf" -> if (hasParam) style.colorIndex = param
            "highlight" -> if (hasParam) style.highlightIndex = param
            "fs" -> if (hasParam && param >= 8) style.fontSizeHalfPoints = param
            "plain" -> style = Style()
            "pard" -> { /* paragraph defaults: nothing to reset in our model */ }
            "par", "page", "sect" -> { if (emit) sink?.paragraphBreak() }
            "line" -> { if (emit) sink?.append('\n', style) }
            "tab" -> { if (emit) sink?.append('\t', style) }
            "u" -> {
                if (hasParam) {
                    val code = if (param < 0) param + 0x10000 else param
                    if (code in 0..0x10FFFF) {
                        if (emit) sink?.append(code.toChar(), style)
                        // Skip the ANSI fallback character that follows \uN.
                        if (pos < text.length) {
                            val fallback = text[pos]
                            if (fallback != '\\' && fallback != '{' && fallback != '}' && fallback.code in 32..126) pos++
                        }
                    }
                }
            }
            "emdash" -> if (emit) sink?.append('\u2014', style)
            "endash" -> if (emit) sink?.append('\u2013', style)
            "lquote" -> if (emit) sink?.append('\u2018', style)
            "rquote" -> if (emit) sink?.append('\u2019', style)
            "ldblquote" -> if (emit) sink?.append('\u201C', style)
            "rdblquote" -> if (emit) sink?.append('\u201D', style)
            else -> { /* unknown control words are ignored */ }
        }
    }

    /** Looks at the first control word of a group (after optional `\*`) without consuming. */
    private fun peekDestination(): String? {
        val saved = pos
        try {
            while (pos < text.length && (text[pos] == ' ' || text[pos] == '\n' || text[pos] == '\r')) pos++
            if (pos >= text.length || text[pos] != '\\') return null
            pos++
            if (pos < text.length && text[pos] == '*') return "*"
            if (pos >= text.length || !text[pos].isLetter()) return null
            val start = pos
            while (pos < text.length && text[pos].isLetter()) pos++
            return text.substring(start, pos)
        } finally {
            pos = saved
        }
    }

    private fun parseColorTable() {
        var red = -1; var green = -1; var blue = -1
        while (pos < text.length) {
            when (text[pos]) {
                '}' -> { pos++; break }
                '\\' -> {
                    pos++
                    val start = pos
                    while (pos < text.length && text[pos].isLetter()) pos++
                    val word = text.substring(start, pos)
                    if (pos < text.length && text[pos] == ' ') pos++
                    val numStart = pos
                    while (pos < text.length && text[pos].isDigit()) pos++
                    val value = if (pos > numStart) text.substring(numStart, pos).toIntOrNull() ?: 0 else 0
                    when (word) {
                        "red" -> red = value
                        "green" -> green = value
                        "blue" -> blue = value
                    }
                }
                ';' -> {
                    if (red >= 0 && green >= 0 && blue >= 0) {
                        colorTable += "#%02x%02x%02x".format(red, green, blue)
                    }
                    red = -1; green = -1; blue = -1
                    pos++
                }
                '{' -> pos++
                else -> pos++
            }
        }
    }

    // ------------------------------------------------------------------ model

    data class Style(
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var colorIndex: Int = 0,
        var highlightIndex: Int = 0,
        var fontSizeHalfPoints: Int = 24,
    )

    data class RtfSpan(
        val start: Int,
        val end: Int,
        val style: Style,
    )

    data class RtfBlock(
        val text: String,
        val spans: List<RtfSpan>,
    )

    data class RtfDocument(
        val blocks: List<RtfBlock>,
        val colorTable: List<String>,
        val title: String,
        val author: String,
    ) {
        val text: String get() = blocks.joinToString("\n") { it.text }
    }

    /** Collects text + style-change markers; turns them into blocks with spans. */
    private class Sink {
        val blocks = mutableListOf<RtfBlock>()
        private val text = StringBuilder()
        private val spanStarts = mutableListOf<Pair<Int, Style>>()
        private var hasContent = false
        private var lastBlockEmpty = false

        fun append(ch: Char, style: Style) {
            val snapshot = style.copy()
            if (spanStarts.isEmpty() || spanStarts.last().second != snapshot) {
                spanStarts += text.length to snapshot
            }
            text.append(ch)
            hasContent = true
        }

        fun paragraphBreak(force: Boolean = false) {
            if (!hasContent && !force) {
                // Consecutive blank lines collapse into a single empty block.
                lastBlockEmpty = true
                return
            }
            if (force && !hasContent && text.isEmpty() && blocks.isEmpty()) return
            val length = text.length
            val spans = spanStarts.mapIndexed { i, (start, st) ->
                val end = if (i + 1 < spanStarts.size) spanStarts[i + 1].first else length
                RtfSpan(start, end, st)
            }
            blocks += RtfBlock(text.toString(), spans)
            text.clear()
            spanStarts.clear()
            hasContent = false
            lastBlockEmpty = blocks.last().text.isEmpty()
        }

        fun plainText(): String =
            if (text.isNotEmpty()) text.toString()
            else blocks.joinToString(" ") { it.text }
    }

    companion object {
        private val CP1252 = Charset.forName("windows-1252")

        private val SKIP_DESTINATIONS = setOf(
            "fonttbl", "stylesheet", "info", "pict", "object", "header", "footer",
            "footnote", "listtable", "listoverridetable", "generator", "revtbl",
            "themedata", "colorschememapping", "shp", "xmlnstbl", "latentstyles",
            "rsidtbl", "datastore", "mmathPr", "wgrffmtfilter", "fldinst", "flddata",
            "upr", "ud", "comment", "annotation", "hlinkbase", "nonshppict",
        )

        fun parse(bytes: ByteArray): RtfDocument =
            RtfParser(String(bytes, CP1252)).parse()
    }
}

/** Converts a parsed RTF document into display HTML (used by the reader WebView). */
object RtfToHtml {

    fun render(doc: RtfParser.RtfDocument): String {
        val sb = StringBuilder()
        sb.append("<div class=\"prose\">")
        for ((index, block) in doc.blocks.withIndex()) {
            if (block.text.isBlank() && block.spans.isEmpty()) {
                sb.append("<p>&nbsp;</p>")
                continue
            }
            sb.append("<p>")
            if (block.spans.isEmpty()) {
                sb.append(block.text.escapeHtml().lineBreaksToHtml())
            } else {
                for (span in block.spans) {
                    val style = span.style
                    val segment = block.text.substring(span.start.coerceAtMost(block.text.length), span.end.coerceAtMost(block.text.length))
                    if (segment.isEmpty()) continue
                    sb.append("<span")
                    val css = mutableListOf<String>()
                    if (style.bold) css += "font-weight:bold"
                    if (style.italic) css += "font-style:italic"
                    if (style.underline) css += "text-decoration:underline"
                    if (style.fontSizeHalfPoints != 24) css += "font-size:${(style.fontSizeHalfPoints / 2.0).coerceAtLeast(6)}pt"
                    val fg = doc.colorTable.getOrNull(style.colorIndex)
                    if (fg != null && style.colorIndex > 0) css += "color:$fg"
                    val bg = HIGHLIGHT_COLORS[style.highlightIndex]
                    if (bg != null) css += "background-color:$bg"
                    if (css.isNotEmpty()) sb.append(" style=\"").append(css.joinToString(";")).append("\"")
                    sb.append(">").append(segment.escapeHtml().lineBreaksToHtml()).append("</span>")
                }
            }
            sb.append("</p>")
        }
        sb.append("</div>")
        return sb.toString()
    }

    private val HIGHLIGHT_COLORS = mapOf(
        1 to "#ffff00", 2 to "#00ff00", 3 to "#00ffff", 4 to "#ff00ff",
        5 to "#ff0000", 6 to "#0000ff", 7 to "#000000", 8 to "#808080",
        9 to "#ffa500", 10 to "#008000", 11 to "#008080", 12 to "#800080",
        13 to "#800000", 14 to "#000080", 15 to "#808000", 16 to "#c0c0c0",
    )
}

private fun String.lineBreaksToHtml(): String = replace("\n", "<br/>")
