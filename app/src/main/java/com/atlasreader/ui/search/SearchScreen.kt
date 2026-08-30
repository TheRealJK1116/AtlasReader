package com.atlasreader.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atlasreader.domain.model.DocumentSummary
import com.atlasreader.domain.model.SearchResultGroup
import com.atlasreader.ui.components.CoverImage
import com.atlasreader.ui.components.EmptyState
import com.atlasreader.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = viewModel.query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        placeholder = { Text("Search title, author or content") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (viewModel.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Outlined.Clear, "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = viewModel.mode == SearchMode.METADATA,
                    onClick = { if (viewModel.mode != SearchMode.METADATA) viewModel.toggleMode() },
                    label = { Text("Title & author") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = viewModel.mode == SearchMode.FULL_TEXT,
                    onClick = { if (viewModel.mode != SearchMode.FULL_TEXT) viewModel.toggleMode() },
                    label = { Text("Full text") },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.submit() }) { Text("Search") }
            }

            when {
                viewModel.query.isBlank() -> {
                    if (history.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Recent searches", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { viewModel.clearHistory() }) { Text("Clear") }
                                }
                            }
                            items(history) { query ->
                                ListItem(
                                    headlineContent = { Text(query) },
                                    leadingContent = { Icon(Icons.Outlined.History, null) },
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .combinedClickable(
                                            onClick = { viewModel.onQueryChange(query); viewModel.submit() },
                                            onLongClick = {},
                                        ),
                                )
                            }
                        }
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = "Search your library",
                            message = "Find documents by title or author, or search inside the full text of indexed documents.",
                        )
                    }
                }
                viewModel.searching -> {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                viewModel.mode == SearchMode.METADATA -> {
                    MetadataResults(
                        results = viewModel.metadataResults,
                        onOpen = { navController.navigate(Routes.reader(it.id)) },
                    )
                }
                else -> {
                    FullTextResults(
                        results = viewModel.fullTextResults,
                        onOpen = { navController.navigate(Routes.reader(it.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataResults(
    results: List<DocumentSummary>,
    onOpen: (DocumentSummary) -> Unit,
) {
    if (results.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.MenuBook,
            title = "No matching documents",
            message = "Try a different title, author or switch to full-text search.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(results, key = { it.id }) { doc ->
            ListItem(
                headlineContent = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(doc.author ?: doc.format.label) },
                leadingContent = {
                    CoverImage(doc.coverPath, doc.title, Modifier.width(40.dp).height(56.dp))
                },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .combinedClickable(onClick = { onOpen(doc) }, onLongClick = {}),
            )
        }
    }
}

@Composable
private fun FullTextResults(
    results: List<SearchResultGroup>,
    onOpen: (DocumentSummary) -> Unit,
) {
    if (results.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.MenuBook,
            title = "No matches in content",
            message = "Only indexed documents are searchable. Reindex from Settings → Data management if you imported before indexing.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(results, key = { it.document.id }) { group ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.document.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${group.matches.size} matches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                group.document.author?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                group.matches.take(4).forEach { match ->
                    Text(
                        match.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                TextButton(onClick = { onOpen(group.document) }) { Text("Open") }
            }
            androidx.compose.material3.HorizontalDivider()
        }
    }
}
