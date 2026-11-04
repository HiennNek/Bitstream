package dev.hien.bitstream.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationChromeTest {
    @Test
    fun `compact width uses bottom bar`() = assertEquals(NavChrome.BAR, navChromeFor(599))

    @Test
    fun `medium width uses rail`() = assertEquals(NavChrome.RAIL, navChromeFor(600))

    @Test
    fun `expanded width uses rail`() = assertEquals(NavChrome.RAIL, navChromeFor(1280))
}
