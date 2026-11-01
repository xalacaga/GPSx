# Pylux native engine

GPSx ships the native Remote Play and PlayStation Plus cloud engine built from
[Pylux](https://github.com/ForWard-Technologies-LLC/Pylux) commit
`ca87a49a12bab071948f565ca53ffdad790775d3`, licensed AGPL-3.0-only with the
OpenSSL exception (`LICENSE-AGPL-3.0-only-OpenSSL.txt`).

`gpsx-pylux-ca87a49.patch` holds the GPSx changes:

- Build: Windows-host protobuf Python override; no per-packet DEBUG session logging.
- Robustness: null checks before `display_sink.cantdisplay_cb`; `set_surface(NULL)`
  no longer returns with the codec mutex locked (nor re-locks it in `kill_decoder`).
- Audio: a shared Oboe output that reopens after route changes (and on request when a
  Bluetooth output appears), can target an output device and reports its state; a
  40 ms jitter buffer (refilled after running dry, delay capped at 120 ms).
- Video decoder: counters (samples before the decoder exists, queued, rendered);
  optional low-latency hints with a plain retry; the codec follows the codec read from
  the stream; only the newest decoded frame is rendered; samples are copied into a
  queue fed to the codec by a separate thread that waits up to 5 s for an input buffer,
  so a decoder stall neither drops frames nor blocks network reception; output format
  changes are logged.
- H.264 (PS4 cloud on the GP520 MStar decoder): presentation times 1/60 s apart (with
  the upstream 1 us step the MStar display path kept the first picture), SPS rewritten
  to allow frame_num gaps and announce 1 reference frame, parameter sets queued as
  codec config.
- Cloud: a frame whose reference was lost is still decoded instead of being dropped
  (PS4 cloud servers never send a new IDR); the Takion receive thread runs at nice -10
  on Android.

Rebuild the stripped `RelWithDebInfo` libraries for all ABIs with
`tools/powershell/build-pylux-native.ps1`. See `docs/PROVENANCE.md` and
`docs/STREAM_TUNING.md`.
