package dev.hien.bitstream.ui

import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEvent.Companion.EDGE_NONE
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
import dev.hien.bitstream.ui.components.rememberArtworkHue
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
    val artworkHue = rememberArtworkHue(nowPlayingState.track?.artworkUri)
    // Fall back to the generated cover's hue while artwork decodes, and for
    // tracks without embedded art, so the theme never flashes to the wallpaper.
    // Same per-track draw the cookie uses, so app color == cookie color.
    val albumHue = artworkHue ?: nowPlayingState.track?.let { trackHue(it.id) }

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
                if (currentRoute != destination.route) {
                    val isStart = destination.route == navController.graph.startDestinationRoute
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            // The player is a global overlay: never fold it into a
                            // tab's saved state. The start destination is surfaced
                            // from the bottom of the stack instead of restored, so a
                            // stale saved tab stack cannot replay on top of it.
                            saveState = !isStart && currentRoute != NOW_PLAYING_ROUTE
                        }
                        launchSingleTop = true
                        restoreState = !isStart
                    }
                }
            },
            icon = {
                Icon(
                    painter = painterResource(destination.icon),
                    contentDescription = destination.label,
                )
            },
            labelContent = {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selectedIndex == index) FontWeight(700) else FontWeight(500),
                )
            },
        )
    }

    BitstreamTheme(
        darkTheme = darkTheme,
        albumHue = albumHue,
        albumColors = albumColors,
    ) {
        // Firefox-style window chrome: the system paints the window background
        // (not Compose) behind the freeform caption bar and in the rounded
        // corners, so keep it in sync with the themed surface or it flashes
        // the static launch color whenever artwork theming retints the app.
        val activity = LocalContext.current as? ComponentActivity
        val surfaceColor = MaterialTheme.colorScheme.surface
        LaunchedEffect(activity, surfaceColor) {
            activity?.window?.setBackgroundDrawable(
                ColorDrawable(surfaceColor.toArgb()),
            )
        }
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val slide = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
        // Exit slides must not overshoot: the expressive spatial spring has a
        // 0.8 damping ratio, which would bounce the leaving screen back inward.
        val exitSlide =
            spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
        // Same depth collapse for predictive-back gestures so the gesture
        // preview and the committed pop read identically, with the screen
        // exiting toward the grabbed edge: right-edge swipe exits right (+1),
        // left-edge swipe exits left (-1), like the system predictive back.
        // The committed pop resolves through the classic popExit path
        // (inPredictiveBack is already false on commit), so the last gesture
        // edge is recorded here and both paths read the same direction.
        val lastSwipeEdge = remember { mutableIntStateOf(EDGE_NONE) }
        fun swipeExitDirection(edge: Int) = if (edge == EDGE_LEFT) -1 else 1
        val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition =
            { detailPopEnter(spatial, effects) }
        val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition =
            { swipeEdge ->
                lastSwipeEdge.intValue = swipeEdge
                detailPopExit(exitSlide, spatial, exitDirection = swipeExitDirection(swipeEdge))
            }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            val showNavigation = currentRoute != NOW_PLAYING_ROUTE ||
                (maxWidth >= 840.dp && maxHeight >= 600.dp)
            // Rail layouts bleed the rail's container color to the true window
            // edges — behind the status bar in fullscreen and the caption zone
            // in freeform — so the left panel reads full-height like system
            // apps. The scaffold then consumes only horizontal insets; the rail
            // branch pads its content below the top inset itself.
            val railChrome = showNavigation && maxWidth >= 600.dp
            if (railChrome) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (maxHeight < 600.dp) 80.dp else 96.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .align(Alignment.CenterStart),
                )
            }
            AdaptiveNavigationScaffold(
                items = navItems,
                showNavigation = showNavigation,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (railChrome) {
                            WindowInsetsSides.Horizontal
                        } else {
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        },
                    ),
                ),
            ) {
            SharedTransitionLayout {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                        navController = navController,
                        startDestination = Destination.Library.route,
                        predictivePopEnterTransition = predictivePopEnter,
                        predictivePopExitTransition = predictivePopExit,
                    ) {
                        composable(
                            route = Destination.Library.route,
                            enterTransition = { fadeThroughEnter(effects) },
                            exitTransition = { fadeThroughExit(effects) },
                            popEnterTransition = { fadeThroughEnter(effects) },
                            popExitTransition = { fadeThroughExit(effects) },
                        ) {
                            LibraryRoute(
                                onTrack = container::playTrack,
                                onSearch = {
                                    navController.navigate(Destination.Search.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onFavorites = {
                                    navController.navigate(FAVORITES_ROUTE) { launchSingleTop = true }
                                },
                                onSettings = {
                                    navController.navigate(Destination.Settings.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
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
                            popEnterTransition = { detailPopEnter(spatial, effects) },
                            popExitTransition = { detailPopExit(exitSlide, spatial, exitDirection = swipeExitDirection(lastSwipeEdge.intValue)) },
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
                            popEnterTransition = { detailPopEnter(spatial, effects) },
                            popExitTransition = { detailPopExit(exitSlide, spatial, exitDirection = swipeExitDirection(lastSwipeEdge.intValue)) },
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
                            popEnterTransition = { detailPopEnter(spatial, effects) },
                            popExitTransition = { detailPopExit(exitSlide, spatial, exitDirection = swipeExitDirection(lastSwipeEdge.intValue)) },
                        ) {
                            DiagnosticsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            route = FAVORITES_ROUTE,
                            enterTransition = { detailEnter(slide, effects) },
                            exitTransition = { detailExit(slide, effects) },
                            popEnterTransition = { detailPopEnter(spatial, effects) },
                            popExitTransition = { detailPopExit(exitSlide, spatial, exitDirection = swipeExitDirection(lastSwipeEdge.intValue)) },
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
                            popEnterTransition = { detailPopEnter(spatial, effects) },
                            popExitTransition = { detailPopExit(exitSlide, spatial, exitDirection = swipeExitDirection(lastSwipeEdge.intValue)) },
                        ) {
                            NowPlayingRoute(
                                onBack = { navController.popBackStack() },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable,
                            )
                        }
                    }
                }

                // Floating mini player: overlays the content instead of reserving
                // a row, so list items flow underneath it to the window edge.
                AnimatedVisibility(
                    visible = currentRoute != NOW_PLAYING_ROUTE && nowPlayingState.track != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    // Theme springs, so the mini player bar enters/exits on the
                    // same physics instead of legacy fixed-duration tweens; the
                    // exit uses a critically-damped spring so the bar drops out
                    // without bouncing back up.
                    enter = slideInVertically(
                        animationSpec = slide,
                        initialOffsetY = { it },
                    ) + fadeIn(effects),
                    exit = slideOutVertically(
                        animationSpec = exitSlide,
                        targetOffsetY = { it },
                    ) + fadeOut(effects),
                ) {
                    // Collect the fast transport flows here so the 100 ms tick
                    // recomposes only the mini player, not the whole app.
                    val miniPosition by nowPlayingViewModel.positionMs.collectAsState()
                    val miniDuration by nowPlayingViewModel.durationMs.collectAsState()
                    MiniPlayerBar(
                        state = nowPlayingState.copy(
                            position = miniPosition,
                            duration = miniDuration,
                        ),
                        onOpenNowPlaying = {
                            navController.navigate(NOW_PLAYING_ROUTE) {
                                launchSingleTop = true
                            }
                        },
                        onPlayPause = nowPlayingViewModel::togglePlayPause,
                        onPrevious = nowPlayingViewModel::previous,
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
