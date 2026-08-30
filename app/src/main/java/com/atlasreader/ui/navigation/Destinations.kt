package com.atlasreader.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level destinations and the reader route. */
object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val COLLECTIONS = "collections"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val READER = "reader/{documentId}"

    fun reader(documentId: Long) = "reader/$documentId"
}

enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    LIBRARY(Routes.LIBRARY, "Library", Icons.Outlined.LibraryBooks),
    SEARCH(Routes.SEARCH, "Search", Icons.Outlined.Search),
    COLLECTIONS(Routes.COLLECTIONS, "Collections", Icons.Outlined.CollectionsBookmark),
    STATS(Routes.STATS, "Stats", Icons.Outlined.BarChart),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
}
