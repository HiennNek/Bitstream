package dev.hien.bitstream.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.NavChrome
import dev.hien.bitstream.core.designsystem.navChromeFor

private enum class Destination(
    val label: String,
    @DrawableRes val icon: Int,
) {
    Library("Library", R.drawable.ic_library_music),
    Search("Search", R.drawable.ic_search),
    Playlists("Playlists", R.drawable.ic_playlist_play),
    Settings("Settings", R.drawable.ic_settings),
}

@Composable
fun BitstreamApp() {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val destinations = Destination.entries

    BoxWithConstraints {
        val chrome = navChromeFor(maxWidth.value.toInt())
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                destinations.forEachIndexed { index, destination ->
                    item(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
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
            PlaceholderScreen(title = destinations[selectedIndex].label)
        }
    }
}
