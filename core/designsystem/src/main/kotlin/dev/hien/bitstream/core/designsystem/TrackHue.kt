package dev.hien.bitstream.core.designsystem

/**
 * Hue anchors that RENDER as 12 evenly spaced families every 30 degrees.
 * These are NOT evenly spaced: the OKLCH-to-sRGB rendering drifts hue by up to
 * -50 degrees through the warm band (a requested 83 renders as gold-30, a
 * requested 26 renders as red-0), so naively even anchors collapsed into the
 * red/orange neighborhood — the "too many red cookies" effect. Computed by
 * inverting the renderer at the primary's chroma/tone for both light and dark.
 */
private val HUE_ANCHORS = intArrayOf(26, 83, 110, 127, 143, 165, 195, 234, 279, 311, 328, 345)

/**
 * Deterministic per-track hue: hash of the track id (stable across restarts),
 * NOT album — each song gets its own independent color draw. Picks one of
 * [HUE_ANCHORS] uniformly, then jitters +-10 degrees within the family.
 */
fun trackHue(trackId: Long): Int {
    var z = trackId
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    z = z xor (z ushr 31)
    val family = Math.floorMod(z ushr 32, HUE_ANCHORS.size.toLong()).toInt()
    val jitter = Math.floorMod(z and 0xFF, 21L).toInt() - 10
    return Math.floorMod(HUE_ANCHORS[family] + jitter, 360)
}

/**
 * Album-identity hue (legacy): album hash so every track on a record shares
 * one color; singles fall back to artist and title. Kept for callers that
 * group by record (album grids).
 */
fun trackHue(
    albumArtist: String,
    album: String,
    artist: String,
    title: String,
): Int {
    val key = if (album.isNotBlank()) "$albumArtist|$album" else "$artist|$title"
    var hash = -0x340d631b7c7d5ccdL // FNV-1a 64-bit offset basis
    for (character in key) {
        hash = (hash xor character.code.toLong()) * 0x100000001b3L
    }
    // splitmix64 finalizer: avalanches the FNV state so draws are uniform.
    var z = hash
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    z = z xor (z ushr 31)
    val family = Math.floorMod(z ushr 32, HUE_ANCHORS.size.toLong()).toInt()
    val jitter = Math.floorMod(z and 0xFF, 21L).toInt() - 10
    return Math.floorMod(HUE_ANCHORS[family] + jitter, 360)
}
