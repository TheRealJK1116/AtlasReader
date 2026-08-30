package com.atlasreader.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.atlasreader.core.database.LibraryFilter
import com.atlasreader.core.database.LibrarySort
import com.atlasreader.core.database.ReadingStatusFilter
import com.atlasreader.core.engine.DocumentFormat
import com.atlasreader.domain.model.DocumentSummary
import com.atlasreader.ui.components.CoverImage
import com.atlasreader.ui.components.EmptyState
import com.atlasreader.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val items = viewModel.library.collectAsLazyPagingItems()
    val continueReading by viewModel.continueReadingFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }

    val collections by viewModel.collectionsFlow.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importUris(uris) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importFolderUri(uri)
        }
    }

    // Auto-open freshly imported documents.
    LaunchedEffect(Unit) {
        viewModel.autoOpen.collect { documentId ->
            navController.navigate(Routes.reader(documentId))
        }
    }

    // Import completion snackbar.
    LaunchedEffect(importState) {
        if (!importState.running && importState.done > 0 && importState.total > 0) {
            snackbarHostState.showSnackbar(
                "Imported ${importState.imported}, ${importState.duplicates} duplicates, " +
                    "${importState.failed} failed"
            )
        }
    }

    Scaffold(
        topBar = {
            if (viewModel.selectionMode) {
                SelectionTopBar(
                    count = viewModel.selectedIds.size,
                    onClose = { viewModel.exitSelection() },
                    onFavorite = { viewModel.favoriteSelected(true) },
                    onDelete = { viewModel.deleteSelected() },
                    onAddToCollection = { showCollectionPicker = true },
                )
            } else {
                TopAppBar(
                    title = { Text("Library") },
                    navigationIcon = {
                        IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Outlined.ImportExport, contentDescription = "Import")
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search")
                        }
                        Box {
                            IconButton(onClick = { showViewMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = showViewMenu, onDismissRequest = { showViewMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Sort") },
                                    leadingIcon = { Icon(Icons.Outlined.Sort, null) },
                                    onClick = { showViewMenu = false; showSortSheet = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Filter") },
                                    leadingIcon = { Icon(Icons.Outlined.FilterList, null) },
                                    onClick = { showViewMenu = false; showFilterSheet = true },
                                )
                                HorizontalDivider()
                                Text(
                                    "View",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                LibraryViewMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label()) },
                                        leadingIcon = {
                                            Icon(
                                                if (mode.isList()) Icons.Outlined.ViewList else Icons.Outlined.ViewModule,
                                                null,
                                            )
                                        },
                                        onClick = {
                                            showViewMenu = false
                                            viewModel.setViewMode(mode)
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (importState.running) {
                ImportBanner(importState.done, importState.total, importState.currentFile)
            }
            if (items.itemCount == 0 && items.loadState.refresh is androidx.paging.LoadState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (items.itemCount == 0 && items.loadState.refresh is androidx.paging.LoadState.Error) {
                EmptyState(
                    icon = Icons.Outlined.MenuBook,
                    title = "Could not load the library",
                    message = "Check that imported files are still on the device.",
                )
            } else {
                LibraryBody(
                    items = items,
                    continueReading = continueReading,
                    viewMode = viewModel.viewMode,
                    selectionMode = viewModel.selectionMode,
                    selectedIds = viewModel.selectedIds,
                    onOpen = { navController.navigate(Routes.reader(it.id)) },
                    onToggleSelection = { viewModel.toggleSelection(it) },
                    onEnterSelection = { viewModel.enterSelection(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    emptyContent = {
                        EmptyState(
                            icon = Icons.Outlined.MenuBook,
                            title = "Your library is empty",
                            message = "Import EPUB, PDF, Markdown, TXT, RTF or HTML files to start reading.",
                        )
                    },
                )
            }
        }
    }

    if (showSortSheet) {
        SortSheet(
            current = sort,
            onSelect = { viewModel.setSort(it); showSortSheet = false },
            onDismiss = { showSortSheet = false },
        )
    }

    if (showFilterSheet) {
        FilterSheet(
            current = filter,
            onApply = { newFilter ->
                viewModel.updateFilter { newFilter }
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showCollectionPicker) {
        CollectionPickerSheet(
            collections = collections,
            onPick = { id -> viewModel.addSelectedToCollection(id) },
            onDismiss = { showCollectionPicker = false },
        )
    }
}

@Composable
private fun ImportBanner(done: Int, total: Int, current: String?) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Importing… ${current.orEmpty()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBody(
    items: LazyPagingItems<DocumentSummary>,
    continueReading: List<DocumentSummary>,
    viewMode: LibraryViewMode,
    selectionMode: Boolean,
    selectedIds: List<Long>,
    onOpen: (DocumentSummary) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onToggleFavorite: (DocumentSummary) -> Unit,
    emptyContent: @Composable () -> Unit,
) {
    when (viewMode) {
        LibraryViewMode.GRID, LibraryViewMode.COMPACT_GRID -> {
            val minSize = if (viewMode == LibraryViewMode.COMPACT_GRID) 92.dp else 116.dp
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    ContinueReadingRow(continueReading, onOpen, selectionMode, onToggleSelection)
                }
                items(items.itemCount, key = items.itemKey { it.id }) { index ->
                    val doc = items[index] ?: return@items
                    LibraryCard(
                        doc = doc,
                        compact = viewMode == LibraryViewMode.COMPACT_GRID,
                        selected = doc.id in selectedIds,
                        selectionMode = selectionMode,
                        onClick = { if (selectionMode) onToggleSelection(doc.id) else onOpen(doc) },
                        onLongClick = { onEnterSelection(doc.id) },
                        onToggleFavorite = { onToggleFavorite(doc) },
                    )
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    if (items.itemCount == 0) emptyContent()
                    else if (items.loadState.append is androidx.paging.LoadState.Loading) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
        LibraryViewMode.LIST, LibraryViewMode.DETAILED -> {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    ContinueReadingRow(continueReading, onOpen, selectionMode, onToggleSelection)
                }
                items(items.itemCount, key = items.itemKey { it.id }) { index ->
                    val doc = items[index] ?: return@items
                    LibraryListRow(
                        doc = doc,
                        detailed = viewMode == LibraryViewMode.DETAILED,
                        selected = doc.id in selectedIds,
                        selectionMode = selectionMode,
                        onClick = { if (selectionMode) onToggleSelection(doc.id) else onOpen(doc) },
                        onLongClick = { onEnterSelection(doc.id) },
                    )
                }
                item {
                    if (items.itemCount == 0) emptyContent()
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingRow(
    documents: List<DocumentSummary>,
    onOpen: (DocumentSummary) -> Unit,
    selectionMode: Boolean,
    onToggleSelection: (Long) -> Unit,
) {
    if (documents.isEmpty() || selectionMode) return
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            "Continue reading",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
            items(documents, key = { it.id }) { doc ->
                Column(Modifier.width(110.dp).combinedClickable(onClick = { onOpen(doc) }, onLongClick = { onToggleSelection(doc.id) })) {
                    CoverImage(doc.coverPath, doc.title, Modifier.width(110.dp).height(158.dp))
                    Text(
                        doc.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    doc.progressLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    doc: DocumentSummary,
    compact: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box {
        Column(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .then(
                    if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    else Modifier
                )
                .padding(4.dp)
        ) {
            val coverHeight = if (compact) 132.dp else 168.dp
            Box {
                CoverImage(doc.coverPath, doc.title, Modifier.fillMaxWidth().height(coverHeight))
                if (doc.favorite) {
                    Icon(
                        Icons.Outlined.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                doc.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            doc.author?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (doc.progressPercent > 0f && doc.progressPercent < 99.5f) {
                LinearProgressIndicator(
                    progress = { doc.progressPercent.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp),
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(22.dp),
            )
        }
    }
}

@Composable
private fun LibraryListRow(
    doc: DocumentSummary,
    detailed: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(doc.title, maxLines = if (detailed) 2 else 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            if (detailed) {
                Column {
                    doc.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    doc.progressLabel?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            } else {
                Text(listOfNotNull(doc.author, doc.progressLabel).joinToString(" · "))
            }
        },
        leadingContent = {
            CoverImage(
                doc.coverPath,
                doc.title,
                Modifier.width(if (detailed) 54.dp else 40.dp).height(if (detailed) 76.dp else 56.dp),
            )
        },
        trailingContent = {
            if (selected) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            } else if (doc.favorite) {
                Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onAddToCollection: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close selection") }
        },
        actions = {
            IconButton(onClick = onFavorite) { Icon(Icons.Outlined.FavoriteBorder, "Favorite") }
            IconButton(onClick = onAddToCollection) { Icon(Icons.Outlined.Checklist, "Add to collection") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}

@Composable
private fun SortSheet(
    current: LibrarySort,
    onSelect: (LibrarySort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Sort by", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        LibrarySort.entries.forEach { sort ->
            ListItem(
                headlineContent = { Text(sort.label()) },
                leadingContent = {
                    if (sort == current) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.combinedClickable(onClick = { onSelect(sort) }, onLongClick = {}),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FilterSheet(
    current: LibraryFilter,
    onApply: (LibraryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var favoritesOnly by remember { mutableStateOf(current.favoritesOnly) }
    var status by remember { mutableStateOf(current.status) }
    var formats by remember { mutableStateOf(current.formats) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Status", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingStatusFilter.entries.forEach { s ->
                    FilterChip(
                        selected = status == s,
                        onClick = { status = s },
                        label = { Text(s.label()) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Format", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentFormat.entries.forEach { format ->
                    FilterChip(
                        selected = format in formats,
                        onClick = {
                            formats = if (format in formats) formats - format else formats + format
                        },
                        label = { Text(format.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Favourites only") },
                leadingIcon = {
                    Icon(
                        if (favoritesOnly) Icons.Outlined.Star else Icons.Outlined.Star,
                        null,
                        Modifier.size(18.dp),
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        onApply(
                            LibraryFilter(
                                query = current.query,
                                favoritesOnly = favoritesOnly,
                                status = status,
                                formats = formats,
                                collectionId = current.collectionId,
                                tagId = current.tagId,
                            )
                        )
                    }
                ) { Text("Apply") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CollectionPickerSheet(
    collections: List<com.atlasreader.data.repository.CollectionWithCount>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Add to collection", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        if (collections.isEmpty()) {
            Text(
                "No collections yet — create one on the Collections tab.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        collections.forEach { collection ->
            ListItem(
                headlineContent = { Text(collection.name) },
                supportingContent = { Text("${collection.documentCount} documents") },
                modifier = Modifier.combinedClickable(onClick = { onPick(collection.id); onDismiss() }, onLongClick = {}),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun LibrarySort.label(): String = when (this) {
    LibrarySort.TITLE -> "Title"
    LibrarySort.AUTHOR -> "Author"
    LibrarySort.DATE_ADDED -> "Date added"
    LibrarySort.DATE_OPENED -> "Date opened"
    LibrarySort.PROGRESS -> "Progress"
    LibrarySort.SIZE -> "File size"
}

private fun ReadingStatusFilter.label(): String = when (this) {
    ReadingStatusFilter.ANY -> "Any"
    ReadingStatusFilter.UNREAD -> "Unread"
    ReadingStatusFilter.READING -> "Reading"
    ReadingStatusFilter.FINISHED -> "Finished"
}

private fun LibraryViewMode.label(): String = when (this) {
    LibraryViewMode.GRID -> "Grid"
    LibraryViewMode.COMPACT_GRID -> "Compact grid"
    LibraryViewMode.LIST -> "List"
    LibraryViewMode.DETAILED -> "Detailed list"
}

private fun LibraryViewMode.isList(): Boolean = this == LibraryViewMode.LIST || this == LibraryViewMode.DETAILED
