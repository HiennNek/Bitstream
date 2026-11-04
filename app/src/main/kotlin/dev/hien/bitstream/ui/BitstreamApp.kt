package dev.hien.bitstream.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.NavChrome
import dev.hien.bitstream.core.designsystem.navChromeFor
import dev.hien.bitstream.ui.library.LibraryRoute

private enum class Destination(
    val label: String,
    @DrawableRes val icon: Int,
    val route: String,
) {
    Library("Library", R.drawable.ic_library_music, "library"),
    Search("Search", R.drawable.ic_search, "search"),
    Playlists("Playlists", R.drawable.ic_playlist_play, "playlists"),
    Settings("Settings", R.drawable.ic_settings, "settings"),
}

@Composable
fun BitstreamApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val destinations = Destination.entries
    val selectedIndex = destinations
        .indexOfFirst { it.route == currentRoute }
        .coerceAtLeast(0)

    BoxWithConstraints {
        val chrome = navChromeFor(maxWidth.value.toInt())

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                destinations.forEachIndexed { index, destination ->
                    item(
                        selected = selectedIndex == index,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            },
            layoutType = when (chrome) {
                NavChrome.BAR -> NavigationSuiteType.NavigationBar
                NavChrome.RAIL -> NavigationSuiteType.NavigationRail
            },
        ) {
            NavHost(
                navController = navController,
                startDestination = Destination.Library.route,
            ) {
                composable(Destination.Library.route) {
                    LibraryRoute(onTrack = {})
                }
                composable(Destination.Search.route) {
                    PlaceholderScreen(title = Destination.Search.label)
                }
                composable(Destination.Playlists.route) {
                    PlaceholderScreen(title = Destination.Playlists.label)
                }
                composable(Destination.Settings.route) {
                    PlaceholderScreen(title = Destination.Settings.label)
                }
            }
        }
    }
}
