# Embedded diagnostic lab

GPSx includes an on-device measurement lab so GP520 sessions can produce
reproducible evidence without attaching a debugger. Local capture works without
GitHub or Internet access. GitHub transfer is an optional second step.

First implementation (2026-10-31): one `gpsx-stream-test/1` JSON report per
streamed session, uploaded on request through the GitHub contents API to the
diagnostics repository, public for now by owner decision (2026-10-31); see `INSTALL_GP520.md`. Guided scenarios and the
zip bundle below remain planned.

## Guided scenarios

- Quick health check: 2 minutes.
- Standard comparison: 10 minutes, with a warm-up period excluded from results.
- Stability run: 30 or 60 minutes.
- Soak run: user-selected multi-hour duration.
- Input test: Physical controller A/B event-path latency, reconnect isolation and merged-state correctness.
- Bluetooth coexistence: two DualSense controllers, TV remote and Bluetooth audio.
- Audio route test and Surface/background recovery test.

Each run records a scenario identifier, monotonic start time, app/build revision,
Android build, device fingerprint, non-secret configuration, network class and
an explicit evidence class. Console/account identifiers are redacted or replaced
with per-export random aliases.

## Captured signals

- Session state changes, reconnects and classified errors.
- Video receive/decode/render FPS, frame-time distribution, drops, codec name,
  resolution, bitrate and bounded-queue depths.
- RTT, jitter, loss, reordering, duplication and congestion signals.
- Decode and render P50/P95/P99, plus thermal state, CPU and memory when available.
- Audio route changes, granted mode, buffer depth, underruns and overruns.
- Per-`ControllerId` OS receive, normalization, queue and send timestamps; button
  transitions are retained while continuous values may be sampled/coalesced.
- Bluetooth device arrival/removal and audio-route events, correlated by time but
  without MAC addresses or stable personal identifiers.

GPSx does not claim controller-to-photon or network-wide latency from internal
timestamps. Those require external instrumentation and are reported separately.

## Export bundle

An export is a versioned `.gpsx-diagnostics.zip` containing:

- `manifest.json`: schema, build, scenario, evidence class and SHA-256 inventory;
- `metrics.jsonl`: bounded, machine-readable time series;
- `events.jsonl`: state and lifecycle events;
- `summary.json` and `summary.md`: percentiles and test outcome;
- `redaction-report.json`: categories removed, never removed values.

No credentials, tokens, cookies, registration keys, redirect URLs, session keys,
console identifiers, controller descriptors, MAC addresses or raw packet captures
may enter the bundle. Export runs a deny-list and structured-field allow-list,
then presents size, duration and included categories for user confirmation.

## Optional private GitHub path

The preferred personal workflow is a dedicated private diagnostics repository:

1. GPSx records and validates the bundle locally.
2. The user reviews its manifest and explicitly sends it.
3. A narrowly scoped credential, never embedded in the APK and protected by
   Android Keystore, uploads the bundle as an asset to a private test release.
4. A repository workflow validates hashes/schema and runs the Python comparator.
5. The generated Markdown/JSON report is retained as a workflow artifact.

The credential is restricted to the single diagnostics repository, is revocable,
expires in at most 30 days, and is never written to logs or exports. The Settings
screen exposes its expiry and a removal action; revocation instructions point to
GitHub's token settings, and GPSx treats revocation as a normal recoverable error.
OAuth/GitHub App authentication is preferred before public distribution; a
fine-grained personal token with `Contents: write` is acceptable only for the
owner's private diagnostics repository and must never grant access to the public
source repository. Failed upload never deletes the local bundle.

The analysis workflow treats every uploaded archive as hostile. Before extraction
it rejects absolute paths, `..` components, symbolic/hard links, duplicate names,
more than 64 entries, any single expanded entry above 16 MiB, and total expanded
content above 64 MiB. It streams extraction into a fresh directory, never executes
bundle content, validates the manifest allow-list and hashes, and scans for secrets
before parsing metrics. These limits require adversarial zip-slip and zip-bomb tests.

## Comparison rules

Baseline and candidate must match device, firmware, network class, scenario,
quality, codec, duration and thermal window. Reports show deltas and confidence
limits where practical, and classify results as improvement, regression or
inconclusive. A code change is not called an optimization from averages alone.
