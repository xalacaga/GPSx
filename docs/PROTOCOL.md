# Protocol evidence register

Status: parser and UDP loopback adapter tested; console interoperability remains
`NOT_TESTED`.

This document deliberately contains no implementation copied or translated
from Chiaki-family sources. It records only the externally observed behaviors
needed for the M0.5 gate.

## State evidence to capture

| Area | Current status | Required evidence |
|---|---|---|
| Local discovery | LOOPBACK_TESTED / console NOT_TESTED | Sanitized packet metadata and state timestamps |
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

## UDP discovery adapter

`gpsx-transport` is a GPSx-authored platform adapter around the pure
`gpsx-protocol` SRCH API. It sends exactly one request per console generation and
collects replies for a response window greater than zero and at most five
seconds. A fixed 65,535-byte receive buffer covers every ordinary IPv4 UDP
payload, so data exceeding the 1,024-byte protocol limit reaches the parser and is
rejected rather than being truncated or converted to a Windows socket error.

At most 16 unique source addresses are retained. The first 16 win; later unique
responses, malformed datagrams and duplicates are counted separately. Errors
contain an operation and an `ErrorKind`, never packet bytes or addresses. Debug
formatting redacts peer addresses. ICMP connection resets/refusals are non-fatal
and counted. Replies from a source port other than 9302 for PS5 or 987 for PS4
remain accepted but are counted separately while real console behavior is
UNKNOWN. The command-line probe prints state, status, request port, the local
ephemeral source port and aggregate counters, but no peer address.

Every discovered endpoint is an unauthenticated, untrusted hint. Discovery alone
must never authorize registration or session setup; those future layers require
independent authentication. GPSx currently uses an OS-selected source port,
whereas the pinned upstream first tries 9303–9319 before falling back to a random
port. The first approved console run records that port as evidence.

The loopback tests are FACT only about the Windows host and local UDP stack. No
packet has been sent to a PS4, PS5 or GP520, so console discovery remains
NOT_TESTED. The probe requires an explicit `--live` argument before it emits a
limited-broadcast packet.

