# Android input adapter plan

Status: **PLAN CANDIDATE — INDEPENDENT BOUNDARY REVIEW REQUIRED**.

## Scope and evidence boundary

This M3 slice connects Android `InputDevice`, `KeyEvent` and `MotionEvent`
capture to the existing portable `gpsx-input` model. It is limited to synthetic
emulator events and redistribution-safe replay fixtures. It does not connect to
a console or GP520 and cannot validate the DualSense mapping on Android 11.

FACT: the portable core already owns controller identity, deterministic
two-controller merging, edge derivation and the bounded transition queue.
FACT: no path currently connects `MainActivity` to `InputTransitionQueue`.

## Ownership and layers

- Kotlin owns Android device discovery, stream/UI focus and Android event
  objects. It never assigns physical button semantics.
- A new Android-only Rust adapter crate owns JNI decoding, validation, mapping
  profile selection and conversion into `gpsx-input` values. It may depend on
  `jni` and Android APIs; portable crates must not depend on it.
- `gpsx-input` remains the only owner of normalized multi-controller state,
  merging and transition derivation.
- The future feedback sender owns protocol history and transmission. This slice
  does not add a wire sender.

The adapter exposes opaque registry IDs, not Rust addresses. Destroy removes the
registry entry before releasing the owner. Every JNI call obtains a temporary
shared owner, so concurrent shutdown cannot invalidate an in-flight event.

## Coarse JNI contract

One `KeyEvent` produces at most one JNI call containing:

```text
device_id, descriptor token, source, action, key_code, scan_code,
repeat_count, event_time_ns
```

One `MotionEvent` produces at most one JNI call containing the current sample
and every historical sample in chronological order. The call carries one closed
axis-ID vector and a row-major value vector plus:

```text
device_id, descriptor token, source, action, history_size,
axis_count, sample_times_ns
```

Limits are fail-closed and checked before reading array elements:

- at most 32 axes;
- at most 64 samples including the current sample;
- exactly `axis_count × sample_count` finite float values;
- monotonically non-decreasing, non-negative event times;
- closed Android action and source classifications.

An invalid call changes no controller state and returns a classified status.
There is no JNI call per axis, sample, button, packet or frame. Kotlin does not
coalesce events or historical samples.

## Device identity and routing

Kotlin maintains an `InputDeviceListener` snapshot for diagnostics, but the
adapter keys live state by its own monotonic `ControllerId`. Android device IDs
may be reused and are never the stable identity. Descriptor, VID, PID, version,
sources, keys and motion ranges select a versioned mapping profile; device name
and Android key-code names are diagnostic only.

At most two physical game controllers can occupy active slots. A TV remote is
UI-only. While streaming, gamepad events are consumed before Android fallback
can turn a controller button into `BACK`; remote Back/Menu stays on the UI path.
When stream ownership ends, focus moves to an overlay, or a controller is
removed, the adapter publishes a neutral state before releasing the slot.

The API-30 Generic.kl DualSense profile remains a HYPOTHESIS until physical
evidence exists. The first implementation uses synthetic profiles with explicit
scan-code and axis maps; it must not label emulator key codes as DualSense facts.

## State, edges and bounded work

Each accepted raw event is mapped synchronously during its one JNI call. No
unbounded raw-event queue is introduced. The adapter updates `ControllerSet` and
pushes the complete transition batch to the existing 256-entry
`InputTransitionQueue` atomically.

- Button edges and trigger `0 ↔ non-zero` crossings are never coalesced.
- Stick, motion and intermediate trigger values are latest-wins state.
- Axis/key trigger variants and HAT/key D-pad variants merge before diffing.
- Queue overflow rejects the entire state change, increments the existing
  overflow counter and leaves the prior normalized state intact so the caller
  can fail the session rather than continue with an unknown held-button state.
- JNI returns without waiting for network, feedback, telemetry or UI work.

No steady-state native event path allocates after device/profile registration.
The Kotlin boundary may construct the two primitive arrays for one
`MotionEvent`; this allocation and its latency must be measured before an
optimization claim.

## Native safety

Portable crates retain `#![forbid(unsafe_code)]`. Unsafe code is restricted to
the Android adapter crate. Every unsafe block has a `// SAFETY:` invariant. JNI
arrays are length-checked before access, local references do not escape the call,
no borrowed JVM memory is retained, and panics are caught before crossing JNI.

Miri covers mapping and registry logic where JNI is substituted. Android ASan
covers the real JNI library. A release-symbol audit must show no test hooks.

## Closing tests

Portable/replay tests:

- every button press/release, including both within one sender interval;
- trigger sweep with exactly one engage and release crossing;
- axis/key trigger deduplication and HAT/key D-pad merge;
- two-controller isolation and deterministic merged state;
- disconnect, overlay and shutdown neutralization;
- overflow atomicity with no state change;
- malformed dimensions, NaN/infinity, time regression and unknown actions.

Android emulator instrumentation:

- one sink/JNI submission per `KeyEvent` and per `MotionEvent`;
- every historical motion sample arrives in order and exactly once;
- gamepad `BACK` fallback is consumed only while stream ownership is active;
- TV-remote Back remains UI input;
- device add/remove/reuse cannot inherit an old controller state;
- concurrent event submission and destroy returns classified results and leaks
  no registry entry;
- normal and ASan profiles retain hashed XML evidence.

Release checks:

- all four Android ABIs compile with warnings denied;
- portable-core dependency purity remains unchanged;
- the adapter links no media or protocol implementation into Kotlin;
- dynamic symbols contain only the reviewed production JNI surface.

## Explicit residuals

- NOT_TESTED: physical DualSense delivery, API-30 Generic.kl behavior, PS/Home
  interception, controller vibration, real Bluetooth report rate and GP520.
- NOT_IMPLEMENTED: feedback sender, protocol history/tail resend, exit-chord UI,
  touch coordinates, motion sensors and output reports.
- UNKNOWN: real scan/axis mapping, vendor key layouts and the GP520 kernel driver.
- The physical-hardware gate remains closed.
