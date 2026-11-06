package dev.hien.bitstream.core.designsystem

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
)

/**
 * Material 3 Expressive theme. When [albumColors] is on, the scheme follows the
 * playing artwork's dominant hue, then the Android wallpaper scheme, then the
 * static deep-teal brand fallback.
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
    val wallpaperScheme = remember(context, darkTheme, albumColors) {
        if (albumColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        content = content,
    )
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
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerLow = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        outlineVariant = Color(outlineVariant),
    )
}

/** Cross-fades the roles that paint large areas when the album hue changes. */
@Composable
private fun rememberAnimatedColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 500)
    return target.copy(
        primary = animateColorAsState(target.primary, spec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, spec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(
            target.primaryContainer,
            spec,
            label = "primaryContainer",
        ).value,
        onPrimaryContainer = animateColorAsState(
            target.onPrimaryContainer,
            spec,
            label = "onPrimaryContainer",
        ).value,
        secondaryContainer = animateColorAsState(
            target.secondaryContainer,
            spec,
            label = "secondaryContainer",
        ).value,
        onSecondaryContainer = animateColorAsState(
            target.onSecondaryContainer,
            spec,
            label = "onSecondaryContainer",
        ).value,
        tertiaryContainer = animateColorAsState(
            target.tertiaryContainer,
            spec,
            label = "tertiaryContainer",
        ).value,
        onTertiaryContainer = animateColorAsState(
            target.onTertiaryContainer,
            spec,
            label = "onTertiaryContainer",
        ).value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, spec, label = "onSurface").value,
        surfaceContainerLow = animateColorAsState(
            target.surfaceContainerLow,
            spec,
            label = "surfaceContainerLow",
        ).value,
        surfaceContainer = animateColorAsState(
            target.surfaceContainer,
            spec,
            label = "surfaceContainer",
        ).value,
        surfaceContainerHigh = animateColorAsState(
            target.surfaceContainerHigh,
            spec,
            label = "surfaceContainerHigh",
        ).value,
        surfaceContainerHighest = animateColorAsState(
            target.surfaceContainerHighest,
            spec,
            label = "surfaceContainerHighest",
        ).value,
        onSurfaceVariant = animateColorAsState(
            target.onSurfaceVariant,
            spec,
            label = "onSurfaceVariant",
        ).value,
        outlineVariant = animateColorAsState(
            target.outlineVariant,
            spec,
            label = "outlineVariant",
        ).value,
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
