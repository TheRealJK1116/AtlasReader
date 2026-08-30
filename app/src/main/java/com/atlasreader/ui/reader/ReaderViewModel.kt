package com.atlasreader.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasreader.core.common.AtlasResult
import com.atlasreader.core.common.TimeProvider
import com.atlasreader.core.engine.ProseChunk
import com.atlasreader.core.engine.TocEntry
import com.atlasreader.data.repository.ReaderRepository
import com.atlasreader.data.repository.SettingsRepository
import com.atlasreader.domain.model.Bookmark
import com.atlasreader.domain.model.Highlight
import com.atlasreader.domain.model.ReaderNote
import com.atlasreader.domain.model.ReadingPosition
import com.atlasreader.domain.usecase.OpenDocumentUseCase
import com.atlasreader.domain.usecase.ReaderAnnotationsUseCase
import com.atlasreader.domain.usecase.ReaderPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val openDocument: OpenDocumentUseCase,
    private val annotations: ReaderAnnotationsUseCase,
    private val positionUseCase: ReaderPositionUseCase,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val documentId: Long = checkNotNull(savedStateHandle["documentId"])

    // ------------------------------------------------------------------ state

    sealed interface UiState {
        data object Loading : UiState
        data class Content(val opened: ReaderRepository.OpenedDocument) : UiState
        data class Error(val message: String) : UiState
    }

    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set

    var currentChunkIndex by mutableIntStateOf(0)
        private set

    var currentScrollFraction by mutableFloatStateOf(0f)
        private set

    var currentPdfPage by mutableIntStateOf(0)
        private set

    var barsVisible by mutableStateOf(true)
        private set

    var pendingHighlightColor by mutableStateOf(HighlightPalette.defaultColor)
        private set

    var searchVisible by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    // ------------------------------------------------------------- settings

    val fontSizeSp: StateFlow<Int> =
        settings.readerFontSizeSp.stateIn(viewModelScope, SharingStarted.Eagerly, 18)
    val fontFamily: StateFlow<String> =
        settings.readerFontFamily.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val lineSpacing: StateFlow<Float> =
        settings.readerLineSpacing.stateIn(viewModelScope, SharingStarted.Eagerly, 1.4f)
    val pageWidthPercent: StateFlow<Int> =
        settings.readerPageWidthPercent.stateIn(viewModelScope, SharingStarted.Eagerly, 92)
    val marginPercent: StateFlow<Int> =
        settings.readerMarginPercent.stateIn(viewModelScope, SharingStarted.Eagerly, 6)
    val brightnessPercent: StateFlow<Int> =
        settings.readerBrightnessPercent.stateIn(viewModelScope, SharingStarted.Eagerly, -1)
    val fullscreen: StateFlow<Boolean> =
        settings.readerFullscreen.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val orientationLock: StateFlow<Int> =
        settings.readerOrientationLock.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val readerTheme: StateFlow<ReaderTheme> =
        settings.readerTheme
            .map { runCatching { ReaderTheme.valueOf(it) }.getOrDefault(ReaderTheme.AUTO) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderTheme.AUTO)

    // ---------------------------------------------------------- annotations

    val bookmarks: StateFlow<List<Bookmark>> =
        annotations.observeBookmarks(documentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val highlights: StateFlow<List<Highlight>> =
        annotations.observeHighlights(documentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<ReaderNote>> =
        annotations.observeNotes(documentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------------------------------------------------- derived data

    private val content: ReaderRepository.OpenedDocument?
        get() = (uiState as? UiState.Content)?.opened

    val title: String?
        get() = content?.title

    val pageProvider: com.atlasreader.core.engine.PageProvider?
        get() = content?.parsed?.pageProvider

    val chunks: List<ProseChunk>
        get() = content?.parsed?.chunks ?: emptyList()

    val toc: List<TocEntry>
        get() = content?.parsed?.tableOfContents ?: emptyList()

    val isPdf: Boolean
        get() = content?.parsed?.pageProvider != null

    /** Filled once when the document opens — [PageProvider.pageCount] is suspend. */
    private var cachedPageCount: Int = 0

    val pageCount: Int
        get() = cachedPageCount

    val chunkCount: Int get() = chunks.size

    val progressPercent: Float
        get() = when {
            isPdf && pageCount > 0 -> ((currentPdfPage + 1).toFloat() / pageCount).coerceIn(0f, 0.99f)
            chunkCount > 0 -> ((currentChunkIndex + currentScrollFraction.coerceIn(0f, 1f)) / chunkCount)
                .coerceIn(0f, 0.99f)
            else -> 0f
        }

    val currentChunk: ProseChunk?
        get() = chunks.getOrNull(currentChunkIndex)

    val highlightsInCurrentChunk: List<Highlight>
        get() = currentChunk?.let { chunk ->
            highlights.value.filter { it.resourceToken == chunk.resourceToken }
        } ?: emptyList()

    private var saveJob: Job? = null
    private var sessionActive = false
    private var sessionStartedMs = 0L

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = when (val result = openDocument(documentId)) {
                is AtlasResult.Success -> {
                    val opened = result.value
                    cachedPageCount = opened.parsed.pageProvider?.pageCount() ?: 0
                    opened.position?.let { position ->
                        val index = opened.parsed.chunks.indexOfFirst {
                            it.resourceToken == position.resourceToken
                        }
                        if (index >= 0) currentChunkIndex = index
                        currentScrollFraction = position.scrollFraction
                        currentPdfPage = position.pageIndex
                    }
                    UiState.Content(opened)
                }
                is AtlasResult.Failure -> UiState.Error(result.error.toString())
            }
        }
    }

    // ------------------------------------------------------------- lifecycle

    fun onReaderStarted() {
        if (sessionActive) return
        sessionActive = true
        sessionStartedMs = time.epochMillis()
    }

    fun onReaderStopped() {
        if (!sessionActive) return
        sessionActive = false
        val elapsed = time.epochMillis() - sessionStartedMs
        viewModelScope.launch { positionUseCase.recordSession(documentId, elapsed) }
    }

    // ------------------------------------------------------------ navigation

    fun goToChunk(index: Int) {
        val clamped = index.coerceIn(0, (chunkCount - 1).coerceAtLeast(0))
        if (clamped == currentChunkIndex) return
        currentChunkIndex = clamped
        currentScrollFraction = 0f
        saveJob?.cancel()
        scheduleSave()
    }

    fun nextChunk() = goToChunk(currentChunkIndex + 1)
    fun previousChunk() = goToChunk(currentChunkIndex - 1)

    fun goToTocEntry(entry: TocEntry) = goToChunk(entry.chunkIndex)

    fun onScrollFraction(fraction: Float) {
        currentScrollFraction = fraction
        scheduleSave()
    }

    fun onPdfPage(page: Int) {
        if (page == currentPdfPage) return
        currentPdfPage = page
        scheduleSave()
    }

    fun toggleBars() {
        barsVisible = !barsVisible
    }

    // --------------------------------------------------------------- progress

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1500)
            saveNow()
        }
    }

    fun saveNow() {
        val document = content ?: return
        saveJob?.cancel()
        viewModelScope.launch {
            positionUseCase.save(
                ReadingPosition(
                    documentId = document.documentId,
                    resourceToken = currentChunk?.resourceToken,
                    charOffset = 0,
                    pageIndex = currentPdfPage,
                    scrollFraction = currentScrollFraction,
                    percent = progressPercent,
                    updatedAtMs = time.epochMillis(),
                    sessionAccumMs = document.position?.sessionAccumMs ?: 0L,
                )
            )
        }
    }

    // ----------------------------------------------------------- annotations

    fun updatePendingHighlightColor(color: String) {
        pendingHighlightColor = color
    }

    fun addHighlightFromSelection(start: Int, end: Int, text: String) {
        val chunk = currentChunk ?: return
        if (end <= start || text.isBlank()) return
        viewModelScope.launch {
            annotations.addHighlight(documentId, chunk.resourceToken, start, end, text, pendingHighlightColor)
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch { annotations.deleteHighlight(highlight) }
    }

    fun recolorHighlight(highlight: Highlight, color: String) {
        viewModelScope.launch { annotations.recolorHighlight(highlight.id, color) }
    }

    fun addBookmarkAtCurrentPosition() {
        val chunk = currentChunk ?: return
        val idx = (currentScrollFraction * chunk.text.length).toInt()
            .coerceIn(0, chunk.text.length.coerceAtLeast(0))
        val snippet = chunk.text.substring(idx, (idx + 80).coerceAtMost(chunk.text.length)).trim()
        viewModelScope.launch {
            annotations.addBookmark(documentId, chunk.resourceToken, idx, snippet)
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { annotations.deleteBookmark(bookmark) }
    }

    fun addNote(text: String, highlightId: Long? = null) {
        val chunk = currentChunk ?: return
        val idx = (currentScrollFraction * chunk.text.length).toInt()
            .coerceIn(0, chunk.text.length.coerceAtLeast(0))
        viewModelScope.launch {
            annotations.addNote(documentId, chunk.resourceToken, idx, text.trim(), highlightId)
        }
    }

    fun updateNote(note: ReaderNote) {
        viewModelScope.launch { annotations.updateNote(note) }
    }

    fun deleteNote(note: ReaderNote) {
        viewModelScope.launch { annotations.deleteNote(note) }
    }

    // ------------------------------------------------------------- settings

    fun setFontSize(value: Int) = viewModelScope.launch { settings.setFontSizeSp(value) }
    fun setFontFamily(value: String) = viewModelScope.launch { settings.setFontFamily(value) }
    fun setLineSpacing(value: Float) = viewModelScope.launch { settings.setLineSpacing(value) }
    fun setPageWidth(value: Int) = viewModelScope.launch { settings.setPageWidthPercent(value) }
    fun setMargin(value: Int) = viewModelScope.launch { settings.setMarginPercent(value) }
    fun setBrightness(value: Int) = viewModelScope.launch { settings.setBrightnessPercent(value) }
    fun setFullscreen(value: Boolean) = viewModelScope.launch { settings.setFullscreen(value) }
    fun setOrientationLock(value: Int) = viewModelScope.launch { settings.setOrientationLock(value) }
    fun setReaderTheme(theme: ReaderTheme) = viewModelScope.launch { settings.setReaderTheme(theme.id) }

    // ---------------------------------------------------------------- search

    fun toggleSearch() {
        searchVisible = !searchVisible
        if (!searchVisible) searchQuery = ""
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    // ------------------------------------------------------------------ misc

    fun closePdfProvider() {
        val provider = content?.parsed?.pageProvider
        if (provider is com.atlasreader.core.engine.engines.PdfPageProvider) {
            viewModelScope.launch { provider.close() }
        }
    }

    override fun onCleared() {
        closePdfProvider()
        super.onCleared()
    }
}

/** Highlight colour palette offered in the reader. */
object HighlightPalette {
    const val defaultColor = "#FFC107"
    val colors = listOf("#FFC107", "#4CAF50", "#2196F3", "#E91E63", "#FF9800", "#9C27B0")
}
