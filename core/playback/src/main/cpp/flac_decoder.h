#pragma once

#include <atomic>
#include <cstdint>

#include "ring_buffer.h"

enum FlacMd5State {
    kMd5Unavailable = 0,
    kMd5Verified = 1,
    kMd5Failed = 2,
};

enum FlacError {
    kFlacOk = 0,
    kFlacFormatMismatch = -1,
    kFlacIoError = -2,
    kFlacDecodeError = -3,
};

struct FlacDecodeResult {
    bool complete;
    int md5_state;
    int error;
    uint64_t frames_decoded;
};

/**
 * Decodes one FLAC stream from [fd] into [ring], packing each sample into the
 * USB subslot width. The fd is duplicated, so the caller keeps ownership of the
 * original descriptor.
 */
FlacDecodeResult decode_flac_fd(int fd, RingBuffer* ring, int sample_rate, int bit_depth,
                                int channels, int subslot_bytes, int64_t start_frame,
                                std::atomic<bool>* stop,
                                const std::atomic<int32_t>* software_gain_q30 = nullptr);
