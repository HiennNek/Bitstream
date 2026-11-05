package dev.hien.bitstream.ui.adaptive

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.hien.bitstream.BitstreamApp as BitstreamApplication
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.ui.BitstreamApp
import dev.hien.bitstream.ui.nowplaying.NowPlayingRoute
import org.junit.Rule
import org.junit.Test

class AdaptiveLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compact_width_shows_bottom_navigation() {
        val app = ApplicationProvider.getApplicationContext<BitstreamApplication>()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                BitstreamTheme {
                    DeviceConfigurationOverride(
                        DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp)),
                    ) {
                        BitstreamApp()
                    }
                }
            }
        }
        composeRule.onNodeWithTag("navigation_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_rail").assertDoesNotExist()
    }

    @Test
    fun expanded_width_shows_navigation_rail() {
        val app = ApplicationProvider.getApplicationContext<BitstreamApplication>()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                BitstreamTheme {
                    DeviceConfigurationOverride(
                        DeviceConfigurationOverride.ForcedSize(DpSize(1280.dp, 800.dp)),
                    ) {
                        BitstreamApp()
                    }
                }
            }
        }
        composeRule.onNodeWithTag("navigation_rail").assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_bar").assertDoesNotExist()
    }

    @Test
    fun expanded_width_shows_two_pane_player() {
        val app = ApplicationProvider.getApplicationContext<BitstreamApplication>()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                BitstreamTheme {
                    DeviceConfigurationOverride(
                        DeviceConfigurationOverride.ForcedSize(DpSize(1280.dp, 800.dp)),
                    ) {
                        NowPlayingRoute()
                    }
                }
            }
        }
        composeRule.onNodeWithTag("now_playing_song_list").assertIsDisplayed()
        composeRule.onNodeWithTag("now_playing_pane").assertIsDisplayed()
    }
}
