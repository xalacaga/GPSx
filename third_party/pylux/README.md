# Pylux native engine

GPSx ships the native Remote Play and PlayStation Plus cloud engine built from
[Pylux](https://github.com/ForWard-Technologies-LLC/Pylux) commit
`ca87a49a12bab071948f565ca53ffdad790775d3`, licensed AGPL-3.0-only with the
OpenSSL exception (`LICENSE-AGPL-3.0-only-OpenSSL.txt`).

`gpsx-pylux-ca87a49.patch` holds the GPSx changes: Windows-host protobuf Python
override, null checks before `display_sink.cantdisplay_cb`, no per-packet DEBUG
session logging, low-latency decoder hints with a plain retry, and a shared Oboe audio output
that reopens after route changes (and on request from GPSx when a Bluetooth output
appears), can target a given output device, and reports its state and device;
video decoder counters (samples before the decoder exists, queued, rendered) and a
fix for `set_surface(NULL)` returning with the codec mutex still locked (and
re-locking it in `kill_decoder`); the codec is created on the first sample with the
codec read from the stream; stale decoded frames are skipped; optional low-latency
decoder hints; on cloud sessions an IDR request (Takion IDRREQUEST) is sent at
most once per second when a frame misses its reference or FEC fails; samples are copied into a queue fed to the codec by a separate thread, which waits up to 5 s for an input buffer instead of dropping them, so a decoder stall never blocks network reception; PS4 cloud streams can use Android's software H.264 decoder (set from Kotlin); for the hardware decoder the H.264 SPS is rewritten to allow frame_num gaps (PS4 cloud skips frame_num after a loss while declaring gaps impossible) and to announce at most 4 reference frames; the Takion receive thread runs at nice -10 on Android; on cloud sessions a frame whose reference was lost is still handed to the decoder instead of being dropped; a hardware H.264 variant switch (1: 4 references, 2: Moonlight style with 1 reference and parameter sets as codec config, 3: 2 with 1/60 s presentation times) and decoder output logging help diagnose the GP520 hardware decoder.

Rebuild the stripped `RelWithDebInfo` libraries for all ABIs with
`tools/powershell/build-pylux-native.ps1`. See `docs/PROVENANCE.md` and
`docs/STREAM_TUNING.md`.
