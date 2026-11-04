#ifndef BITSTREAM_PCM_PACKER_H
#define BITSTREAM_PCM_PACKER_H

#include <algorithm>
#include <cstdint>
#include <cstring>

/**
 * Applies a 32-bit fixed-point Q1.30 linear gain (`0` = mute, `1 << 30` = unity)
 * with triangular PDF (TPDF) 1-LSB dither before clamping to the signed
 * `bit_depth` range. Used only when the user explicitly enables software volume.
 */
inline int32_t apply_software_volume_q30(int32_t sample, int bit_depth, int32_t gain_q30,
                                         uint32_t* rng_state) {
    if (gain_q30 <= 0) {
        return 0;
    }
    const int64_t scaled = static_cast<int64_t>(sample) * static_cast<int64_t>(gain_q30);
    int64_t dither = 0;
    if (rng_state != nullptr) {
        uint32_t s = *rng_state;
        s ^= s << 13;
        s ^= s >> 17;
        s ^= s << 5;
        const int64_t r1 = static_cast<int64_t>(s & 0x3FFFFFFFu);
        s ^= s << 13;
        s ^= s >> 17;
        s ^= s << 5;
        const int64_t r2 = static_cast<int64_t>(s & 0x3FFFFFFFu);
        *rng_state = s;
        dither = r1 - r2;
    }
    const int64_t rounded = (scaled + dither + (1LL << 29)) >> 30;
    const int64_t max_val = (1LL << (bit_depth - 1)) - 1;
    const int64_t min_val = -(1LL << (bit_depth - 1));
    return static_cast<int32_t>(std::clamp(rounded, min_val, max_val));
}

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
