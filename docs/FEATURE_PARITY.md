# Feature parity baseline

Status: product contract, 2026-10-25. This is not proof that a feature is implemented.

GPSx targets at least the user-visible capabilities found across the audited Chiaki,
chiaki-ng and Pylux references, while adapting them to a ten-foot Android TV
experience. A capability is only `DONE` after its implementation and relevant
tests exist. Hardware-dependent capabilities additionally require GP520 evidence.

| Area | Minimum GPSx capability | Initial UI | Delivery gate |
|---|---|---:|---|
| Consoles | PS4/PS5 discovery, manual host, registration, wake, hide/remove | Present | Protocol + hardware |
| Connectivity | Local LAN and PSN-assisted remote connection | Present | Protocol + security |
| Profiles | Multiple users and per-console local/remote settings | Present | Secure storage |
| Stream | H.264 720p60/1080p60, auto quality, bitrate, reconnect, clean stop | Present | Media + hardware |
| Advanced video | HEVC, HDR when supported, fit/zoom/stretch modes | Present | Capability detection |
| Audio | Output selection, audio-only/video-only modes, bounded buffer controls | Present | Media + route tests |
| Microphone | Input selection, mute, noise/echo controls where supportable | Present | Permissions + media |
| Controllers | DualSense-first mapping, generic controller mapping, remapping | Present | Input + physical tests |
| DualSense | Analog triggers, touchpad, motion, rumble/haptics, adaptive triggers | Present | Capability + physical tests |
| Accessibility | Deterministic D-pad focus, remote-only operation, readable TV scale | Implemented | Emulator + device |
| Diagnostics | Controller inspector, logs, live performance overlay, decoder/audio/network panels | Implemented shell | Subsystem integration |
| Telemetry | FPS, drops, RTT, jitter, loss, bitrate, decode/audio/input percentiles | Implemented shell | Real metric sources |
| Portability | Import/export non-secret settings and JSONL metric export | Present | Storage + redaction |
| Recovery | Network loss, controller reconnect, Surface recreation, audio-route change | Present | Integration + soak |

`Present` means the navigation and product location are reserved in the first UI.
It does not mean the capability works. The current Android build is an offline,
synthetic prototype and labels all generated data accordingly.

## Product rules

- Common tasks stay within two focus moves from the home screen.
- Basic settings remain short; advanced controls are grouped by subsystem.
- Unsupported capabilities are shown as unavailable, never silently emulated.
- GPSx may exceed upstream functionality through clearer diagnostics, evidence
  separation, safer defaults and TV-specific interaction, but may not omit a
  baseline capability without an explicit product decision.
- Store distribution, trademarks and PSN terms remain separate release gates.

## Sources

The baseline comes from the upstream evidence recorded in `UPSTREAM_ANALYSIS.md`
and `LICENSE_AUDIT.md`. No upstream source was copied to create this inventory.

