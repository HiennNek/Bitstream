package dev.hien.bitstream.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The mockup's type ramp. Every size, line height, weight and tracking below is
 * taken from `mork.html`; Roboto Flex supplies the variable weights.
 */
private val RobotoFlex = FontFamily(
    listOf(400, 500, 600, 650, 700, 750).map { weight ->
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

private fun style(
    size: Int,
    line: Int,
    weight: Int,
    tracking: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = RobotoFlex,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
)

private val displayLarge = style(48, 56, 750, -0.8f)
private val displayMedium = style(44, 52, 750, -0.6f)
private val displaySmall = style(36, 44, 750, -0.5f)
private val headlineLarge = style(32, 40, 750, -0.4f)
private val headlineMedium = style(28, 34, 700, -0.2f)
private val headlineSmall = style(24, 30, 700, -0.2f)
private val titleLarge = style(22, 28, 650)
private val titleMedium = style(16, 22, 600)
private val titleSmall = style(15, 20, 600)
private val bodyLarge = style(16, 24, 500)
private val bodyMedium = style(14, 20, 500)
private val bodySmall = style(13, 18, 400)
private val labelLarge = style(15, 20, 600)
private val labelMedium = style(12, 16, 500, 0.4f)
private val labelSmall = style(11, 16, 500, 0.5f)

val BitstreamTypography = Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    displaySmall = displaySmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
    displayLargeEmphasized = displayLarge,
    displayMediumEmphasized = displayMedium,
    displaySmallEmphasized = displaySmall,
    headlineLargeEmphasized = headlineLarge,
    headlineMediumEmphasized = headlineMedium,
    headlineSmallEmphasized = headlineSmall,
    titleLargeEmphasized = titleLarge,
    titleMediumEmphasized = titleMedium,
    titleSmallEmphasized = titleSmall,
    bodyLargeEmphasized = bodyLarge,
    bodyMediumEmphasized = bodyMedium,
    bodySmallEmphasized = bodySmall,
    labelLargeEmphasized = labelLarge,
    labelMediumEmphasized = labelMedium,
    labelSmallEmphasized = labelSmall,
)
