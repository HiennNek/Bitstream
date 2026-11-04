#include "flac_decoder.h"

#include <FLAC/stream_decoder.h>
#include <android/log.h>
#include <unistd.h>

// libFLAC's private MD5 helpers are C symbols; the header itself is not
// wrapped in extern "C".
extern "C" {
#include <private/md5.h>
}

#include <chrono>
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>

#include "pcm_packer.h"

namespace {

constexpr const char* kTag = "BitstreamFlac";

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, kTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kTag, __VA_ARGS__)

struct DecoderState {
    RingBuffer* ring = nullptr;
    int sample_rate = 0;
    int bit_depth = 0;
    int channels = 0;
    int subslot_bytes = 0;
    std::atomic<bool>* stop = nullptr;

    bool format_mismatch = false;
    bool failed = false;
    bool eof = false;
    bool md5_available = false;
    bool md5_initialized = false;
    uint8_t stream_md5[16] = {};
    FLAC__MD5Context md5 = {};
    uint64_t total_samples = 0;
    uint64_t frames_decoded = 0;
    std::vector<uint8_t> pack_buffer;
};

FLAC__StreamDecoderWriteStatus write_callback(
    const FLAC__StreamDecoder*, const FLAC__Frame* frame,
    const FLAC__int32* const buffer[], void* client_data) {
    auto* state = static_cast<DecoderState*>(client_data);
    if (state->format_mismatch || state->failed ||
        state->stop->load(std::memory_order_acquire)) {
        return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    }

    const FLAC__FrameHeader& header = frame->header;
    if (header.channels != state->channels || header.bits_per_sample != state->bit_depth) {
        state->format_mismatch = true;
        return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
    }

    if (state->md5_initialized) {
        if (!FLAC__MD5Accumulate(&state->md5, buffer, header.channels, header.blocksize,
                                 (header.bits_per_sample + 7) / 8)) {
            state->md5_available = false;
            state->md5_initialized = false;
        }
    }

    const size_t frame_bytes =
        static_cast<size_t>(state->channels) * static_cast<size_t>(state->subslot_bytes);
    const size_t needed = static_cast<size_t>(header.blocksize) * frame_bytes;
    if (state->pack_buffer.size() < needed) {
        state->pack_buffer.resize(needed);
    }

    uint8_t* out = state->pack_buffer.data();
    for (uint32_t i = 0; i < header.blocksize; ++i) {
        for (int channel = 0; channel < state->channels; ++channel) {
            pack_sample(buffer[channel][i], header.bits_per_sample, state->subslot_bytes,
                        out + static_cast<size_t>(i) * frame_bytes +
                            static_cast<size_t>(channel) * state->subslot_bytes);
        }
    }

    size_t written = 0;
    while (written < needed) {
        if (state->stop->load(std::memory_order_acquire)) {
            return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        }
        const size_t count = state->ring->write(out + written, needed - written);
        written += count;
        if (count == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }

    state->frames_decoded += header.blocksize;
    return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
}

void metadata_callback(const FLAC__StreamDecoder*, const FLAC__StreamMetadata* metadata,
                       void* client_data) {
    auto* state = static_cast<DecoderState*>(client_data);
    if (metadata->type != FLAC__METADATA_TYPE_STREAMINFO) {
        return;
    }

    const FLAC__StreamMetadata_StreamInfo& info = metadata->data.stream_info;
    state->total_samples = info.total_samples;

    state->md5_available = false;
    for (unsigned char byte : info.md5sum) {
        if (byte != 0) {
            state->md5_available = true;
            break;
        }
    }
    std::memcpy(state->stream_md5, info.md5sum, 16);

    if (info.channels != static_cast<uint32_t>(state->channels) ||
        info.bits_per_sample != static_cast<uint32_t>(state->bit_depth) ||
        info.sample_rate != static_cast<uint32_t>(state->sample_rate) ||
        info.max_blocksize == 0) {
        LOGE("stream format %u Hz/%u-bit/%u ch does not match requested %d/%d/%d",
             info.sample_rate, info.bits_per_sample, info.channels, state->sample_rate,
             state->bit_depth, state->channels);
        state->format_mismatch = true;
        return;
    }

    state->pack_buffer.assign(
        static_cast<size_t>(info.max_blocksize) * state->channels * state->subslot_bytes, 0);

    if (state->md5_available) {
        FLAC__MD5Init(&state->md5);
        state->md5_initialized = true;
    }
}

void error_callback(const FLAC__StreamDecoder*, FLAC__StreamDecoderErrorStatus status,
                    void* client_data) {
    auto* state = static_cast<DecoderState*>(client_data);
    LOGE("decoder error: %s", FLAC__StreamDecoderErrorStatusString[status]);
    state->failed = true;
}

}  // namespace

FlacDecodeResult decode_flac_fd(int fd, RingBuffer* ring, int sample_rate, int bit_depth,
                                int channels, int subslot_bytes, int64_t start_frame,
                                std::atomic<bool>* stop) {
    FlacDecodeResult result{false, kMd5Unavailable, kFlacOk};
    if (ring == nullptr || stop == nullptr || bit_depth <= 0 || channels <= 0 ||
        subslot_bytes <= 0 || bit_depth > subslot_bytes * 8) {
        result.error = kFlacFormatMismatch;
        return result;
    }

    const int owned_fd = dup(fd);
    if (owned_fd < 0) {
        result.error = kFlacIoError;
        return result;
    }
    FILE* file = fdopen(owned_fd, "rb");
    if (file == nullptr) {
        close(owned_fd);
        result.error = kFlacIoError;
        return result;
    }

    FLAC__StreamDecoder* decoder = FLAC__stream_decoder_new();
    if (decoder == nullptr) {
        fclose(file);
        result.error = kFlacIoError;
        return result;
    }

    DecoderState state;
    state.ring = ring;
    state.sample_rate = sample_rate;
    state.bit_depth = bit_depth;
    state.channels = channels;
    state.subslot_bytes = subslot_bytes;
    state.stop = stop;

    FLAC__stream_decoder_set_md5_checking(decoder, false);
    const FLAC__StreamDecoderInitStatus init_status = FLAC__stream_decoder_init_FILE(
        decoder, file, write_callback, metadata_callback, error_callback, &state);
    if (init_status != FLAC__STREAM_DECODER_INIT_STATUS_OK) {
        LOGE("decoder init failed: %s", FLAC__StreamDecoderInitStatusString[init_status]);
        FLAC__stream_decoder_delete(decoder);
        fclose(file);
        result.error = kFlacIoError;
        return result;
    }

    if (start_frame > 0 &&
        !FLAC__stream_decoder_seek_absolute(decoder, static_cast<FLAC__uint64>(start_frame))) {
        LOGE("seek to frame %lld failed", static_cast<long long>(start_frame));
        state.failed = true;
    }

    const bool processed =
        !state.failed && FLAC__stream_decoder_process_until_end_of_stream(decoder);
    // process_until_end_of_stream() returns true only when the decoder reached
    // END_OF_STREAM; the FILE init entry point has no separate EOF callback.
    state.eof = processed;

    int md5_state = kMd5Unavailable;
    if (state.md5_initialized) {
        uint8_t computed[16] = {};
        FLAC__MD5Final(computed, &state.md5);
        state.md5_initialized = false;
        if (state.md5_available && state.eof && start_frame == 0 && !state.failed) {
            md5_state = std::memcmp(computed, state.stream_md5, 16) == 0 ? kMd5Verified
                                                                        : kMd5Failed;
        }
    }

    if (state.format_mismatch) {
        result.error = kFlacFormatMismatch;
    } else if (state.failed || (!processed && !state.eof && !stop->load())) {
        result.error = kFlacDecodeError;
    }

    result.complete = state.eof && !state.failed && !stop->load(std::memory_order_acquire);
    result.md5_state = md5_state;

    FLAC__stream_decoder_finish(decoder);
    FLAC__stream_decoder_delete(decoder);

    LOGI("decoded %llu frames, eof=%d md5=%d error=%d",
         static_cast<unsigned long long>(state.frames_decoded), state.eof, md5_state,
         result.error);
    return result;
}
