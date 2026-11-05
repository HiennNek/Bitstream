package dev.hien.bitstream.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.core.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverArtTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun artworkUriOverloadRendersWithoutArtwork() {
        composeRule.setContent {
            BitstreamTheme {
                Box(Modifier.size(120.dp)) {
                    CoverArt(artworkUri = null, seed = 7L)
                }
            }
        }

        composeRule.onNodeWithTag("cover_art").assertIsDisplayed()
    }

    @Test
    fun trackOverloadRendersWithoutArtwork() {
        composeRule.setContent {
            BitstreamTheme {
                Box(Modifier.size(120.dp)) {
                    CoverArt(
                        track = Track(
                            id = 7,
                            uri = "content://test/7.flac",
                            title = "Paper Lanterns",
                            artworkUri = null,
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("cover_art").assertIsDisplayed()
    }
}
