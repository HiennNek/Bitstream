package dev.hien.bitstream.core.designsystem

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val tealLight = trackPalette(hue = 180, dark = false)
private val tealDark = trackPalette(hue = 180, dark = true)

private val DeepTealLight = lightColorScheme(
    primary = Color(0xFF006A6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF7F6),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    // Without these the container roles fall back to Material's purple baseline.
    surfaceVariant = Color(tealLight.surfaceContainerHigh),
    surfaceContainerLowest = Color(tealLight.surface),
    surfaceContainerLow = Color(tealLight.surfaceContainer),
    surfaceContainer = Color(tealLight.surfaceContainer),
    surfaceContainerHigh = Color(tealLight.surfaceContainerHigh),
    surfaceContainerHighest = Color(tealLight.surfaceContainerHighest),
    outline = Color(tealLight.onSurfaceVariant),
    outlineVariant = Color(tealLight.outlineVariant),
)

private val DeepTealDark = darkColorScheme(
    primary = Color(0xFF4CDADA),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = Color(0xFF6FF7F6),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8),
    onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863),
    onTertiaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF191C1C),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191C1C),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(tealDark.surfaceContainerHigh),
    surfaceContainerLowest = Color(tealDark.surface),
    surfaceContainerLow = Color(tealDark.surfaceContainer),
    surfaceContainer = Color(tealDark.surfaceContainer),
    surfaceContainerHigh = Color(tealDark.surfaceContainerHigh),
    surfaceContainerHighest = Color(tealDark.surfaceContainerHighest),
    outline = Color(tealDark.onSurfaceVariant),
    outlineVariant = Color(tealDark.outlineVariant),
)

/**
 * Material 3 Expressive theme. When [albumColors] is on and a track is playing,
 * the scheme follows the artwork's dominant hue; when off (or idle), it uses the
 * Android wallpaper scheme on API 31+, falling back to static deep teal on API 29–30.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BitstreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    albumHue: Int? = null,
    albumColors: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val wallpaperScheme = remember(context, darkTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            null
        }
    }
    val target = remember(albumHue, albumColors, darkTheme, wallpaperScheme) {
        when {
            albumColors && albumHue != null ->
                trackPalette(albumHue, darkTheme).toColorScheme(darkTheme)
            wallpaperScheme != null -> wallpaperScheme
            darkTheme -> DeepTealDark
            else -> DeepTealLight
        }
    }
    val colorScheme = rememberAnimatedColorScheme(target)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = BitstreamTypography,
        motionScheme = MotionScheme.expressive(),
    ) {
        // MaterialTheme does not set LocalContentColor (it defaults to Color.Black),
        // so without this every Text/Icon that has no explicit color and no
        // Surface ancestor renders black — invisible in dark mode. Provide the
        // theme's onSurface as the ambient text color; any Surface or Material
        // component still overrides it with its own content color.
        CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
            content()
        }
    }
}

private fun TrackPalette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(surface),
        onBackground = Color(onSurface),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceContainerHigh),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceContainerLowest = Color(surface),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerLow = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        outlineVariant = Color(outlineVariant),
    )
}

/**
 * Spring-driven color transitions (modeled on KernelSU's `ColorScheme.animateAsState`):
 * retargets smoothly on rapid track skips instead of restarting a fixed tween from zero
 * velocity, and covers every active role so no surface or outline snaps on frame 1.
 */
@Composable
private fun rememberAnimatedColorScheme(target: ColorScheme): ColorScheme {
    val spec = spring<Color>(stiffness = Spring.StiffnessLow)
    @Composable
    fun animate(color: Color, label: String): Color =
        animateColorAsState(color, spec, label = label).value

    return target.copy(
        primary = animate(target.primary, "primary"),
        onPrimary = animate(target.onPrimary, "onPrimary"),
        primaryContainer = animate(target.primaryContainer, "primaryContainer"),
        onPrimaryContainer = animate(target.onPrimaryContainer, "onPrimaryContainer"),
        secondary = animate(target.secondary, "secondary"),
        onSecondary = animate(target.onSecondary, "onSecondary"),
        secondaryContainer = animate(target.secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = animate(target.onSecondaryContainer, "onSecondaryContainer"),
        tertiary = animate(target.tertiary, "tertiary"),
        onTertiary = animate(target.onTertiary, "onTertiary"),
        tertiaryContainer = animate(target.tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = animate(target.onTertiaryContainer, "onTertiaryContainer"),
        surface = animate(target.surface, "surface"),
        onSurface = animate(target.onSurface, "onSurface"),
        background = animate(target.background, "background"),
        onBackground = animate(target.onBackground, "onBackground"),
        surfaceVariant = animate(target.surfaceVariant, "surfaceVariant"),
        surfaceContainerLowest = animate(target.surfaceContainerLowest, "surfaceContainerLowest"),
        surfaceContainerLow = animate(target.surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainer = animate(target.surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = animate(target.surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerHighest = animate(target.surfaceContainerHighest, "surfaceContainerHighest"),
        onSurfaceVariant = animate(target.onSurfaceVariant, "onSurfaceVariant"),
        outline = animate(target.outline, "outline"),
        outlineVariant = animate(target.outlineVariant, "outlineVariant"),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> expressiveSpatialSpec() =
    MaterialTheme.motionScheme.defaultSpatialSpec<T>()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> expressiveEffectsSpec() =
    MaterialTheme.motionScheme.defaultEffectsSpec<T>()
