package dev.hien.bitstream.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
 * `navigation_bar` and `navigation_rail` semantics test tags.
 */
@Composable
fun AdaptiveNavigationScaffold(
    items: List<AdaptiveNavItem>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AdaptiveScaffold { _ ->
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val chrome = navChromeFor(maxWidth.value.toInt())
            when (chrome) {
                NavChrome.BAR -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            content()
                        }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.testTag("navigation_bar"),
                        ) {
                            items.forEach { item ->
                                NavigationBarItem(
                                    selected = item.selected,
                                    onClick = item.onClick,
                                    icon = item.icon,
                                    label = item.labelContent,
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }

                NavChrome.RAIL -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("navigation_rail"),
                        ) {
                            items.forEach { item ->
                                NavigationRailItem(
                                    selected = item.selected,
                                    onClick = item.onClick,
                                    icon = item.icon,
                                    label = item.labelContent,
                                    colors = NavigationRailItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}
