# Architecture Audit

Status: Phase 0, 2026-10-25. No production implementation has started.

## Current architecture

**FACT:** At audit start, `D:\GPSx` contained no Git repository, source, build files, docs, tests, or prior architecture. Current local artifacts are Phase 0 documents and agent/tooling configuration. Nothing exists to preserve, benchmark, or migrate; the structure in the brief is a proposal.

## Reference architecture

chiaki-ng documents shared C code in `lib/include/chiaki`, protocol/network/media work in `lib/src`, and Qt/QML desktop integration in `gui`. Its video path is receive → frame reconstruction/FEC → FFmpeg → renderer; audio uses shared receive/Opus with frontend device handling. [chiaki-ng contributor guide](https://github.com/chiaki-ng/chiaki-ng/blob/main/CONTRIBUTOR_GUIDE.md)

Pylux retains that model and adds Android, iOS, cloud play, and packaging. Its shared library separates discovery, registration, session, transport, reorder, congestion control, FEC, receivers, controller feedback, and crypto. [Pylux library](https://github.com/ForWard-Technologies-LLC/Pylux/tree/master/lib/src)

```text
PS5
 ├─ discovery / registration / wake
 └─ session control
      └─ encrypted transport
          ├─ video → reorder/FEC/frame assembly → compressed units
          ├─ audio → reorder/FEC → Opus
          ├─ controller state → feedback sender
          └─ quality reports → adaptation + telemetry

Portable core
 ├─ Windows validation adapter
 └─ Android adapter
      ├─ Kotlin lifecycle + TV UI + secure persistence
      ├─ Input Framework → normalized controller state
      ├─ compressed video → MediaCodec/Surface
      └─ PCM → Android audio
```

## Reuse, wrap, or replace

| Area | Evidence | Recommendation |
|---|---|---|
| Protocol/session/crypto | Mature Chiaki C implementation | Decide AGPL reuse vs clean-room Rust before code |
| Reorder/FEC | Explicit upstream queues, FEC, frame processor | Behavioral oracle; copy only under accepted AGPL strategy |
| Desktop media | FFmpeg + libplacebo/Qt | Validation oracle, not Android production renderer |
| Android video | Upstream disables shared FFmpeg decoder on Android | Narrow MediaCodec-to-Surface adapter, measured on device |
| Android UI | Kotlin/View-based upstream | New TV-first Compose shell |
| Input | Normalized upstream state; SDL desktop/framework Android | New documented model with physical validation |
| Telemetry | Selected metrics only | New low-overhead JSONL subsystem |

## Proposed Rust architecture

The end-state preference is Rust, but this is not authorization to port AGPL code.

Revised 2026-10-25 after independent review. The protocol-side crates depend on the license option chosen in `LICENSE_AUDIT.md`:

```text
crates/                          (portable core: no jni/ndk/windows deps, forbid(unsafe_code))
  model/       IDs, errors, diagnostics                       path-independent
  input/       wire-exact state, mapping profiles (data)      path-independent
  telemetry/   counters, histograms, per-thread rings, JSONL, redaction   path-independent
  protocol/    parsers/serializers                            Option B/C: Rust; Option A: C wrapper
  transport/   sockets, bounded reorder queues, congestion    Option B/C: Rust; Option A: C wrapper
  session/     lifecycle and cancellation                     Option B/C: Rust; Option A: C wrapper
  core/        orchestration and platform ports
adapters/                        (platform-specific; unsafe allowed with SAFETY invariants)
  android-native/  NDK AMediaCodec, ANativeWindow, AAudio/Oboe, JNI entry points
  windows/         Windows decoder/audio/input for validation
apps/
  desktop/     Windows validation app
  android/     Kotlin TV shell (UI, lifecycle, Surface hand-off, input capture)
```

Only the path-independent crates may start before the license decision.

Platform interfaces exchange coarse operations and ownership-bearing batches: start/stop, Surface update, raw input event batches, compressed access units, audio blocks, events, and metrics. Compressed video and PCM never cross into the JVM; they stay inside the native Android adapter. Threads, queue capacities, overflow policies, and shutdown order are specified in `CONCURRENCY.md`.

## Top engineering problems

### 1. Protocol provenance and licensing

- Classification: **FACT**
- Evidence: Chiaki, chiaki-ng, and Pylux are AGPL-3.0 with an OpenSSL exception.
- Severity: Critical
- Affected components: protocol, crypto, transport, session, packaging
- Root cause: No GPSx license/distribution strategy has been chosen.
- Required instrumentation: Not runtime instrumentation; a provenance ledger and legal/product decision.
- Proposed approach: Option A (AGPL reuse/wrapper), B (independently authored clean-room implementation), or C (GPSx under AGPL-3.0 with a declared-derivative Rust port); see `LICENSE_AUDIT.md`.
- Tests: SPDX/license scan, provenance and binary-notice review.
- Performance implications: C FFI lowers schedule risk; clean-room Rust increases correctness/performance risk.
- Regression risks: Non-compliance, incompatibility, delay.

### 2. Unknown PS5 protocol behavior

- Classification: **UNKNOWN**
- Evidence: Upstream works for others, but GPSx has no spec, fixtures, captures, or PS5 results.
- Severity: Critical
- Affected components: registration, authentication, session, crypto, reconnect
- Root cause: Not demonstrated.
- Required instrumentation: Sanitized state/handshake traces, errors, packet corpus with secrets removed.
- Proposed approach: Establish a known-good upstream oracle against the user's PS5.
- Tests: Golden parsers, malformed inputs, connect/disconnect/reconnect hardware scenarios.
- Performance implications: Control-path tracing is low cost when bounded.
- Regression risks: Unit tests may falsely imply interoperability.

### 3. Android decoder lifecycle and latency

- Classification: **INFERENCE**
- Evidence: API 30 supports MediaCodec to Surface; upstream Android contains explicit Surface lifecycle handling. [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)
- Severity: High
- Affected components: Android adapter, Surface, video, lifecycle
- Root cause: Not demonstrated on GP520.
- Required instrumentation: Codec identity, queue depth, timestamps, drops, errors, Surface generations.
- Proposed approach: Direct compressed-unit decode to Surface; probe and verify actual components.
- Tests: Surface recreation, background/foreground, reset, 720p60/1080p60, IDR recovery.
- Performance implications: Avoid raw-frame JNI copies; validate minimal buffering.
- Regression risks: Hangs, software fallback, accumulated latency.

### 4. DualSense semantics through Android

- Classification: **FACT** for Android variability; **UNKNOWN** for GP520
- Evidence: Android documents key/HAT D-pad variants and duplicate axis/key triggers. [Controller input](https://developer.android.com/games/sdk/game-controller/controller-input)
- Severity: Blocking
- Affected components: Android input, normalized state, UI focus, feedback
- Root cause: GP520 behavior has not been observed.
- Required instrumentation: Descriptor, VID/PID, sources, keys, ranges, raw/normalized events, timestamps.
- Proposed approach: Capability descriptors, per-device state, deduplication, live inspector, fixtures.
- Tests: Full matrix, reconnect, trigger duplication, D-pad variants.
- Performance implications: Allocation-free snapshots without global locks.
- Regression risks: Face-button inversion, stuck inputs, TV remote capture.

### 5. Bluetooth coexistence and audio latency

- Classification: **FACT** for BenQ's ~300 ms external Bluetooth-audio warning; **UNKNOWN** for coexistence
- Evidence: [GP520 specifications](https://www.benq.com/en-sg/projector/cinema/gp520/spec.html), [BenQ Bluetooth FAQ](https://www.benq.com/en-us/support/downloads-faq/faq/product/application/projector-faq-kn-00208.html)
- Severity: High
- Affected components: audio, Bluetooth, input, UX, telemetry
- Root cause: No application root cause demonstrated.
- Required instrumentation: Routes, underruns, depth, controller disconnects, Wi-Fi quality, continuity.
- Proposed approach: Isolate route changes from session and separate app/OS/device latency.
- Tests: Internal speakers; soundbar; DualSense + soundbar + remote; route changes; soak.
- Performance implications: Device latency may dominate and cannot be eliminated in app code.
- Regression risks: Session restart, dead audio sink, controller interruption.

## Unknowns

- Exact PS5 firmware/interoperability.
- Whether AGPL distribution is acceptable.
- GP520 codec names, hardware classification, latency, and thermals.
- GP520 delivery of PS, touchpad, motion, vibration, and output reports.
- ADB/install access and debugging method.
- Internet Remote Play scope.
- Actual Android audio format/burst/route behavior.

