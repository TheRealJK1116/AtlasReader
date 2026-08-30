package com.atlasreader.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasreader.core.database.entity.CollectionWithCount
import com.atlasreader.domain.model.DocumentSummary
import com.atlasreader.domain.usecase.CollectionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionsUseCase: CollectionsUseCase,
) : ViewModel() {

    val collections: StateFlow<List<CollectionWithCount>> =
        collectionsUseCase.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String) {
        viewModelScope.launch { collectionsUseCase.create(name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { collectionsUseCase.delete(id) }
    }

    fun documentsIn(collectionId: Long): Flow<List<DocumentSummary>> =
        collectionsUseCase.documentsIn(collectionId)

    fun removeDocument(collectionId: Long, documentId: Long) {
        viewModelScope.launch { collectionsUseCase.removeDocument(collectionId, documentId) }
    }
}
