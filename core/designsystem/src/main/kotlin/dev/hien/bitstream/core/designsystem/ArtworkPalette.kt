package dev.hien.bitstream.core.designsystem

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Picks the dominant hue (0..359, OKLCH degrees — the same space the theme's
 * palette renders in, so extracted hue and displayed color agree) from ARGB
 * pixels. Weighted by OKLCH chroma so vivid areas dominate, and near-black,
 * near-white and grey pixels are ignored so a monochrome sleeve falls through
 * to the fallback scheme instead of tinting everything grey.
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
        // sRGB -> linear-light (the OKLab transform's first step).
        val lr = srgbToLinear(r)
        val lg = srgbToLinear(g)
        val lb = srgbToLinear(b)
        val l = 0.4122214708f * lr + 0.5363325363f * lg + 0.0514459929f * lb
        val m = 0.2119034982f * lr + 0.6806995451f * lg + 0.1073969566f * lb
        val s = 0.0883024619f * lr + 0.2817188376f * lg + 0.6299787005f * lb
        val lPrime = cbrt(l)
        val mPrime = cbrt(m)
        val sPrime = cbrt(s)
        val bigA = 1.9779984951f * lPrime - 2.428592205f * mPrime + 0.4505937099f * sPrime
        val bigB = 0.0259040371f * lPrime + 0.7827717662f * mPrime - 0.808675766f * sPrime
        val chroma = kotlin.math.sqrt(bigA * bigA + bigB * bigB)
        // Grey/monochrome pixels: chroma below threshold carries no hue signal.
        if (chroma < MIN_CHROMA || lPrime <= 0f) continue

        var hueDegrees = Math.toDegrees(atan2(bigB.toDouble(), bigA.toDouble())).toFloat()
        if (hueDegrees < 0f) hueDegrees += 360f
        val weight = chroma * chroma * lPrime
        val bin = (hueDegrees / BIN_DEGREES).toInt().coerceIn(0, HUE_BINS - 1)
        val angle = Math.toRadians(hueDegrees.toDouble())
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

/** sRGB 0..255 channel to linear light. */
private fun srgbToLinear(channel: Int): Float {
    val value = channel / 255f
    return if (value <= 0.04045f) value / 12.92f else Math.pow(value.toDouble(), 2.4).toFloat()
}

private fun cbrt(value: Float): Float = Math.cbrt(value.toDouble()).toFloat()

private const val HUE_BINS = 36
private const val BIN_DEGREES = 10f
private const val OPAQUE = 0x80

/** OKLCH chroma under which a pixel reads as grey. */
private const val MIN_CHROMA = 0.02f
