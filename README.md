# GPSx

GPSx is an Android TV app for playing on the big screen from one place:

- **PS5 / PS4 Remote Play** on the local network;
- **PlayStation Plus cloud streaming** (Premium subscription, no console needed);
- **retro consoles** — Super Nintendo, Neo Geo, arcade — with games read from
  your own NAS.

The first target is the BenQ GP520 projector (Android TV 11, 32-bit ARM). GPSx is
an unofficial client, not affiliated with or endorsed by Sony Interactive
Entertainment, Nintendo or SNK.

## Status

Test builds are published from the `gp520-test-channel` branch and run on the
GP520 (see [docs/INSTALL_GP520.md](docs/INSTALL_GP520.md)). Highlights:

- Remote Play and PlayStation Plus cloud streaming run on the GP520 through the
  Pylux native engine, with controller mapping for DualSense and DualShock 4.
- The Retro section reads the owner's EmulationStation-style NAS share
  (covers and descriptions from `gamelist.xml`, box art from the libretro
  thumbnail server), and runs libretro cores in a native host: SNES
  (Snes9x 2010), Neo Geo (FB Alpha 2012) and arcade (FinalBurn Neo), with sound
  and one player per controller. Amiga (PUAE) is planned.
- What is verified and on which device is recorded per feature in the plans
  under [docs/](docs/), labelled FACT, INFERENCE, HYPOTHESIS or UNKNOWN.

## Install on Android TV

1. Install **Downloader** (AFTVnews) on the TV and allow it to install apps.
2. Open `https://github.com/xalacaga/GPSx/releases/latest/download/gpsx.apk`.
3. Later versions install from inside GPSx: Settings → *Mettre à jour GPSx*.

Every release APK is signed with the same GPSx key; the in-app updater checks the
APK SHA-256, package name and signing certificate before Android installs it.

## Using GPSx

The home screen picks the console; each console has its own environment:

| Environment | Tabs |
| --- | --- |
| PS5 | Play (Remote Play) · Cloud PS Plus · Settings (video quality, low latency, stream statistics) |
| Each retro console | Games (list with covers) · Controls (what each PlayStation button does) |

The top bar keeps what is shared by all consoles: Controllers, Diagnostics
(live log on the local network, test reports), Settings (audio output, NAS
access, storage used by retro games, updates) and About. L1/R1 switch tabs;
holding L1 + R1 + Options for one second, or Back on the remote, leaves a game.

## Retro games: what GPSx does and does not provide

- GPSx never ships, downloads or points to ROM or BIOS files: games come from
  your own share (`\\server\share\folder`, guest or NAS account; the password is
  stored encrypted on the TV and never logged).
- Emulator cores are downloaded on request from the official libretro build
  server into the app's private storage. Several of them forbid commercial use;
  they are never part of this repository or of the APK.
- Box art from the libretro thumbnail server belongs to its publishers; the TV
  downloads it only to show your own games and keeps it in its cache.
- Games are copied once to the TV and kept; Settings shows the space used and
  erases games, covers and cores, or saves, separately.

## Repository layout

| Path | Content |
| --- | --- |
| `apps/android-tv/` | Android TV app: Kotlin + Jetpack Compose for TV UI, native adapter and retro host in C++ (`app/src/main/cpp/`) |
| `crates/` | Portable Rust core: protocol, transport, crypto, input, model, telemetry (no Android or Windows dependency, `unsafe` forbidden) |
| `platform/android/` | Rust JNI adapter for controller input |
| `third_party/pylux/` | Local patch applied to the pinned Pylux engine (AGPL) |
| `tools/` | PowerShell build, emulator and release scripts; Python evidence tooling |
| `docs/` | Plans, architecture, protocol notes, licence audit — each in English (`NAME.md`) and French (`NAME.fr.md`) |

## Build

Prerequisites: Rust stable with `rustfmt` and `clippy`, `cargo-ndk`, Python 3.12+,
JDK 17+, Android SDK 36 with NDK 28.2.13676358.

```powershell
# Rust and Python gates: format, Clippy (warnings denied), workspace and tooling tests
./tools/powershell/check.ps1

# Native Pylux engine for the four Android ABIs (once, or after a patch change)
./tools/powershell/build-pylux-native.ps1

# Android unit tests and debug APK
./apps/android-tv/gradlew.bat -p ./apps/android-tv testDebugUnitTest assembleDebug

# Google TV emulator with the GPSx bridges, then install the debug APK
./start-gpsx-emulator.ps1
```

Release builds need the signing material outside the repository
(`~/.gpsx/signing.properties`) and a clean, pushed tree; the maintainer publishes
with `./tools/powershell/publish-release.ps1`.

A successful build is not device validation: emulator, Android device and GP520
results are recorded separately.

## Evidence and secrets

Never commit credentials, tokens, cookies, registration keys, session keys or
secret-bearing URLs. Local captures go to `docs/evidence/` (generated files are
ignored by Git); only reviewed, sanitized fixtures belong in `tests/fixtures/`.

## Licence

GPSx is licensed under the GNU AGPL-3.0-only, with the OpenSSL linking
permission stated at the end of [LICENSE](LICENSE). Upstream attribution and
third-party notices are in [NOTICE](NOTICE); the licence audit is in
[docs/LICENSE_AUDIT.md](docs/LICENSE_AUDIT.md). The source of every release is
the tagged commit on GitHub.
