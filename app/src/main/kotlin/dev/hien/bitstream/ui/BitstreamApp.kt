package dev.hien.bitstream.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.ui.adaptive.AdaptiveNavItem
import dev.hien.bitstream.ui.adaptive.AdaptiveNavigationScaffold
import dev.hien.bitstream.ui.debug.UsbDebugRoute
import dev.hien.bitstream.ui.library.LibraryRoute
import dev.hien.bitstream.ui.nowplaying.MiniPlayerBar
import dev.hien.bitstream.ui.nowplaying.NowPlayingRoute
import dev.hien.bitstream.ui.nowplaying.NowPlayingViewModel
import dev.hien.bitstream.ui.playlists.PlaylistsRoute
import dev.hien.bitstream.ui.search.SearchRoute
import dev.hien.bitstream.ui.settings.SettingsRoute

private const val USB_DEBUG_ROUTE = "usb-debug"
private const val NOW_PLAYING_ROUTE = "now-playing"

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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BitstreamApp() {
    val container = LocalAppContainer.current
    val nowPlayingViewModel = remember(container.playbackEngine) {
        NowPlayingViewModel(container.playbackEngine)
    }
    val nowPlayingState by nowPlayingViewModel.state.collectAsState()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val destinations = Destination.entries
    val selectedIndex = destinations
        .indexOfFirst { it.route == currentRoute }
        .takeIf { it >= 0 }
        ?: if (currentRoute == USB_DEBUG_ROUTE) {
            destinations.indexOf(Destination.Settings)
        } else {
            0
        }

    val navItems = destinations.mapIndexed { index, destination ->
        AdaptiveNavItem(
            label = destination.label,
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
            labelContent = { Text(destination.label) },
        )
    }

    AdaptiveNavigationScaffold(items = navItems) {
        SharedTransitionLayout {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = Destination.Library.route,
                    ) {
                        composable(Destination.Library.route) {
                            LibraryRoute(onTrack = container::playTrack)
                        }
                        composable(Destination.Search.route) {
                            SearchRoute(
                                onTrack = container::playTrack,
                                onAlbum = {},
                                onArtist = {},
                            )
                        }
                        composable(Destination.Playlists.route) {
                            PlaylistsRoute(onTrack = container::playTrack)
                        }
                        composable(Destination.Settings.route) {
                            SettingsRoute(
                                onUsbDiagnostics = { navController.navigate(USB_DEBUG_ROUTE) },
                            )
                        }
                        composable(USB_DEBUG_ROUTE) {
                            UsbDebugRoute(onBack = { navController.popBackStack() })
                        }
                        composable(NOW_PLAYING_ROUTE) {
                            NowPlayingRoute(
                                onBack = { navController.popBackStack() },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = currentRoute != NOW_PLAYING_ROUTE && nowPlayingState.track != null,
                ) {
                    MiniPlayerBar(
                        state = nowPlayingState,
                        onOpenNowPlaying = {
                            navController.navigate(NOW_PLAYING_ROUTE) {
                                launchSingleTop = true
                            }
                        },
                        onPlayPause = nowPlayingViewModel::togglePlayPause,
                        onNext = nowPlayingViewModel::next,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                    )
                }
            }
        }
    }
}
