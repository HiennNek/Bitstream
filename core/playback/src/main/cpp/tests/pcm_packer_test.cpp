#include <cassert>
#include <cstdint>

#include "pcm_packer.h"

int main() {
    int32_t in[2] = {0x123456, -0x123456};
    uint8_t out[8] = {};
    pack_pcm(in, 2, 24, 4, out);
    assert(read_le32(out) == 0x12345600);
    assert(read_le32(out + 4) == static_cast<uint32_t>(-0x123456 << 8));

    // 16-bit in a 32-bit subslot is left-justified by 16 bits.
    int32_t sixteen[2] = {0x1234, -0x1234};
    uint8_t wide[8] = {};
    pack_pcm(sixteen, 2, 16, 4, wide);
    assert(read_le32(wide) == 0x12340000);
    assert(read_le32(wide + 4) == static_cast<uint32_t>(-0x1234 << 16));

    // A 24-bit subslot keeps the low three bytes only.
    uint8_t narrow[3] = {};
    pack_sample(0x123456, 24, 3, narrow);
    assert(narrow[0] == 0x56 && narrow[1] == 0x34 && narrow[2] == 0x12);

    // 16-bit in a 16-bit subslot is a direct little-endian copy.
    uint8_t direct[2] = {};
    pack_sample(-0x1234, 16, 2, direct);
    assert(direct[0] == 0xCC && direct[1] == 0xED);

    // Software volume Q1.30 scaling: 0 gain mutes to 0; half gain (1 << 29)
    // halves the sample amplitude within +/- 1 LSB of TPDF dither.
    uint32_t dither_state = 0x12345678u;
    assert(apply_software_volume_q30(0x400000, 24, 0, &dither_state) == 0);
    int32_t half = apply_software_volume_q30(0x400000, 24, 1 << 29, &dither_state);
    assert(half >= 0x1FFFFE && half <= 0x200002);

    return 0;
}
