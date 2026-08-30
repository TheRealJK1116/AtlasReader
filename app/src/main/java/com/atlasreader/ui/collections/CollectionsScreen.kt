package com.atlasreader.ui.collections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atlasreader.core.database.entity.CollectionWithCount
import com.atlasreader.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    navController: NavController,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Collections") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("New") },
            )
        },
    ) { padding ->
        if (collections.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Bookmarks,
                title = "No collections yet",
                message = "Group related documents — e.g. “Sci-fi”, “Work”, “Course reading”.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(collections, key = { it.id }) { collection ->
                    CollectionCard(
                        collection = collection,
                        onClick = {
                            navController.navigate(collectionDetailRoute(collection.id))
                        },
                        onDelete = { viewModel.delete(collection.id) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.create(name)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun CollectionCard(
    collection: CollectionWithCount,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(110.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                collection.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${collection.documentCount} documents",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New collection") },
        text = {
            TextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Collection name") })
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

fun collectionDetailRoute(collectionId: Long) = "collection/$collectionId"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onBack: () -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val documents by viewModel.documentsIn(collectionId).collectAsStateWithLifecycle(initialValue = emptyList())
    var removeId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        if (documents.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Bookmarks,
                title = "Empty collection",
                message = "Select documents in the library and choose “Add to collection”.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(documents, key = { it.id }) { doc ->
                    ListItem(
                        headlineContent = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(doc.author ?: doc.format.label) },
                        trailingContent = {
                            IconButton(onClick = { removeId = doc.id }) {
                                Icon(Icons.Outlined.Remove, "Remove from collection")
                            }
                        },
                        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = {}),
                    )
                }
            }
        }
    }

    removeId?.let { documentId ->
        AlertDialog(
            onDismissRequest = { removeId = null },
            title = { Text("Remove from collection?") },
            text = { Text("The document stays in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeDocument(collectionId, documentId)
                    removeId = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removeId = null }) { Text("Cancel") }
            },
        )
    }
}
