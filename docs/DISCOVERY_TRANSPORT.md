# Windows UDP discovery transport

Status: implemented and tested on loopback; PS4/PS5 interoperability is
NOT_TESTED.

## Scope

This slice adds the first I/O adapter outside the portable core. The
`gpsx-transport` crate depends on `gpsx-protocol`; no portable core crate depends
on the transport. It supports IPv4 SRCH discovery only.

It does not implement WAKEUP, registration, authentication, credential storage,
cryptography, session setup, media, controllers, JNI, Android or GP520 access.

## Bounds and failure behavior

- One SRCH datagram is sent for each requested console generation.
- The response window is greater than zero and at most five seconds; the probe
  uses 750 milliseconds for PS5 followed by 750 milliseconds for PS4.
- Receive storage is a single fixed 65,535-byte allocation covering every
  ordinary IPv4 UDP payload, so oversized protocol payloads reach and are rejected by the
  existing 1,024-byte parser limit on Windows as well as Unix.
- At most 16 unique consoles are retained. First response per generation and
  source IP wins; duplicates and later capacity overflows are counted.
- Malformed packets are counted and ignored. Socket failures return a static
  operation plus `std::io::ErrorKind`, without packet contents or addresses.
- `ConnectionReset` and `ConnectionRefused`, which can represent ICMP port
  unreachable, are non-fatal and counted in `icmp_errors`; collection continues
  until its deadline. Any other receive error returns `Err` and does not return
  the partial report.
- Valid replies whose source port differs from the generation's discovery port
  are retained but counted in `unexpected_source_ports`.
- Peer addresses are available to the next transport layer but are redacted from
  `Debug`; the probe does not print them. It prints the local ephemeral source
  port and only aggregate reply counters.

Discovery replies are unauthenticated, untrusted hints. A host on the LAN can
spoof a response or consume one of the 16 retained slots. No future registration
or session layer may trust a discovered peer without performing its own
authentication and identity checks.

GPSx currently binds an OS-selected ephemeral source port. This deliberately
differs from the pinned upstream implementation, which first attempts ports
9303–9319 and then falls back to an OS-selected port. Whether consoles or common
Windows firewall configurations depend on that preferred range is UNKNOWN. The
first approved live run must record the local source port, reply source-port
mismatch count, firewall decision and classified outcome without retaining peer
addresses or packet contents.

## Local validation

The safe command below only displays help and performs no network I/O:

```powershell
cargo run -p gpsx-transport --bin gpsx-discover -- --help
```

The live command is intentionally not part of automated tests. It sends limited
broadcasts on the local network and must be run only after explicit approval:

```powershell
cargo run -p gpsx-transport --bin gpsx-discover -- --live
```

Windows Firewall may ask whether the freshly built executable may receive local
UDP replies. Denying the prompt is safe; discovery may then return zero consoles.
The command does not wake or register a console and stores no output.

## Evidence boundary

Loopback tests prove request bytes, bounded timing, continued collection after a
malformed response, duplicate coalescing, capacity policy and peer redaction on
the Windows host. They also prove that Windows ICMP resets are counted without
discarding the report. They do not prove console compatibility, broadcast
routing, expected console source ports, firewall behavior on another machine,
or any GP520 behavior.

The transport test suite passed 50 consecutive runs under local CPU load after
the response windows were raised to 500 milliseconds. This is stability evidence
for this Windows host only, not a timing or performance claim.
