package dev.hien.bitstream.core.designsystem

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Picks the dominant saturated hue (0..359) from ARGB pixels. Near-black,
 * near-white and grey pixels are ignored so a monochrome sleeve falls through
 * to the wallpaper scheme instead of tinting everything grey.
 */
fun dominantArtworkHue(argbPixels: IntArray): Int? {
    val weightPerBin = FloatArray(HUE_BINS)
    val xPerBin = FloatArray(HUE_BINS)
    val yPerBin = FloatArray(HUE_BINS)
    for (argb in argbPixels) {
        if (argb ushr 24 < OPAQUE) continue
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (max == 0 || delta == 0) continue
        val saturation = delta / max.toFloat()
        val value = max / 255f
        if (saturation < MIN_SATURATION || value < MIN_VALUE) continue

        var hue = when (max) {
            r -> 60f * ((g - b) / delta.toFloat())
            g -> 60f * (2f + (b - r) / delta.toFloat())
            else -> 60f * (4f + (r - g) / delta.toFloat())
        }
        if (hue < 0f) hue += 360f
        val weight = saturation * saturation * value
        val bin = (hue / BIN_DEGREES).toInt().coerceIn(0, HUE_BINS - 1)
        val angle = Math.toRadians(hue.toDouble())
        weightPerBin[bin] += weight
        xPerBin[bin] += (weight * cos(angle)).toFloat()
        yPerBin[bin] += (weight * sin(angle)).toFloat()
    }

    val best = weightPerBin.indices.maxByOrNull { weightPerBin[it] } ?: return null
    if (weightPerBin[best] <= 0f) return null

    var x = 0f
    var y = 0f
    for (offset in -1..1) {
        val index = Math.floorMod(best + offset, HUE_BINS)
        x += xPerBin[index]
        y += yPerBin[index]
    }
    val degrees = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).roundToInt()
    return ((degrees % 360) + 360) % 360
}

private const val HUE_BINS = 36
private const val BIN_DEGREES = 10f
private const val OPAQUE = 0x80
private const val MIN_SATURATION = 0.15f
private const val MIN_VALUE = 0.10f
