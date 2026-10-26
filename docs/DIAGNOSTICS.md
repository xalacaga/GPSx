# Diagnostic export

Status: portable JSONL encoder implemented; platform persistence and sharing are
not implemented yet.

## Purpose and boundary

The portable telemetry core produces deterministic diagnostic reports that can
later be persisted by Windows or Android adapters and compared by offline tools.
It performs no file, network, Android, JNI, PS5, or GP520 access.

An export is evidence only about the environment named in its run record and the
procedure that produced it. Emulator data never proves physical GP520 behavior.
The encoder rejects synthetic runs labelled `android_generic` or `gp520`, and a
synthetic run cannot contain `fact` evidence. Platform adapters remain
responsible for establishing the provenance of real hardware captures. A report
is not a performance claim without the comparable environment and procedure
required by `PERFORMANCE.md`.

## JSONL v1

The first line is one run record. Every following line is one metric record:

```jsonl
{"schema":"gpsx.diagnostics.v1","record":"run","source_revision":"development","environment":"android_emulator","run_kind":"synthetic","sample_count":1,"started_unix_ms":1000}
{"schema":"gpsx.diagnostics.v1","record":"metric","metric":"network.rtt","value":4.8e0,"unit":"ms","monotonic_ns":42,"wall_unix_ms":1234,"evidence":"hypothesis"}
```

Metric names and units are a closed Rust enumeration. Evidence is exactly one of
`fact`, `inference`, `hypothesis`, `unknown`, or `not_tested`. The source revision
is either `development` or a 40-character lowercase Git SHA. Metric values are
finite JSON numbers written in shortest scientific notation that round-trips to
the same `f64` bits, including the sign of zero. Consumers must not infer a
separate integer type from their lexical representation.

`monotonic_ns` is non-decreasing within a report and is the only field used for
sample ordering and intervals. Equal timestamps are allowed. `started_unix_ms`
and `wall_unix_ms` are optional metadata; wall clocks can jump and are not used
for ordering. A wall clock is rejected if it precedes the Unix epoch or its
millisecond count does not fit in `u64`. The maximum representable `SystemTime`
is platform-dependent.

## Metric semantics

Unless a row says otherwise, a `count` is cumulative since the run started, and
a gauge is the value observed at `monotonic_ns`.

| Metric | Unit | v1 meaning |
| --- | --- | --- |
| `network.rtt` | ms | Latest measured network round-trip time. |
| `network.jitter` | ms | Absolute difference between the current and previous RTT samples. |
| `network.packets_lost` | count | Cumulative detected lost packets. |
| `network.packets_reordered` | count | Cumulative packets received out of order. |
| `network.bitrate` | bit/s | Payload bits received during the latest complete one-second window. |
| `video.frames_per_second` | fps | Frames presented during the latest complete one-second window. |
| `video.receive_to_submit` | ms | Latest compressed access-unit receipt to decoder submission duration. |
| `video.submit_to_present` | ms | Latest decoder submission to frame presentation duration. |
| `video.dropped_frames` | count | Cumulative frames dropped after receipt. |
| `video.queue_depth` | frames | Complete compressed access units currently awaiting decoder submission. |
| `audio.buffer_depth` | ms | Playable PCM duration currently queued for output. |
| `audio.underruns` | count | Cumulative audio output underruns. |
| `audio.drift` | ms | Audio presentation time minus the session monotonic clock; positive means audio is ahead. |
| `audio.route_changes` | count | Cumulative output route changes. |
| `input.event_interval` | ms | Time since the preceding captured controller event. |
| `input.to_response` | ms | Latest measured controller capture to correlated response duration. |
| `runtime.cpu_percent` | percent | Process CPU use normalized to total device capacity, from 0 to 100. |
| `runtime.memory_bytes` | bytes | Current resident process memory. |
| `runtime.jni_calls` | count | Cumulative JNI boundary calls made by the run. |
| `runtime.thermal_celsius` | celsius | Hottest relevant temperature reported by the platform adapter; its stable sensor source must be recorded by the test procedure. |

## Bounds and failure behavior

- A report contains at most 4,096 samples.
- A run record is at most 223 UTF-8 bytes and a metric record at most 233 bytes,
  excluding their newlines. These limits are calculated from the longest value
  of every closed field. The derived report limit is 958,688 bytes: one run line
  plus 4,096 metric lines, each including its newline. Therefore every valid
  maximum-size v1 report fits.
- Non-finite numbers, invalid revisions, forbidden synthetic evidence,
  decreasing monotonic timestamps, pre-Unix wall clocks, timestamp overflow,
  excessive samples, and violated size invariants are rejected.
- Semantic inputs are checked before construction. Defensive size checks also
  run while formatting. No failure returns a partial report.
- Errors contain only static descriptions and never echo input data.
- The core returns bytes in memory. Atomic file creation, retention, sharing,
  and user consent belong to platform adapters and require separate tests.

The format intentionally contains no free-form keys, device identifiers, URLs,
credentials, registration data, or session secrets. Adding any such field
requires a new security review and adversarial redaction tests.

## Compatibility

Schema v1 is frozen: field names, record kinds, enumeration values, units, and
metric meanings must not be changed or extended under the v1 identifier. Such a
change requires a new schema identifier and migration tests. Readers must reject
unknown schema identifiers or record kinds. They may ignore unknown object
members only after recognizing a supported schema and record kind. Record order
remains one run line followed by exactly `sample_count` metric lines.
