package com.atlasreader.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.atlasreader.core.engine.ProseChunk
import com.atlasreader.domain.model.Highlight
import org.json.JSONArray
import org.json.JSONObject

/**
 * The continuous-reader WebView.
 *
 * Rendering model: every chunk is one `loadDataWithBaseURL` page carrying the
 * chunk HTML + theme CSS. Annotations anchor to character offsets in the
 * chunk's plain text; JS walks the DOM with the same block-boundary rules as
 * `HtmlUtils.textFromHtml` so the walker text == chunk text, and highlights
 * are wrapped in <mark> elements. Snippet-based fuzzy fallback keeps
 * annotations robust when markup whitespace differs.
 */
@Composable
fun ReaderWebView(
    chunk: ProseChunk,
    css: String,
    highlights: List<Highlight>,
    initialScrollFraction: Float,
    allowSelectionActions: Boolean,
    onReady: () -> Unit,
    onScrollFraction: (Float) -> Unit,
    onSelection: (start: Int, end: Int, text: String) -> Unit,
    onTap: () -> Unit = {},
    onLinkRequested: (String) -> Boolean,
    controller: ReaderWebController = remember { ReaderWebController() },
) {
    val context = LocalContext.current
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnScroll by rememberUpdatedState(onScrollFraction)
    val currentOnSelection by rememberUpdatedState(onSelection)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLink by rememberUpdatedState(onLinkRequested)
    val currentHighlights by rememberUpdatedState(highlights)
    val currentCss by rememberUpdatedState(css)
    val currentInitialFraction by rememberUpdatedState(initialScrollFraction)

    val webView = remember { mutableWebViewHolder() }

    DisposableEffect(Unit) {
        onDispose {
            webView.value?.destroy()
            webView.value = null
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                val webView = this
                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.textZoom = 100
                settings.defaultTextEncodingName = "UTF-8"
                setBackgroundColor(Color.TRANSPARENT)

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onReady() = currentOnReady()

                        @JavascriptInterface
                        fun onScrollFraction(fraction: Double) =
                            currentOnScroll(fraction.toFloat().coerceIn(0f, 1f))

                        @JavascriptInterface
                        fun onSelection(start: Int, end: Int, text: String) =
                            currentOnSelection(start, end, text)

                        @JavascriptInterface
                        fun onTap() = currentOnTap()
                    },
                    "AtlasJs",
                )

                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (url.startsWith("file://")) {
                            // Internal link (epub chapter, local resource) — delegate.
                            if (currentOnLink(url)) return true
                            // Not handled: fall through to default (opens in this WebView).
                            return super.shouldOverrideUrlLoading(view, request)
                        }
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            // External link — open in the browser, never inside the reader.
                            runCatching {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url),
                                )
                                view.context.startActivity(intent)
                            }
                            return true
                        }
                        return super.shouldOverrideUrlLoading(view, request)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        injectAtlasScripts(view)
                        applyHighlights(view, currentHighlights)
                        if (currentInitialFraction > 0f) {
                            scrollToFraction(view, currentInitialFraction)
                        }
                        currentOnReady()
                    }
                }

                if (allowSelectionActions) {
                    setCustomSelectionActionModeCallback(object : ActionMode.Callback {
                        private val HIGHLIGHT = 1001
                        private val NOTE = 1002

                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            menu.add(0, HIGHLIGHT, 10, "Highlight")
                            menu.add(0, NOTE, 20, "Add note")
                            return true
                        }

                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                            return when (item.itemId) {
                                HIGHLIGHT, NOTE -> {
                                    requestSelection(webView) { start, end, text ->
                                        currentOnSelection(start, end, text)
                                    }
                                    mode.finish()
                                    true
                                }
                                else -> false
                            }
                        }

                        override fun onDestroyActionMode(mode: ActionMode) {}
                    })
                }

                webView.value = this
            }
        },
        modifier = Modifier,
    )

    // (Re)load whenever the chunk changes.
    LaunchedEffect(chunk.resourceToken) {
        val view = webView.value ?: return@LaunchedEffect
        val document = buildDocumentHtml(chunk.html ?: chunk.text, currentCss)
        val base = chunk.baseUrl ?: ""
        view.loadDataWithBaseURL(base, document, "text/html", "UTF-8", null)
    }

    // Re-inject CSS when display settings change.
    LaunchedEffect(css) {
        val view = webView.value ?: return@LaunchedEffect
        if (view.url != null) {
            view.evaluateJavascript("injectCss(${cssJson(currentCss)});", null)
        }
    }

    // Apply highlights whenever the set for this chunk changes.
    LaunchedEffect(chunk.resourceToken, highlights) {
        val view = webView.value ?: return@LaunchedEffect
        if (view.url != null) {
            applyHighlights(view, currentHighlights)
        }
    }

    controller.webView = webView.value
}

private class WebViewHolder {
    var value: WebView? = null
}

private fun mutableWebViewHolder(): WebViewHolder = WebViewHolder()

private fun buildDocumentHtml(bodyHtml: String, css: String): String {
    val escapedCss = css.replace("\\", "\\\\").replace("\"", "&quot;")
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style id="atlas-style">$escapedCss</style>
        </head>
        <body dir="auto">
        <div id="atlas-prose">$bodyHtml</div>
        </body>
        </html>
    """.trimIndent()
}

private fun cssJson(css: String): String =
    JSONObject().put("css", css).toString()

/** Kotlin-side handle for driving the WebView (search, TOC jumps, restore). */
class ReaderWebController {
    var webView: WebView? = null
        internal set

    fun scrollToFraction(fraction: Float) {
        val view = webView ?: return
        if (view.url == null) return
        view.evaluateJavascript("window.ATLAS_SCROLL_TO_FRACTION($fraction);", null)
    }

    fun scrollToAnchor(anchor: String) {
        val view = webView ?: return
        val escaped = JSONObject().put("a", anchor).toString()
        view.evaluateJavascript("window.ATLAS_SCROLL_TO_ANCHOR($escaped);", null)
    }

    fun search(query: String) {
        val view = webView ?: return
        if (query.isBlank()) {
            view.clearMatches()
            return
        }
        view.findAllAsync(query)
        view.setFindListener { _, _, _ -> }
    }

    fun nextMatch() {
        val view = webView ?: return
        view.findNext(true)
    }

    fun previousMatch() {
        val view = webView ?: return
        view.findNext(false)
    }

    fun clearSearch() {
        val view = webView ?: return
        view.clearMatches()
    }
}

// ---------------------------------------------------------------------------
// JavaScript
// ---------------------------------------------------------------------------

private fun injectAtlasScripts(view: WebView) {
    view.evaluateJavascript(ATLAS_JS, null)
}

private fun applyHighlights(view: WebView, highlights: List<Highlight>) {
    val payload = JSONArray().apply {
        highlights.forEach { h ->
            put(
                JSONObject()
                    .put("id", h.id)
                    .put("start", h.startOffset)
                    .put("end", h.endOffset)
                    .put("text", h.text)
                    .put("color", h.colorHex)
            )
        }
    }.toString()
    view.evaluateJavascript("window.ATLAS_APPLY_HIGHLIGHTS($payload);", null)
}

private fun requestSelection(view: WebView, onResult: (Int, Int, String) -> Unit) {
    view.evaluateJavascript("window.ATLAS_SELECTION_INFO();") { result ->
        if (result == "null" || result.isBlank()) return@evaluateJavascript
        runCatching {
            val json = JSONObject(result)
            val start = json.getInt("start")
            val end = json.getInt("end")
            val text = json.getString("text")
            onResult(start, end, text)
        }
    }
}

private fun scrollToFraction(view: WebView, fraction: Float) {
    view.evaluateJavascript("window.ATLAS_SCROLL_TO_FRACTION($fraction);", null)
}

private val ATLAS_JS = """
(function () {
    if (window.__atlasInstalled) return;
    window.__atlasInstalled = true;

    var BLOCK = ['p','div','h1','h2','h3','h4','h5','h6','li','ul','ol','blockquote','pre','table','tr','section','article','header','footer','aside','nav','figure','figcaption','br','hr','dt','dd','dl'];

    function isBlock(tag) { return BLOCK.indexOf(tag) >= 0; }

    function collectText(root) {
        var text = '';
        var nodes = [];
        var stack = [root];
        while (stack.length > 0) {
            var node = stack.pop();
            if (node.nodeType === 3) {
                nodes.push({node: node, start: text.length});
                text += node.nodeValue;
            } else if (node.nodeType === 1) {
                var tag = node.tagName.toLowerCase();
                if (tag === 'br') { if (text.length > 0 && text.charAt(text.length-1) !== '\n') text += '\n'; continue; }
                var block = isBlock(tag);
                if (block && text.length > 0 && text.charAt(text.length-1) !== '\n') text += '\n';
                for (var i = node.childNodes.length - 1; i >= 0; i--) stack.push(node.childNodes[i]);
                if (block && text.length > 0 && text.charAt(text.length-1) !== '\n') text += '\n';
            }
        }
        return {text: text, nodes: nodes};
    }

    function locateRange(info, start, end) {
        var sNode = null, sOff = 0, eNode = null, eOff = 0;
        for (var i = 0; i < info.nodes.length; i++) {
            var entry = info.nodes[i];
            var ns = entry.start;
            var ne = entry.start + entry.node.nodeValue.length;
            if (sNode === null && start <= ne) {
                sNode = entry.node;
                sOff = Math.max(0, Math.min(start - ns, entry.node.nodeValue.length));
            }
            if (eNode === null && end <= ne) {
                eNode = entry.node;
                eOff = Math.max(0, Math.min(end - ns, entry.node.nodeValue.length));
            }
            if (sNode !== null && eNode !== null) break;
        }
        return {sNode: sNode, sOff: sOff, eNode: eNode, eOff: eOff};
    }

    function locateWithFallback(hl) {
        var prose = document.getElementById('atlas-prose');
        if (!prose) return null;
        var info = collectText(prose);
        var range = locateRange(info, hl.start, hl.end);
        if (range.sNode && range.eNode) {
            // sanity: requested offsets should land inside the document text
            if (hl.start >= 0 && hl.end <= info.text.length) return range;
        }
        // fuzzy: locate the snippet text
        var idx = info.text.indexOf(hl.text);
        if (idx < 0) return null;
        return locateRange(info, idx, idx + hl.text.length);
    }

    function hexToRgba(hex, alpha) {
        var h = hex.replace('#', '');
        if (h.length === 3) h = h.charAt(0)+h.charAt(0)+h.charAt(1)+h.charAt(1)+h.charAt(2)+h.charAt(2);
        var num = parseInt(h, 16);
        var r = (num >> 16) & 255, g = (num >> 8) & 255, b = num & 255;
        return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
    }

    function wrapInMark(sNode, sOff, eNode, eOff, color) {
        var range = document.createRange();
        try {
            range.setStart(sNode, sOff);
            range.setEnd(eNode, eOff);
        } catch (e) { return false; }
        if (range.collapsed) return false;
        var mark = document.createElement('mark');
        mark.className = 'atlas-hl';
        mark.style.background = hexToRgba(color, 0.45);
        try {
            var fragment = range.extractContents();
            mark.appendChild(fragment);
            range.insertNode(mark);
            return true;
        } catch (e) { return false; }
    }

    window.ATLAS_APPLY_HIGHLIGHTS = function(items) {
        var prose = document.getElementById('atlas-prose');
        if (!prose) return;
        // clear existing marks
        var marks = prose.querySelectorAll('mark.atlas-hl');
        for (var i = 0; i < marks.length; i++) {
            var m = marks[i];
            var parent = m.parentNode;
            while (m.firstChild) parent.insertBefore(m.firstChild, m);
            parent.removeChild(m);
            parent.normalize();
        }
        if (!items || !items.length) return;
        for (var i = 0; i < items.length; i++) {
            var hl = items[i];
            var range = locateWithFallback(hl);
            if (!range) continue;
            if (!wrapInMark(range.sNode, range.sOff, range.eNode, range.eOff, hl.color)) {
                var info = collectText(prose);
                var idx = info.text.indexOf(hl.text);
                if (idx >= 0) {
                    var r2 = locateRange(info, idx, idx + hl.text.length);
                    wrapInMark(r2.sNode, r2.sOff, r2.eNode, r2.eOff, hl.color);
                }
            }
        }
    };

    window.ATLAS_SELECTION_INFO = function() {
        var sel = window.getSelection();
        if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return 'null';
        var prose = document.getElementById('atlas-prose');
        if (!prose) return 'null';
        var info = collectText(prose);
        var start = -1, end = -1;
        for (var i = 0; i < info.nodes.length; i++) {
            var entry = info.nodes[i];
            if (entry.node === sel.anchorNode) { start = entry.start + sel.anchorOffset; }
            if (entry.node === sel.focusNode) { end = entry.start + sel.focusOffset; }
        }
        if (start < 0 || end < 0) return 'null';
        if (start > end) { var t = start; start = end; end = t; }
        var text = info.text.substring(start, end).trim();
        if (text.length === 0) return 'null';
        return JSON.stringify({start: start, end: end, text: text});
    };

    window.ATLAS_SCROLL_TO_FRACTION = function(fraction) {
        var body = document.body;
        var max = body.scrollHeight - window.innerHeight;
        if (max <= 0) return;
        window.scrollTo(0, Math.round(fraction * max));
    };

    window.ATLAS_SCROLL_TO_ANCHOR = function(anchor) {
        if (!anchor) return;
        var el = document.getElementById(anchor) || document.getElementsByName(anchor)[0];
        if (el) { el.scrollIntoView(true); return; }
        // heading text fallback
        var headings = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
        for (var i = 0; i < headings.length; i++) {
            if (headings[i].textContent.trim() === anchor) { headings[i].scrollIntoView(true); return; }
        }
    };

    window.injectCss = function(payload) {
        var style = document.getElementById('atlas-style');
        if (style) style.textContent = payload.css;
    };

    // scroll reporting (throttled)
    var lastReport = 0;
    window.addEventListener('scroll', function() {
        var now = Date.now();
        if (now - lastReport < 200) return;
        lastReport = now;
        var body = document.body;
        var max = body.scrollHeight - window.innerHeight;
        if (max <= 0) return;
        var fraction = window.scrollY / max;
        try { AtlasJs.onScrollFraction(fraction); } catch (e) {}
    }, {passive: true});

    window.addEventListener('load', function() {
        try { AtlasJs.onReady(); } catch (e) {}
    });

    // tap-to-toggle chrome: a tap that is not a scroll/drag/link activation
    var touchStartX = 0, touchStartY = 0, touchStartTime = 0, touchMoved = false;
    document.addEventListener('touchstart', function(e) {
        touchStartX = e.touches[0].clientX;
        touchStartY = e.touches[0].clientY;
        touchStartTime = Date.now();
        touchMoved = false;
    }, {passive: true});
    document.addEventListener('touchmove', function(e) {
        var dx = e.touches[0].clientX - touchStartX;
        var dy = e.touches[0].clientY - touchStartY;
        if (dx * dx + dy * dy > 100) touchMoved = true;
    }, {passive: true});
    document.addEventListener('touchend', function(e) {
        if (touchMoved) return;
        if (Date.now() - touchStartTime > 600) return;
        var target = e.target;
        if (target && (target.tagName === 'A' || target.tagName === 'IMG')) return;
        try { AtlasJs.onTap(); } catch (err) {}
    }, {passive: true});
})();
""".trimIndent()
