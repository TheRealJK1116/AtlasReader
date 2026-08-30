package com.atlasreader.ui.library

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.atlasreader.core.database.LibraryFilter
import com.atlasreader.core.database.LibrarySort
import com.atlasreader.core.importer.ImportRequest
import com.atlasreader.data.repository.SettingsRepository
import com.atlasreader.domain.model.DocumentSummary
import com.atlasreader.domain.usecase.CollectionsUseCase
import com.atlasreader.domain.usecase.ContinueReadingUseCase
import com.atlasreader.domain.usecase.DeleteDocumentsUseCase
import com.atlasreader.domain.usecase.ImportFilesUseCase
import com.atlasreader.domain.usecase.ImportFolderUseCase
import com.atlasreader.domain.usecase.LibraryCountUseCase
import com.atlasreader.domain.usecase.ObserveLibraryUseCase
import com.atlasreader.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryViewMode { GRID, COMPACT_GRID, LIST, DETAILED }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val observeLibrary: ObserveLibraryUseCase,
    private val continueReading: ContinueReadingUseCase,
    private val count: LibraryCountUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val deleteDocuments: DeleteDocumentsUseCase,
    private val importFiles: ImportFilesUseCase,
    private val importFolder: ImportFolderUseCase,
    private val collections: CollectionsUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    private val _sort = MutableStateFlow(LibrarySort.DATE_ADDED)
    val sort: StateFlow<LibrarySort> = _sort.asStateFlow()

    var viewMode by mutableStateOf(LibraryViewMode.GRID)
        private set

    var selectionMode by mutableStateOf(false)
        private set

    val selectedIds = mutableStateListOf<Long>()

    val continueReadingFlow: Flow<List<DocumentSummary>> = continueReading(12)
    val countFlow: Flow<Int> = count()
    val importState = importFiles.state
    val autoOpen: SharedFlow<Long> = importFiles.autoOpen

    val library: Flow<PagingData<DocumentSummary>> =
        combine(_filter, _sort) { filter, sort -> filter to sort }
            .flatMapLatest { (filter, sort) ->
                androidx.paging.Pager(
                    config = androidx.paging.PagingConfig(
                        pageSize = 30,
                        prefetchDistance = 6,
                        enablePlaceholders = false,
                    ),
                    pagingSourceFactory = { observeLibrary(filter, sort) },
                ).flow
            }
            .cachedIn(viewModelScope)

    val collectionsFlow: StateFlow<List<com.atlasreader.data.repository.CollectionWithCount>> =
        collections.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _sort.value = settings.savedLibrarySort()
            viewMode = runCatching { LibraryViewMode.valueOf(settings.savedViewMode()) }
                .getOrDefault(LibraryViewMode.GRID)
        }
    }

    // ------------------------------------------------------------- filtering

    fun updateFilter(transform: (LibraryFilter) -> LibraryFilter) = _filter.update(transform)

    fun setSort(sort: LibrarySort) {
        _sort.value = sort
        viewModelScope.launch { settings.saveLibrarySort(sort) }
    }

    fun setViewMode(mode: LibraryViewMode) {
        viewMode = mode
        viewModelScope.launch { settings.saveViewMode(mode.name) }
    }

    // --------------------------------------------------------------- actions

    fun toggleFavorite(document: DocumentSummary) {
        viewModelScope.launch { toggleFavorite(document.id, !document.favorite) }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importFiles(uris.map { ImportRequest(uri = it) })
    }

    fun importFolderUri(uri: Uri) {
        viewModelScope.launch {
            val requests = importFolder(uri)
            if (requests.isNotEmpty()) importFiles(requests)
        }
    }

    // -------------------------------------------------------------- selection

    fun enterSelection(documentId: Long) {
        selectionMode = true
        selectedIds.add(documentId)
    }

    fun toggleSelection(documentId: Long) {
        if (documentId in selectedIds) selectedIds.remove(documentId) else selectedIds.add(documentId)
        if (selectedIds.isEmpty()) selectionMode = false
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            deleteDocuments(selectedIds.toList())
            exitSelection()
        }
    }

    fun favoriteSelected(favorite: Boolean) {
        viewModelScope.launch {
            selectedIds.toList().forEach { toggleFavorite(it, favorite) }
        }
    }

    fun addSelectedToCollection(collectionId: Long) {
        viewModelScope.launch {
            selectedIds.toList().forEach { collections.addDocument(collectionId, it) }
        }
    }
}
