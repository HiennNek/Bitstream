#pragma once

#include <cstdint>

extern "C" {

int64_t bitstream_usb_open(int fd, int endpoint, int interface_number, int alt_setting,
                           int subslot_bytes, int channels, int sample_rate, int sync_type,
                           int feedback_endpoint, int clock_source_id);

int bitstream_usb_start_silence(int64_t handle, int64_t frames);

void bitstream_usb_stop(int64_t handle);

void bitstream_usb_close(int64_t handle);

}
