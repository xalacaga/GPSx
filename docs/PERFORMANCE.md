# Performance baseline

No performance claim is valid until the environment and comparable before/after
measurements are recorded.

## Required run metadata

- application and immutable revision;
- console model and firmware;
- host/projector build and power mode;
- wired/Wi-Fi topology, band, channel width, RSSI where available;
- controller and audio connection mode;
- requested resolution, frame rate, codec, and bitrate;
- warm-up duration and measurement duration.

## Required metrics

| Layer | Metrics |
|---|---|
| Network | RTT P50/P95/P99, jitter, packets lost/reordered, bitrate |
| Video | receive-to-submit and submit-to-present P50/P95/P99, drops, queue depth |
| Audio | buffer depth, underruns, drift, route changes |
| Input | event interval and input-to-observable-response P50/P95/P99 |
| Runtime | CPU, memory, GC/JNI activity, thermal state |

The first gate is a 10-minute run. Passing configurations proceed to 30- and
60-minute runs. Results are reported separately for Windows, emulator, generic
Android hardware, and the physical GP520.

