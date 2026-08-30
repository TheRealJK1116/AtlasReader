package com.atlasreader.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivity
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atlasreader.core.engine.PageProvider
import com.atlasreader.domain.model.Bookmark
import com.atlasreader.domain.model.Highlight
import com.atlasreader.domain.model.ReaderNote
import com.atlasreader.ui.reader.ReaderViewModel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    navController: NavController,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onReaderStarted() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onReaderStopped()
        viewModel.saveNow()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveNow()
            viewModel.closePdfProvider()
        }
    }

    when (val state = viewModel.uiState) {
        is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Couldn't open this document", style = MaterialTheme.typography.titleLarge)
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = { navController.popBackStack() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Back to library")
                }
            }
        }
        is UiState.Content -> {
            ReaderContent(navController = navController, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    navController: NavController,
    viewModel: ReaderViewModel,
) {
    val activity = LocalActivity.current
    val density = LocalDensity.current

    val fontSize by viewModel.fontSizeSp.collectAsStateWithLifecycle()
    val fontFamily by viewModel.fontFamily.collectAsStateWithLifecycle()
    val lineSpacing by viewModel.lineSpacing.collectAsStateWithLifecycle()
    val pageWidth by viewModel.pageWidthPercent.collectAsStateWithLifecycle()
    val margin by viewModel.marginPercent.collectAsStateWithLifecycle()
    val brightness by viewModel.brightnessPercent.collectAsStateWithLifecycle()
    val fullscreen by viewModel.fullscreen.collectAsStateWithLifecycle()
    val orientation by viewModel.orientationLock.collectAsStateWithLifecycle()
    val theme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val css = remember(fontSize, fontFamily, lineSpacing, pageWidth, margin, theme, systemDark) {
        ReaderCss.build(theme, systemDark, fontSize, fontFamily, lineSpacing, pageWidth, margin)
    }

    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHighlights by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showReaderSettings by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteDialogText by remember { mutableStateOf("") }

    val webController = remember { ReaderWebController() }

    // Immersive mode + orientation lock driven by settings/state.
    LaunchedEffect(fullscreen, viewModel.barsVisible) {
        val controller = activity?.window?.let {
            WindowInsetsControllerCompat(it, it.decorView)
        } ?: return@LaunchedEffect
        val immersive = fullscreen && viewModel.barsVisible
        if (immersive) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(orientation) {
        activity?.requestedOrientation = when (orientation) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (viewModel.isPdf) {
            PdfReader(viewModel)
        } else {
            ContinuousReader(viewModel, css, webController)
        }

        if (viewModel.barsVisible) {
            ReaderTopBar(
                title = viewModel.title ?: "",
                progress = viewModel.progressPercent,
                onBack = {
                    viewModel.saveNow()
                    navController.popBackStack()
                },
                onToc = { showToc = true },
                onSearch = { viewModel.toggleSearch() },
                onBookmarks = { showBookmarks = true },
                onHighlights = { showHighlights = true },
                onNotes = { showNotes = true },
                onSettings = { showReaderSettings = true },
            )
            if (viewModel.isPdf) {
                PdfBottomBar(viewModel)
            } else {
                ContinuousBottomBar(viewModel)
            }
        }

        if (viewModel.searchVisible) {
            InDocumentSearchBar(viewModel, webController)
        }

        // Brightness scrim (fixed brightness mode).
        if (brightness >= 0) {
            val alpha = ((100 - brightness) / 100f) * 0.85f
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha))
                    .clickable(enabled = false) {}
            )
        }
    }

    if (showToc) {
        TocSheet(viewModel.toc, onPick = { entry ->
            viewModel.goToTocEntry(entry)
            showToc = false
        }, onDismiss = { showToc = false })
    }
    if (showBookmarks) {
        BookmarkSheet(
            bookmarks = bookmarks,
            onDelete = { viewModel.deleteBookmark(it) },
            onDismiss = { showBookmarks = false },
        )
    }
    if (showHighlights) {
        HighlightSheet(
            highlights = highlights,
            onDelete = { viewModel.deleteHighlight(it) },
            onRecolor = { hl, color -> viewModel.recolorHighlight(hl, color) },
            onDismiss = { showHighlights = false },
        )
    }
    if (showNotes) {
        NoteSheet(
            notes = notes,
            onDelete = { viewModel.deleteNote(it) },
            onAdd = {
                noteDialogText = ""
                showNoteDialog = true
            },
            onDismiss = { showNotes = false },
        )
    }
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("New note") },
            text = {
                OutlinedTextField(
                    value = noteDialogText,
                    onValueChange = { noteDialogText = it },
                    placeholder = { Text("Write a note…") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = noteDialogText.isNotBlank(),
                    onClick = {
                        viewModel.addNote(noteDialogText)
                        showNoteDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") }
            },
        )
    }
    if (showReaderSettings) {
        ReaderSettingsSheet(
            fontSize = fontSize,
            fontFamily = fontFamily,
            lineSpacing = lineSpacing,
            pageWidth = pageWidth,
            margin = margin,
            brightness = brightness,
            fullscreen = fullscreen,
            orientation = orientation,
            theme = theme,
            onFontSize = { viewModel.setFontSize(it) },
            onFontFamily = { viewModel.setFontFamily(it) },
            onLineSpacing = { viewModel.setLineSpacing(it) },
            onPageWidth = { viewModel.setPageWidth(it) },
            onMargin = { viewModel.setMargin(it) },
            onBrightness = { viewModel.setBrightness(it) },
            onFullscreen = { viewModel.setFullscreen(it) },
            onOrientation = { viewModel.setOrientationLock(it) },
            onTheme = { viewModel.setReaderTheme(it) },
            onPickHighlightColor = { viewModel.setPendingHighlightColor(it) },
            onDismiss = { showReaderSettings = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Continuous (WebView) reader
// ---------------------------------------------------------------------------

@Composable
private fun ContinuousReader(
    viewModel: ReaderViewModel,
    css: String,
    webController: ReaderWebController,
) {
    val chunk = viewModel.currentChunk ?: return
    var restoredOnce by remember { mutableStateOf(false) }

    ReaderWebView(
        chunk = chunk,
        css = css,
        highlights = viewModel.highlightsInCurrentChunk,
        initialScrollFraction = if (!restoredOnce) viewModel.currentScrollFraction else 0f,
        allowSelectionActions = true,
        onReady = { restoredOnce = true },
        onScrollFraction = { viewModel.onScrollFraction(it) },
        onSelection = { start, end, text -> viewModel.addHighlightFromSelection(start, end, text) },
        onTap = { viewModel.toggleBars() },
        onLinkRequested = { url ->
            val path = url.substringBefore('#')
            val index = viewModel.chunks.indexOfFirst { it.baseUrl == path }
            if (index >= 0) {
                viewModel.goToChunk(index)
                true
            } else {
                false
            }
        },
        controller = webController,
    )
}

@Composable
private fun ContinuousBottomBar(viewModel: ReaderViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.previousChunk() }, enabled = viewModel.currentChunkIndex > 0) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous chapter")
            }
            Text(
                "Chapter ${viewModel.currentChunkIndex + 1} of ${viewModel.chunkCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 1,
            )
            IconButton(onClick = { viewModel.addBookmarkAtCurrentPosition() }) {
                Icon(Icons.Outlined.BookmarkAdd, "Add bookmark")
            }
            IconButton(onClick = { viewModel.nextChunk() }, enabled = viewModel.currentChunkIndex < viewModel.chunkCount - 1) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Next chapter")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PDF reader
// ---------------------------------------------------------------------------

@Composable
private fun PdfReader(viewModel: ReaderViewModel) {
    val provider = viewModel.pageProvider ?: return
    val pagerState = rememberPagerState(initialPage = viewModel.currentPdfPage, pageCount = { viewModel.pageCount })

    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPdfPage(pagerState.currentPage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectTapGestures { viewModel.toggleBars() }
            },
    ) { page ->
        PdfPage(provider, page, Modifier.fillMaxSize())
    }
}

@Composable
private fun PdfPage(
    provider: PageProvider,
    pageIndex: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx().toInt().coerceAtLeast(1) }
        val heightPx = with(density) { maxHeight.toPx().toInt().coerceAtLeast(1) }
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = pageIndex, key2 = widthPx, key3 = heightPx) {
            value = PdfBitmapCache.get(provider, pageIndex, widthPx, heightPx)
        }
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(32.dp))
            }
        }
    }
}

/** Small LRU of rendered PDF pages keyed by page + requested size. */
private object PdfBitmapCache {
    private val cache = LruCache<String, Bitmap>(8)

    suspend fun get(provider: PageProvider, page: Int, widthPx: Int, heightPx: Int): Bitmap? {
        val key = "${System.identityHashCode(provider)}:$page:$widthPx:$heightPx"
        cache.get(key)?.let { return it }
        val rendered = withContext(Dispatchers.IO) {
            provider.renderPage(page, widthPx, heightPx, 1f)
        } ?: return null
        cache.put(key, rendered)
        return rendered
    }
}

@Composable
private fun PdfBottomBar(viewModel: ReaderViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Text(
            "Page ${viewModel.currentPdfPage + 1} of ${viewModel.pageCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// Overlays
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    progress: Float,
    onBack: () -> Unit,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onHighlights: () -> Unit,
    onNotes: () -> Unit,
    onSettings: () -> Unit,
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            },
            actions = {
                IconButton(onClick = onToc) { Icon(Icons.Outlined.MenuBook, "Table of contents") }
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search in document") }
                IconButton(onClick = onBookmarks) { Icon(Icons.Outlined.Bookmark, "Bookmarks") }
                IconButton(onClick = onHighlights) { Icon(Icons.Outlined.Highlight, "Highlights") }
                IconButton(onClick = onNotes) { Icon(Icons.Outlined.StickyNote2, "Notes") }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Reading settings") }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ),
        )
    }
}

@Composable
private fun InDocumentSearchBar(
    viewModel: ReaderViewModel,
    controller: ReaderWebController,
) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = {
                    viewModel.setSearchQuery(it)
                    controller.search(it)
                },
                placeholder = { Text("Search in document") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { controller.previousMatch() }) {
                Icon(Icons.Outlined.KeyboardArrowUp, "Previous match")
            }
            IconButton(onClick = { controller.nextMatch() }) {
                Icon(Icons.Outlined.KeyboardArrowDown, "Next match")
            }
            IconButton(onClick = { viewModel.toggleSearch() }) {
                Icon(Icons.Outlined.Close, "Close search")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    entries: List<com.atlasreader.core.engine.TocEntry>,
    onPick: (com.atlasreader.core.engine.TocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Table of contents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.height(420.dp)) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        "No table of contents in this document.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries) { entry ->
                ListItem(
                    headlineContent = {
                        Text(entry.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier
                        .padding(start = ((entry.level - 1) * 16).dp)
                        .clickable { onPick(entry) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkSheet(
    bookmarks: List<Bookmark>,
    onDelete: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Bookmarks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.height(380.dp)) {
            if (bookmarks.isEmpty()) {
                item {
                    Text(
                        "No bookmarks yet — use the bookmark button while reading.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(bookmarks) { bookmark ->
                ListItem(
                    headlineContent = { Text(bookmark.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                            .format(java.util.Date(bookmark.createdAtMs)))
                    },
                    trailingContent = {
                        IconButton(onClick = { onDelete(bookmark) }) { Icon(Icons.Outlined.Close, "Delete bookmark") }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightSheet(
    highlights: List<Highlight>,
    onDelete: (Highlight) -> Unit,
    onRecolor: (Highlight, String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Highlights", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.height(420.dp)) {
            if (highlights.isEmpty()) {
                item {
                    Text(
                        "Select text while reading, then choose Highlight from the menu.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(highlights) { highlight ->
                ListItem(
                    headlineContent = { Text(highlight.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HighlightPalette.colors.forEach { color ->
                                Box(
                                    Modifier
                                        .padding(end = 6.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(parseColor(color))
                                        .clickable { onRecolor(highlight, color) }
                                )
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onDelete(highlight) }) {
                            Icon(Icons.Outlined.Close, "Delete highlight")
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(
    notes: List<ReaderNote>,
    onDelete: (ReaderNote) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("Add note") }
        }
        LazyColumn(Modifier.height(380.dp)) {
            if (notes.isEmpty()) {
                item {
                    Text(
                        "No notes yet — notes are pinned to your reading position.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(notes) { note ->
                ListItem(
                    headlineContent = { Text(note.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { onDelete(note) }) { Icon(Icons.Outlined.Close, "Delete note") }
                    },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    fontSize: Int,
    fontFamily: String,
    lineSpacing: Float,
    pageWidth: Int,
    margin: Int,
    brightness: Int,
    fullscreen: Boolean,
    orientation: Int,
    theme: ReaderTheme,
    onFontSize: (Int) -> Unit,
    onFontFamily: (String) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onPageWidth: (Int) -> Unit,
    onMargin: (Int) -> Unit,
    onBrightness: (Int) -> Unit,
    onFullscreen: (Boolean) -> Unit,
    onOrientation: (Int) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onPickHighlightColor: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .height(520.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        ) {
            Text("Reading settings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Text("Theme", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReaderTheme.entries.forEach { t ->
                    FilterChip(
                        selected = theme == t,
                        onClick = { onTheme(t) },
                        label = { Text(t.label) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            Text("Font", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to "System", "serif" to "Serif", "sans" to "Sans", "mono" to "Mono").forEach { (id, label) ->
                    FilterChip(
                        selected = fontFamily == id,
                        onClick = { onFontFamily(id) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Font size — ${fontSize}sp")
            Slider(value = fontSize.toFloat(), onValueChange = { onFontSize(it.toInt()) }, valueRange = 12f..32f, steps = 19)
            Text("Line spacing — ${"%.2f".format(lineSpacing)}")
            Slider(value = lineSpacing, onValueChange = { onLineSpacing(it) }, valueRange = 1.0f..2.2f, steps = 11)
            Text("Page width — $pageWidth%")
            Slider(value = pageWidth.toFloat(), onValueChange = { onPageWidth(it.toInt()) }, valueRange = 60f..100f, steps = 7)
            Text(if (brightness < 0) "Brightness — system" else "Brightness — $brightness%")
            Slider(value = if (brightness < 0) 0f else brightness.toFloat(), onValueChange = { onBrightness(it.toInt()) }, valueRange = -1f..100f, steps = 100)

            ListItem(
                headlineContent = { Text("Fullscreen") },
                trailingContent = { Switch(checked = fullscreen, onCheckedChange = { onFullscreen(it) }) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Orientation", modifier = Modifier.weight(1f))
                listOf(0 to "Auto", 1 to "Portrait", 2 to "Landscape").forEach { (id, label) ->
                    FilterChip(selected = orientation == id, onClick = { onOrientation(id) }, label = { Text(label) }, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Highlight colour", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HighlightPalette.colors.forEach { color ->
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(parseColor(color))
                            .clickable { onPickHighlightColor(color) }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color.Yellow)
