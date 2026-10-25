# DualSense Analysis

Reference controller: Sony DualSense over Bluetooth to BenQ GP520 / Android TV 11.

Revised 2026-10-25 after independent review: added the integer wire model, edge preservation, scan-code profiles, the Android 11 Generic.kl fallback, Back-fallback handling, the stream-exit chord, and output-capability rows.

## Evidence

Android exposes buttons as `KeyEvent` and axes as `MotionEvent`. Its documentation describes sticks on X/Y and Z/RZ, analog triggers on LTRIGGER/RTRIGGER, the D-pad through keys or HAT axes, and controllers that emit both analog and button trigger events. That describes devices with a matching key layout; it is not a guarantee for DualSense on API 30. [Android controller input](https://developer.android.com/games/sdk/game-controller/controller-input)

**FACT:** AOSP `android11-release/data/keyboards` contains Sony layouts for products 0268, 05c4, 09cc, and 0ba0, and **no layout for 0ce6 (DualSense)**. [AOSP android11 keyboards](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/data/keyboards/)

**FACT:** In Android 11, `Generic.kl` maps scan codes 304–318 to `BUTTON_A, B, C, X, Y, Z, L1, R1, L2, R2, SELECT, START, MODE, THUMBL, THUMBR`, axes 0x00–0x05 to `X, Y, Z, RX, RY, RZ`, and 0x10/0x11 to `HAT_X/HAT_Y`.

**INFERENCE:** Without a vendor layout or the `hid-playstation` kernel driver (mainline since Linux 5.12), the DualSense reaches Android through `hid-generic` using the Bluetooth "simple" input report. Its HID button order (Square, Cross, Circle, Triangle, L1, R1, L2, R2, Create, Options, L3, R3, PS, Touchpad) then lands on Generic.kl like this:

| Physical | Likely Android keycode (Generic.kl fallback) |
|---|---|
| Square | BUTTON_A |
| Cross | BUTTON_B |
| Circle | BUTTON_C |
| Triangle | BUTTON_X |
| L1 / R1 | BUTTON_Y / BUTTON_Z |
| L2 / R2 (digital) | BUTTON_L1 / BUTTON_R1 |
| Create / Options | BUTTON_L2 / BUTTON_R2 |
| L3 / R3 | BUTTON_SELECT / BUTTON_START |
| PS | BUTTON_MODE |
| Touchpad click | BUTTON_THUMBL |
| Left stick | AXIS_X / AXIS_Y |
| Right stick | AXIS_Z / AXIS_RZ |
| L2 / R2 analog | AXIS_RX / AXIS_RY (rest value possibly -1.0) |
| D-pad | AXIS_HAT_X / AXIS_HAT_Y |

The whole table is a HYPOTHESIS until the inspector records it on the GP520. A vendor layout or a backported driver would change it (UNKNOWN). **Android keycode names must therefore never be trusted as physical semantics.**

**FACT:** The Linux driver maintained with Sony copyright documents separate USB/Bluetooth reports, face/shoulder/Create/Options/PS/touchpad masks, two touch points, calibrated motion sensors, battery state, output reports, and CRC handling. It is GPL-2.0-or-later and is not a copy source without review. [Linux hid-playstation](https://github.com/torvalds/linux/blob/master/drivers/hid/hid-playstation.c)

chiaki-ng demonstrates desktop support through SDL/platform code for standard input, touchpad, motion, haptics, and adaptive triggers. Desktop success is not evidence that Android TV exposes the same capabilities. [chiaki-ng controller documentation](https://github.com/chiaki-ng/chiaki-ng/blob/main/docs/setup/controlling.md)

**FACT (wire reference, chiaki-ng `controller.h`):**

- The Remote Play button set is a 16-bit mask: Cross, Moon (Circle), Box (Square), Pyramid (Triangle), D-pad ×4, L1, R1, L3, R3, Options, Share (Create), Touchpad, PS. It has no Mic button.
- L2 and R2 are `uint8` values; sticks are `int16`.

**FACT (chiaki-ng `feedbacksender.c`):** Button and trigger changes travel as a history of events, separate from the stick/motion state packet. The last history packet is resent at 20/50/100/200 ms so that a lost release cannot leave a button stuck.

## Proposed normalized model

```text
ControllerDescriptor
  internal_id, android_device_id, descriptor, name, vendor_id, product_id, version
  sources, keys, axes/ranges, touchpad, motion, vibration, output capabilities
  profile_id (versioned mapping profile selected for this device)

ControllerState                        (wire-exact integers)
  event_time_ns, sequence
  buttons: u32 bitmask
           Cross Circle Square Triangle, DpadUp/Down/Left/Right,
           L1 R1 L3 R3, Create Options PS TouchpadClick
  sticks:   lx ly rx ry : i16
  triggers: l2 r2       : u8
  touches and motion: only actually observed capabilities
```

- Quantization from raw Android float axes to `i16`/`u8`, rounding, clamping, and the Y-axis sign convention are defined once in `docs/DUALSENSE_MAPPING.md`. They are verified against PS5 behavior, not assumed. Floats appear only in diagnostics and UI.
- A trigger axis whose range rests at the minimum (for example -1.0) is rebased so that rest equals `0`. The calibration records the observed rest value.
- Mic is not a Remote Play control. If it is ever exposed, it is a local-only action and is never placed in the wire bitmask.
- Dead zones belong to a named calibration policy. Diagnostics keep both raw and normalized values.

## Mapping profiles

- A mapping profile is data: (VID, PID, version, optional vendor-layout fingerprint) → map from **scan code** and **raw axis** to physical control. It is not code keyed on Android keycode names.
- Profiles are versioned and pure Rust data, so Windows can test them by replaying recorded Android events (see Testing).
- `profile:android11-generic-kl-dualsense` is the expected default on API 30, pending GP520 evidence.
- If an analog axis and a digital key both report the same trigger, the axis wins. The key only fills in when no axis exists, and must not create duplicate transitions. D-pad HAT and key sources merge into one state, with the source recorded in diagnostics.
- Never identify the device only by `Wireless Controller`.

## Edge preservation

- Kotlin forwards every `KeyEvent`, and every `MotionEvent` with its historical samples, to the native side as raw data in one JNI call per event. It never coalesces snapshots.
- The core derives transitions by diffing consecutive normalized states and appends them to the protocol history. A press and a release within one sender interval must both reach the wire.
- Only continuous state may be coalesced into the latest state packet: sticks, motion, and intermediate trigger values. A trigger's engage/release crossing (0 ↔ non-zero) is a transition and is never dropped. (Revised after CR1: treating every analog trigger value as a transition would overflow any bounded queue during a fast pull.)
- On disconnect, controller loss, or stream exit, the core sends a neutral state (all buttons released, sticks centered, triggers 0) before stopping the sender.

## Two physical controllers, one proven Remote Play player

**FACT (CR1 upstream review):** pinned chiaki-ng stores one controller state per
session and merges all local controllers before sending it. No independent second
Remote Play player channel is established by current evidence. GPSx therefore
accepts at most two physical controllers but exposes one merged remote state:

- local identity, capabilities, latency, disconnect and reconnect remain isolated by `ControllerId`;
- buttons use logical OR;
- triggers use the greatest pressure;
- each stick axis uses the value with greatest absolute magnitude; an equal magnitude keeps the lower `ControllerId` value;
- discrete transitions are derived only after merging, so one controller releasing Cross cannot release it remotely while the other still holds it;
- a third controller is rejected from the active slots and may only remain an unassigned UI device.

Independent remote players remain `UNKNOWN` until protocol evidence demonstrates
them. The merge policy is a deterministic compatibility policy, not proof that
simultaneous control is a good game experience.

## Stream input ownership and exit

- During a stream, the stream handler consumes all gamepad keys, including ones Android would turn into fallback keys. **HYPOTHESIS:** under the Generic.kl fallback, Cross arrives as `BUTTON_B`, which Android turns into `BACK` when unhandled.
- PS may be intercepted by Google TV, forwarded, or absent (UNKNOWN). The design must not depend on it for leaving the stream.
- Stream exit / overlay entry:
  1. **Primary:** TV remote Back/Menu. The remote is a different `InputDevice` and is UI-only.
  2. **Secondary:** a configurable controller chord, defaulting to holding Create + Options for 2 s. This is a HYPOTHESIS and needs a usability test, because a chord can collide with in-game actions. The chord is forwarded to the PS5 normally until the hold time elapses.
- While the overlay is open, controller input drives the UI and the stream receives a neutral state.

## Required inspector

- device identity, capabilities, and the selected profile;
- raw key code, scan code, action, repeat, source, and timestamp;
- every motion axis with range/value/flat/fuzz, including the observed rest value;
- raw and normalized sticks/triggers/HAT;
- merged normalized state and the Remote Play wire mapping;
- detected fallback/system keys (BACK, HOME, MODE interception);
- inter-event interval histogram (Bluetooth report rate);
- connection events and per-stage timing;
- touch contacts only when Android delivers them.

## Validation matrix

No row is PASS. Coding or upstream behavior cannot replace physical validation.

| Control | Expected representation (API 30) | Normalized | Windows | Android | GP520 | Notes |
|---|---|---|---|---|---|---|
| Cross/Circle/Square/Triangle | scan codes; likely Generic.kl scramble | physical PS semantics | NOT_TESTED | NOT_TESTED | NOT_TESTED | Never trust Android keycode names |
| D-pad | HAT and/or DPAD keys | DPAD_* | NOT_TESTED | NOT_TESTED | NOT_TESTED | Deduplicate representations |
| L1/R1 | keys (likely BUTTON_Y/Z) | L1/R1 | NOT_TESTED | NOT_TESTED | NOT_TESTED | — |
| L2/R2 | axes (likely RX/RY) + digital keys | u8 0–255 | NOT_TESTED | NOT_TESTED | NOT_TESTED | Axis preferred; rebase rest value |
| L3/R3 | keys (likely SELECT/START) | L3/R3 | NOT_TESTED | NOT_TESTED | NOT_TESTED | — |
| Create/Options | keys (likely BUTTON_L2/R2) | CREATE/OPTIONS | NOT_TESTED | NOT_TESTED | NOT_TESTED | — |
| PS | BUTTON_MODE if delivered | PS | NOT_TESTED | NOT_TESTED | NOT_TESTED | System interception UNKNOWN |
| Touchpad click | key (likely THUMBL) | TOUCHPAD_CLICK | NOT_TESTED | NOT_TESTED | NOT_TESTED | Separate from coordinates |
| LX/LY | X/Y | i16 | NOT_TESTED | NOT_TESTED | NOT_TESTED | Record flat/dead-zone, sign |
| RX/RY | Z/RZ or variant | i16 | NOT_TESTED | NOT_TESTED | NOT_TESTED | Capability-driven |
| Back fallback | Cross/BUTTON_B → BACK? | consumed in stream | n/a | NOT_TESTED | NOT_TESTED | Must never exit stream |
| Stream-exit chord | Create+Options hold | local action | NOT_TESTED | NOT_TESTED | NOT_TESTED | Usability test |
| Touch coordinates | touchpad source if exposed | observed coordinates | NOT_TESTED | NOT_TESTED | NOT_TESTED | HYPOTHESIS: absent in simple report |
| Gyro/accelerometer | sensor/input path if exposed | calibrated samples | NOT_TESTED | NOT_TESTED | NOT_TESTED | HYPOTHESIS: absent; post-MVP |
| Rumble | `InputDevice.getVibrator()` if kernel FF | — | NOT_TESTED | NOT_TESTED | NOT_TESTED | UNKNOWN; no VibratorManager on API 30 |
| Haptics (voice coil) | raw BT output report | — | NOT_TESTED | NOT_TESTED | NOT_TESTED | INFERENCE: OS_LIMITATION for apps on API 30; out of scope |
| Adaptive triggers | raw BT output report | — | NOT_TESTED | NOT_TESTED | NOT_TESTED | INFERENCE: OS_LIMITATION; out of scope |
| Lightbar / player LEDs | output report | — | NOT_TESTED | NOT_TESTED | NOT_TESTED | INFERENCE: unavailable; out of scope |
| Battery level | `InputDevice` battery (API 31) / none | diagnostics only | NOT_TESTED | NOT_TESTED | NOT_TESTED | Likely unavailable on API 30 |

Windows results (SDL/HIDAPI reads the full report) are a superset and are never evidence for the Android or GP520 columns.

## Testing

- Replay fixtures: recorded Android `KeyEvent`/`MotionEvent` streams (scan codes, axes, ranges, timestamps) run through the Rust mapping profiles in `cargo test` on Windows and CI. This tests mapping logic only; delivery is tested only on device.
- Edge tests: a press and release within one sender interval, a lost-release tail resend, a neutral state on disconnect, axis/key trigger deduplication, and HAT/key D-pad merge.
- Fallback tests: no BACK/HOME side effect while streaming; the exit chord fires only after the hold time.

## Latency and reconnect

Timestamp OS receipt, JNI entry, normalized publication, history insertion, packet construction, and socket send. Export P50/P95/P99 for application processing only. Measure end-to-end button-to-photon separately.

Test connected-before-launch, connected-after-launch, disconnect/reconnect in stream, sleep/wake, background/foreground, UI-to-stream focus transition, and two-controller state isolation.
