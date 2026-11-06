package dev.hien.bitstream.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Large display header used by every screen instead of a [androidx.compose.material3.TopAppBar]. */
@Composable
fun BitstreamLargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    height: Dp = 56.dp,
    startPadding: Dp = Spacing.xxl,
    endPadding: Dp = Spacing.md,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(start = startPadding, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/** Shortcut tile: a lobed blob glyph on the left, label on the right. */
@Composable
fun BitstreamTile(
    label: String,
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sides: Int = 8,
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
            BlobBackground(
                sides = sides,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(blobSize),
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
