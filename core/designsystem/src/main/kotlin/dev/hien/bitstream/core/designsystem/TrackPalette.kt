package dev.hien.bitstream.core.designsystem

/**
 * The mockup's color roles, generated from a single hue. Values are ARGB ints so
 * this stays free of Compose and can be unit-tested.
 */
data class TrackPalette(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val surface: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outlineVariant: Int,
)

private const val PRIMARY = 0.16
private const val SECONDARY = 0.05
private const val TERTIARY = 0.11
private const val NEUTRAL = 0.012
private const val VARIANT = 0.022

private const val LIGHT_PRIMARY = 40.0
private const val DARK_PRIMARY = 80.0
private const val LIGHT_CONTAINER = 90.0
private const val DARK_CONTAINER = 30.0
private const val LIGHT_ON = 100.0
private const val DARK_ON = 20.0
private const val LIGHT_ON_CONTAINER = 10.0
private const val DARK_ON_CONTAINER = 90.0
private const val LIGHT_SURFACE = 98.0
private const val DARK_SURFACE = 6.0
private const val LIGHT_SURFACE_CONTAINER = 94.0
private const val DARK_SURFACE_CONTAINER = 12.0
private const val LIGHT_SURFACE_HIGH = 92.0
private const val DARK_SURFACE_HIGH = 17.0
private const val LIGHT_SURFACE_HIGHEST = 90.0
private const val DARK_SURFACE_HIGHEST = 22.0
private const val LIGHT_ON_SURFACE = 10.0
private const val DARK_ON_SURFACE = 90.0
private const val LIGHT_ON_SURFACE_VARIANT = 30.0
private const val DARK_ON_SURFACE_VARIANT = 80.0
private const val LIGHT_OUTLINE_VARIANT = 80.0
private const val DARK_OUTLINE_VARIANT = 30.0

fun trackPalette(hue: Int, dark: Boolean): TrackPalette {
    val base = hue.toDouble()
    val tertiaryHue = (hue + 60) % 360
    fun role(chroma: Double, roleHue: Double, light: Double, darkTone: Double): Int =
        oklchToArgb(
            tone = lightness(if (dark) darkTone else light),
            chroma = chroma,
            hueDegrees = roleHue,
        )

    return TrackPalette(
        primary = role(PRIMARY, base, LIGHT_PRIMARY, DARK_PRIMARY),
        onPrimary = role(PRIMARY, base, LIGHT_ON, DARK_ON),
        primaryContainer = role(PRIMARY, base, LIGHT_CONTAINER, DARK_CONTAINER),
        onPrimaryContainer = role(PRIMARY, base, LIGHT_ON_CONTAINER, DARK_ON_CONTAINER),
        secondary = role(SECONDARY, base, LIGHT_PRIMARY, DARK_PRIMARY),
        onSecondary = role(SECONDARY, base, LIGHT_ON, DARK_ON),
        secondaryContainer = role(SECONDARY, base, LIGHT_CONTAINER, DARK_CONTAINER),
        onSecondaryContainer = role(SECONDARY, base, LIGHT_ON_CONTAINER, DARK_ON_CONTAINER),
        tertiary = role(TERTIARY, tertiaryHue.toDouble(), LIGHT_PRIMARY, DARK_PRIMARY),
        onTertiary = role(TERTIARY, tertiaryHue.toDouble(), LIGHT_ON, DARK_ON),
        tertiaryContainer = role(TERTIARY, tertiaryHue.toDouble(), LIGHT_CONTAINER, DARK_CONTAINER),
        onTertiaryContainer = role(
            TERTIARY,
            tertiaryHue.toDouble(),
            LIGHT_ON_CONTAINER,
            DARK_ON_CONTAINER,
        ),
        surface = role(NEUTRAL, base, LIGHT_SURFACE, DARK_SURFACE),
        surfaceContainer = role(
            NEUTRAL,
            base,
            LIGHT_SURFACE_CONTAINER,
            DARK_SURFACE_CONTAINER,
        ),
        surfaceContainerHigh = role(NEUTRAL, base, LIGHT_SURFACE_HIGH, DARK_SURFACE_HIGH),
        surfaceContainerHighest = role(
            NEUTRAL,
            base,
            LIGHT_SURFACE_HIGHEST,
            DARK_SURFACE_HIGHEST,
        ),
        onSurface = role(NEUTRAL, base, LIGHT_ON_SURFACE, DARK_ON_SURFACE),
        onSurfaceVariant = role(
            VARIANT,
            base,
            LIGHT_ON_SURFACE_VARIANT,
            DARK_ON_SURFACE_VARIANT,
        ),
        outlineVariant = role(
            VARIANT,
            base,
            LIGHT_OUTLINE_VARIANT,
            DARK_OUTLINE_VARIANT,
        ),
    )
}

private fun lightness(tone: Double): Double =
    if (tone <= 8.0) Math.cbrt(tone / 903.3) else (tone + 16.0) / 116.0

/** OKLCH -> sRGB, shrinking chroma until every channel is representable. */
private fun oklchToArgb(tone: Double, chroma: Double, hueDegrees: Double): Int {
    val radians = Math.PI / 180.0
    var c = chroma
    while (true) {
        val a = c * Math.cos(hueDegrees * radians)
        val b = c * Math.sin(hueDegrees * radians)
        val l = Math.pow(tone + 0.3963377774 * a + 0.2158037573 * b, 3.0)
        val m = Math.pow(tone - 0.1055613458 * a - 0.0638541728 * b, 3.0)
        val s = Math.pow(tone - 0.0894841775 * a - 1.291485548 * b, 3.0)
        val red = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val green = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val blue = -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s
        val inGamut = listOf(red, green, blue).all { it >= -0.001 && it <= 1.001 }
        if (c <= 0.0001 || inGamut) {
            return argb(red, green, blue)
        }
        c = Math.max(0.0, c - 0.004)
    }
}

private fun argb(red: Double, green: Double, blue: Double): Int =
    (0xFF shl 24) or
        (channel(red) shl 16) or
        (channel(green) shl 8) or
        channel(blue)

private fun channel(linear: Double): Int {
    val value = linear.coerceIn(0.0, 1.0)
    val encoded = if (value <= 0.0031308) {
        12.92 * value
    } else {
        1.055 * Math.pow(value, 1.0 / 2.4) - 0.055
    }
    return Math.round(encoded * 255.0).toInt()
}
