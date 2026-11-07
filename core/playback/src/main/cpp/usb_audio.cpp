#include "usb_audio.h"

#include <android/log.h>
#include <libusb.h>
#include <sys/time.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <deque>
#include <mutex>
#include <thread>
#include <vector>

#include "feedback.h"
#include "flac_decoder.h"

namespace {

constexpr const char* kTag = "BitstreamUsb";
// 8 URBs keep ~16 ms of audio in flight, so a scheduler gap under UI load
// does not starve the endpoint; the added latency is inaudible.
constexpr int kUrbCount = 8;
constexpr int kPacketsPerUrb = 16;
constexpr int kControlTimeoutMs = 1000;
constexpr int kMaxErrors = 8;
constexpr int kRingSeconds = 2;

// Matches the SyncType enum order in UacModels.kt.
constexpr int kSyncAsync = 0;

constexpr int kUac2SamplingFreqControl = 0x01;
constexpr int kUac2ClockSourceControl = 0x00;
constexpr int kUac1SamplingFreqControl = 0x01;

constexpr int kStateIdle = 0;
constexpr int kStatePlaying = 1;
constexpr int kStateEnded = 2;
constexpr int kStateError = 3;

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, kTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kTag, __VA_ARGS__)

struct StreamState;

struct PlayFile {
    int fd = -1;
    int64_t start_frame = 0;
    int sample_rate = 0;
    int bit_depth = 0;
    int channels = 0;
    int subslot_bytes = 0;
};

struct TrackBoundary {
    uint64_t end_frame = 0;
    int md5_state = kMd5Unavailable;
};

struct UsbAudioHandle {
    libusb_context* context = nullptr;
    libusb_device_handle* device = nullptr;

    int streaming_interface = -1;
    int control_interface = -1;
    int alt_setting = 0;
    int endpoint = 0;
    int subslot_bytes = 0;
    int channels = 0;
    int sample_rate = 0;
    int sync_type = kSyncAsync;
    int feedback_endpoint = 0;
    int feedback_max_packet_size = 0;
    int clock_source_id = 0;
    int uac_version = 2;
    int max_packet_size = 0;
    int interval = 1;
    int packets_per_second = 8000;
    int bytes_per_frame = 0;

    RingBuffer* ring = nullptr;
    std::mutex next_mutex;
    std::deque<PlayFile> next_files;
    std::deque<TrackBoundary> boundaries;
    uint64_t total_decoded_frames = 0;
    uint64_t current_track_base_frame = 0;
    int64_t enqueued_tracks = 0;
    int64_t active_track_index = 0;

    std::mutex mutex;
    std::thread worker;
    std::thread decoder_worker;
    StreamState* stream = nullptr;
    std::atomic<bool> running{false};
    std::atomic<bool> stop{false};
    std::atomic<bool> decoder_done{true};
    std::atomic<int32_t> software_gain_q30{-1};
    std::atomic<uint64_t> frames_written{0};
    std::atomic<uint32_t> underruns{0};
    std::atomic<int> state{kStateIdle};
    std::atomic<int> md5_state{kMd5Unavailable};
    std::atomic<int> error_code{kUsbOk};
    int last_error = 0;
};

struct StreamState {
    UsbAudioHandle* handle = nullptr;
    libusb_transfer* transfers[kUrbCount] = {};
    std::vector<uint8_t> buffers[kUrbCount];
    libusb_transfer* feedback_transfer = nullptr;
    std::vector<uint8_t> feedback_buffer;
    std::atomic<uint32_t> feedback_q16_16{0};
    std::atomic<int> feedback_in_flight{0};
    bool silence = false;
    uint64_t target_frames = 0;
    std::atomic<uint64_t> frames_done{0};
    std::atomic<bool> stop{false};
    std::atomic<int> errors{0};
    std::atomic<int> in_flight{0};
    double carry = 0.0;
};

struct DeviceLayout {
    int control_interface = -1;
    int uac_version = 2;
    int max_packet_size = 0;
    int interval = 1;
    int feedback_max_packet_size = 0;
};

int map_libusb_error(int err) {
    switch (err) {
        case LIBUSB_SUCCESS:
            return kUsbOk;
        case LIBUSB_ERROR_NO_DEVICE:
        case LIBUSB_ERROR_NOT_FOUND:
            return kUsbErrNoDevice;
        case LIBUSB_ERROR_BUSY:
            return kUsbErrBusy;
        case LIBUSB_ERROR_PIPE:
            return kUsbErrStall;
        case LIBUSB_ERROR_OVERFLOW:
            return kUsbErrOverrun;
        case LIBUSB_ERROR_INVALID_PARAM:
            return kUsbErrFormatMismatch;
        case LIBUSB_ERROR_IO:
        case LIBUSB_ERROR_TIMEOUT:
            return kUsbErrIo;
        default:
            return kUsbErrProtocol;
    }
}

bool discover_layout(libusb_device_handle* device, int streaming_interface, int alt_setting,
                     int endpoint_address, int feedback_endpoint, DeviceLayout* layout) {
    libusb_config_descriptor* config = nullptr;
    if (libusb_get_active_config_descriptor(libusb_get_device(device), &config) != 0) {
        return false;
    }

    bool found_endpoint = false;
    for (int i = 0; i < config->bNumInterfaces; ++i) {
        const libusb_interface& iface = config->interface[i];
        for (int j = 0; j < iface.num_altsetting; ++j) {
            const libusb_interface_descriptor& alt = iface.altsetting[j];
            if (alt.bInterfaceClass == LIBUSB_CLASS_AUDIO &&
                alt.bInterfaceSubClass == 1 && layout->control_interface < 0) {
                layout->control_interface = alt.bInterfaceNumber;
                layout->uac_version = (alt.bInterfaceProtocol == 0x20) ? 2 : 1;
            }
            if (alt.bInterfaceNumber != streaming_interface ||
                alt.bAlternateSetting != alt_setting) {
                continue;
            }
            for (int k = 0; k < alt.bNumEndpoints; ++k) {
                const libusb_endpoint_descriptor& ep = alt.endpoint[k];
                const int packet = ep.wMaxPacketSize & 0x7FF;
                const int mult = 1 + ((ep.wMaxPacketSize >> 11) & 0x3);
                if (ep.bEndpointAddress == endpoint_address) {
                    layout->max_packet_size = packet * mult;
                    layout->interval = std::max(1, static_cast<int>(ep.bInterval));
                    found_endpoint = true;
                } else if (feedback_endpoint != 0 &&
                           ep.bEndpointAddress == feedback_endpoint) {
                    layout->feedback_max_packet_size = packet * mult;
                }
            }
        }
    }
    libusb_free_config_descriptor(config);
    return layout->control_interface >= 0 && found_endpoint && layout->max_packet_size > 0;
}

int set_sample_rate(UsbAudioHandle* handle) {
    if (handle->sample_rate <= 0) {
        return kUsbOk;
    }

    if (handle->uac_version == 2) {
        if (handle->clock_source_id <= 0) {
            LOGW("no UAC2 clock source id; skipping rate control");
            return kUsbOk;
        }
        const uint32_t rate = static_cast<uint32_t>(handle->sample_rate);
        uint8_t data[4] = {
            static_cast<uint8_t>(rate & 0xFF),
            static_cast<uint8_t>((rate >> 8) & 0xFF),
            static_cast<uint8_t>((rate >> 16) & 0xFF),
            static_cast<uint8_t>((rate >> 24) & 0xFF),
        };
        const int index = (handle->control_interface & 0xFF) |
                          ((handle->clock_source_id & 0xFF) << 8);
        const int result = libusb_control_transfer(
            handle->device, 0x21, 0x01,
            (kUac2SamplingFreqControl << 8) | kUac2ClockSourceControl,
            index, data, sizeof(data), kControlTimeoutMs);
        if (result != static_cast<int>(sizeof(data))) {
            LOGE("UAC2 SET_CUR sample rate %d failed: %d", handle->sample_rate, result);
            return result < 0 ? map_libusb_error(result) : kUsbErrIo;
        }
        return kUsbOk;
    }

    uint8_t data[3] = {
        static_cast<uint8_t>(handle->sample_rate & 0xFF),
        static_cast<uint8_t>((handle->sample_rate >> 8) & 0xFF),
        static_cast<uint8_t>((handle->sample_rate >> 16) & 0xFF),
    };
    const int result = libusb_control_transfer(
        handle->device, 0x22, 0x01,
        kUac1SamplingFreqControl << 8,
        handle->endpoint, data, sizeof(data), kControlTimeoutMs);
    if (result != static_cast<int>(sizeof(data))) {
        LOGE("UAC1 SET_CUR sample rate %d failed: %d", handle->sample_rate, result);
        return result < 0 ? map_libusb_error(result) : kUsbErrIo;
    }
    return kUsbOk;
}

/** Reads back the UAC2 clock source's current sample rate; -1 when unknown. */
int read_sample_rate(UsbAudioHandle* handle) {
    if (handle->uac_version != 2 || handle->clock_source_id <= 0) {
        return -1;
    }
    uint8_t data[4] = {};
    const int index = (handle->control_interface & 0xFF) |
                      ((handle->clock_source_id & 0xFF) << 8);
    const int result = libusb_control_transfer(
        handle->device, 0xA1, 0x01, kUac2SamplingFreqControl << 8, index, data,
        sizeof(data), kControlTimeoutMs);
    if (result != static_cast<int>(sizeof(data))) {
        LOGW("UAC2 GET_CUR sample rate failed: %d", result);
        return -1;
    }
    const uint32_t rate = static_cast<uint32_t>(data[0]) |
                          (static_cast<uint32_t>(data[1]) << 8) |
                          (static_cast<uint32_t>(data[2]) << 16) |
                          (static_cast<uint32_t>(data[3]) << 24);
    return static_cast<int>(rate);
}

void fill_transfer(StreamState* stream, libusb_transfer* transfer) {
    UsbAudioHandle* handle = stream->handle;
    // USB audio packets must carry a whole number of frames. The endpoint's
    // frame rate is not an integer multiple of the microframe rate at rates
    // like 44.1 kHz (44100 / 8000 = 5.5125 frames), so alternate between
    // floor and ceil frame counts using a fractional carry, paced by async
    // feedback when available and a gentle ring-fill outer loop.
    const double nominal_frames =
        static_cast<double>(handle->sample_rate) /
        static_cast<double>(handle->packets_per_second);
    const int max_by_packet =
        handle->max_packet_size / std::max(1, handle->bytes_per_frame);
    const int min_frames =
        std::max(1, static_cast<int>(std::floor(nominal_frames)) - 1);
    const int max_frames =
        std::min(max_by_packet, static_cast<int>(std::ceil(nominal_frames)) + 1);

    double frames_per_packet = nominal_frames;
    const uint32_t fb_q16_16 = stream->feedback_q16_16.load(std::memory_order_acquire);
    if (fb_q16_16 != 0) {
        double fb_frames = static_cast<double>(fb_q16_16) / 65536.0;
        if (std::abs(fb_frames / 8.0 - nominal_frames) <= nominal_frames * 0.10) {
            fb_frames /= 8.0;
        }
        if (std::abs(fb_frames - nominal_frames) <= nominal_frames * 0.10) {
            frames_per_packet = fb_frames;
        }
    }

    if (!stream->silence && handle->ring != nullptr &&
        !handle->decoder_done.load(std::memory_order_acquire)) {
        const int nudge =
            ring_fill_nudge(handle->ring->available(), handle->ring->capacity());
        frames_per_packet += static_cast<double>(nudge) * (nominal_frames * 0.0005);
    }

    int offset = 0;
    for (int i = 0; i < transfer->num_iso_packets; ++i) {
        stream->carry += frames_per_packet;
        int frames = static_cast<int>(stream->carry);
        stream->carry -= static_cast<double>(frames);
        frames = std::clamp(frames, min_frames, std::max(min_frames, max_frames));
        int length = frames * handle->bytes_per_frame;
        if (length > handle->max_packet_size) {
            length = handle->max_packet_size;
        }
        transfer->iso_packet_desc[i].length = length;

        if (length > 0) {
            if (stream->silence || handle->ring == nullptr) {
                std::memset(transfer->buffer + offset, 0, static_cast<size_t>(length));
            } else {
                const size_t got = handle->ring->read(transfer->buffer + offset,
                                                      static_cast<size_t>(length));
                if (got < static_cast<size_t>(length)) {
                    if (handle->decoder_done.load(std::memory_order_acquire)) {
                        // End of stream: send the remaining bytes as a short
                        // packet (or an empty one) instead of padding with
                        // silence and counting an underrun.
                        length = static_cast<int>(got);
                        transfer->iso_packet_desc[i].length = length;
                    } else {
                        std::memset(transfer->buffer + offset + got, 0,
                                    static_cast<size_t>(length) - got);
                        handle->underruns.fetch_add(1, std::memory_order_acq_rel);
                    }
                }
            }
        }
        offset += length;
    }
    transfer->length = offset;
}

bool should_continue(StreamState* stream) {
    UsbAudioHandle* handle = stream->handle;
    if (stream->stop.load(std::memory_order_acquire) ||
        handle->stop.load(std::memory_order_acquire)) {
        return false;
    }
    if (stream->silence) {
        return stream->frames_done.load(std::memory_order_acquire) < stream->target_frames;
    }
    if (handle->decoder_done.load(std::memory_order_acquire) &&
        (handle->ring == nullptr || handle->ring->available() == 0)) {
        return false;
    }
    return true;
}

int submit_transfer(StreamState* stream, libusb_transfer* transfer) {
    const int result = libusb_submit_transfer(transfer);
    if (result == 0) {
        stream->in_flight.fetch_add(1, std::memory_order_acq_rel);
    }
    return result;
}

void LIBUSB_CALL on_feedback_transfer(libusb_transfer* transfer) {
    auto* stream = static_cast<StreamState*>(transfer->user_data);
    UsbAudioHandle* handle = stream->handle;
    stream->feedback_in_flight.fetch_sub(1, std::memory_order_acq_rel);

    if (transfer->status == LIBUSB_TRANSFER_COMPLETED && transfer->num_iso_packets > 0) {
        const libusb_iso_packet_descriptor& packet = transfer->iso_packet_desc[0];
        if (packet.status == LIBUSB_TRANSFER_COMPLETED && packet.actual_length >= 3) {
            const int version =
                (packet.actual_length >= 4 && handle->uac_version == 2) ? 2 : 1;
            const uint32_t fb = feedback_to_q16_16(transfer->buffer, version);
            if (fb != 0) {
                stream->feedback_q16_16.store(fb, std::memory_order_release);
            }
        }
    }

    if (!should_continue(stream)) {
        return;
    }

    transfer->iso_packet_desc[0].length =
        static_cast<unsigned int>(stream->feedback_buffer.size());
    transfer->length = static_cast<int>(stream->feedback_buffer.size());
    if (libusb_submit_transfer(transfer) == 0) {
        stream->feedback_in_flight.fetch_add(1, std::memory_order_acq_rel);
    }
}

void LIBUSB_CALL on_transfer(libusb_transfer* transfer) {
    auto* stream = static_cast<StreamState*>(transfer->user_data);
    UsbAudioHandle* handle = stream->handle;
    stream->in_flight.fetch_sub(1, std::memory_order_acq_rel);

    if (transfer->status == LIBUSB_TRANSFER_COMPLETED) {
        uint64_t completed_frames = 0;
        for (int i = 0; i < transfer->num_iso_packets; ++i) {
            const libusb_iso_packet_descriptor& packet = transfer->iso_packet_desc[i];
            if (packet.status == LIBUSB_TRANSFER_COMPLETED) {
                completed_frames +=
                    packet.actual_length / std::max(1, handle->bytes_per_frame);
            } else if (packet.status == LIBUSB_TRANSFER_OVERFLOW) {
                handle->underruns.fetch_add(1, std::memory_order_acq_rel);
            }
        }
        stream->frames_done.fetch_add(completed_frames, std::memory_order_acq_rel);
        if (!stream->silence) {
            handle->frames_written.fetch_add(completed_frames, std::memory_order_acq_rel);
        }
    } else if (transfer->status != LIBUSB_TRANSFER_CANCELLED) {
        handle->last_error = transfer->status;
        stream->errors.fetch_add(1, std::memory_order_acq_rel);
        LOGE("isochronous transfer failed: %d", transfer->status);
    }

    if (!should_continue(stream)) {
        return;
    }

    fill_transfer(stream, transfer);
    const int result = submit_transfer(stream, transfer);
    if (result < 0) {
        handle->last_error = result;
        stream->errors.fetch_add(1, std::memory_order_acq_rel);
        LOGE("resubmit failed: %d", result);
    }
}

void stream_thread(UsbAudioHandle* handle) {
    StreamState* stream = handle->stream;

    if (!stream->silence && handle->ring != nullptr) {
        // Give the decoder a head start so the first URBs carry real samples
        // instead of immediately starving the endpoint.
        const size_t prebuffer = std::min(
            handle->ring->capacity() / 4,
            static_cast<size_t>(handle->sample_rate) *
                static_cast<size_t>(handle->bytes_per_frame) / 4);
        const auto deadline =
            std::chrono::steady_clock::now() + std::chrono::seconds(2);
        while (!handle->stop.load(std::memory_order_acquire) &&
               !handle->decoder_done.load(std::memory_order_acquire) &&
               handle->ring->available() < prebuffer &&
               std::chrono::steady_clock::now() < deadline) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
        }
    }

    for (int i = 0; i < kUrbCount; ++i) {
        fill_transfer(stream, stream->transfers[i]);
        const int result = submit_transfer(stream, stream->transfers[i]);
        if (result < 0) {
            handle->last_error = result;
            stream->errors.fetch_add(1, std::memory_order_acq_rel);
            LOGE("initial submit failed: %d", result);
        }
    }

    if (stream->feedback_transfer != nullptr) {
        if (libusb_submit_transfer(stream->feedback_transfer) == 0) {
            stream->feedback_in_flight.fetch_add(1, std::memory_order_acq_rel);
        }
    }

    while (should_continue(stream)) {
        struct timeval timeout = {0, 100000};
        libusb_handle_events_timeout_completed(handle->context, &timeout, nullptr);
        if (stream->errors.load(std::memory_order_acquire) > kMaxErrors) {
            LOGE("too many isochronous errors; stopping");
            handle->error_code.store(map_libusb_error(handle->last_error),
                                     std::memory_order_release);
            break;
        }
        if (stream->silence &&
            stream->frames_done.load(std::memory_order_acquire) >= stream->target_frames) {
            break;
        }
    }

    const bool completed = !handle->stop.load(std::memory_order_acquire) &&
                           handle->error_code.load(std::memory_order_acquire) == kUsbOk;
    stream->stop.store(true, std::memory_order_release);
    for (auto* transfer : stream->transfers) {
        if (transfer != nullptr) {
            libusb_cancel_transfer(transfer);
        }
    }
    if (stream->feedback_transfer != nullptr &&
        stream->feedback_in_flight.load(std::memory_order_acquire) > 0) {
        libusb_cancel_transfer(stream->feedback_transfer);
    }

    const auto deadline =
        std::chrono::steady_clock::now() + std::chrono::milliseconds(500);
    while ((stream->in_flight.load(std::memory_order_acquire) > 0 ||
            stream->feedback_in_flight.load(std::memory_order_acquire) > 0) &&
           std::chrono::steady_clock::now() < deadline) {
        struct timeval timeout = {0, 20000};
        libusb_handle_events_timeout_completed(handle->context, &timeout, nullptr);
    }

    if (stream->silence) {
        handle->frames_written.store(stream->frames_done.load(std::memory_order_acquire),
                                     std::memory_order_release);
    }
    handle->running.store(false, std::memory_order_release);
    if (completed) {
        handle->state.store(kStateEnded, std::memory_order_release);
    } else if (handle->error_code.load(std::memory_order_acquire) != kUsbOk) {
        handle->state.store(kStateError, std::memory_order_release);
    } else {
        handle->state.store(kStateIdle, std::memory_order_release);
    }
    LOGI("stream finished: %llu frames, %u underruns, last error %d",
         static_cast<unsigned long long>(
             handle->frames_written.load(std::memory_order_acquire)),
         handle->underruns.load(std::memory_order_acquire), handle->last_error);
}

void decoder_thread(UsbAudioHandle* handle) {
    while (!handle->stop.load(std::memory_order_acquire)) {
        PlayFile file;
        {
            std::lock_guard<std::mutex> lock(handle->next_mutex);
            if (handle->next_files.empty()) {
                break;
            }
            file = handle->next_files.front();
            handle->next_files.pop_front();
        }

        const FlacDecodeResult result = decode_flac_fd(
            file.fd, handle->ring, file.sample_rate, file.bit_depth, file.channels,
            file.subslot_bytes, file.start_frame, &handle->stop, &handle->software_gain_q30);
        close(file.fd);

        if (!result.complete) {
            if (result.error != kFlacOk && !handle->stop.load(std::memory_order_acquire)) {
                handle->error_code.store(result.error, std::memory_order_release);
                handle->state.store(kStateError, std::memory_order_release);
            }
            break;
        }
        {
            std::lock_guard<std::mutex> lock(handle->next_mutex);
            handle->total_decoded_frames += result.frames_decoded;
            handle->boundaries.push_back(
                TrackBoundary{handle->total_decoded_frames, result.md5_state});
            handle->md5_state.store(result.md5_state, std::memory_order_release);
        }
    }
    handle->decoder_done.store(true, std::memory_order_release);
}

void destroy_stream(StreamState* stream) {
    if (stream == nullptr) {
        return;
    }
    for (auto* transfer : stream->transfers) {
        if (transfer != nullptr) {
            libusb_free_transfer(transfer);
        }
    }
    if (stream->feedback_transfer != nullptr) {
        libusb_free_transfer(stream->feedback_transfer);
    }
    delete stream;
}

void attach_feedback_transfer(UsbAudioHandle* handle, StreamState* stream) {
    if (handle->sync_type != kSyncAsync || handle->feedback_endpoint == 0 ||
        handle->feedback_max_packet_size < 3) {
        return;
    }
    stream->feedback_buffer.assign(
        static_cast<size_t>(handle->feedback_max_packet_size), 0);
    libusb_transfer* fb = libusb_alloc_transfer(1);
    if (fb == nullptr) {
        return;
    }
    libusb_fill_iso_transfer(fb, handle->device, handle->feedback_endpoint,
                             stream->feedback_buffer.data(),
                             handle->feedback_max_packet_size, 1,
                             on_feedback_transfer, stream, 0);
    fb->iso_packet_desc[0].length =
        static_cast<unsigned int>(handle->feedback_max_packet_size);
    stream->feedback_transfer = fb;
}

void stop_locked(UsbAudioHandle* handle) {
    std::thread usb_worker;
    std::thread decoder_worker;
    StreamState* stream = nullptr;
    {
        std::lock_guard<std::mutex> lock(handle->mutex);
        if (handle->stream == nullptr && handle->ring == nullptr) {
            return;
        }
        handle->stop.store(true, std::memory_order_release);
        stream = handle->stream;
        if (stream != nullptr) {
            stream->stop.store(true, std::memory_order_release);
        }
        if (handle->worker.joinable()) {
            usb_worker = std::move(handle->worker);
        }
        if (handle->decoder_worker.joinable()) {
            decoder_worker = std::move(handle->decoder_worker);
        }
    }

    if (decoder_worker.joinable()) {
        decoder_worker.join();
    }
    if (usb_worker.joinable()) {
        usb_worker.join();
    }

    if (stream != nullptr &&
        stream->in_flight.load(std::memory_order_acquire) == 0 &&
        stream->feedback_in_flight.load(std::memory_order_acquire) == 0) {
        destroy_stream(stream);
    } else if (stream != nullptr) {
        LOGW("leaving %d isochronous transfers to libusb after cancel timeout",
             stream->in_flight.load(std::memory_order_acquire));
    }

    {
        std::lock_guard<std::mutex> lock(handle->mutex);
        handle->stream = nullptr;
        handle->running.store(false, std::memory_order_release);
        handle->state.store(kStateIdle, std::memory_order_release);
        delete handle->ring;
        handle->ring = nullptr;
        for (PlayFile& file : handle->next_files) {
            if (file.fd >= 0) {
                close(file.fd);
            }
        }
        handle->next_files.clear();
    }
}

void close_handle(UsbAudioHandle* handle) {
    if (handle == nullptr) {
        return;
    }
    stop_locked(handle);

    if (handle->device != nullptr) {
        if (handle->streaming_interface >= 0) {
            libusb_set_auto_detach_kernel_driver(handle->device, 0);
            libusb_set_interface_alt_setting(handle->device, handle->streaming_interface, 0);
            libusb_release_interface(handle->device, handle->streaming_interface);
        }
        libusb_close(handle->device);
    }
    if (handle->context != nullptr) {
        libusb_exit(handle->context);
    }
    delete handle;
}

bool format_matches(const UsbAudioHandle* handle, int sample_rate, int bit_depth,
                    int channels, int subslot_bytes) {
    return handle->sample_rate == sample_rate && handle->channels == channels &&
           handle->subslot_bytes == subslot_bytes && bit_depth > 0 &&
           bit_depth <= subslot_bytes * 8;
}

}  // namespace

extern "C" int64_t bitstream_usb_open(int fd, int endpoint, int interface_number,
                                      int alt_setting, int subslot_bytes, int channels,
                                      int sample_rate, int sync_type, int feedback_endpoint,
                                      int clock_source_id) {
    auto* handle = new UsbAudioHandle();
    handle->streaming_interface = interface_number;
    handle->alt_setting = alt_setting;
    handle->endpoint = endpoint;
    handle->subslot_bytes = subslot_bytes;
    handle->channels = channels;
    handle->sample_rate = sample_rate;
    handle->sync_type = sync_type;
    handle->feedback_endpoint = feedback_endpoint;
    handle->clock_source_id = clock_source_id;
    handle->bytes_per_frame = channels * subslot_bytes;

    struct libusb_init_option options[1];
    options[0].option = LIBUSB_OPTION_NO_DEVICE_DISCOVERY;
    options[0].value.ival = 1;
    int result = libusb_init_context(&handle->context, options, 1);
    if (result < 0) {
        LOGE("libusb_init_context failed: %d", result);
        delete handle;
        return 0;
    }

    result = libusb_wrap_sys_device(handle->context, static_cast<intptr_t>(fd),
                                    &handle->device);
    if (result < 0 || handle->device == nullptr) {
        LOGE("libusb_wrap_sys_device(fd=%d) failed: %d", fd, result);
        close_handle(handle);
        return 0;
    }

    result = libusb_set_auto_detach_kernel_driver(handle->device, 1);
    if (result < 0) {
        LOGW("auto detach kernel driver failed: %d", result);
    }

    DeviceLayout layout;
    if (!discover_layout(handle->device, interface_number, alt_setting, endpoint,
                         feedback_endpoint, &layout)) {
        LOGE("could not find control interface or endpoint 0x%02x on alt %d",
             endpoint, alt_setting);
        close_handle(handle);
        return 0;
    }
    handle->control_interface = layout.control_interface;
    handle->uac_version = layout.uac_version;
    handle->max_packet_size = layout.max_packet_size;
    handle->interval = layout.interval;
    handle->feedback_max_packet_size = layout.feedback_max_packet_size;

    const int speed = libusb_get_device_speed(libusb_get_device(handle->device));
    const int frames_per_second =
        (speed == LIBUSB_SPEED_FULL || speed == LIBUSB_SPEED_LOW) ? 1000 : 8000;
    handle->packets_per_second = std::max(1, frames_per_second / handle->interval);

    result = libusb_claim_interface(handle->device, handle->streaming_interface);
    if (result < 0) {
        LOGE("claim streaming interface %d failed: %d", handle->streaming_interface, result);
        close_handle(handle);
        return 0;
    }

    // A DAC attached after launch may still be mid-configuration by Android's
    // USB audio HAL, leaving the streaming interface on an active alt setting.
    // Reset to alt 0 before changing the clock: some devices ignore SET_CUR
    // while their stream is running, which leaves playback at the previous
    // (system) rate and produces noise. bitstream_usb_set_sample_rate already
    // does this for later rate switches.
    result = libusb_set_interface_alt_setting(handle->device, handle->streaming_interface, 0);
    if (result < 0) {
        LOGW("reset alt setting 0 before rate change failed: %d", result);
    }

    result = set_sample_rate(handle);
    if (result < 0) {
        close_handle(handle);
        return 0;
    }
    const int applied_rate = read_sample_rate(handle);
    LOGI("clock source %d current rate: %d Hz (requested %d)",
         handle->clock_source_id, applied_rate, handle->sample_rate);
    if (applied_rate > 0 && applied_rate != handle->sample_rate) {
        LOGW("DAC reports %d Hz after requesting %d Hz", applied_rate, handle->sample_rate);
    }

    result = libusb_set_interface_alt_setting(handle->device, handle->streaming_interface,
                                              handle->alt_setting);
    if (result < 0) {
        LOGE("set alt setting %d failed: %d", handle->alt_setting, result);
        close_handle(handle);
        return 0;
    }

    LOGI("wrapped fd=%d uac=%d control=%d streaming=%d alt=%d endpoint=0x%02x "
         "max_packet=%d interval=%d rate=%d channels=%d subslot=%d sync=%d clock=%d",
         fd, handle->uac_version, handle->control_interface, handle->streaming_interface,
         handle->alt_setting, handle->endpoint, handle->max_packet_size, handle->interval,
         handle->sample_rate, handle->channels, handle->subslot_bytes, handle->sync_type,
         handle->clock_source_id);
    return reinterpret_cast<int64_t>(handle);
}

extern "C" int bitstream_usb_set_sample_rate(int64_t raw_handle, int sample_rate) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr || handle->device == nullptr || sample_rate <= 0) {
        return kUsbErrFormatMismatch;
    }
    stop_locked(handle);
    if (handle->sample_rate == sample_rate) {
        return kUsbOk;
    }

    libusb_set_interface_alt_setting(handle->device, handle->streaming_interface, 0);
    handle->sample_rate = sample_rate;
    const int rate_result = set_sample_rate(handle);
    if (rate_result != kUsbOk) {
        handle->error_code.store(rate_result, std::memory_order_release);
        return rate_result;
    }
    LOGI("clock source %d switched rate: %d Hz (requested %d)",
         handle->clock_source_id, read_sample_rate(handle), handle->sample_rate);

    const int alt_result = libusb_set_interface_alt_setting(
        handle->device, handle->streaming_interface, handle->alt_setting);
    if (alt_result < 0) {
        LOGE("set alt setting %d after rate switch failed: %d",
             handle->alt_setting, alt_result);
        const int mapped = map_libusb_error(alt_result);
        handle->error_code.store(mapped, std::memory_order_release);
        return mapped;
    }
    return kUsbOk;
}

extern "C" int bitstream_usb_start_silence(int64_t raw_handle, int64_t frames) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr || frames <= 0 || handle->bytes_per_frame <= 0) {
        return kUsbErrFormatMismatch;
    }

    stop_locked(handle);

    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->stream != nullptr || handle->running.load(std::memory_order_acquire)) {
        return kUsbErrBusy;
    }

    auto* stream = new StreamState();
    stream->handle = handle;
    stream->silence = true;
    stream->target_frames = static_cast<uint64_t>(frames);
    stream->stop.store(false, std::memory_order_release);

    const size_t buffer_size =
        static_cast<size_t>(kPacketsPerUrb) * static_cast<size_t>(handle->max_packet_size);
    for (int i = 0; i < kUrbCount; ++i) {
        stream->buffers[i].assign(buffer_size, 0);
        libusb_transfer* transfer = libusb_alloc_transfer(kPacketsPerUrb);
        if (transfer == nullptr) {
            destroy_stream(stream);
            return kUsbErrIo;
        }
        libusb_fill_iso_transfer(transfer, handle->device, handle->endpoint,
                                 stream->buffers[i].data(), 0, kPacketsPerUrb,
                                 on_transfer, stream, 0);
        stream->transfers[i] = transfer;
    }
    attach_feedback_transfer(handle, stream);

    handle->stream = stream;
    handle->stop.store(false, std::memory_order_release);
    handle->decoder_done.store(true, std::memory_order_release);
    handle->frames_written.store(0, std::memory_order_release);
    handle->underruns.store(0, std::memory_order_release);
    handle->error_code.store(kUsbOk, std::memory_order_release);
    handle->state.store(kStatePlaying, std::memory_order_release);
    handle->running.store(true, std::memory_order_release);
    handle->worker = std::thread(stream_thread, handle);

    LOGI("starting %lld frames of silence at %d Hz (%d bytes/packet nominal)",
         static_cast<long long>(frames), handle->sample_rate,
         static_cast<int>((static_cast<double>(handle->sample_rate) *
                           handle->bytes_per_frame) /
                          handle->packets_per_second));
    return kUsbOk;
}

extern "C" int bitstream_usb_play_file(int64_t raw_handle, int fd, int64_t start_frame,
                                       int sample_rate, int bit_depth, int channels,
                                       int subslot_bytes) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr || fd < 0 || start_frame < 0 ||
        !format_matches(handle, sample_rate, bit_depth, channels, subslot_bytes)) {
        return kUsbErrFormatMismatch;
    }

    const int owned_fd = dup(fd);
    if (owned_fd < 0) {
        return kUsbErrIo;
    }

    stop_locked(handle);

    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->stream != nullptr || handle->running.load(std::memory_order_acquire)) {
        close(owned_fd);
        return kUsbErrBusy;
    }

    const size_t ring_bytes = static_cast<size_t>(sample_rate) *
                              static_cast<size_t>(channels) *
                              static_cast<size_t>(subslot_bytes) * kRingSeconds;
    if (ring_bytes == 0) {
        close(owned_fd);
        return kUsbErrFormatMismatch;
    }

    auto* stream = new StreamState();
    stream->handle = handle;
    stream->silence = false;
    stream->stop.store(false, std::memory_order_release);

    const size_t buffer_size =
        static_cast<size_t>(kPacketsPerUrb) * static_cast<size_t>(handle->max_packet_size);
    for (int i = 0; i < kUrbCount; ++i) {
        stream->buffers[i].assign(buffer_size, 0);
        libusb_transfer* transfer = libusb_alloc_transfer(kPacketsPerUrb);
        if (transfer == nullptr) {
            destroy_stream(stream);
            close(owned_fd);
            return kUsbErrIo;
        }
        libusb_fill_iso_transfer(transfer, handle->device, handle->endpoint,
                                 stream->buffers[i].data(), 0, kPacketsPerUrb,
                                 on_transfer, stream, 0);
        stream->transfers[i] = transfer;
    }
    attach_feedback_transfer(handle, stream);

    handle->ring = new RingBuffer(ring_bytes);
    handle->stream = stream;
    handle->stop.store(false, std::memory_order_release);
    handle->decoder_done.store(false, std::memory_order_release);
    handle->frames_written.store(0, std::memory_order_release);
    handle->underruns.store(0, std::memory_order_release);
    handle->md5_state.store(kMd5Unavailable, std::memory_order_release);
    handle->error_code.store(kUsbOk, std::memory_order_release);
    handle->state.store(kStatePlaying, std::memory_order_release);
    handle->running.store(true, std::memory_order_release);

    {
        std::lock_guard<std::mutex> next_lock(handle->next_mutex);
        handle->next_files.clear();
        handle->boundaries.clear();
        handle->total_decoded_frames = 0;
        handle->current_track_base_frame = 0;
        handle->enqueued_tracks = 1;
        handle->active_track_index = 0;
        handle->next_files.push_back(PlayFile{owned_fd, start_frame, sample_rate, bit_depth,
                                              channels, subslot_bytes});
    }

    handle->worker = std::thread(stream_thread, handle);
    handle->decoder_worker = std::thread(decoder_thread, handle);

    LOGI("playing fd=%d start=%lld %d Hz/%d-bit/%d ch subslot=%d ring=%zu",
         fd, static_cast<long long>(start_frame), sample_rate, bit_depth, channels,
         subslot_bytes, ring_bytes);
    return kUsbOk;
}

extern "C" int bitstream_usb_enqueue_next(int64_t raw_handle, int fd, int sample_rate,
                                          int bit_depth, int channels, int subslot_bytes) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr || fd < 0 ||
        !format_matches(handle, sample_rate, bit_depth, channels, subslot_bytes)) {
        return kUsbErrFormatMismatch;
    }
    if (!handle->running.load(std::memory_order_acquire) ||
        handle->stop.load(std::memory_order_acquire)) {
        return kUsbErrBusy;
    }

    const int owned_fd = dup(fd);
    if (owned_fd < 0) {
        return kUsbErrIo;
    }

    std::lock_guard<std::mutex> lock(handle->mutex);
    if (!handle->running.load(std::memory_order_acquire) ||
        handle->stop.load(std::memory_order_acquire)) {
        close(owned_fd);
        return kUsbErrBusy;
    }
    {
        std::lock_guard<std::mutex> next_lock(handle->next_mutex);
        handle->next_files.push_back(PlayFile{owned_fd, 0, sample_rate, bit_depth, channels,
                                              subslot_bytes});
        handle->enqueued_tracks += 1;
    }
    if (handle->decoder_done.load(std::memory_order_acquire)) {
        if (handle->decoder_worker.joinable()) {
            handle->decoder_worker.join();
        }
        handle->decoder_done.store(false, std::memory_order_release);
        handle->decoder_worker = std::thread(decoder_thread, handle);
    }
    return kUsbOk;
}

extern "C" void bitstream_usb_set_software_volume(int64_t raw_handle, int32_t gain_q30) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr) {
        return;
    }
    handle->software_gain_q30.store(gain_q30, std::memory_order_relaxed);
}

extern "C" void bitstream_usb_stop(int64_t raw_handle) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr) {
        return;
    }
    stop_locked(handle);
}

extern "C" void bitstream_usb_close(int64_t raw_handle) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    close_handle(handle);
}

extern "C" void bitstream_usb_stats(int64_t raw_handle, int64_t* out) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr || out == nullptr) {
        return;
    }
    const uint64_t raw_written = handle->frames_written.load(std::memory_order_acquire);
    int64_t relative_written = static_cast<int64_t>(raw_written);
    int md5 = handle->md5_state.load(std::memory_order_acquire);
    int64_t track_index = 0;
    {
        std::lock_guard<std::mutex> next_lock(handle->next_mutex);
        while (!handle->boundaries.empty() &&
               raw_written >= handle->boundaries.front().end_frame &&
               handle->active_track_index + 1 < handle->enqueued_tracks) {
            handle->current_track_base_frame = handle->boundaries.front().end_frame;
            handle->boundaries.pop_front();
            handle->active_track_index += 1;
        }
        relative_written = static_cast<int64_t>(
            raw_written >= handle->current_track_base_frame
                ? raw_written - handle->current_track_base_frame
                : 0);
        if (!handle->boundaries.empty()) {
            md5 = handle->boundaries.front().md5_state;
        }
        track_index = handle->active_track_index;
    }

    int64_t ring_fill_percent = 0;
    {
        std::lock_guard<std::mutex> lock(handle->mutex);
        if (handle->ring != nullptr && handle->ring->capacity() > 0) {
            ring_fill_percent = static_cast<int64_t>(
                (handle->ring->available() * 100) / handle->ring->capacity());
        }
    }

    out[0] = handle->state.load(std::memory_order_acquire);
    out[1] = handle->underruns.load(std::memory_order_acquire);
    out[2] = relative_written;
    out[3] = md5;
    out[4] = handle->error_code.load(std::memory_order_acquire);
    out[5] = track_index;
    out[6] = ring_fill_percent;
}
