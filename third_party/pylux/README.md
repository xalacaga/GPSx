# Pylux native engine

GPSx ships the native Remote Play and PlayStation Plus cloud engine built from
[Pylux](https://github.com/ForWard-Technologies-LLC/Pylux) commit
`ca87a49a12bab071948f565ca53ffdad790775d3`, licensed AGPL-3.0-only with the
OpenSSL exception (`LICENSE-AGPL-3.0-only-OpenSSL.txt`).

`gpsx-pylux-ca87a49.patch` holds the GPSx changes: Windows-host protobuf Python
override, null checks before `display_sink.cantdisplay_cb`, no per-packet DEBUG
session logging, low-latency decoder hints with a plain retry, and a shared Oboe audio output
that reopens after route changes (and on request from GPSx when a Bluetooth output
appears), can target a given output device, and reports its state and device.

Rebuild the stripped `RelWithDebInfo` libraries for all ABIs with
`tools/powershell/build-pylux-native.ps1`. See `docs/PROVENANCE.md` and
`docs/STREAM_TUNING.md`.
