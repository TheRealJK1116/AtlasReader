package com.atlasreader.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atlasreader.ui.collections.CollectionsScreen
import com.atlasreader.ui.components.AdaptiveScaffold
import com.atlasreader.ui.library.LibraryScreen
import com.atlasreader.ui.navigation.Routes
import com.atlasreader.ui.reader.ReaderScreen
import com.atlasreader.ui.search.SearchScreen
import com.atlasreader.ui.settings.SettingsScreen
import com.atlasreader.ui.stats.StatsScreen
import com.atlasreader.ui.theme.AtlasReaderTheme

/**
 * Root composition: theme settings are collected once here so every screen
 * re-themes instantly (dynamic color / light / dark / AMOLED), then the
 * adaptive shell decides bottom bar vs rail vs drawer.
 */
@Composable
fun AtlasApp(rootViewModel: RootViewModel = hiltViewModel()) {
    val themeMode by rootViewModel.themeMode.collectAsState()
    val dynamicColor by rootViewModel.dynamicColor.collectAsState()

    AtlasReaderTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val showShell = backStackEntry?.destination?.isReaderRoute() != true

            AdaptiveScaffold(
                navController = navController,
                showShell = showShell,
            ) { modifier ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.LIBRARY,
                    modifier = modifier.fillMaxSize(),
                ) {
                    composable(Routes.LIBRARY) { LibraryScreen(navController = navController) }
                    composable(Routes.SEARCH) { SearchScreen(navController = navController) }
                    composable(Routes.COLLECTIONS) { CollectionsScreen(navController = navController) }
                    composable(
                        route = "collection/{collectionId}",
                        arguments = listOf(navArgument("collectionId") { type = NavType.LongType }),
                    ) { entry ->
                        val collectionId = entry.arguments?.getLong("collectionId") ?: return@composable
                        com.atlasreader.ui.collections.CollectionDetailScreen(
                            collectionId = collectionId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.STATS) { StatsScreen() }
                    composable(Routes.SETTINGS) { SettingsScreen() }
                    composable(
                        route = Routes.READER,
                        arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
                    ) { ReaderScreen(navController = navController) }
                }
            }
        }
    }
}

private fun NavDestination?.isReaderRoute(): Boolean =
    this?.hierarchy?.any { it.route == Routes.READER } == true
