# UDP discovery transport

Status: implemented on Windows and Android and tested on loopback and the
Android emulator; real PS4/PS5 interoperability is NOT_TESTED.

## Scope

This slice adds the first I/O adapter outside the portable core. The
`gpsx-transport` crate depends on `gpsx-protocol`; no portable core crate depends
on the transport. It supports IPv4 SRCH discovery only.

The Android-only adapter exposes a coarse JNI call for PS5 discovery. It does
not implement WAKEUP, authentication, credential storage, cryptography, media,
or GP520 access.

## Android UI integration

The Session screen starts one bounded 1.2-second PS5 broadcast search on its
existing worker. Kotlin temporarily holds a non-reference-counted Wi-Fi
multicast lock, and the Rust Android adapter invokes the same
`gpsx-transport` implementation used by the CLI. The first IPv4 result only
prefills the host field; it is an unauthenticated hint and registration remains
responsible for authenticating the console.

The standard Google TV AVD does not forward IPv4 limited broadcasts between its
virtual network and the physical LAN. GPSx therefore selects an emulator-only
bridge when Android reports `ranchu`, `goldfish`, an SDK product or an emulator
model. The AVD sends one fixed request to the host alias `10.0.2.2:49302`; the
host helper listens only on `127.0.0.1:49302`, performs the bounded LAN
broadcast, and returns only the first IPv4 address. No registration data,
credential or stream traffic crosses this bridge. A real Android build always
uses direct UDP broadcast.

Start both emulator bridges with:

```powershell
tools/powershell/start-android-emulator-bridges.ps1
```

On 2026-10-29 this bridge discovered the real PS5 at `192.168.1.50` from the
Google TV API 36 AVD. This is evidence for that host and LAN only, not GP520
evidence.

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
or any GP520 behavior. They also do not prove that an AVD NAT forwards LAN
broadcasts.

The transport test suite passed 50 consecutive runs under local CPU load after
the response windows were raised to 500 milliseconds. This is stability evidence
for this Windows host only, not a timing or performance claim.
