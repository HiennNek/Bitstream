package dev.hien.bitstream.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.core.model.Album
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun albumTitleIsShownInTheLibrary() {
        composeRule.setContent {
            BitstreamTheme {
                LibraryScreen(
                    state = LibraryUiState(
                        albums = listOf(
                            Album(
                                id = "Coltrane|Blue Train",
                                title = "Blue Train",
                                albumArtist = "Coltrane",
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Blue Train").assertIsDisplayed()
    }

    @Test
    fun emptyLibraryOffersFolderPicker() {
        composeRule.setContent {
            BitstreamTheme {
                LibraryScreen(state = LibraryUiState(), onPickFolder = {})
            }
        }

        composeRule.onNodeWithText("Choose folder").assertIsDisplayed()
    }
}
