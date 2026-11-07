package dev.hien.bitstream.ui.adaptive

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
                        if (showNavigation) {
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
                        if (showNavigation) {
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
                                    if (showNavigation) Modifier.padding(start = 16.dp) else Modifier,
                                )
                                .then(
                                    // Rail layouts have no bar to consume the
                                    // system insets (the rail itself intentionally
                                    // bleeds behind the top inset); content scrolls
                                    // under the bottom inset so rows reach the
                                    // window edge beneath the floating mini player.
                                    // Full-screen player layouts handle insets
                                    // themselves when the rail is hidden.
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
