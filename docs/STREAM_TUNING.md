# Stream tuning for the GP520 over Wi-Fi

Status: implemented 2026-10-31; emulator-measured, GP520 NOT_TESTED.

## Target

- BenQ GP520: native 3840×2160 DLP (XPR), 60 Hz, HDR10/HDR10+/HLG, Wi-Fi 6,
  Google TV on Android 11 (API 30). BenQ lists 17.9 ms input lag at 1080p60 and
  4K60, reached with the **Game** picture preset plus **Game Boost**.
- Network: the stream will mostly run over Wi-Fi.
- FACT: PS5 Remote Play tops out at 1080p60; the projector upscales to 4K.

## What GPSx does

| Area | Change | Why |
|---|---|---|
| Native engine | Pylux built with `RelWithDebInfo` (`-O2`) instead of the debug `-O0` build, then stripped | Decryption, FEC and Opus run in software on the receive path |
| Engine logging | Per-packet `DEBUG` lines dropped unless verbose logging is requested | Logging every packet costs CPU on the receive thread |
| Decoder | `low-latency`, `priority=0` and Qualcomm/MediaTek/Exynos low-latency keys, with a plain-format retry if refused | Ask the decoder to output each frame immediately |
| Wi-Fi | `WIFI_MODE_FULL_LOW_LATENCY` lock (API 29+, high-perf before) held only while a session exists | Wi-Fi power save adds tens of ms of jitter |
| Display | `preferMinimalPostProcessing` (API 30) and screen kept on while streaming | Asks the display for its game mode |
| Frame rate | `Surface.setFrameRate(60, FIXED_SOURCE)` (API 30) | Keeps the panel at 60 Hz without judder conversion |
| Codec | AUTO picks HEVC 1080p60 when a hardware HEVC decoder supports it, else H.264; the emulator stays on H.264 | Better picture at the same 15 Mbit/s |
| Metrics | Real FPS, bitrate, RTT, loss, lost frames and audio state shown during the stream | Diagnose Wi-Fi problems on screen |
| Audio | AAudio, then OpenSL ES when AAudio opens but never pulls audio for 1.5 s (or OpenSL ES forced in Settings); shared Oboe stream (low latency, then default), Game usage, reopened after a route change such as a Bluetooth speaker (re)connecting; underflow/overflow/reopen counters in reports | The original engine never reopened a disconnected stream and requested exclusive mode, which Bluetooth A2DP lacks |

## Owner settings on the GP520

- Picture mode **Game** and **Game Boost** on. UNKNOWN whether the Android
  minimal post-processing hint switches the built-in Google TV to Game mode by
  itself, so set it manually.
- Prefer 5 GHz or 6 GHz Wi-Fi, PS5 wired to the router if possible, and the
  projector within direct sight of the access point.

## Before/after (2026-10-31, Google TV API 36 x86_64 emulator, real PS5 home screen)

| Metric | Debug engine | Optimized engine |
|---|---|---|
| Engine log lines, 60 s stream | 1,225 | 287 (−77 %) |
| Decoder threads CPU (3 × 10 s) | 47.1–48.2 % | 46.3–47.1 % |
| Engine + app threads CPU (3 × 10 s) | 17.9–18.5 % | 17.1–18.5 % |
| FPS / loss / lost frames | 60 / 0 % / 0 | 60 / 0 % / 0 |
| Decoder low-latency hints | not requested | accepted (`AMediaCodec configured with low-latency hints`) |

- FACT: the home screen streams at only 0.1–0.3 Mbit/s, so the engine had little
  work; CPU differences are within run-to-run noise. No CPU improvement is
  claimed. The `-O2` build matters for gameplay at about 15 Mbit/s, which was
  not measured.
- FACT: on the emulator the "decoder" threads are host-emulated decoding; on the
  GP520 that work runs on the hardware decoder.
- FACT: emulator RTT (26–155 ms) reflects the emulator NAT, not Wi-Fi.
- UNKNOWN: GP520 latency, decoder low-latency support, Game mode switching and
  thermals until the physical gate opens.
