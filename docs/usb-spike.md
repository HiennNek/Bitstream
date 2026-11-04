# USB isochronous spike — JA11 on Android 16

Date: 2026-11-04
Device: JadeAudio / FiiO JA11 (`2972:0102`), UAC2, Android 16 (SDK 36)
Host: Samsung SM-X526B (gts10fe)

## Claim strategy

Android's `UsbDeviceConnection` opens the device, then Bitstream force-claims the
**audio control interface** (`claimInterface(iface, true)`). Without that claim the
kernel `snd-usb-audio` driver keeps ownership and every class-specific UAC request
returns `-1`, even though a standard `GET_DESCRIPTOR` succeeds.

The control interface stays claimed by Android. The native writer wraps the same fd
with libusb:

1. `libusb_init_context` with `LIBUSB_OPTION_NO_DEVICE_DISCOVERY`.
2. `libusb_wrap_sys_device(fd)`.
3. `libusb_set_auto_detach_kernel_driver(dev, 1)`.
4. Discover the control interface, UAC version, endpoint max packet size, and
   interval from the active configuration descriptor.
5. `libusb_claim_interface(streaming_interface)`.
6. `SET_CUR` sample rate on the UAC2 clock source (before the alt setting).
7. `libusb_set_interface_alt_setting(streaming_interface, alt)`.
8. Submit a pool of 4 isochronous OUT URBs, 16 packets each, filled with zeros.

`libusb_close` does not close the Android-owned fd; Kotlin keeps the
`UsbDeviceConnection` alive and closes it after the native handle is released.

## JA11 quirks

- The device enumerates as an IAD (`0xEF/2/1`), not as an audio-class device at the
  device level. The manifest attach filter therefore matches both audio class
  1/1 and IAD 239/2/1.
- The control interface must be claimed before any UAC2 `GET_RANGE`/`GET_CUR`
  request. This is the difference between all control requests returning `-1` and
  the device reporting its rates and volume range.
- UAC2 reports 8 discrete sample-rate ranges (44.1, 48, 88.2, 96, 176.4, 192,
  352.8, 384 kHz) through `UAC2_CS_RANGE`; the format descriptor itself has no rate
  table.
- Volume range is `-12800..0` in 1/256 dB with step `128` (-50.0 dB to 0.0 dB in
  0.5 dB steps).
- Streaming alt 3 is the selected setting: 2 channels, 4-byte subslot, 32-bit,
  asynchronous, feedback endpoint `0x85`, max packet 392, interval 1.
- At 48 kHz the nominal packet is exactly 48 bytes (2 ch × 4 bytes × 48000 / 8000),
  which is why the silence spike needs no feedback pacing yet.

## Result

Host test:

```
cmake -S core/playback/src/main/cpp -B build/native -DBITSTREAM_HOST_TEST=ON -G Ninja
cmake --build build/native
ctest --test-dir build/native --output-on-failure
```

`ring_buffer_test` passed.

Hardware run (logcat tag `BitstreamUsb`):

```
wrapped fd=6 uac=2 control=0 streaming=1 alt=3 endpoint=0x04 max_packet=392 interval=1 rate=48000 channels=2 subslot=4 sync=0 clock=6
starting 96000 frames of silence at 48000 Hz (48 bytes/packet nominal)
silence stream finished: 96000 frames, 0 underruns, last error 0
```

The 2 seconds of silence completed with zero underruns and no libusb error. The DAC
stayed connected, the audio server stayed alive, and no `LIBUSB_ERROR_BUSY` or kernel
claim failure appeared. Relaunching the app auto-connected the remembered JA11 and
reported the same formats and rates.

## Follow-ups for Task 13

- Add async feedback parsing and per-packet sizing for non-48 kHz rates.
- Report native underruns and typed errors to Kotlin.
- Keep the same claim order; do not claim the control interface from libusb while
  Android owns it.
