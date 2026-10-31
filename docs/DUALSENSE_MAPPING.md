# DualSense validation matrix

No row may become `PASS` without raw event evidence from the target device.

| Control/capability | Windows USB | Windows Bluetooth | GP520 USB | GP520 Bluetooth |
|---|---|---|---|---|
| Face buttons | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| D-pad | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| L1/R1 | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| L2/R2 analog range | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Stick axes | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| L3/R3 | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Create/Options/PS | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Touchpad click | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Touch contacts | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Gyroscope | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Accelerometer | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Basic rumble | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Advanced haptics | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Adaptive triggers | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| LED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Mute button/LED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Back fallback (Cross as `BUTTON_B` → `BACK`) | n/a | n/a | NOT_TESTED | NOT_TESTED |
| PS button system interception | n/a | n/a | NOT_TESTED | NOT_TESTED |
| Stream-exit chord (Create + Options, 2 s) | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Battery level | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |

Allowed statuses: `PASS`, `FAIL`, `OS_LIMITATION`, `NOT_SUPPORTED`,
`NOT_TESTED`. Each changed cell must reference an evidence artifact and include
OS/build, connection mode, controller firmware, event codes/axes, observed
range, dead zone, and reconnect behavior.

GP520 cells must record **scan codes and raw axis IDs**, not only Android
keycode names. AOSP Android 11 ships no DualSense layout (0ce6), so the expected
API 30 baseline is the `Generic.kl` fallback, where labels such as `BUTTON_A`
do not match physical buttons (HYPOTHESIS until observed; see
`DUALSENSE_ANALYSIS.md`). Also record the trigger axis rest value (possibly
-1.0), the kernel driver in use, and any vendor key layout. Mute is local only:
the Remote Play button set has no Mic bit. Windows columns are a superset and
are never evidence for GP520 columns.

## Raw HID profile (no Sony kernel driver)

GPSx applies this profile to a Sony pad (vendor `054c`) whose key layout reports
both `BUTTON_C` and `BUTTON_Z`. Sony kernel drivers never emit those two buttons,
and the AOSP DualSense fallback layout maps them to face buttons, so their
presence identifies HID buttons read through `Generic.kl` (INFERENCE, pending GP520
scan-code evidence). Buttons are then mapped from the **scan code**, not the
Android keycode:

| Scan code | `Generic.kl` label | PS5 control |
|---|---|---|
| `0x130` | `BUTTON_A` | Square |
| `0x131` | `BUTTON_B` | Cross |
| `0x132` | `BUTTON_C` | Circle |
| `0x133` | `BUTTON_X` | Triangle |
| `0x134` | `BUTTON_Y` | L1 |
| `0x135` | `BUTTON_Z` | R1 |
| `0x136`, `0x137` | `BUTTON_L1`, `BUTTON_R1` | L2/R2 digital, ignored (the axes win) |
| `0x138` | `BUTTON_L2` | Create |
| `0x139` | `BUTTON_R2` | Options |
| `0x13a`, `0x13b` | `BUTTON_SELECT`, `BUTTON_START` | L3, R3 |
| `0x13c` | `BUTTON_MODE` | PS |
| `0x13d` | `BUTTON_THUMBL` | Touchpad click |

Axes: `X`/`Y` left stick, `Z`/`RZ` right stick, `RX`/`RY` L2/R2 pressure. Android
centres `RX`/`RY` on -1..1, so GPSx rescales them from their reported motion range
(rest = 0). The D-pad comes from `HAT_X`/`HAT_Y`. Pads with a Sony driver or a
vendor layout keep the keycode mapping.
