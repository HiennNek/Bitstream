package dev.hien.bitstream.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MotionObserverTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun coversShareOneMotionSettingObserver() {
        compose.setContent {
            Row {
                repeat(5) { index ->
                    CoverArt(
                        artworkUri = null,
                        seed = index.toLong(),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        assertEquals(1, shadowOf(context.contentResolver).getContentObservers(uri).size)
    }
}
