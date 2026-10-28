# Android native adapter — first CR4 slice

Status: **INTEGRATED — INDEPENDENT CR4 GO RECORDED 2026-10-28**.

## Scope

This emulator-only slice establishes the Android ownership and lifecycle
boundary. It does not connect to a console or GP520, decode Remote Play traffic,
or claim device performance.

Kotlin creates one `NativeEngine` lazily for the future stream owner, owns the
`SurfaceView` callback, and assigns monotonically increasing Surface generations.
`MainActivity` does not load the adapter or start native threads. JNI receives an
opaque registry ID rather than a native address. The registry takes a shared
native owner for every call, so concurrent destroy cannot invalidate an in-flight
operation. No JNI global reference or native callback into Kotlin exists.

The Android-only C++ adapter owns the retained `ANativeWindow`, H.264
`AMediaCodec`, dynamically loaded AAudio stream, fixed 8,192-byte stereo-s16 ring
(about 85.3 ms at 48 kHz), and two native lifecycle workers. A replacement
generation first attempts `AMediaCodec_setOutputSurface`; failure recreates the
decoder. If recreation fails, both old and replacement windows are released and
the attempted generation is recorded, so its matching clear remains valid.
Stale attach or clear calls do not change ownership.

The AAudio callback performs fixed-capacity ring reads, lock-free atomics and
zero-fill only. It does not allocate, lock, log or call JNI. AAudio symbols are
loaded with `dlopen`; output is enabled only on API 27 or later. Shutdown issues
`requestStop`, performs a bounded state wait, then closes. The error callback is
lock-free and performs one atomic store. INFERENCE: on API 27 or later,
`AAudioStream_close` returns only after callbacks have finished; API 27/28 remain
NOT_TESTED. A disconnect is exposed as health state; automatic stream reopen is
not implemented. PCM and compressed video never cross the JVM.

Shutdown removes the registry entry, wakes and joins both workers, deletes the
codec, releases the native window, then performs the ordered audio shutdown.
Constructor failure joins any worker already started. `NativeEngine.close()` and
native shutdown are idempotent.

## Evidence

FACT (2026-10-28): NDK 28.2.13676358 compiled the adapter with warnings denied
for arm64-v8a, armeabi-v7a, x86 and x86_64 as part of `assembleDebug`.

FACT (2026-10-28): the emulator-only wrapper executed all 14 instrumentation
tests on the Google TV API 36 x86_64 AVD. The eleven native-adapter tests cover
create/destroy ×1000, stale and failed Surface generations, live Surface-to-
Surface replacement, AAudio start/stop and disconnect reporting with 1,000
cycles, a substitute AAudio table invoking and joining the registered error
callback from a second thread during both stop and close for 1,000 cycles,
concurrent attach/clear/start/stop racing close in both lifecycle-test and real
codec/AAudio modes, idempotent shutdown and real `SurfaceView` controller
replacement. Every worker observes `OK` before close. Engine, worker and
native-window counters returned to zero.

FACT (2026-10-28): the same eleven native-adapter tests passed under the NDK
AddressSanitizer profile on API 36, including a `/proc/self/maps` assertion that
the ASan runtime was loaded. LeakSanitizer is unavailable on this Android runtime.
`new_delete_type_mismatch` is disabled because the API 36 system
`libsfplugin_ccodec.so` triggers it internally; other ASan errors remain fatal.

FACT (2026-10-28): test-only JNI exports are compiled only with `NDK_DEBUG=1`.
The independent reviewer built all four ABIs with `NDK_DEBUG=0`; none exposed
`ForTest`, resource-count, window-count or fake-AAudio symbols, and none linked
directly to `libaaudio`. The instrumentation wrapper clears current Gradle
results before each run and retains separate normal and ASan XML copies.

FACT (2026-10-28): independent CR4 re-review returned `GO`. Its four LOW
observations were subsequently hardened by asserting pre-close `OK` per worker,
checking the ASan mapping in `/proc/self/maps`, retaining run-stamped XML and
SHA-256 summaries outside `app/build`, and starting the ADB server before parsing
the fail-closed device list.

FACT: the existing native-userspace virtual-LAN discovery and ctrl test passed
once in the same emulator session. This remains separate protocol evidence.

## Explicit limits

- NOT_TESTED: API 27/28 execution because no API 27/28 system image is installed.
- NOT_TESTED: delivery of a real AAudio disconnect callback, automatic AAudio
  recovery after disconnect, compressed access-unit
  feeding, decoded-frame pacing, IDR recovery, real PCM production, ring overflow,
  audio drift, process-death restoration, HWASan and a 60-minute audio soak.
- FACT: codec creation/recreation is currently synchronous under the adapter
  control mutex and may run on the Kotlin caller thread; moving it to a dedicated
  codec command worker is deferred before production streaming.
- UNKNOWN: GP520 codecs, audio routes, thermal behavior and performance.
- No performance or interoperability claim follows from this slice.
- The physical-hardware gate remains closed.
