package dev.hien.bitstream.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CoverArtSnapshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent(content)
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w1300dp-h340dp-xhdpi")
    fun light290() = snapshot("cover_art_290_light") {
        CoverArtAngleGrid(dark = false, size = 290.dp)
    }

    @Test
    @Config(qualifiers = "w1300dp-h340dp-xhdpi")
    fun dark290() = snapshot("cover_art_290_dark") {
        CoverArtAngleGrid(dark = true, size = 290.dp)
    }

    @Test
    @Config(qualifiers = "w260dp-h80dp-xhdpi")
    fun light40() = snapshot("cover_art_40_light") {
        CoverArtAngleGrid(dark = false, size = 40.dp)
    }

    @Test
    @Config(qualifiers = "w260dp-h80dp-xhdpi")
    fun dark40() = snapshot("cover_art_40_dark") {
        CoverArtAngleGrid(dark = true, size = 40.dp)
    }
}
