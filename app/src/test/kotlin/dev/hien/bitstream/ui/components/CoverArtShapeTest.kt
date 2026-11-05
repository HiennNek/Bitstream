package dev.hien.bitstream.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class CoverArtShapeTest {
    @Test
    fun sameSeedAlwaysPicksTheSameShapes() {
        for (seed in 0L..99L) {
            assertEquals(coverShape(seed), coverShape(seed))
            assertEquals(innerCoverShape(seed), innerCoverShape(seed))
        }
    }

    @Test
    fun shapesComeFromTheMaterialShapeSets() {
        val outers = listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.Flower,
            MaterialShapes.Puffy,
            MaterialShapes.Burst,
        )
        val inners = listOf(
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.Sunny,
            MaterialShapes.PuffyDiamond,
        )
        for (seed in 0L..99L) {
            assertTrue(coverShape(seed) in outers)
            assertTrue(innerCoverShape(seed) in inners)
        }
    }
}
