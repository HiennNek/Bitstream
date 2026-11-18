package dev.hien.bitstream.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/**
 * Extra bottom clearance needed when the floating mini player overlays the bottom of the
 * screen, modeled on KernelSU's `bottomInnerPadding`. Lists still draw edge-to-edge under
 * the bar while scrolling, and can scroll their final item above it.
 */
val LocalBottomOverlayPadding = compositionLocalOf { 0.dp }

/**
 * Tonal leading back button modeled on KernelSU's `TopBarBackButton`: places back navigation
 * on the leading edge (where Android users expect it) inside a `surfaceContainerHighest` pill.
 */
@Composable
fun TopBarBackButton(
    onClick: () -> Unit,
    painter: Painter,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back",
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(painter = painter, contentDescription = contentDescription)
    }
}

/** Large display header used by every screen instead of a [androidx.compose.material3.TopAppBar]. */
@Composable
fun BitstreamLargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    height: Dp = 56.dp,
    startPadding: Dp = Spacing.xxl,
    endPadding: Dp = Spacing.md,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(
                start = if (navigationIcon != null) Spacing.md else startPadding,
                end = endPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            navigationIcon()
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = title,
            style = style,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/**
 * M3 Expressive segmented island shape (modeled on KernelSU's `SegmentedList.kt`):
 * outer corners (`20.dp`) on the first/last item of a group, tight inner corners (`4.dp`)
 * on adjacent edges separated by a `2.dp` gap.
 */
fun segmentedItemShape(
    index: Int,
    count: Int,
    outerRadius: Dp = Radii.lg,
    innerRadius: Dp = Spacing.xs,
): RoundedCornerShape {
    val top = if (index <= 0) outerRadius else innerRadius
    val bottom = if (index >= count - 1) outerRadius else innerRadius
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

val LocalSegmentedShape = compositionLocalOf<Shape?> { null }

/**
 * Grouped island of segmented rows (modeled on KernelSU's `SegmentedColumn`):
 * replaces heavy `ElevatedCard` + `HorizontalDivider` boxes with tonal `2.dp`-gapped items.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    content: List<@Composable () -> Unit>,
) {
    if (content.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content.forEachIndexed { index, itemContent ->
                CompositionLocalProvider(
                    LocalSegmentedShape provides segmentedItemShape(index, content.size),
                ) {
                    itemContent()
                }
            }
        }
    }
}

/** Tonal container for one item inside a [SegmentedColumn]. */
@Composable
fun SegmentedItemSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val shape = LocalSegmentedShape.current ?: RoundedCornerShape(Radii.lg)
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = color,
            interactionSource = interactionSource,
            modifier = modifier.fillMaxWidth(),
            content = content,
        )
    } else {
        Surface(
            shape = shape,
            color = color,
            modifier = modifier.fillMaxWidth(),
            content = content,
        )
    }
}

/**
 * Full-row clickable switch item (modeled on KernelSU's `SegmentedSwitchItem`):
 * shares one [MutableInteractionSource] between the row and the [Switch] and fires
 * haptic feedback on toggle.
 */
@Composable
fun SegmentedSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    switchModifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    SegmentedItemSurface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(!checked)
        },
        interactionSource = interactionSource,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource,
                modifier = switchModifier,
            )
        }
    }
}

/** Full-width search affordance used on the Library screen. */
@Composable
fun BitstreamSearchPill(
    text: String,
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    iconSize: Dp = 24.dp,
    contentPadding: Dp = Spacing.xl,
    gap: Dp = Spacing.lg,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Icon(painter = painter, contentDescription = null, modifier = Modifier.size(iconSize))
            Text(
                text = text,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shortcut tile: a scalloped cookie glyph on the left, label on the right. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BitstreamTile(
    label: String,
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    height: Dp = 80.dp,
    radius: Dp = Radii.tile,
    blobSize: Dp = 52.dp,
    iconSize: Dp = 24.dp,
    contentPadding: Dp = Spacing.md,
    gap: Dp = Spacing.md,
    labelStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Box(
                modifier = Modifier
                    .size(blobSize)
                    .clip(shape.toShape())
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = label,
                style = labelStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
