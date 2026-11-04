#include "feedback.h"

#include <cassert>
#include <cstdint>

int main() {
    uint8_t uac2[4] = {0x00, 0x80, 0x00, 0x00};  // 0.5 in 16.16, little-endian
    assert(feedback_to_q16_16(uac2, 2) == 0x00008000);
    uint8_t uac1[3] = {0x00, 0x40, 0x00};        // 1.0 in 10.14
    assert(feedback_to_q16_16(uac1, 1) == 0x00010000);
    assert(packet_frames(0x00010000, 48000, 48, 44, 52) == 48);
    assert(packet_frames(0x00018000, 48000, 48, 44, 52) == 52);  // clamped
    assert(packet_frames(0x00008000, 48000, 48, 44, 52) == 44);  // clamped low
    assert(packet_frames(0x00300000, 48000, 48, 44, 52) == 48);  // absolute 48.0 in 16.16
    assert(ring_fill_nudge(80, 100) == 1);
    assert(ring_fill_nudge(50, 100) == 0);
    assert(ring_fill_nudge(20, 100) == -1);
    return 0;
}
