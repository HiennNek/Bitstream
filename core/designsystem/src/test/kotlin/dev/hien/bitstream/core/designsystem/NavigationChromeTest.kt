package dev.hien.bitstream.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationChromeTest {
    @Test
    fun `compact width uses bottom bar`() = assertEquals(NavChrome.BAR, navChromeFor(599))

    @Test
    fun `medium width uses rail`() = assertEquals(NavChrome.RAIL, navChromeFor(600))

    @Test
    fun `expanded width uses rail`() = assertEquals(NavChrome.RAIL, navChromeFor(1280))

    @Test
    fun `segmentedItemShape rounds outer edges and tightens inner edges`() {
        assertEquals(RoundedCornerShape(Radii.lg), segmentedItemShape(0, 1))
        assertEquals(
            RoundedCornerShape(topStart = Radii.lg, topEnd = Radii.lg, bottomStart = Spacing.xs, bottomEnd = Spacing.xs),
            segmentedItemShape(0, 3),
        )
        assertEquals(RoundedCornerShape(Spacing.xs), segmentedItemShape(1, 3))
        assertEquals(
            RoundedCornerShape(topStart = Spacing.xs, topEnd = Spacing.xs, bottomStart = Radii.lg, bottomEnd = Radii.lg),
            segmentedItemShape(2, 3),
        )
    }
}
