#ifndef BITSTREAM_PCM_PACKER_H
#define BITSTREAM_PCM_PACKER_H

#include <cstdint>
#include <cstring>

/**
 * Packs a signed integer PCM sample into a USB subslot, left-justified.
 *
 * The source's significant bits occupy the most significant bits of the
 * subslot and the unused low bits are zero-filled. This is the standard
 * 16-in-24, 16-in-32 and 24-in-32 representation: no scaling, truncation,
 * dithering, or rounding happens here.
 */
inline void pack_sample(int32_t sample, int bit_depth, int subslot_bytes, uint8_t* out) {
    const int shift = subslot_bytes * 8 - bit_depth;
    const int64_t widened = static_cast<int64_t>(sample) << shift;
    for (int i = 0; i < subslot_bytes; ++i) {
        out[i] = static_cast<uint8_t>((static_cast<uint64_t>(widened) >> (8 * i)) & 0xFF);
    }
}

/** Packs `count` interleaved samples. */
inline void pack_pcm(const int32_t* in, int count, int bit_depth, int subslot_bytes,
                     uint8_t* out) {
    for (int i = 0; i < count; ++i) {
        pack_sample(in[i], bit_depth, subslot_bytes, out + i * subslot_bytes);
    }
}

inline uint32_t read_le32(const uint8_t* data) {
    return static_cast<uint32_t>(data[0]) |
           (static_cast<uint32_t>(data[1]) << 8) |
           (static_cast<uint32_t>(data[2]) << 16) |
           (static_cast<uint32_t>(data[3]) << 24);
}

#endif  // BITSTREAM_PCM_PACKER_H
