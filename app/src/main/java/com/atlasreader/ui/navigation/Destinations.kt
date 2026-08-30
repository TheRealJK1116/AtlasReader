package com.atlasreader.ui.navigation

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
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    LIBRARY(Routes.LIBRARY, "Library", androidx.compose.material.icons.Icons.Outlined.LibraryBooks),
    SEARCH(Routes.SEARCH, "Search", androidx.compose.material.icons.Icons.Outlined.Search),
    COLLECTIONS(Routes.COLLECTIONS, "Collections", androidx.compose.material.icons.Icons.Outlined.CollectionsBookmark),
    STATS(Routes.STATS, "Stats", androidx.compose.material.icons.Icons.Outlined.BarChart),
    SETTINGS(Routes.SETTINGS, "Settings", androidx.compose.material.icons.Icons.Outlined.Settings),
}
