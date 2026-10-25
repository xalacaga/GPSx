# Phase 0 Independent Review

Date: 2026-10-25. Reviewer: Claude Code acting as an independent adversarial reviewer of the Phase 0 analysis written by Codex.

Evidence base: the GPSx docs; chiaki-ng `lib/include/chiaki/controller.h` and `lib/src/feedbacksender.c`; Pylux `android/app/src/main/cpp/` (`video-decoder.c`, `audio-output.cpp`); AOSP `android11-release/data/keyboards/` and its `Generic.kl`. Nothing was built and no hardware was tested.

## Decision

**GO WITH CHANGES.** M0.5 evidence collection may proceed. M1 is gated by the entry criteria in `ROADMAP.md`.

## Findings

| ID | Finding | Classification | Severity | Status |
|---|---|---|---|---|
| F1 | "Kotlin owns media" contradicts "no per-frame JNI" | INCORRECT | BLOCKER | Docs fixed: native Android adapter layer |
| F2 | DualSense mapping assumed an Android 12+ layout; AOSP 11 has no 0ce6 layout | INCORRECT / NEEDS_HARDWARE_TEST | HIGH | Docs fixed: scan-code profiles, Generic.kl fallback |
| F3 | Cross may produce a BACK fallback key | HYPOTHESIS → NEEDS_HARDWARE_TEST | HIGH | Docs fixed: consume in stream, exit design |
| F4 | Coalesced input can drop button edges | QUESTIONABLE | HIGH | Docs fixed: edge preservation |
| F5 | Float controller model is not wire-exact | QUESTIONABLE | MEDIUM | Docs fixed: i16/u8 model |
| F6 | Mic has no Remote Play equivalent | INCORRECT (minor) | LOW | Docs fixed |
| F7 | Touch and motion over Bluetooth on API 30 | UNPROVEN | MEDIUM | Matrix updated |
| F8 | Haptics/adaptive triggers over Bluetooth | INFERENCE: OS_LIMITATION | MEDIUM | Marked out of scope |
| F9 | Matrix lacked rumble, haptics, triggers, Back, and exit rows | QUESTIONABLE | MEDIUM | Matrix extended |
| F10 | License options omitted an AGPL Rust port | QUESTIONABLE | HIGH | Resolved: Option C accepted by the owner on 2026-10-25 (ADR-0001) |
| F11 | Crate tree drawn before the license path | QUESTIONABLE | MEDIUM | Made conditional |
| F12 | Threading and queue contracts unspecified | UNPROVEN | HIGH | `CONCURRENCY.md` drafted; **approval pending** |
| F13 | Decoder input blocking can stall network receive | CONFIRMED risk (upstream) | HIGH | Docs fixed: SPSC + drop-to-IDR |
| F14 | Audio clock drift unaddressed | UNPROVEN | MEDIUM | Drift policy drafted |
| F15 | Audio through Kotlin implies per-block JNI | INCORRECT (via F1) | HIGH | Docs fixed: native AAudio/Oboe |
| F16 | Low-latency decode on GP520 | NEEDS_HARDWARE_TEST | HIGH | Open (M0.5) |
| F17 | Stream surface type unspecified | QUESTIONABLE | MEDIUM | SurfaceView mandated |
| F18 | No `unsafe` policy or purity CI | UNPROVEN | MEDIUM | `AGENTS.md` rules added |
| F19 | Windows UDP `WSAECONNRESET` behavior | FACT (risk) | LOW | Open (Windows adapter) |
| F20 | Android-mapping replay tests on Windows missing | QUESTIONABLE | MEDIUM | Testing section added |
| F21 | GP520 sideload/ADB may block M0.5 | UNKNOWN | HIGH | First M0.5 step |
| F22 | PS button interception by Google TV | NEEDS_HARDWARE_TEST | MEDIUM | Inspector row |
| F23 | Trademarks and PSN terms not assessed | UNPROVEN | MEDIUM | Flagged; **human/legal decision pending** |
| F24 | Unauthenticated-UDP resource exhaustion | QUESTIONABLE | MEDIUM | Open: parser limits spec |
| F25 | Pre-stream network test (Senkusha/MTU) omitted from the protocol map | INFERENCE | LOW | Open: verify |
| F26 | `EncryptedSharedPreferences` choice | INFERENCE (deprecation) | LOW | Keystore AES-GCM recommended |

## Still open before M1 at review time

- Human license decision (A, B, or C) and trademark/PSN note: `LICENSE_AUDIT.md`. License resolved: Option C (ADR-0001).
- Human approval of `CONCURRENCY.md` and the DualSense design.
- GP520 evidence: ADB/sideload, kernel and key layouts, DualSense scan codes and axes, codec list. This item is superseded by the owner constraint below.

## Owner constraint added after review

On 2026-10-25 the owner required offline-first development without connecting
to the GP520. Physical GP520 evidence is therefore no longer an M1 entry
condition. M1 requires sound adapter seams and explicit UNKNOWN/NOT_TESTED
labels instead. Physical evidence is deferred to M4 and requires explicit owner
approval; see `OFFLINE_FIRST.md`.
