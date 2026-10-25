# Feature parity baseline

Status: product contract, 2026-10-25. This is not proof that a feature is implemented.

GPSx targets at least the user-visible capabilities found across the audited Chiaki,
chiaki-ng and Pylux references, while adapting them to a ten-foot Android TV
experience. A capability is only `DONE` after its implementation and relevant
tests exist. Hardware-dependent capabilities additionally require GP520 evidence.

| Area | Minimum GPSx capability | UI status | Evidence status | Delivery gate |
|---|---|---|---|---|
| Consoles | PS4/PS5 discovery, manual host, registration, wake, hide/remove | Shell | NOT_TESTED | Protocol + hardware |
| Connectivity | Local LAN and PSN-assisted remote connection | Shell | NOT_TESTED | Protocol + security |
| Profiles | Multiple users and per-console local/remote settings | Shell | NOT_TESTED | Secure storage |
| Stream | H.264 720p60/1080p60, auto quality, bitrate, reconnect, clean stop | Simulated shell | NOT_TESTED | Media + hardware |
| Advanced video | HEVC, HDR when supported, fit/zoom/stretch modes | Shell | UNKNOWN on GP520 | Capability detection |
| Audio | Output selection, audio-only/video-only modes, bounded buffer controls | Shell | NOT_TESTED | Media + route tests |
| Microphone | Input selection, mute, noise/echo controls where supportable | Shell | NOT_TESTED | Permissions + media |
| Controllers | Two physical DualSense inputs isolated locally, then merged into the one proven Remote Play player state | Shell | Core merge UNIT_TESTED; Android/GP520 NOT_TESTED | Input + two-controller physical tests |
| DualSense input | Standard buttons, analog triggers/sticks, touchpad click and capability inspection | Shell | Model UNIT_TESTED; physical mapping NOT_TESTED | Capability + physical tests |
| DualSense advanced output | Motion where exposed; haptics and adaptive triggers excluded from the Android Bluetooth MVP | Shell | Expected OS_LIMITATION on Android 11 Bluetooth; NOT_TESTED | Explicit later hardware decision |
| Accessibility | Deterministic D-pad focus, remote-only operation, readable TV scale | Shell | NOT_TESTED | Emulator + device |
| Diagnostics | Controller inspector, logs, live performance overlay, decoder/audio/network panels | Shell | NOT_TESTED | Subsystem integration |
| Telemetry | FPS, drops, RTT, jitter, loss, bitrate, decode/audio/input percentiles | Shell | Percentile core UNIT_TESTED; sources NOT_TESTED | Real metric sources |
| Portability | Import/export non-secret settings and JSONL metric export | Shell | Redaction core UNIT_TESTED; Android export NOT_TESTED | Storage + redaction |
| Recovery | Network loss, controller reconnect, Surface recreation, audio-route change | Shell | NOT_TESTED | Integration + soak |
| Bluetooth coexistence | Two DualSense controllers + TV remote + Bluetooth audio concurrently, with independent event correlation | Shell | NOT_TESTED | GP520 hardware matrix |
| Diagnostic lab | Guided captures, redacted bundle, private GitHub transfer and baseline comparison | Shell | Design only, NOT_TESTED | Security + GP520 evidence |
| Updates | Downloader bootstrap, then checked in-app GitHub Release updates | Shell | Design only, NOT_TESTED | Signing + security review |

`Present` means the navigation and product location are reserved in the first UI.
It does not mean the capability works. The current Android build is an offline,
synthetic prototype and labels all generated data accordingly.

## Product rules

- Common tasks stay within two focus moves from the home screen.
- Basic settings remain short; advanced controls are grouped by subsystem.
- Unsupported capabilities are shown as unavailable, never silently emulated.
- Two physical controller slots are mandatory. Input, capability and reconnect
  state are isolated locally by `ControllerId`, then buttons/triggers/axes are
  merged into a single Remote Play player state. Independent remote players remain
  `UNKNOWN` until protocol evidence proves support.
- Android Bluetooth haptics and adaptive triggers are explicitly outside the MVP;
  standard controller input must not depend on them.
- Android owns Bluetooth pairing. GPSx observes device and audio-route changes and never reconnects or resets unrelated devices.
- GPSx may exceed upstream functionality through clearer diagnostics, evidence
  separation, safer defaults and TV-specific interaction, but may not omit a
  baseline capability without an explicit product decision.
- Store distribution, trademarks and PSN terms remain separate release gates.

## Sources

The baseline comes from the upstream evidence recorded in `UPSTREAM_ANALYSIS.md`
and `LICENSE_AUDIT.md`. No upstream source was copied to create this inventory.

