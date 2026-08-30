package com.atlasreader.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasreader.domain.model.DocumentSummary
import com.atlasreader.domain.model.SearchResultGroup
import com.atlasreader.domain.usecase.SearchLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode { METADATA, FULL_TEXT }

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchLibraryUseCase,
) : ViewModel() {

    var query by mutableStateOf("")
        private set

    var mode by mutableStateOf(SearchMode.METADATA)
        private set

    var metadataResults by mutableStateOf<List<DocumentSummary>>(emptyList())
        private set

    var fullTextResults by mutableStateOf<List<SearchResultGroup>>(emptyList())
        private set

    var searching by mutableStateOf(false)
        private set

    var searchedOnce by mutableStateOf(false)
        private set

    val history: StateFlow<List<String>> =
        search.history(10).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var debounceJob: Job? = null

    fun onQueryChange(value: String) {
        query = value
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(300)
            if (query.isBlank()) {
                metadataResults = emptyList()
                fullTextResults = emptyList()
                searching = false
                searchedOnce = false
            } else {
                runMetadataSearch()
            }
        }
    }

    fun submit() {
        if (query.isBlank()) return
        debounceJob?.cancel()
        viewModelScope.launch {
            search.recordHistory(query)
            runSearch()
        }
    }

    fun toggleMode() {
        mode = if (mode == SearchMode.METADATA) SearchMode.FULL_TEXT else SearchMode.METADATA
        viewModelScope.launch { runSearch() }
    }

    fun clearHistory() {
        viewModelScope.launch { search.clearHistory() }
    }

    private suspend fun runMetadataSearch() {
        searching = true
        searchedOnce = true
        metadataResults = search.metadata(query, limit = 12)
        searching = false
    }

    private suspend fun runSearch() {
        searching = true
        searchedOnce = true
        if (mode == SearchMode.METADATA) {
            metadataResults = search.metadata(query, limit = 24)
        } else {
            fullTextResults = search.fullText(query, limit = 20)
        }
        searching = false
    }
}
