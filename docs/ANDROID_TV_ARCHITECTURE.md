# Android TV Architecture

Target: BenQ GP520, Google TV / Android 11, API 30.

## Device facts

BenQ lists Android 11, Bluetooth 5.2, Wi-Fi 2.4/5/6 GHz, and a 60 Hz display pipeline. Its manual advertises H.264 and HEVC decode up to 4K60, but file playback does not prove that an app-selected MediaCodec is hardware-backed or low latency. [GP520 specifications](https://www.benq.com/en-sg/projector/cinema/gp520/spec.html), [GP520 manual](https://esupportdownload.benq.com/esupport/PROJECTOR%20FOR%20CONSUMER/UserManual/GP520/GP520_UM_EN_260324150604.pdf)

Third-party data identifies an MT9629/T31, quad Cortex-A55, Mali-G52, and 2 GB RAM. Treat this as **INFERENCE** until confirmed with physical-device diagnostics. [Android TV Guide](https://www.androidtv-guide.com/streaming-gaming/benq-gp520/)

## Layers

```text
Kotlin (JVM)
  Compose for TV UI: Home / Consoles / Register / Settings / Diagnostics
  Session coordinator: lifecycle, permissions, secure storage, routes,
                       Surface lifecycle, InputDevice/KeyEvent/MotionEvent capture
           │ narrow JNI: commands, Surface hand-off, input batches, event/metric batches
Native Android adapter (Rust ndk/jni or C; Android-only; NOT part of the core)
  ANativeWindow from Surface, AMediaCodec feeder + output threads,
  AAudio/Oboe stream + PCM ring, JNI callback threads
           │ in-process Rust/C calls only; no JVM on this path
Portable core (no Android dependencies)
  discovery, registration, session, transport, crypto, telemetry, input
  emits compressed access units, PCM, events through platform ports
```

**Decision (review 2026-10-25):** compressed video and PCM never cross into the JVM. Kotlin hands over a `Surface` once per generation; the native adapter converts it with `ANativeWindow_fromSurface` and owns the codec. This matches upstream Android practice (FACT: Pylux `video-decoder.c` uses NDK `AMediaCodec` with a native output thread; `audio-output.cpp` uses Oboe).

The stream view is a `SurfaceView` hosted in Compose through `AndroidView`, never a `TextureView` (TextureView adds GPU composition and a copy). Overlay impact on hardware-composer planes is UNKNOWN on GP520 and must be measured.

Compose for TV supports API 21+, so API 30 is compatible. Android requires a `LEANBACK_LAUNCHER` activity and TV apps must work without touch. [Compose for TV](https://developer.android.com/training/tv/playback/compose), [TV app setup](https://developer.android.com/training/tv/get-started/create)

Use explicit focus groups/requesters and test every screen by D-pad and DualSense. Stream input must not navigate overlays unless menu mode is active. [Compose focus](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior)

## JNI boundary

The first emulator-only implementation candidate is documented in
`ANDROID_NATIVE_ADAPTER.md`. It uses an Android-only C++ adapter built by the
pinned NDK; the portable Rust crates remain free of JNI/NDK dependencies.

Recommended operations: create/destroy engine, start/stop discovery, register, connect/disconnect, set/clear Surface generation, submit raw input event batches, update audio route, and snapshot batched metrics/events.

- Kotlin owns Activity/Service/Surface lifecycle, permissions, and Android objects.
- Native code owns sockets, protocol state, queues, and transport threads.
- The native Android adapter owns `AMediaCodec`, the `ANativeWindow` for exactly one current Surface generation, and the AAudio/Oboe stream.
- Input crosses JNI once per Android input event (one call per `KeyEvent`, one per `MotionEvent` including its historical samples). Each call carries raw scan codes, key codes, axis values, and event time; mapping happens natively. Snapshots are never coalesced at this boundary (see `DUALSENSE_ANALYSIS.md`).
- Native threads that call back into Kotlin attach once, use cached global class/method references, and deliver batched events.
- Callbacks use documented executors and valid global references.
- Shutdown is idempotent and joins workers before callback/global-reference release.
- CR4-review authorization and physical GP520-access authorization are separate;
  the latter remains closed until a reviewed evidence package receives `GO` and
  the owner confirms date and scope.

## Video

Start with H.264 720p60, then 1080p60. HEVC follows only after H.264 lifecycle stability.

Feed complete compressed access units to MediaCodec and render directly to a Surface. Query `MediaCodecList`, select by exact format, and record component name, format, profile/level, classification, queue depth, timestamps, dropped frames, exceptions, resets, and pacing. API availability is not proof of hardware acceleration. [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec), [NDK media](https://developer.android.com/ndk/reference/group/media)

Surface generation IDs prevent late callbacks from binding a replaced Surface. Decoder errors should first recreate the decoder and recover at an IDR, not restart authentication. A replacement Surface uses `AMediaCodec_setOutputSurface` (API 23) before falling back to decoder recreation.

The core never calls the codec. Frame assembly hands complete access units to the adapter through the bounded video queue in `CONCURRENCY.md`. Only the adapter's feeder thread waits on `AMediaCodec_dequeueInputBuffer`. (FACT: Pylux waits up to 3 × 10 ms on the sample path; GPSx must not block network receive this way.) Exactly one `memcpy` into the codec input buffer is allowed; no JVM buffer hop.

API 30 notes (FACT): `MediaFormat` low-latency key and `FEATURE_LowLatency` exist on API 30; whether GP520 decoders honour them is UNKNOWN. Pylux sets no CSD and relies on in-band SPS/PPS (FACT); GP520 acceptance of in-band parameter sets is NEEDS_HARDWARE_TEST. Measure decoder hold depth (input-queued → rendered) per component.

Projector display latency (picture mode, motion processing) is outside app control. Measure it separately with a glass-to-glass method: a high-speed camera filming both the PS5 reference output and the projected image, plus button-to-photon tests with a visible input indicator.

## Audio

Keep network/Opus decode independent from output. The native adapter drives AAudio on API 27+ or Oboe (Apache-2.0); PCM never transits `AudioTrack`/JVM. Use a bounded PCM ring with a small measured target depth, underrun/overrun metrics, and the clock-drift policy in `CONCURRENCY.md`. Exclusive/MMAP mode may be denied on GP520 (UNKNOWN); record what is granted. Route changes recreate only platform audio resources when required.

For reference, the Pylux ring is 32 × 1024 bytes, about 170 ms of 48 kHz stereo s16 (computed from FACT values). That is an upper bound to beat, not a target.

BenQ warns external Bluetooth audio may add about 300 ms. Report this separately from application latency and do not hide it with larger global buffers. A live stream cannot delay video to compensate, so A/V sync over Bluetooth audio is a documented user-facing limitation. [BenQ Bluetooth FAQ](https://www.benq.com/en-us/support/downloads-faq/faq/product/application/projector-faq-kn-00208.html)

## Input, Bluetooth, and security

Use `InputDeviceListener`; maintain state by internal ID and collect descriptor, VID/PID, sources, keys, motion ranges, and vibrator capability. The TV remote is UI input, not a Remote Play controller.

Bluetooth links are OS-managed. Observe device/route events and correlate them with stream metrics; do not unnecessarily reconnect unrelated devices.

Gamepad input during a stream is consumed entirely by the stream handler, including keys Android would otherwise turn into fallback actions (an unhandled `BUTTON_B` becomes `BACK`). See `DUALSENSE_ANALYSIS.md` for the stream-exit design.

Input-event batching per vsync may add delay (HYPOTHESIS). Measure OS-receipt-to-JNI timing, and evaluate `View.requestUnbufferedDispatch` (verify API level) for joystick sources.

API 30 has no `VibratorManager` (API 31). Rumble is possible only through `InputDevice.getVibrator()` if the kernel exposes force feedback, which is UNKNOWN.

Protect persistent credentials with an Android Keystore AES-GCM key (hardware-backed when the GP520 provides it; UNKNOWN) and app-managed encrypted storage on API 30. Do not adopt `androidx.security:security-crypto` without first confirming its maintenance status (INFERENCE: deprecated upstream). Keep secrets outside normal preferences and redact structured logs/exports centrally.

## GP520 validation

This section is a deferred physical-hardware gate. Per `OFFLINE_FIRST.md`, none
of these checks run and none block M1–M3 until the user explicitly authorizes
GP520 access. Emulator evidence remains separate.

0. Confirm ADB access and APK sideloading on the GP520 first; every later step depends on it.
1. Record firmware/build, kernel version, presence of the `hid-playstation` driver, vendor key layouts (`/vendor/usr/keylayout`, `/system/usr/keylayout`), SoC, memory, thermal state, network, display mode, codecs, and audio devices.
2. Verify launcher visibility and every screen with only the remote.
3. Enumerate MediaCodec behavior with 720p60 and 1080p60 test streams.
4. Validate live PS5 video, Surface recreation, foreground/background, and shutdown.
5. Execute the Bluetooth DualSense matrix.
6. Test internal speakers and soundbar route changes.
7. Test DualSense + soundbar + remote coexistence while correlating Wi-Fi and stream data.
8. Run 10-, 30-, 60-minute and multi-hour soak tests.

