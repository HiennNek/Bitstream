package dev.hien.bitstream.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
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
@Composable
fun AdaptiveNavigationScaffold(
    items: List<AdaptiveNavItem>,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    content: @Composable () -> Unit,
) {
    AdaptiveScaffold { _ ->
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val chrome = navChromeFor(maxWidth.value.toInt())
            val short = maxHeight < 600.dp
            if (!showNavigation) {
                Box(modifier = Modifier.fillMaxSize()) { content() }
                return@BoxWithConstraints
            }
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
                                    icon = {
                                        NavPill(
                                            selected = item.selected,
                                            width = 60.dp,
                                            height = 30.dp,
                                            radius = 15.dp,
                                        ) {
                                            item.icon()
                                        }
                                    },
                                    label = item.labelContent,
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color.Transparent,
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    icon = {
                                        NavPill(
                                            selected = item.selected,
                                            width = if (short) 56.dp else 56.dp,
                                            height = if (short) 30.dp else 32.dp,
                                            radius = if (short) 15.dp else 16.dp,
                                        ) {
                                            item.icon()
                                        }
                                    },
                                    label = item.labelContent,
                                    colors = NavigationRailItemDefaults.colors(
                                        indicatorColor = Color.Transparent,
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    modifier = Modifier.height(if (short) 64.dp else 68.dp),
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

@Composable
private fun NavPill(
    selected: Boolean,
    width: Dp,
    height: Dp,
    radius: Dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(radius),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        contentColor = LocalContentColor.current,
        modifier = Modifier.size(width = width, height = height),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}
