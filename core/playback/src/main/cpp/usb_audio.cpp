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

#include "flac_decoder.h"

namespace {

constexpr const char* kTag = "BitstreamUsb";
constexpr int kUrbCount = 4;
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
    int clock_source_id = 0;
    int uac_version = 2;
    int max_packet_size = 0;
    int interval = 1;
    int packets_per_second = 8000;
    int bytes_per_frame = 0;

    RingBuffer* ring = nullptr;
    std::mutex next_mutex;
    std::deque<PlayFile> next_files;

    std::mutex mutex;
    std::thread worker;
    std::thread decoder_worker;
    StreamState* stream = nullptr;
    std::atomic<bool> running{false};
    std::atomic<bool> stop{false};
    std::atomic<bool> decoder_done{true};
    std::atomic<uint64_t> frames_written{0};
    std::atomic<uint32_t> underruns{0};
    std::atomic<int> state{kStateIdle};
    std::atomic<int> md5_state{kMd5Unavailable};
    std::atomic<int> error_code{kFlacOk};
    int last_error = 0;
};

struct StreamState {
    UsbAudioHandle* handle = nullptr;
    libusb_transfer* transfers[kUrbCount] = {};
    std::vector<uint8_t> buffers[kUrbCount];
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
};

bool discover_layout(libusb_device_handle* device, int streaming_interface, int alt_setting,
                     int endpoint_address, DeviceLayout* layout) {
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
                if (ep.bEndpointAddress != endpoint_address) {
                    continue;
                }
                const int packet = ep.wMaxPacketSize & 0x7FF;
                const int mult = 1 + ((ep.wMaxPacketSize >> 11) & 0x3);
                layout->max_packet_size = packet * mult;
                layout->interval = std::max(1, static_cast<int>(ep.bInterval));
                found_endpoint = true;
            }
        }
    }
    libusb_free_config_descriptor(config);
    return layout->control_interface >= 0 && found_endpoint && layout->max_packet_size > 0;
}

int set_sample_rate(UsbAudioHandle* handle) {
    if (handle->sample_rate <= 0) {
        return 0;
    }

    if (handle->uac_version == 2) {
        if (handle->clock_source_id <= 0) {
            LOGW("no UAC2 clock source id; skipping rate control");
            return 0;
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
            return result < 0 ? result : LIBUSB_ERROR_IO;
        }
        return 0;
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
        return result < 0 ? result : LIBUSB_ERROR_IO;
    }
    return 0;
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
    // floor and ceil frame counts using a fractional carry.
    const double frames_per_packet =
        static_cast<double>(handle->sample_rate) /
        static_cast<double>(handle->packets_per_second);

    int offset = 0;
    for (int i = 0; i < transfer->num_iso_packets; ++i) {
        stream->carry += frames_per_packet;
        int frames = static_cast<int>(stream->carry);
        stream->carry -= static_cast<double>(frames);
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
    if (handle->bytes_per_frame > 0) {
        handle->frames_written.fetch_add(
            static_cast<uint64_t>(offset / handle->bytes_per_frame),
            std::memory_order_acq_rel);
    }
}

int submit_transfer(StreamState* stream, libusb_transfer* transfer) {
    stream->in_flight.fetch_add(1, std::memory_order_acq_rel);
    const int result = libusb_submit_transfer(transfer);
    if (result < 0) {
        stream->in_flight.fetch_sub(1, std::memory_order_acq_rel);
    }
    return result;
}

bool should_continue(StreamState* stream) {
    if (stream->stop.load(std::memory_order_acquire)) {
        return false;
    }
    if (stream->silence) {
        return stream->frames_done.load(std::memory_order_acquire) < stream->target_frames;
    }
    return !(stream->handle->decoder_done.load(std::memory_order_acquire) &&
             stream->handle->ring->available() == 0);
}

void LIBUSB_CALL on_transfer(libusb_transfer* transfer) {
    auto* stream = static_cast<StreamState*>(transfer->user_data);
    auto* handle = stream->handle;
    stream->in_flight.fetch_sub(1, std::memory_order_acq_rel);

    if (transfer->status == LIBUSB_TRANSFER_COMPLETED) {
        for (int i = 0; i < transfer->num_iso_packets; ++i) {
            const libusb_iso_packet_descriptor& packet = transfer->iso_packet_desc[i];
            if (packet.status == LIBUSB_TRANSFER_COMPLETED) {
                stream->frames_done.fetch_add(
                    packet.actual_length / std::max(1, handle->bytes_per_frame),
                    std::memory_order_acq_rel);
            } else if (packet.status == LIBUSB_TRANSFER_OVERFLOW) {
                handle->underruns.fetch_add(1, std::memory_order_acq_rel);
            }
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

    while (should_continue(stream)) {
        struct timeval timeout = {0, 100000};
        libusb_handle_events_timeout_completed(handle->context, &timeout, nullptr);
        if (stream->errors.load(std::memory_order_acquire) > kMaxErrors) {
            LOGE("too many isochronous errors; stopping");
            handle->error_code.store(kFlacDecodeError, std::memory_order_release);
            break;
        }
        if (stream->silence &&
            stream->frames_done.load(std::memory_order_acquire) >= stream->target_frames) {
            break;
        }
    }

    const bool completed = !handle->stop.load(std::memory_order_acquire) &&
                           handle->error_code.load(std::memory_order_acquire) == kFlacOk;
    stream->stop.store(true, std::memory_order_release);
    for (auto* transfer : stream->transfers) {
        if (transfer != nullptr) {
            libusb_cancel_transfer(transfer);
        }
    }

    const auto deadline =
        std::chrono::steady_clock::now() + std::chrono::milliseconds(500);
    while (stream->in_flight.load(std::memory_order_acquire) > 0 &&
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
    } else if (handle->error_code.load(std::memory_order_acquire) != kFlacOk) {
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

        handle->md5_state.store(kMd5Unavailable, std::memory_order_release);
        const FlacDecodeResult result = decode_flac_fd(
            file.fd, handle->ring, file.sample_rate, file.bit_depth, file.channels,
            file.subslot_bytes, file.start_frame, &handle->stop);
        close(file.fd);

        if (!result.complete) {
            if (result.error != kFlacOk && !handle->stop.load(std::memory_order_acquire)) {
                handle->error_code.store(result.error, std::memory_order_release);
                handle->state.store(kStateError, std::memory_order_release);
            }
            break;
        }
        handle->md5_state.store(result.md5_state, std::memory_order_release);
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
    delete stream;
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

    if (stream != nullptr && stream->in_flight.load(std::memory_order_acquire) == 0) {
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
    if (!discover_layout(handle->device, interface_number, alt_setting, endpoint, &layout)) {
        LOGE("could not find control interface or endpoint 0x%02x on alt %d",
             endpoint, alt_setting);
        close_handle(handle);
        return 0;
    }
    handle->control_interface = layout.control_interface;
    handle->uac_version = layout.uac_version;
    handle->max_packet_size = layout.max_packet_size;
    handle->interval = layout.interval;

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

    result = set_sample_rate(handle);
    if (result < 0) {
        close_handle(handle);
        return 0;
    }
    LOGI("clock source %d current rate: %d Hz (requested %d)",
         handle->clock_source_id, read_sample_rate(handle), handle->sample_rate);

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

extern "C" int bitstream_usb_start_silence(int64_t raw_handle, int64_t frames) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr || frames <= 0 || handle->bytes_per_frame <= 0) {
        return LIBUSB_ERROR_INVALID_PARAM;
    }

    stop_locked(handle);

    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->stream != nullptr || handle->running.load(std::memory_order_acquire)) {
        return LIBUSB_ERROR_BUSY;
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
            return LIBUSB_ERROR_NO_MEM;
        }
        libusb_fill_iso_transfer(transfer, handle->device, handle->endpoint,
                                 stream->buffers[i].data(), 0, kPacketsPerUrb,
                                 on_transfer, stream, 0);
        stream->transfers[i] = transfer;
    }

    handle->stream = stream;
    handle->stop.store(false, std::memory_order_release);
    handle->decoder_done.store(true, std::memory_order_release);
    handle->frames_written.store(0, std::memory_order_release);
    handle->underruns.store(0, std::memory_order_release);
    handle->error_code.store(kFlacOk, std::memory_order_release);
    handle->state.store(kStatePlaying, std::memory_order_release);
    handle->running.store(true, std::memory_order_release);
    handle->worker = std::thread(stream_thread, handle);

    LOGI("starting %lld frames of silence at %d Hz (%d bytes/packet nominal)",
         static_cast<long long>(frames), handle->sample_rate,
         static_cast<int>((static_cast<double>(handle->sample_rate) *
                           handle->bytes_per_frame) /
                          handle->packets_per_second));
    return 0;
}

extern "C" int bitstream_usb_play_file(int64_t raw_handle, int fd, int64_t start_frame,
                                       int sample_rate, int bit_depth, int channels,
                                       int subslot_bytes) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr || fd < 0 || start_frame < 0 ||
        !format_matches(handle, sample_rate, bit_depth, channels, subslot_bytes)) {
        return kFlacFormatMismatch;
    }

    const int owned_fd = dup(fd);
    if (owned_fd < 0) {
        return kFlacIoError;
    }

    stop_locked(handle);

    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->stream != nullptr || handle->running.load(std::memory_order_acquire)) {
        close(owned_fd);
        return LIBUSB_ERROR_BUSY;
    }

    const size_t ring_bytes = static_cast<size_t>(sample_rate) *
                              static_cast<size_t>(channels) *
                              static_cast<size_t>(subslot_bytes) * kRingSeconds;
    if (ring_bytes == 0) {
        close(owned_fd);
        return kFlacFormatMismatch;
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
            return LIBUSB_ERROR_NO_MEM;
        }
        libusb_fill_iso_transfer(transfer, handle->device, handle->endpoint,
                                 stream->buffers[i].data(), 0, kPacketsPerUrb,
                                 on_transfer, stream, 0);
        stream->transfers[i] = transfer;
    }

    handle->ring = new RingBuffer(ring_bytes);
    handle->stream = stream;
    handle->stop.store(false, std::memory_order_release);
    handle->decoder_done.store(false, std::memory_order_release);
    handle->frames_written.store(0, std::memory_order_release);
    handle->underruns.store(0, std::memory_order_release);
    handle->md5_state.store(kMd5Unavailable, std::memory_order_release);
    handle->error_code.store(kFlacOk, std::memory_order_release);
    handle->state.store(kStatePlaying, std::memory_order_release);
    handle->running.store(true, std::memory_order_release);

    {
        std::lock_guard<std::mutex> next_lock(handle->next_mutex);
        handle->next_files.clear();
        handle->next_files.push_back(PlayFile{owned_fd, start_frame, sample_rate, bit_depth,
                                              channels, subslot_bytes});
    }

    handle->worker = std::thread(stream_thread, handle);
    handle->decoder_worker = std::thread(decoder_thread, handle);

    LOGI("playing fd=%d start=%lld %d Hz/%d-bit/%d ch subslot=%d ring=%zu",
         fd, static_cast<long long>(start_frame), sample_rate, bit_depth, channels,
         subslot_bytes, ring_bytes);
    return 0;
}

extern "C" int bitstream_usb_enqueue_next(int64_t raw_handle, int fd, int sample_rate,
                                          int bit_depth, int channels, int subslot_bytes) {
    auto* handle = reinterpret_cast<UsbAudioHandle*>(raw_handle);
    if (handle == nullptr || fd < 0 ||
        !format_matches(handle, sample_rate, bit_depth, channels, subslot_bytes)) {
        return kFlacFormatMismatch;
    }
    if (handle->decoder_done.load(std::memory_order_acquire)) {
        return LIBUSB_ERROR_BUSY;
    }

    const int owned_fd = dup(fd);
    if (owned_fd < 0) {
        return kFlacIoError;
    }

    std::lock_guard<std::mutex> lock(handle->next_mutex);
    handle->next_files.push_back(PlayFile{owned_fd, 0, sample_rate, bit_depth, channels,
                                          subslot_bytes});
    return 0;
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
    out[0] = handle->state.load(std::memory_order_acquire);
    out[1] = handle->underruns.load(std::memory_order_acquire);
    out[2] = static_cast<int64_t>(handle->frames_written.load(std::memory_order_acquire));
    out[3] = handle->md5_state.load(std::memory_order_acquire);
    out[4] = handle->error_code.load(std::memory_order_acquire);
}
