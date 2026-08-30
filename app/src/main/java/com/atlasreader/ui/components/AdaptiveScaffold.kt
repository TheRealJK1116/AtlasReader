package com.atlasreader.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.atlasreader.ui.navigation.TopDestination
import kotlinx.coroutines.launch

/**
 * Adaptive navigation shell (Material 3 guidance):
 *  - compact width (< 600dp):  bottom navigation bar
 *  - medium width  (600–839dp): navigation rail
 *  - expanded width (≥ 840dp):  navigation drawer
 * The reader route is rendered without the shell (full-bleed reading).
 */
@Composable
fun AdaptiveScaffold(
    navController: NavHostController,
    showShell: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val destinations = TopDestination.entries
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val windowWidth = windowWidthDp()

    if (!showShell) {
        content(Modifier.fillMaxSize())
        return
    }

    when {
        windowWidth < 600.dp -> {
            BottomBarScaffold(destinations, currentDestination, navController, content)
        }
        windowWidth < 840.dp -> {
            RailScaffold(destinations, currentDestination, navController, content)
        }
        else -> {
            DrawerScaffold(destinations, currentDestination, navController, content)
        }
    }
}

@Composable
private fun BottomBarScaffold(
    destinations: List<TopDestination>,
    currentDestination: NavDestination?,
    navController: NavHostController,
    content: @Composable (Modifier) -> Unit,
) {
    androidx.compose.material3.Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination.isTopLevel(destination)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigateToTop(destination)
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

@Composable
private fun RailScaffold(
    destinations: List<TopDestination>,
    currentDestination: NavDestination?,
    navController: NavHostController,
    content: @Composable (Modifier) -> Unit,
) {
    androidx.compose.material3.Scaffold(
        content = { innerPadding ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    destinations.forEach { destination ->
                        val selected = currentDestination.isTopLevel(destination)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navController.navigateToTop(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                content(Modifier.padding(innerPadding))
            }
        }
    )
}

@Composable
private fun DrawerScaffold(
    destinations: List<TopDestination>,
    currentDestination: NavDestination?,
    navController: NavHostController,
    content: @Composable (Modifier) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var headerTitle by remember { mutableStateOf("Atlas Reader") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Atlas Reader",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                )
                destinations.forEach { destination ->
                    val selected = currentDestination.isTopLevel(destination)
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = {
                            navController.navigateToTop(destination)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    ) {
        content(Modifier.fillMaxSize())
    }
}

private fun NavDestination?.isTopLevel(destination: TopDestination): Boolean =
    this?.hierarchy?.any { it.route == destination.route } == true

private fun NavHostController.navigateToTop(destination: TopDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Reads the current window width class in dp. */
@Composable
private fun windowWidthDp(): Dp {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return with(LocalDensity.current) { configuration.screenWidthDp.dp }
}
