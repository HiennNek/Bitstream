#pragma once

#include <cstddef>
#include <cstdint>

/**
 * Parses a USB Audio async feedback packet into Q16.16 fixed-point format.
 * UAC2 uses 4-byte 16.16 little-endian; UAC1 uses 3-byte 10.14 little-endian.
 */
uint32_t feedback_to_q16_16(const uint8_t* data, int uacVersion);

/**
 * Computes the integer frame count for a single isochronous OUT packet from a
 * Q16.16 feedback value (either normalized around 1.0 or absolute frames per
 * interval in Q16.16), clamped to [min, max].
 */
int packet_frames(uint32_t feedback_q16_16, int rate, int interval_frames, int min, int max);

/**
 * Computes a ring-fill outer correction in [-1, +1] frames so the ring stays
 * centered near 50% capacity without exceeding the endpoint's +/-1 frame window.
 */
int ring_fill_nudge(size_t available_bytes, size_t capacity_bytes);
