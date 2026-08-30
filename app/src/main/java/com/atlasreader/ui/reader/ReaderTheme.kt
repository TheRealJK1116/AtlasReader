package com.atlasreader.ui.reader

/** Reader content themes — independent from the app Material theme. */
enum class ReaderTheme(val id: String, val label: String) {
    AUTO("auto", "Follow system"),
    LIGHT("light", "Light"),
    SEPIA("sepia", "Sepia"),
    DARK("dark", "Dark"),
    AMOLED("amoled", "AMOLED"),
}

/** Pure function building the CSS injected into every reader page. */
object ReaderCss {

    data class Palette(val background: String, val foreground: String, val accent: String, val selectionBg: String)

    fun palette(theme: ReaderTheme, systemDark: Boolean): Palette = when {
        theme == ReaderTheme.AUTO && systemDark -> Palette("#121417", "#D6D3CB", "#8FA8D8", "#334155")
        theme == ReaderTheme.AUTO -> Palette("#FFFFFF", "#1A1A1A", "#415F91", "#D8E2FF")
        theme == ReaderTheme.SEPIA -> Palette("#F4ECD8", "#3B3226", "#8B6F47", "#E3D5B8")
        theme == ReaderTheme.DARK -> Palette("#121417", "#D6D3CB", "#8FA8D8", "#334155")
        theme == ReaderTheme.AMOLED -> Palette("#000000", "#C9C9C9", "#7C9BD4", "#1F2937")
        else -> Palette("#FFFFFF", "#1A1A1A", "#415F91", "#D8E2FF")
    }

    fun build(
        theme: ReaderTheme,
        systemDark: Boolean,
        fontSizeSp: Int,
        fontFamily: String,
        lineSpacing: Float,
        pageWidthPercent: Int,
        marginPercent: Int,
    ): String {
        val palette = palette(theme, systemDark)
        val family = when (fontFamily) {
            "serif" -> "Georgia, 'Times New Roman', 'Noto Serif', serif"
            "sans" -> "'Roboto', 'Noto Sans', sans-serif"
            "mono" -> "'Roboto Mono', 'Noto Sans Mono', monospace"
            else -> "system-ui, -apple-system, 'Roboto', sans-serif"
        }
        return """
            :root { --bg:${palette.background}; --fg:${palette.foreground}; --accent:${palette.accent}; }
            html, body { background: var(--bg) !important; color: var(--fg); }
            body {
                font-family: $family;
                font-size: ${fontSizeSp}px;
                line-height: $lineSpacing;
                padding: ${marginPercent}vw ${marginPercent * 2}vw;
                margin: 0;
                word-wrap: break-word;
                overflow-wrap: break-word;
            }
            #atlas-prose { max-width: ${pageWidthPercent}vw; margin: 0 auto; }
            p { margin: 0 0 1em 0; }
            h1, h2, h3, h4, h5, h6 { line-height: 1.3; margin: 1.2em 0 0.6em 0; }
            a { color: var(--accent); }
            img { max-width: 100%; height: auto; }
            pre { white-space: pre-wrap; font-family: monospace; background: rgba(128,128,128,0.12); padding: 0.8em; border-radius: 6px; }
            blockquote { margin: 1em 0; padding-left: 1em; border-left: 3px solid var(--accent); opacity: 0.92; }
            table { border-collapse: collapse; width: 100%; }
            td, th { border: 1px solid rgba(128,128,128,0.4); padding: 4px 8px; }
            ::selection { background: ${palette.selectionBg}; }
            mark.atlas-hl { border-radius: 2px; padding: 0 1px; }
        """.trimIndent()
    }
}
