package dev.hien.bitstream.ui

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.detailEnter
import dev.hien.bitstream.core.designsystem.detailExit
import dev.hien.bitstream.core.designsystem.detailPopEnter
import dev.hien.bitstream.core.designsystem.detailPopExit
import dev.hien.bitstream.core.designsystem.fadeThroughEnter
import dev.hien.bitstream.core.designsystem.fadeThroughExit
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.core.designsystem.trackHue
import dev.hien.bitstream.ui.adaptive.AdaptiveNavItem
import dev.hien.bitstream.ui.adaptive.AdaptiveNavigationScaffold
import dev.hien.bitstream.ui.library.AlbumScreen
import dev.hien.bitstream.ui.library.ArtistScreen
import dev.hien.bitstream.ui.library.FavoritesRoute
import dev.hien.bitstream.ui.library.LibraryRoute
import dev.hien.bitstream.ui.nowplaying.MiniPlayerBar
import dev.hien.bitstream.ui.nowplaying.NowPlayingRoute
import dev.hien.bitstream.ui.nowplaying.NowPlayingViewModel
import dev.hien.bitstream.ui.playlists.PlaylistsRoute
import dev.hien.bitstream.ui.search.SearchRoute
import dev.hien.bitstream.ui.settings.DiagnosticsScreen
import dev.hien.bitstream.ui.settings.SettingsRoute
import dev.hien.bitstream.ui.settings.ThemePreference
import androidx.compose.foundation.isSystemInDarkTheme

private const val USB_DEBUG_ROUTE = "usb-debug"
private const val NOW_PLAYING_ROUTE = "now-playing"
private const val FAVORITES_ROUTE = "favorites"
private const val ALBUM_DETAIL_ROUTE = "album/{albumId}"
private const val ARTIST_DETAIL_ROUTE = "artist/{artistId}"

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

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BitstreamApp() {
    val container = LocalAppContainer.current
    val nowPlayingViewModel: NowPlayingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NowPlayingViewModel(container.playbackEngine) }
        },
    )
    val nowPlayingState by nowPlayingViewModel.state.collectAsState()

    val themePref by container.settingsStore.themeFlow.collectAsState()
    val albumColors by container.settingsStore.dynamicColorFlow.collectAsState()
    val darkTheme = when (themePref) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val albumHue = nowPlayingState.track?.let {
        trackHue(it.albumArtist, it.album, it.artist, it.title)
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val destinations = Destination.entries
    val selectedIndex = destinations
        .indexOfFirst { it.route == currentRoute }
        .takeIf { it >= 0 }
        ?: when (currentRoute) {
            USB_DEBUG_ROUTE -> destinations.indexOf(Destination.Settings)
            FAVORITES_ROUTE -> destinations.indexOf(Destination.Library)
            ALBUM_DETAIL_ROUTE, ARTIST_DETAIL_ROUTE -> destinations.indexOf(Destination.Search)
            else -> 0
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

    BitstreamTheme(
        darkTheme = darkTheme,
        albumHue = albumHue,
        albumColors = albumColors,
    ) {
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val slide = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
        AdaptiveNavigationScaffold(items = navItems) {
            SharedTransitionLayout {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = Destination.Library.route,
                    ) {
                        composable(
                            route = Destination.Library.route,
                            enterTransition = { fadeThroughEnter(effects) },
                            exitTransition = { fadeThroughExit(effects) },
                            popEnterTransition = { fadeThroughEnter(effects) },
                            popExitTransition = { fadeThroughExit(effects) },
                        ) {
                            LibraryRoute(onTrack = container::playTrack)
                        }
                        composable(
                            route = Destination.Search.route,
                            enterTransition = { fadeThroughEnter(effects) },
                            exitTransition = { fadeThroughExit(effects) },
                            popEnterTransition = { fadeThroughEnter(effects) },
                            popExitTransition = { fadeThroughExit(effects) },
                        ) {
                            SearchRoute(
                                onTrack = container::playTrack,
                                onAlbum = { albumId ->
                                    navController.navigate("album/${Uri.encode(albumId)}")
                                },
                                onArtist = { artistId ->
                                    navController.navigate("artist/${Uri.encode(artistId)}")
                                },
                            )
                        }
                        composable(
                            route = ALBUM_DETAIL_ROUTE,
                            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                            enterTransition = { detailEnter(slide, effects) },
                            exitTransition = { detailExit(slide, effects) },
                            popEnterTransition = { detailPopEnter(slide, effects) },
                            popExitTransition = { detailPopExit(slide, effects) },
                        ) { entry ->
                            val albumId = Uri.decode(entry.arguments?.getString("albumId").orEmpty())
                            AlbumScreen(
                                albumId = albumId,
                                onTrack = container::playTrack,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = ARTIST_DETAIL_ROUTE,
                            arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
                            enterTransition = { detailEnter(slide, effects) },
                            exitTransition = { detailExit(slide, effects) },
                            popEnterTransition = { detailPopEnter(slide, effects) },
                            popExitTransition = { detailPopExit(slide, effects) },
                        ) { entry ->
                            val artistId = Uri.decode(entry.arguments?.getString("artistId").orEmpty())
                            ArtistScreen(
                                artistId = artistId,
                                onTrack = container::playTrack,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = Destination.Playlists.route,
                            enterTransition = { fadeThroughEnter(effects) },
                            exitTransition = { fadeThroughExit(effects) },
                            popEnterTransition = { fadeThroughEnter(effects) },
                            popExitTransition = { fadeThroughExit(effects) },
                        ) {
                            PlaylistsRoute(onTrack = container::playTrack)
                        }
                        composable(
                            route = Destination.Settings.route,
                            enterTransition = { fadeThroughEnter(effects) },
                            exitTransition = { fadeThroughExit(effects) },
                            popEnterTransition = { fadeThroughEnter(effects) },
                            popExitTransition = { fadeThroughExit(effects) },
                        ) {
                            SettingsRoute(
                                onUsbDiagnostics = { navController.navigate(USB_DEBUG_ROUTE) },
                            )
                        }
                        composable(
                            route = USB_DEBUG_ROUTE,
                            enterTransition = { detailEnter(slide, effects) },
                            exitTransition = { detailExit(slide, effects) },
                            popEnterTransition = { detailPopEnter(slide, effects) },
                            popExitTransition = { detailPopExit(slide, effects) },
                        ) {
                            DiagnosticsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = FAVORITES_ROUTE,
                            enterTransition = { detailEnter(slide, effects) },
                            exitTransition = { detailExit(slide, effects) },
                            popEnterTransition = { detailPopEnter(slide, effects) },
                            popExitTransition = { detailPopExit(slide, effects) },
                        ) {
                            FavoritesRoute(
                                onTrack = container::playTrack,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = NOW_PLAYING_ROUTE,
                            enterTransition = { detailEnter(slide, effects) },
                            exitTransition = { detailExit(slide, effects) },
                            popEnterTransition = { detailPopEnter(slide, effects) },
                            popExitTransition = { detailPopExit(slide, effects) },
                        ) {
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
                    enter = slideInVertically(
                        animationSpec = tween(250),
                        initialOffsetY = { it },
                    ) + fadeIn(tween(250)),
                    exit = slideOutVertically(
                        animationSpec = tween(150),
                        targetOffsetY = { it },
                    ) + fadeOut(tween(150)),
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
}
