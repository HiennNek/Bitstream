package dev.hien.bitstream.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.hien.bitstream.ui.library.LibraryScreenPreview
import dev.hien.bitstream.ui.nowplaying.NowPlayingScreenPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PreviewSnapshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        // Freeze infinite animations (the spinning cover) at their first frame.
        compose.mainClock.autoAdvance = false
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-xhdpi")
    fun libraryPhonePortrait() =
        snapshot("library_phone_portrait") { LibraryScreenPreview() }

    @Test
    @Config(qualifiers = "w851dp-h393dp-xhdpi")
    fun libraryPhoneLandscape() =
        snapshot("library_phone_landscape") { LibraryScreenPreview() }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun libraryTabletPortrait() =
        snapshot("library_tablet_portrait") { LibraryScreenPreview() }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun libraryTabletLandscape() =
        snapshot("library_tablet_landscape") { LibraryScreenPreview() }

    @Test
    @Config(qualifiers = "w393dp-h851dp-xhdpi")
    fun nowPlayingPhonePortrait() =
        snapshot("now_playing_phone_portrait") { NowPlayingScreenPreview() }

    @Test
    @Config(qualifiers = "w851dp-h393dp-xhdpi")
    fun nowPlayingPhoneLandscape() =
        snapshot("now_playing_phone_landscape") { NowPlayingScreenPreview() }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun nowPlayingTabletPortrait() =
        snapshot("now_playing_tablet_portrait") { NowPlayingScreenPreview() }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun nowPlayingTabletLandscape() =
        snapshot("now_playing_tablet_landscape") { NowPlayingScreenPreview() }
}
