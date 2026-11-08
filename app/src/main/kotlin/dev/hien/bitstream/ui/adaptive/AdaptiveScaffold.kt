package dev.hien.bitstream.ui.adaptive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import dev.hien.bitstream.core.designsystem.NavChrome
import dev.hien.bitstream.core.designsystem.navChromeFor

data class AdaptiveNavItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit,
    val labelContent: @Composable () -> Unit,
)

/**
 * Window-size-aware scaffold wrapper exposing [WindowSizeClass] from
 * [currentWindowAdaptiveInfo].
 */
@Composable
fun AdaptiveScaffold(
    content: @Composable (WindowSizeClass) -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    content(adaptiveInfo.windowSizeClass)
}

/**
 * Switches between bottom [NavigationBar] on compact widths (< 600 dp) and
 * side [NavigationRail] on medium/expanded widths (>= 600 dp), attaching
 * `navigation_bar` and `navigation_rail` semantics test tags. Hidden entirely
 * when [showNavigation] is false.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdaptiveNavigationScaffold(
    items: List<AdaptiveNavItem>,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    content: @Composable () -> Unit,
) {
    AdaptiveScaffold { _ ->
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val short = maxHeight < 600.dp
            val chrome = if (short && maxWidth >= 600.dp) {
                NavChrome.RAIL
            } else {
                navChromeFor(maxWidth.value.toInt())
            }
            // Chrome dismiss/restore rides the theme's fast effects spring
            // (critically damped, no bounce) with a critically damped slide.
            val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val slide = spring<IntSize>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
            when (chrome) {
                NavChrome.BAR -> {
                    // Keep content in the same slot whether or not the bar is
                    // shown; swapping the whole subtree would recreate the NavHost
                    // and kill its transition when the player opens.
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            content()
                        }
                        // The bar slides down out of sight when the player takes
                        // the screen (M3's chrome-dismiss pattern) instead of
                        // snapping away (expandVertically anchors to the bottom).
                        AnimatedVisibility(
                            visible = showNavigation,
                            enter = expandVertically(animationSpec = slide) + fadeIn(fade),
                            exit = shrinkVertically(animationSpec = slide) + fadeOut(fade),
                        ) {
                            ShortNavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.testTag("navigation_bar"),
                            ) {
                                items.forEach { item ->
                                    ShortNavigationBarItem(
                                        selected = item.selected,
                                        onClick = item.onClick,
                                        icon = item.icon,
                                        label = item.labelContent,
                                    )
                                }
                            }
                        }
                    }
                }

                NavChrome.RAIL -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Same dismiss pattern as the bar. expandHorizontally/
                        // shrinkHorizontally default to Alignment.End — the
                        // content side — so anchor the collapse to the leading
                        // window edge explicitly.
                        AnimatedVisibility(
                            visible = showNavigation,
                            enter = expandHorizontally(animationSpec = slide, expandFrom = Alignment.Start) + fadeIn(fade),
                            exit = shrinkHorizontally(animationSpec = slide, shrinkTowards = Alignment.Start) + fadeOut(fade),
                        ) {
                            // Rail draws at the true window edges: the parent
                            // consumes no top inset in rail layouts, so its
                            // container color fills the status-bar / caption
                            // zone like the system apps' side panels.
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .width(if (short) 80.dp else 96.dp)
                                    .fillMaxHeight()
                                    .testTag("navigation_rail"),
                            ) {
                                Spacer(modifier = Modifier.height(if (short) 12.dp else 40.dp))
                                items.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.height(if (short) 6.dp else 20.dp))
                                    }
                                    NavigationRailItem(
                                        selected = item.selected,
                                        onClick = item.onClick,
                                        icon = item.icon,
                                        label = item.labelContent,
                                        modifier = Modifier.height(if (short) 64.dp else 68.dp),
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    // Gutter between the rail and content; screens
                                    // carry their own 16 dp start padding, so this
                                    // evens the left gap with the ~24 dp right one.
                                    // Animated with the rail so content doesn't
                                    // jump 16 dp ahead of the collapsing panel.
                                    Modifier.padding(
                                        start = animateDpAsState(
                                            targetValue = if (showNavigation) 16.dp else 0.dp,
                                            // Same physics as the rail's shrink so
                                            // the gutter tracks the panel edge.
                                            animationSpec = spring<Dp>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                                            label = "railGutter",
                                        ).value,
                                    ),
                                )
                                .then(
                                    // Rail layouts have no bar to consume the
                                    // system insets (the rail itself intentionally
                                    // bleeds behind the top inset); content scrolls
                                    // under the bottom inset so rows reach the
                                    // window edge beneath the floating mini player.
                                    // Full-screen player layouts handle insets
                                    // themselves when the rail is hidden. This and
                                    // the outer scaffold's top pad are a balanced
                                    // pair: when the rail hides, the outer one adds
                                    // the same inset this one drops, so animating
                                    // either side alone would double-count mid-flight.
                                    if (showNavigation) {
                                        Modifier.windowInsetsPadding(
                                            WindowInsets.safeDrawing.only(
                                                WindowInsetsSides.Top,
                                            ),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}
