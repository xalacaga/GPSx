# Upstream Analysis

Audit date: 2026-10-25; integration decision updated 2026-10-28.

## Pinned Android integration decision (2026-10-28)

The project accepts the AGPL/OpenSSL licensing consequences of using chiaki-ng as the first executable Remote Play baseline. The Android integration is pinned to `chiaki-ng/chiaki-ng` commit `c8bea2642d0e95402f373af55584a3b98cd89417`; code derived from its Kotlin/JNI contract carries `LicenseRef-AGPL-3.0-only-OpenSSL` and records that commit in the source header.

FACT: GPSx now wraps the pinned engine behind `RemotePlayEngine`. The boundary exposes registration, start/stop, Surface ownership, login PIN and controller-state submission. Android key, HAT, axis and trigger events first pass through the portable Rust `gpsx-input` merge/edge state machine, then map to chiaki's controller order. Network packets, compressed video and PCM remain native and do not cross the JVM. The first build consumes locally built native libraries through the explicit `chiakiPrebuiltDir` Gradle property; `.tools/` remains an ignored build workspace, not source provenance.

FACT: the Android x86_64 emulator loaded the pinned JNI library and resolved a native method, and the full instrumentation suite passed. UNKNOWN: a real PS5 connection, real audio/video, controller mappings under play, reconnect behavior and performance. No comparison with chiaki-ng is claimed until comparable P50/P95/P99 and queue-depth measurements exist.

## Pylux

Pylux is an active multi-platform fork built on Chiaki and chiaki-ng. Its repository contains a shared C library, Qt desktop UI, Android Kotlin/JNI frontend, iOS/macOS integrations, cloud/PSN components, tests, and packaging. Its project guide explicitly keeps protocol and streaming logic in the shared library and platform-specific behavior in adapters. [Pylux repository](https://github.com/ForWard-Technologies-LLC/Pylux), [Pylux agent guide](https://github.com/ForWard-Technologies-LLC/Pylux/blob/master/CLAUDE.md)

Observed shared modules include:

- discovery and registration: `discovery.c`, `discoveryservice.c`, `regist.c`;
- session/control: `session.c`, `ctrl.c`, `launchspec.c`;
- transport: `takion.c`, `streamconnection.c`, `reorderqueue.c`, `congestioncontrol.c`;
- recovery: `fec.c`, `frameprocessor.c`, corrupt-frame and IDR handling;
- crypto wrappers: `ecdh.c`, `gkcrypt.c`, `rpcrypt.c`;
- media: `videoreceiver.c`, `audioreceiver.c`, `ffmpegdecoder.c`, `opusdecoder.c`;
- input/output: `controller.c`, `feedback.c`, `feedbacksender.c`, `audiosender.c`.

[Pylux shared library](https://github.com/ForWard-Technologies-LLC/Pylux/tree/master/lib/src)

The Android build uses Kotlin and CMake/NDK, enables Opus, mbedTLS, and RUDP, and disables the shared FFmpeg decoder. It is a useful lifecycle reference but its UI is View-based rather than TV-first Compose. [Pylux Android build](https://github.com/ForWard-Technologies-LLC/Pylux/blob/master/android/app/build.gradle)

Pylux contains useful behavioral references: Surface cleanup, controller touchpad pointer capture, metrics overlay, secure-storage dependency, and reconnection UI. Its large `StreamActivity` also indicates that lifecycle, input, overlay, and streaming should be separated more aggressively in GPSx. [Pylux StreamActivity](https://github.com/ForWard-Technologies-LLC/Pylux/blob/master/android/app/src/main/java/com/metallic/chiaki/stream/StreamActivity.kt)

Recommendation: use Pylux as the most relevant Android behavior oracle, not as a translation source until AGPL is accepted.

## Chiaki

The original GitHub repository is archived and points to SourceHut. It established the portable C core plus multiple frontends and implemented discovery, registration, wake, and streaming. [Archived Chiaki repository](https://github.com/thestr4ng3r/chiaki)

Its primary value is provenance: later forks preserve its architecture and core. Use it for history and regression archaeology, not as the preferred Android baseline.

## chiaki-ng

chiaki-ng is the actively maintained next-generation fork. Its contributor guide maps protocol/session work to `ctrl.c` and `takion.c`, packet/media handling to receivers, codecs to FFmpeg/Opus wrappers, and controller feedback to shared senders. Desktop rendering uses Qt/QML and libplacebo; controller support uses SDL plus extensions. [chiaki-ng contributor guide](https://github.com/chiaki-ng/chiaki-ng/blob/main/CONTRIBUTOR_GUIDE.md)

The video receiver performs frame assembly, FEC recovery, missing-reference detection, corrupt-frame reporting, IDR requests, and compressed-sample callbacks. [chiaki-ng video receiver](https://github.com/chiaki-ng/chiaki-ng/blob/main/lib/src/videoreceiver.c)

Desktop functionality includes remapping, touchpad, motion, microphone, haptics, adaptive triggers, packet-loss monitoring, decoder selection, and audio buffering. This makes it the strongest Windows oracle, although Qt/libplacebo is not the intended final frontend. [chiaki-ng StreamSession](https://github.com/chiaki-ng/chiaki-ng/blob/main/gui/src/streamsession.cpp)

Decision: use the pinned chiaki-ng core as the first known-good executable baseline. GPSx may replace measured bottlenecks later, but only with provenance-safe code and comparable evidence.

## Android forks

Pylux is the strongest maintained Android branch found. A specialized `chiaki-ng-android-TV` fork adds shaders, remapping, motion, and haptics, but its own README warns it was AI-generated and not properly reviewed. It is an investigation source, not a trusted foundation. [chiaki-ng-android-TV](https://github.com/drydenmike/chiaki-ng-android-TV)

Recent Android forks and pull requests claim Bluetooth adaptive-trigger and haptics support. These are experimental and not GP520 evidence; none may be used to mark a capability PASS.

## Protocol architecture mapping

```text
discover → select → register/store credentials → wake
→ negotiate/control → establish encrypted real-time transport
→ receive media + send controller feedback
→ recover/reconnect or orderly shutdown
```

Local discovery uses search packets and console-specific ports. Remote connection in chiaki-ng uses PSN-assisted signaling and UDP hole punching and is documented as IPv4-only at the PlayStation side. [Discovery service](https://github.com/chiaki-ng/chiaki-ng/blob/main/lib/src/discoveryservice.c), [remote connection](https://chiaki-ng.github.io/chiaki-ng/setup/remoteconnection/)

The shared core owns sockets, control messages, Takion transport, reordering, FEC, congestion feedback, corruption reports, and IDR recovery. Those stay below UI boundaries.

## Media and input mapping

```text
video packet → decrypt/validate → reorder/FEC → frame assembly
→ compressed H.264/HEVC access unit → platform decoder → Surface

audio packet → decrypt/validate → reorder/FEC → Opus decode
→ bounded PCM queue → platform sink

raw platform input → descriptor/capabilities → normalized state
→ button/trigger transitions → feedback history (edge-preserving, tail resends)
→ stick/motion state → feedback state packet (latest-wins)
```

Correction 2026-10-25: an earlier version said "bounded/coalesced sender". Upstream `feedbacksender.c` (FACT) keeps a 16-event history and resends the last history packet at 20/50/100/200 ms so a lost release cannot leave a button stuck. Button transitions must never be coalesced.

Desktop uses FFmpeg/libplacebo. Android should feed compressed access units to MediaCodec with Surface output. Android supports AVC/HEVC and recommends selecting a named decoder from queried capabilities. [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)

Audio route changes should recreate only the sink when necessary, not the network session. Android input must support key/HAT D-pads and axis/key trigger variants. [Android controller input](https://developer.android.com/games/sdk/game-controller/controller-input)

## Retain conceptually

- Shared core/platform adapter split.
- Separate discovery, registration, session, transport, media, and feedback subsystems.
- Compressed-video callback boundary.
- Normalized controller state.
- FEC/reorder handling and explicit recovery signaling.
- Desktop reference client as protocol/controller oracle.

## Replace or redesign

- Large activity/session integration classes.
- Touch-first phone UI.
- Metrics without a unified telemetry schema.
- Platform controller assumptions without capability descriptors.
- Any unbounded buffer or shutdown ordering that cannot be proven race-safe.

## Evidence limit

No upstream build was executed against the user's PS5 or GP520. Repository inspection establishes architecture and declared behavior only. Reliability, mappings, latency, hardware decoding, and Bluetooth coexistence remain NOT_TESTED.

