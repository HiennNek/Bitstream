package dev.hien.bitstream.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class CoverArtShapeTest {
    private val cookies = listOf(
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Cookie7Sided,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Cookie12Sided,
    )

    @Test
    fun sameSeedAlwaysPicksTheSameShape() {
        for (seed in -50L..500L) {
            assertSame(cookieFor(seed), cookieFor(seed))
        }
    }

    @Test
    fun everyShapeIsAStockCookie() {
        for (seed in -50L..500L) {
            assertTrue(cookieFor(seed) in cookies)
        }
    }
}
