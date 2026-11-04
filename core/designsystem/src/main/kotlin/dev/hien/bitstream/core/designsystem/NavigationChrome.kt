package dev.hien.bitstream.core.designsystem

/** Which navigation surface the window size class calls for. */
enum class NavChrome {
    BAR,
    RAIL,
}

/**
 * Pure width decision so the rule can be tested without a device. Material's
 * compact breakpoint is 600 dp; medium and expanded both use the rail.
 */
fun navChromeFor(widthDp: Int): NavChrome =
    if (widthDp < 600) NavChrome.BAR else NavChrome.RAIL
