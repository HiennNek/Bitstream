#include "feedback.h"

#include <algorithm>

uint32_t feedback_to_q16_16(const uint8_t* data, int uacVersion) {
    if (data == nullptr) {
        return 0;
    }
    if (uacVersion == 2) {
        return static_cast<uint32_t>(data[0]) |
               (static_cast<uint32_t>(data[1]) << 8) |
               (static_cast<uint32_t>(data[2]) << 16) |
               (static_cast<uint32_t>(data[3]) << 24);
    }
    const uint32_t q10_14 = static_cast<uint32_t>(data[0]) |
                            (static_cast<uint32_t>(data[1]) << 8) |
                            (static_cast<uint32_t>(data[2]) << 16);
    return q10_14 << 2;
}

int packet_frames(uint32_t feedback_q16_16, int rate, int interval_frames, int min, int max) {
    (void)rate;
    const int lo = std::min(min, max);
    const int hi = std::max(min, max);
    if (feedback_q16_16 == 0 || interval_frames <= 0) {
        return std::clamp(interval_frames, lo, hi);
    }

    const uint32_t absolute_threshold =
        static_cast<uint32_t>(std::max(1, lo)) << 15;
    int frames = 0;
    if (feedback_q16_16 >= absolute_threshold && lo > 2) {
        frames = static_cast<int>((feedback_q16_16 + 0x8000U) >> 16);
    } else {
        const uint64_t scaled =
            static_cast<uint64_t>(feedback_q16_16) *
            static_cast<uint64_t>(interval_frames);
        frames = static_cast<int>((scaled + 0x8000ULL) >> 16);
    }
    return std::clamp(frames, lo, hi);
}

int ring_fill_nudge(size_t available_bytes, size_t capacity_bytes) {
    if (capacity_bytes == 0) {
        return 0;
    }
    if (available_bytes > (capacity_bytes * 3) / 4) {
        return 1;
    }
    if (available_bytes < capacity_bytes / 4) {
        return -1;
    }
    return 0;
}
