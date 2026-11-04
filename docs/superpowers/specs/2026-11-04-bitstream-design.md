# Bitstream — local bit-perfect FLAC player for Android

**Status:** design, awaiting approval
**Date:** 2026-11-04
**Working name:** Bitstream (rename is trivial; package `dev.hien.bitstream`)

## 1. Intent and why

A device-local music player for people who care about the digital path. It plays FLAC
files from storage and sends integer PCM straight to a USB DAC through a native USB
audio driver, bypassing Android's mixer, resampler, and effects. The UI is Material 3
Expressive.

The point is not "a music player that happens to use USB". The point is that the bytes
leaving the app are the bytes in the FLAC file, at the file's own sample rate and bit
depth, and the app can prove it. Everything else is in service of that claim: the
library exists so the right file is easy to find, and the UI exists so the claim is
visible and trustworthy.

Target user: one person (the owner) with a USB-C DAC dongle and a local FLAC library.
No accounts, no network, no telemetry.

## 2. Goals

1. Bit-perfect playback of stereo FLAC to a USB DAC: no resampling, no float conversion,
   no gain, no DSP, no Android mixer.
2. Per-track sample-rate switching, including 44.1/48/88.2/96/176.4/192/352.8/384 kHz.
3. 16-, 24-, and 32-bit FLAC, sent in the DAC's native USB subslot format.
4. Gapless playback when consecutive tracks share rate, depth, and channel count.
5. A library-first experience: scan folders, browse albums/artists/tracks, search,
   playlists, queue.
6. Background playback with a media session, notification, lockscreen and headset
   controls.
7. Material 3 Expressive UI: expressive motion, dynamic color, adaptive layouts.
8. Honest verification: an in-app integrity check against the FLAC STREAMINFO MD5,
   plus live format/underrun diagnostics.
9. Works on modern Android phones (Android 10+) with UAC2 and UAC1 DACs.

## 3. Non-goals (v1)

- DSD, DoP, MQA, or any format other than FLAC (PCM).
- Multichannel or mono content. Stereo only; non-stereo files are shown as unsupported.
- Streaming, network sources, cloud, casting, Android Auto, Wear, widgets.
- EQ, tone controls, ReplayGain, crossfade, normalization, or any sample modification.
  These contradict the product's claim.
- Output through the phone speaker, Bluetooth, or the Android audio mixer. Playback
  requires a USB DAC.
- Tag editing, folder management, lyrics, visualizers.
- CUE sheets and single-file albums. One FLAC file is one track in v1.
- Resampling fallback for DACs that lack the source rate. v1 refuses and explains.

## 4. Anti-goals (would mean the design failed)

- A "bit-perfect" badge that is only a label, not backed by the actual pipeline.
- Silent resampling or gain applied anywhere between decode and USB.
- A player that is bit-perfect but unpleasant to browse a real library with.
- Locking the app to one DAC model. The JA11 is the test device, not the target.
- Shipping a Material 3 skin that ignores expressive motion, adaptive layout, and
  accessibility.

## 5. Constraints

**Hardware / platform**
- Android 10+ (minSdk 29), compile/target SDK 36.
- Primary test device: Samsung Galaxy Tab S10 FE (SM-X526B), Android 16 / API 36, USB-C
  host confirmed active. It is a tablet, so medium/expanded and landscape layouts are
  first-class, not an afterthought.
- USB host mode required. The device must supply bus power to the DAC.
- Test DAC: JadeAudio/FiiO JA11, UAC2 by default (UAC1 switchable only via FiiO
  Control), PCM up to 384 kHz/32-bit, hardware volume in 60 steps, PEQ in the dongle
  DSP, no physical buttons.
- The development phone is reachable over adb wireless and the JA11 is attached, so USB
  work is hardware-in-the-loop from the start. No UAC1 DAC is available; UAC1 support is
  parser-fixture-tested and marked best-effort.
- Android's `UsbDeviceConnection` supports control, bulk, and interrupt transfers only.
  Isochronous transfers require native code.
- The kernel's USB audio driver may already own the interface; we claim it forcibly and
  keep the `UsbDeviceConnection` open while libusb wraps its file descriptor.
- No real device is attached to the build machine; hardware validation is manual on the
  owner's phone. The emulator can test everything except USB audio.

**Bit-perfect definition used by this app**
A sample is bit-perfect when, for every frame: the integer sample value decoded from
FLAC is the integer value written to the DAC, in the same channel order, at the source
sample rate, with no scaling, dithering, mixing, or format conversion other than
zero-padding into a wider USB subslot. The app cannot observe what the DAC's own DSP
does afterward.

**Known caveat**
The JA11's PEQ runs inside the dongle. If it is enabled in FiiO Control, the DAC output
is not the file, even though our transport is bit-perfect. The app cannot detect or
disable it; it will state this plainly the first time a JA11 connects and in Settings.

## 6. Approaches considered

### A. Custom engine, Media3 as the shell — **chosen**
libFLAC (NDK) → integer PCM → native ring buffer → libusb UAC writer. Media3 supplies
`SimpleBasePlayer` and `MediaSessionService` for system integration, not audio.
*Why:* the PCM path is entirely ours, so "no resampling" is structural. Gapless and
rate switching are under our control. Media3 removes the tedious media-session and
notification work. Cost: we own the engine, ring buffer, and USB driver.

### B. ExoPlayer with a custom USB `AudioSink`
Reuse Media3's decode, timing, and buffering; replace its audio sink.
*Rejected because:* ExoPlayer's pipeline assumes it may insert audio processors and
resample; correctness would depend on auditing and disabling that, and the sink still
has to fight ExoPlayer's timing model for rate switches and gapless. More places for a
silent conversion to hide, for less control over the thing the product is about.

### C. Adopt an existing Android USB-audio driver
*Dropped:* the usable open-source options are UAC1-only or unmaintained, which rules out
high-res UAC2 DACs. The driver is the product; it is worth owning.

## 7. Architecture

```
┌──────────────────────────── app (Compose, M3 Expressive) ───────────────────────────┐
│ Library · Album · Artist · Search · Playlists · Now Playing · Queue · Settings      │
└───────────────▲──────────────────────────────────────────────▲──────────────────────┘
                │ StateFlow / ViewModels                       │ MediaController
┌───────────────┴───────────────┐                ┌──────────────┴──────────────────────┐
│ core:data                     │                │ PlaybackService (MediaSessionService)│
│ Room · SAF scanner · FLAC tags│                │ MediaSession ← SimpleBasePlayer      │
│ artwork cache · playlists     │                │ foreground notification, focus       │
└───────────────▲───────────────┘                └──────────────▲──────────────────────┘
                │                                              │
                │                          ┌───────────────────┴──────────────────────┐
                │                          │ core:playback — PlaybackEngine            │
                │                          │ queue · state machine · gapless · MD5     │
                │                          └───────┬───────────────────────┬───────────┘
                │                                  │ JNI                   │ JNI
                │                    ┌─────────────▼──────────┐  ┌─────────▼───────────┐
                │                    │ libFLAC decode thread  │  │ USB writer thread   │
                │                    │ → PCM → SPSC ring      │  │ libusb iso URBs     │
                │                    └────────────────────────┘  └─────────┬───────────┘
                │                                                          │
                │                                              ┌───────────▼───────────┐
                │                                              │ UsbDeviceConnection fd │
                │                                              │ → libusb_wrap_sys_device│
                └──────────────────────────────────────────────└───────────────────────┘
```

**Modules**
- `:app` — Compose UI, navigation, ViewModels, manual DI container, `PlaybackService`.
- `:core:model` — track/album/artist/playlist/queue/format models, pure Kotlin.
- `:core:data` — Room, SAF library scanner, FLAC metadata reader, artwork cache,
  repositories, search, playlists.
- `:core:playback` — engine state machine, queue, `SimpleBasePlayer`, USB device manager
  (Kotlin), JNI bridge, native CMake build (libFLAC, libusb, ring buffer, UAC driver).
- `:core:designsystem` — M3 Expressive theme, motion, shared components, icons.

No Hilt. A small `AppContainer` in `Application` wires the graph; constructor injection
everywhere else. This keeps build times and complexity down for a single-module-per-layer
app.

## 8. USB audio layer

### 8.1 Enumeration and permission
- `UsbManager.deviceList` → pick devices exposing an audio control interface (class 1,
  subclass 1). Prefer the last-used device (vendor/product/serial in DataStore).
- `requestPermission` with an immutable `PendingIntent`; handle grant/deny and
  attach/detach broadcasts. Auto-connect on attach when the app was playing and the
  device matches the remembered one.
- Open `UsbDeviceConnection`, then `claimInterface(iface, force = true)` for the audio
  control interface; hand the raw fd to native code.

### 8.2 Descriptor parsing (Kotlin, unit-testable)
Parse `UsbDeviceConnection.rawDescriptors` ourselves rather than relying on
`UsbDevice`'s limited model. Produce a `UacDevice` model:
- UAC version (1.0 or 2.0), control interface, streaming interface(s).
- Alternate settings; the one with an isochronous OUT endpoint.
- Format type I: channels, subslot size, bit resolution, supported sample rates.
- Endpoint: address, max packet size, interval, sync type (async/adaptive/sync),
  feedback endpoint (if any).
- UAC2 clock source entity, sample-rate control, feature unit with volume/mute.
- UAC1 endpoint sample-rate control and feature unit layout.

This parser is the main test surface: captured raw descriptor dumps from the JA11 (UAC2)
and at least one UAC1 DAC become fixtures.

### 8.3 Format and rate selection
- Match the FLAC stream's rate against the DAC's advertised rates. Exact match required.
- Select the streaming alt setting whose channel count and subslot can hold the source
  depth. A source may be placed in a wider subslot only by left-justifying it into the
  DAC's declared valid-bit positions and zero-filling the unused low bits (the standard
  24-in-32 and 16-in-32 representations). No other scaling, truncation, or dithering.
  The source's significant bits must be preserved exactly; that is what "bit-perfect"
  means here, regardless of whether the DAC declares 24 or 32 valid bits. A DAC that
  cannot hold the source depth in any alt setting is a format mismatch.
- Set rate before starting the stream:
  - UAC2: `SET_CUR` on the clock source's SAMPLING_FREQ_CONTROL (16.16 Hz).
  - UAC1: `SET_CUR` sampling frequency on the streaming endpoint (3-byte).
- Set the alt setting (bandwidth) only after the rate is accepted.
- If the rate is unsupported: fail with a specific message naming the rate and the
  DAC's supported set. No silent fallback.

### 8.4 Streaming (native)
- `libusb_init` with `LIBUSB_OPTION_NO_DEVICE_DISCOVERY`; `libusb_wrap_sys_device` on
  the `UsbDeviceConnection` fd.
- Claim the streaming interface, select the alt setting, allocate a pool of
  isochronous OUT URBs (triple/quad buffered, sized to ~8–16 ms of audio).
- Fill each URB's packets from the ring buffer; submit; reap on completion; resubmit.
  The loop is a dedicated native thread with real-time-ish priority.
- **Async feedback:** if the OUT endpoint is asynchronous, read the feedback endpoint
  (UAC2 16.16 format; UAC1 10.14) and size packets from it, clamped to the endpoint's
  min/max packet range. The ring fill level provides a slow outer correction; feedback
  provides per-packet pacing. For adaptive/synchronous endpoints, use nominal packet
  sizing and let the ring absorb drift.
- **Underruns:** zero-fill the missing frames, increment an underrun counter, and expose
  it in diagnostics. Never insert stale audio.
- **Stop:** cancel URBs, release the interface, close libusb, release alt setting. Keep
  the fd open until Kotlin closes the connection.
- **Errors:** map libusb/usbfs errors to typed JNI results (`NO_DEVICE`, `BUSY`,
  `STALL`, `OVERRUN`, `UNDERRUN`, `PROTOCOL`); Kotlin decides whether to retry, pause, or
  surface an error.

### 8.5 Ring buffer
- Single-producer (decoder) / single-consumer (USB) lock-free ring in native memory,
  target 2–4 seconds of audio. Fill-level watermark drives decode start/stop.
- On seek: stop decode, flush ring, seek libFLAC, refill, resume.
- On track change: if format is unchanged, keep the USB stream and the ring continuous;
  the decoder swaps files at an exact frame boundary. If format changes, drain, stop the
  stream, reconfigure, restart (an audible gap is unavoidable and will be shown as
  "format change" in diagnostics).

### 8.6 Native build
- CMake + NDK 29. Vendored, pinned libusb (1.0.27+) and libFLAC (1.4.x) sources under
  `third_party/`. libFLAC is BSD-3; libusb is LGPL-2.1, linked as a shared library so
  the app can comply by allowing relinking.
- ABIs: `arm64-v8a`, `x86_64` (emulator). `armeabi-v7a` optional.

## 9. Decoding

- libFLAC streaming decoder in the native layer. It yields interleaved signed integer
  PCM in the stream's native depth (16/24/32), so no conversion is needed before packing.
- The Kotlin layer reads STREAMINFO for rate, depth, channels, total samples, and the
  MD5 signature before playback starts.
- FLAC metadata parsing for the library is Kotlin-side (see §10), independent of decode.
- Unsupported: non-stereo, sample rates the DAC lacks, FLAC files with corrupt frames
  (decode error surfaces as a playback error, not silent skipping).
- **MD5 verification:** the decoder computes MD5 over the exact interleaved PCM it
  produces, before any optional software volume. On track completion, compare against
  STREAMINFO's MD5. This proves decode integrity and that nothing between decode and the
  ring buffer altered samples. Partial plays report "Partial". If STREAMINFO has no MD5
  (all zero), the result is "Unavailable", not "Failed". When software volume is enabled,
  the integrity chip reads "Decode verified" and the path chip carries the
  not-bit-perfect warning, so the two claims never blur together. Results appear in Now
  Playing diagnostics and Settings.

## 10. Library

- **Source:** Storage Access Framework. The user picks one or more folders with
  `ACTION_OPEN_DOCUMENT_TREE`; persisted read permission. No `READ_MEDIA_AUDIO`, no
  broad storage permission. Works with internal storage and SD cards.
- **Scale assumption:** tens of thousands of tracks at most, on internal storage or SD
  card. One FLAC file per track. The scanner must stay responsive at that size but is not
  designed for 100k-track libraries.
- **Scanner:** breadth-first walk of `DocumentFile`s, filter `.flac` (case-insensitive).
  Read metadata by parsing the first metadata blocks through `ContentResolver`:
  STREAMINFO, VORBIS_COMMENT, PICTURE. Handle an optional leading ID3v2 block
  (non-standard but seen in the wild) by skipping it. No full-file reads.
- **Fields:** title, artist, album, album artist, track/disc number, year, genre,
  duration (from STREAMINFO total samples / rate), sample rate, bit depth, channels,
  file size, last modified, document URI, folder, embedded-art hash.
- **Artwork:** embedded PICTURE (front cover preferred), else `cover.jpg`/`folder.jpg`
  in the same folder. Downscale once to a cached WebP/JPEG in app storage, keyed by
  content hash; LRU eviction with a size cap.
- **Incremental rescans:** skip unchanged files by URI + size + last-modified; remove
  entries whose documents disappeared. Scan runs in a coroutine with progress and
  cancellation, on `Dispatchers.IO`, and survives configuration changes via a
  foreground-scoped ViewModel; a WorkManager job handles long scans when the app leaves
  the foreground.
- **Room schema:**
  - `tracks` (id, uri, title, artist, album, album_artist, track_no, disc_no, year,
    genre, duration_ms, sample_rate, bit_depth, channels, has_md5, art_key, folder,
    size, modified, added_at)
  - `albums` (id, title, album_artist, year, art_key) — derived, rebuilt on scan
  - `artists` (id, name) — derived
  - `playlists` (id, name, created_at, updated_at)
  - `playlist_entries` (playlist_id, track_id, position)
  - `scan_folders` (tree_uri, display_name, last_scan)
  - `playback_state` (single row: queue JSON, index, position_ms, shuffle, repeat)
  - FTS4 virtual table over title/artist/album/album_artist for search.
- **Playlists:** create, rename, delete, add/remove, reorder. No smart playlists in v1.
- **Queue:** ordered track IDs, current index, shuffle order (seeded, with history so
  "previous" is sane), repeat off/all/one. Persisted so playback resumes after process
  death.
- **Sorting:** albums by album artist then year then title; tracks by disc then track
  then title. Folder view is out of scope; search covers navigation.

## 11. Playback engine and system integration

- **State machine:** `Idle → Preparing → Buffering → Playing → Paused → Stopped`,
  plus `Error(reason)` and `NoDac`. State includes track, queue position, position ms,
  format, DAC status, bit-perfect status, integrity status, underrun count.
- **Commands:** setQueue, playIndex, play/pause, seek, next/previous, shuffle, repeat,
  setVolume (hardware), stop. All commands serialized on the engine's dispatcher.
- **Gapless:** pre-open the next track when the current one is within ~10 s of its end
  and formats match; the decoder writes directly into the ring at the exact boundary.
  If formats differ, mark the transition as gapped.
- **Seeking:** libFLAC `seek_absolute` to the target frame; flush ring; resume. Position
  is derived from frames written minus frames still in the ring, not wall clock.
- **Unplayable tracks:** when auto-advance or shuffle lands on a track the current DAC
  cannot play (rate/channel mismatch) or that fails to decode, the engine skips to the
  next playable track, posts a one-line notice, and continues. If none remain it stops
  with the specific reason. The album/artist "Play bit-perfect" action pre-filters the
  queue to tracks the connected DAC supports; plain Play keeps the full queue and relies
  on skip.
- **Media session:** `SimpleBasePlayer` on the main looper, fed by engine state;
  `MediaSessionService` owns player + session. Foreground service type `mediaPlayback`.
  Notification: art, title/artist/album, transport, and a format line
  (`FLAC 24/96 · Bit-perfect`). `POST_NOTIFICATIONS` requested at the right moment.
- **Audio focus:** request `AUDIOFOCUS_GAIN`; on loss pause (never duck); resume on gain
  only if the loss was transient and the user had been playing.
- **Lifecycle:** `WakeLock` (partial) only while streaming; release on pause/stop. USB
  detach pauses and posts a notification; reconnect offers resume. `onTaskRemoved` keeps
  playing if streaming, otherwise stops.
- **Bluetooth/headset:** media buttons and AVRCP volume route through the session;
  volume maps to DAC hardware volume when available.

## 12. Volume and the bit-perfect claim

- **Preferred:** UAC feature-unit hardware volume. The Now Playing slider and media
  volume commands set the DAC's own volume in its native step count. This preserves
  bit-perfection at any level, because the DAC's volume stage is downstream of the
  digital transport; "unity" is not required.
- **If the DAC exposes no standard volume:** the slider is disabled and labelled
  "Fixed output". Settings offers an explicit **Software volume (not bit-perfect)**
  toggle, default off. When on, the badge changes and the app explains why.
- No app-side gain, dither, or normalization in the bit-perfect path.
- Android's own media volume is irrelevant to the USB stream; the app says so in
  Settings so users do not think the phone's volume slider is doing anything.

## 13. Bit-perfect status and diagnostics

Now Playing shows a status chip with three independent facts, each with icon + text
(never color alone):

1. **Path** — `Bit-perfect` when: stream active, DAC rate == source rate, every source
   significant bit is preserved in the DAC's valid-bit positions with only zero-filled
   low bits, volume is hardware-controlled (any level) or unity, and software volume is
   off. Otherwise the specific reason (`Resampled`, `Software volume`, `Format mismatch`).
2. **Integrity** — `MD5 verified` after a full track whose decoded PCM matches
   STREAMINFO; `Partial` otherwise; `Failed` on mismatch; `Unavailable` when the file
   carries no MD5.
3. **Transport** — `0 underruns` for the current track, or a count. Underruns insert
   silence and break continuity even though samples are untouched.

Settings → Diagnostics shows: UAC version, selected alt setting, endpoint address and
sync type, subslot/valid bits, negotiated rate, ring fill, URB statistics, libusb
errors, and a copyable descriptor dump. This is the honest evidence, not a marketing
badge.

## 14. UI — Material 3 Expressive

**Theme**
- `MaterialExpressiveTheme` with `MotionScheme.expressive()`, expressive shapes, and a
  full expressive type scale. Opt into `@ExperimentalMaterial3ExpressiveApi` explicitly
  and isolate it in `:core:designsystem`.
- Dynamic color on Android 12+; a designed static light/dark scheme (deep teal seed with
  a warm accent) on older versions. No hardcoded colors outside the theme.
- Edge-to-edge, dark/light/system, predictive back.

**Structure and responsive behavior**

Every screen must work in all four combinations: phone portrait, phone landscape,
tablet portrait, tablet landscape. Layout follows the window size class
(`currentWindowAdaptiveInfo()`), not the device type, so split-screen, freeform, and DeX
are covered by the same rules.

| Window | Navigation | Content | Queue | Now Playing |
|---|---|---|---|---|
| Compact portrait (phone) | bottom `NavigationBar` + mini player | single pane | modal bottom sheet | full screen, art above controls |
| Compact landscape (phone) | `NavigationRail` | single pane, wider grids | bottom sheet, or side pane when width allows | art left, controls right |
| Medium portrait (tablet) | `NavigationRail` | single pane, 4–5 column album grid | modal bottom sheet | two panes, art + controls |
| Medium/expanded landscape (tablet) | `NavigationRail` | `ListDetailPaneScaffold` (library → detail) | persistent supporting pane | art \| controls + queue |
| Expanded/large (split-screen, DeX) | `NavigationRail` or permanent drawer | list-detail, max content width 1040 dp | supporting pane | three panes |

- Use Material 3 adaptive `ListDetailPaneScaffold` / `SupportingPaneScaffold` for
  album/artist/playlist detail and the queue pane; `NavigationSuiteScaffold` switches
  bottom bar ↔ rail automatically.
- Album grids use adaptive minimum tile widths; text columns cap at ~840 dp; Settings
  caps at 720 dp.
- Orientation changes preserve queue, playback position, scroll, and back stack via
  ViewModel + `SavedStateHandle`; no `configChanges` hack.
- Edge-to-edge insets and display cutouts are handled in both orientations; transport
  controls stay within thumb reach in landscape.
- Predictive back, the shared-element artwork transition from the mini player, and the
  full Now Playing destination behave identically in every size class.
- Multi-window/freeform and DeX are treated as expanded layouts; no separate mode.

**Screens**
- **Library:** expressive header, segmented filter (Albums / Artists / Tracks), album
  grid with adaptive columns, recently added row, scan status.
- **Album:** large artwork hero, format line, track list with per-track rate/depth,
  play/shuffle actions, "Play bit-perfect" affordance.
- **Artist:** albums + top tracks.
- **Search:** search field with suggestions, grouped results.
- **Playlists:** list, create/rename, playlist detail with reorder.
- **Now Playing:** artwork, title/artist/album, expressive seek bar with elapsed and
  remaining, transport group, queue button, status chip (path/integrity/transport),
  volume (hardware), output sheet showing DAC name and negotiated format.
- **Queue:** modal bottom sheet, drag reorder, swipe remove, "play next".
- **Settings:** Library folders + rescan; Playback (DAC status, bit-perfect info, software
  volume, gapless); Appearance (theme, dynamic color); About; Diagnostics.
- **First run / empty states:** pick a music folder; connect a DAC. JA11-specific note
  about PEQ and UAC2.

**Expressive details (used with restraint)**
- Spring motion from the theme for press/expand/transition; no bespoke easing curves.
- Shared-element artwork transition, morphing play/pause icon, shape-morphing play FAB.
- Material Symbols as vector drawables (not the deprecated icons library).
- 48 dp touch targets, content descriptions, scalable type, 3:1/4.5:1 contrast checked in
  both schemes.

## 15. Error handling

| Case | Behavior |
|---|---|
| No DAC | `NoDac` state, connect prompt; library fully browsable |
| USB permission denied | explain, retry, remember choice for session |
| Interface claim fails / busy | retry once with reset; then typed error with hint (close other audio apps) |
| Unsupported rate | skip when auto-advancing; refuse with the rate and the DAC's supported set when explicitly requested |
| Unsupported channels | skip when auto-advancing; mark unsupported when requested; never downmix |
| Decode error / MD5 mismatch | stop, mark track failed, surface in diagnostics |
| Underrun | zero-fill, count, keep playing; show in diagnostics |
| DAC detached | pause, keep queue, offer reconnect/resume |
| Folder permission lost | mark library stale, prompt to re-pick folder |
| Service killed | restore queue + position from Room on next start |

## 16. Testing

- **JVM unit tests:** UAC1/UAC2 descriptor parser against captured fixtures; FLAC metadata
  parser (Vorbis comments, pictures, ID3v2-prefixed files); rate/format matcher; queue and
  shuffle logic; MD5 comparison; library grouping/sorting.
- **Instrumented tests:** Room schema/migrations, SAF scanning against a seeded folder,
  repository behavior, ViewModel state.
- **Native tests:** ring buffer SPSC correctness and overflow/underflow behavior via
  host-side CTest; URB packet sizing math including feedback conversion.
- **Version matrix:** Android 16 is the hardware target; API 29/33/35 emulators cover
  lifecycle, notification, foreground-service, permission, and UI behavior. minSdk 29 is
  a supported floor, not a promise of per-OEM testing.
- **Responsive matrix:** phone and tablet, portrait and landscape, compact/medium/expanded
  size classes, plus split-screen; rotation mid-playback must not interrupt audio or lose
  queue/position/scroll state.
- **Manual hardware checklist (JA11 + Galaxy Tab S10 FE, Android 16):** detection and permission, UAC2
  connection, rate switching across 44.1/48/96/192/384, 16/24/32-bit files, gapless
  same-format, gap on format change, seek, pause/resume, background + screen off,
  notification/lockscreen/headset controls, volume (hardware), detach/re-attach, MD5
  verification, underrun counters, battery/wakelock behavior.
- **Bit-perfect evidence:** a set of generated FLAC test files (sine sweeps, known
  patterns, silence boundaries) plus the DAC's own rate display; MD5 must pass for every
  full track; descriptor dump archived for the JA11.
- **UAC1:** no UAC1 hardware is available. Validate the parser and control-request paths
  with fixtures and mark UAC1 support best-effort until a device is tested.

## 17. Tooling and dependencies

- Gradle Kotlin DSL + version catalog. AGP compatible with SDK 36, Kotlin 2.4.x, JDK 17,
  Compose compiler via the Kotlin plugin.
- Compose BOM (2026.06+), `material3` 1.5.0-beta02 for expressive APIs (pin exactly;
  isolate experimental usage), Material 3 adaptive (`adaptive`, `adaptive-layout`,
  `adaptive-navigation`, navigation suite), activity-compose, navigation-compose,
  lifecycle, Media3 `session`/`common` 1.11.x, Room + KSP, Coil 3, DataStore,
  kotlinx-coroutines, kotlinx-serialization, DocumentFile.
- CMake 3.22.1, NDK 29, vendored libFLAC and libusb.
- No analytics, no network permission, no ads.

## 18. Milestones

0. **USB feasibility spike:** on the owner's phone, enumerate the JA11, dump descriptors,
   wrap the fd, claim the interface, and push a short isochronous stream (silence or a
   known PCM pattern) to confirm the libusb path and kernel-driver contention before the
   full build. Throwaway code; findings feed the plan.
1. **Skeleton:** Gradle project, modules, M3 Expressive theme, navigation, fake-data
   library and Now Playing screens. Runs on device/emulator.
2. **Library:** SAF picker, scanner, FLAC metadata parser, Room, artwork, search,
   playlists, queue persistence.
3. **USB foundation:** enumeration, permission, descriptor parser + fixtures, control
   requests, device diagnostics screen.
4. **Playback:** libFLAC decode, ring buffer, libusb iso writer, rate switching, gapless,
   seek, MD5 verification.
5. **System integration:** `SimpleBasePlayer`, `MediaSessionService`, notification,
   background, audio focus, detach/reconnect.
6. **Polish:** expressive motion and shared transitions, adaptive layouts, settings,
   diagnostics, accessibility, error states.
7. **Hardware validation:** JA11 checklist, fixes, release APK.

## 19. Risks

- **Isochronous feedback loop** is the hardest part; async DACs need correct packet
  pacing or they glitch or drift. Mitigation: feedback-first sizing, ring-level outer
  correction, hardware tests at every rate.
- **DAC quirks:** descriptor lies, non-standard control requests, kernel driver
  contention. Mitigation: parse raw descriptors, typed errors, descriptor dump, claim
  with force, per-device quirks table if needed.
- **JA11 PEQ** can invalidate the audible result without the app knowing. Mitigation:
  explicit warning and a first-connect checklist.
- **M3 Expressive API churn:** 1.5.0-beta. Mitigation: pin versions, isolate experimental
  APIs behind the design-system module.
- **SAF performance** on large libraries. Mitigation: metadata-only reads, incremental
  scans, WorkManager, cached art.
- **Battery/heat** from bus-powered DAC + wakelock. Mitigation: release wakelock when
  paused, stop the stream when idle.

## 20. Decisions made in this design (check these)

- Working name **Bitstream**, package `dev.hien.bitstream`, single-user app.
- **minSdk 29**, target/compile 36; arm64 + x86_64.
- **USB DAC required**; no speaker/Bluetooth fallback in v1.
- **Stereo only**; mono/multichannel unsupported rather than altered.
- **No resampling fallback**; unsupported rates are refused with an explanation.
- **SAF folder library**, not MediaStore, and no broad storage permission.
- **Hardware volume preferred**; software volume is an explicit opt-in marked
  not-bit-perfect.
- **MD5 integrity check** against FLAC STREAMINFO as the in-app proof.
- **Media3 shell only** (`SimpleBasePlayer` + `MediaSessionService`); no ExoPlayer audio.
- **libFLAC + libusb in native code**; libusb linked shared for LGPL compliance.
- **PEQ caveat** stated in-app for the JA11.
- **Bit-perfect format rule:** the source's significant bits must be preserved;
  left-justified zero-fill into a wider subslot is allowed and still bit-perfect.
- **MD5 absent** shows `Unavailable`, not failed; software volume demotes the path chip
  but not the decode-integrity check.
- **Auto-advance skips unplayable tracks** with a notice; "Play bit-perfect" pre-filters.
- **Version matrix:** minSdk 29 is the floor; Android 16 is the hardware target and older
  APIs are emulator-tested.
- **Hardware loop:** adb wireless + attached JA11, iterative installs and log capture.
  No UAC1 DAC; UAC1 is best-effort and fixture-tested.
- **Library:** tens of thousands of tracks max, one file per track, no CUE support.
- **Responsive:** phone and tablet, portrait and landscape are all first-class; layout
  follows window size class, and rotation never interrupts playback or loses state.

## 21. Left to the builder

- Exact dependency versions and AGP/Kotlin/Compose BOM pinning at scaffold time.
- Ring buffer size, URB count, packet sizing constants, feedback smoothing parameters.
- Quirks table contents once real DACs are tested beyond the JA11.
- Art cache size/eviction policy, scan concurrency, WorkManager constraints.
- Icon selection (Material Symbols), microcopy, empty-state illustration treatment.
- Whether `armeabi-v7a` ships.
- CI setup (none required for v1; local builds are the target).
