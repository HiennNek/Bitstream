package dev.hien.bitstream.core.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for the app's spacing, corner radius, and motion. Every
 * screen must take its padding and shape values from here so the UI reads as one
 * coherent system instead of a collection of one-off `N.dp` literals.
 */

/** 4 dp grid spacing scale. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Standard horizontal inset for a screen's scrolling content. */
    val screen = 16.dp

    /** Standard gap between card sections and list items. */
    val sectionGap = 16.dp
}

/** Corner radius scale, from smallest (chips, badges) to largest (hero artwork). */
object Radii {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 28.dp

    /** Oversized hero cards (album cards, section panels). */
    val hero = 32.dp

    /** Tiles, mini player, and grouped list containers. */
    val tile = 28.dp
}

/**
 * Cross-fade used when switching between top-level destinations. The caller
 * supplies its timing from [androidx.compose.material3.MotionScheme].
 */
fun fadeThroughEnter(spec: FiniteAnimationSpec<Float>): EnterTransition = fadeIn(spec)

fun fadeThroughExit(spec: FiniteAnimationSpec<Float>): ExitTransition = fadeOut(spec)

/**
 * M3 forward/back pair for detail screens (album, artist, now playing,
 * diagnostics). Forward slides in from the leading edge over a fading parent;
 * back is the depth collapse of the system predictive back: the top screen
 * shrinks toward the trailing edge while the parent rises from 0.9. NavHost
 * seeks these transitions during the back gesture, so they follow the finger.
 */
fun detailEnter(
    spatial: FiniteAnimationSpec<IntOffset>,
    effects: FiniteAnimationSpec<Float>,
): EnterTransition =
    slideInHorizontally(animationSpec = spatial, initialOffsetX = { it / 4 }) + fadeIn(effects)

fun detailExit(
    spatial: FiniteAnimationSpec<IntOffset>,
    effects: FiniteAnimationSpec<Float>,
): ExitTransition =
    slideOutHorizontally(animationSpec = spatial, targetOffsetX = { -it / 4 }) + fadeOut(effects)

/**
 * M3 back: the screen beneath rises from slightly smaller (0.9 -> 1.0) while the
 * top screen shrinks to 0.9 — the system predictive-back look. The enter spring
 * is intentionally underdamped (expressive), so the parent settles with a subtle
 * overshoot; [fadeThroughEnter] keeps the cross-fade silent.
 */
fun detailPopEnter(
    spatial: FiniteAnimationSpec<Float>,
    effects: FiniteAnimationSpec<Float>,
): EnterTransition = scaleIn(animationSpec = spatial, initialScale = 0.9f) + fadeIn(effects)

/**
 * M3 back: the top screen shrinks to 0.9 and slides toward [exitDirection]
 * (+1 = trailing end, -1 = leading) — the depth collapse of the system
 * predictive back. Exits must not bounce: [slide] is expected critically damped
 * (dampingRatio = 1f), unlike the underdamped enter spring.
 */
fun detailPopExit(
    slide: FiniteAnimationSpec<IntOffset>,
    spatial: FiniteAnimationSpec<Float>,
    exitDirection: Int = 1,
): ExitTransition =
    slideOutHorizontally(animationSpec = slide, targetOffsetX = { it * exitDirection }) +
        scaleOut(animationSpec = spatial, targetScale = 0.9f)

/**
 * Canonical `depth/rate` label for a FLAC source, e.g. `24/192` or `16/44.1`.
 * Replaces the former per-screen `formatLabel` / `formatSourceRateDepth` helpers.
 */
fun formatRateDepth(bitDepth: Int, sampleRate: Int): String {
    if (bitDepth <= 0 || sampleRate <= 0) return ""
    val wholeKhz = sampleRate / 1_000
    val remainder = (sampleRate % 1_000) / 100
    val rateText = if (remainder == 0) "$wholeKhz" else "$wholeKhz.$remainder"
    return "$bitDepth/$rateText"
}

/** Canonical `m:ss` playback time. */
fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Section title used consistently across Library, Search, and any grouped list. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BitstreamSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier = modifier,
    )
}

/** Shared empty-state layout so every "nothing here" screen looks identical. */
@Composable
fun BitstreamEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xxl))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/** Label/value row used by the diagnostics and debug screens. */
@Composable
fun LabeledValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 160.dp,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
