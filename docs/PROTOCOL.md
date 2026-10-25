# Protocol evidence register

Status: `UNKNOWN / NOT_TESTED`

This document deliberately contains no implementation copied or translated
from Chiaki-family sources. It records only the externally observed behaviors
needed for the M0.5 gate.

## State evidence to capture

| Area | Current status | Required evidence |
|---|---|---|
| Local discovery | NOT_TESTED | Sanitized packet metadata and state timestamps |
| Registration | NOT_TESTED | Outcome and error class; no registration secret |
| Session setup | NOT_TESTED | State transition timeline and negotiated media metadata |
| Video | NOT_TESTED | Codec, dimensions, FPS, loss, recovery events |
| Audio | NOT_TESTED | Codec, route, underruns, drift observations |
| Input | NOT_TESTED | Normalized input event trace without user identifiers |
| Reconnect | NOT_TESTED | Failure injection and transition timeline |
| Remote connection | NOT_TESTED | Network topology and classified outcome |

## Evidence rules

- Pin the exact upstream revision used for every run.
- Record environment and clock source.
- Prefer counts, timings, lengths, and state changes over payload contents.
- Never retain credentials, cookies, tokens, keys, or secret-bearing URLs.
- Mark interpretations separately as FACT, INFERENCE, or HYPOTHESIS.

