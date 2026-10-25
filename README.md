# GPSx

GPSx is an evidence-first Remote Play client project targeting Windows and
Android TV, with the BenQ GP520 as the first physical Android TV target.

## Current status

Option C is accepted: GPSx is AGPL-3.0-only and the future Rust protocol port is
a declared derivative with file-level provenance. The portable state, input and
telemetry foundations are tested. An offline Android TV interface now provides
D-pad navigation, an explicit simulated-session lifecycle, two DualSense slots,
diagnostics and settings. No console or GP520 connection is implemented yet.

## Prerequisites

- Rust stable with `rustfmt` and `clippy`
- Python 3.12+
- JDK 17+ and Android SDK 36 for the Android TV build
- Android Platform Tools (`adb`) only after the physical-hardware gate opens
- A physical PS4/PS5, two DualSense controllers, and GP520 for later hardware validation

## Validation

```powershell
./tools/powershell/check.ps1
```

The command runs formatting, Clippy with warnings denied, the Rust workspace
tests, and the Python evidence-tooling tests.

Build and test the offline Android TV prototype:

```powershell
./apps/android-tv/gradlew.bat -p ./apps/android-tv testDebugUnitTest assembleDebug
```

The debug APK is written to
`apps/android-tv/app/build/outputs/apk/debug/app-debug.apk`. Building it does not
constitute emulator, Android device or GP520 validation.

## Evidence handling

Never commit raw credentials, tokens, cookies, registration keys, session keys,
or secret-bearing URLs. Put local captures in `docs/evidence/`; generated
capture files are ignored by Git. Only reviewed, sanitized fixtures belong in
`tests/fixtures/`.

