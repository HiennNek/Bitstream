#include <cassert>
#include <cstdint>

#include "ring_buffer.h"

int main() {
    RingBuffer rb(64);
    uint8_t in[8] = {1, 2, 3, 4, 5, 6, 7, 8};
    assert(rb.write(in, 8) == 8);

    uint8_t out[8] = {};
    assert(rb.read(out, 8) == 8);
    assert(out[0] == 1 && out[7] == 8);

    assert(rb.write(in, 64) == 64);  // fills
    assert(rb.write(in, 1) == 0);    // full
    assert(rb.read(out, 8) == 8);
    assert(rb.write(in, 8) == 8);

    // Wrap-around preserves byte order.
    uint8_t rest[56] = {};
    assert(rb.read(rest, 56) == 56);
    assert(rb.read(out, 8) == 8);
    assert(out[0] == 1 && out[7] == 8);

    // clear() empties both ends.
    rb.clear();
    assert(rb.available() == 0);
    assert(rb.free() == 64);
    return 0;
}
