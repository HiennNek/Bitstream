package dev.hien.bitstream.core.designsystem

/**
 * Deterministic hue for a track. Album identity wins so every track on a record
 * shares one color; singles fall back to artist and title.
 */
fun trackHue(
    albumArtist: String,
    album: String,
    artist: String,
    title: String,
): Int {
    val key = if (album.isNotBlank()) "$albumArtist|$album" else "$artist|$title"
    var hash = 0
    for (character in key) {
        hash = 31 * hash + character.code
    }
    return Math.floorMod(hash, 360)
}
