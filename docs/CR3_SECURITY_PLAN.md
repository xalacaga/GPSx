# CR3 protocol security plan

Status: limited GO accepted 2026-10-25 for the pure SRCH slice only.

The proposed first slice is a pure, allocation-bounded discovery formatter and
response parser in `gpsx-protocol`. It performs no socket I/O, registration,
authentication, cryptography, media handling, or GP520 access. The exact
chiaki-ng files are frozen in `PROVENANCE.md` at commit
`c8bea2642d0e95402f373af55584a3b98cd89417`.

## Trust boundary and parser limits

Every network byte is unauthenticated and hostile until a later authenticated
session layer proves otherwise. Limits are part of the API and are checked before
allocation or indexing. Checked arithmetic is mandatory; recursion is forbidden.

| Input | Hard limit | Failure policy | Status |
|---|---:|---|---|
| Discovery UDP datagram | 1,024 bytes | Reject entire datagram | ACCEPTED CR3 |
| Discovery header count | 32 | Reject entire datagram | ACCEPTED CR3 |
| Discovery line | 512 bytes | Reject entire datagram | ACCEPTED CR3 |
| Future registration HTTP headers | 32 KiB / 128 fields | Close attempt; retain no partial secret | PLANNED, outside first slice |
| Future registration body | 1 MiB | Close attempt; zeroize partial secret buffers | PLANNED, outside first slice |
| Future encrypted UDP datagram | 2,048 bytes | Drop and count classified error | PLANNED, outside first slice |
| Future media fragment set | 64 fragments / 4 MiB aggregate | Drop frame and request recovery when supported | PLANNED, outside first slice |
| Future reorder window | 128 packets / 250 ms | Evict oldest and record bounded loss | PLANNED, outside first slice |

The first slice keeps status lines and header names ASCII-oriented, accepts
valid UTF-8 header values without control characters, rejects embedded or
repeated NUL, duplicate security-relevant fields, numeric overflow and trailing
unparsed bytes, and never logs raw datagrams or identifiers. One terminal NUL
is accepted and removed as an explicit interoperability precaution.

## Authorized SRCH behavior

Only `gpsx-protocol` and its four provenance rows are authorized. The crate has
zero dependencies, no build script, forbids unsafe code, and performs no I/O.
WAKEUP, credentials, sockets, registration, authentication, cryptography,
sessions, Takion, media, JNI, Kotlin protocol code, real captures and GP520 work
remain forbidden.

The response parser accepts CRLF, LF and CR line endings and a complete final
line without EOL. It removes exactly one optional space after a colon, rejects
empty values, control characters and non-terminal/repeated NUL, ignores unknown keys, and rejects duplicate
`host-id`, `host-request-port`, `device-discovery-protocol-version` and
`host-type` fields. For other known duplicate fields, the first value wins.
Ports are strict decimal values from 1 through 65535. These are deliberate
hardening differences from the pinned C implementation, which truncates at the
first NUL, abandons a final line without EOL, uses base-0 partial port conversion
and lets the first input duplicate win as a consequence of reverse-list
traversal. GPSx additionally accepts UTF-8 values and one terminal NUL, treats
header names case-insensitively, accepts status-only responses and leading zeros
in nonzero status codes, and rejects a doubled status separator, LF-CR followed
by data, and port whitespace.

## Cryptography decision for later slices

GPSx will not implement AES, CFB mode, AES-GCM/GHASH, SHA-256, HMAC, secp256k1
ECDH, key derivation, random generation, constant-time comparison, or
zeroization primitives itself. The candidate register covers `aes`, `cfb-mode`,
`hmac`, `sha2`, `subtle`, `zeroize`, `secrecy`, platform-backed `getrandom`, and
unresolved maintained families for AES-GCM/GHASH and secp256k1 ECDH. Candidate versions,
licenses, MSRV evidence and the unresolved advisory gate are recorded in
`CRYPTO_DEPENDENCIES.md`. Exact checksums must be pinned and audited before `gpsx-crypto` is created. If the
required protocol mode cannot be represented safely by maintained libraries,
implementation stops for another security review; no custom fallback is allowed.

Secrets use dedicated non-`Clone`, redacted wrappers, are zeroized on drop where
the dependency supports it, never cross logging/telemetry APIs, and are excluded
from panic messages, fixtures and diagnostic archives. Compatibility tests use
public standards vectors and synthetic protocol vectors only, never live keys.

## Fuzzing plan

- `discovery_response`: arbitrary byte slices from 0 through 1,025 bytes; assert
  no panic, no excessive allocation and deterministic classified errors.
- `http_response`: fragmented inputs, duplicate fields, invalid separators,
  embedded NUL, extreme counts and all integer boundaries.
- Later targets: registration envelope, control frame, encrypted framing,
  fragment assembly and reorder-window state machine.
- This slice uses dependency-free deterministic property tests over arbitrary
  byte vectors of every length from 0 through 1,025. Standard `cargo-fuzz`
  layout requires a separately reviewed provenance-guard change and remains a
  later Linux-only gate.
- Run sanitizer-backed native campaigns where supported. Every crash becomes a
  deterministic regression test before the parser can advance.

## Required CR3 evidence

- Independent review of this plan, the exact planned-file inventory and license.
- Tests at every boundary: limit minus one, limit, and limit plus one.
- Property tests for deterministic parsing and formatter/parser round trips.
- Deterministic arbitrary-input sweep for every length from 0 through 1,025;
  cargo-fuzz evidence is deferred to its separately reviewed Linux gate.
- Dependency license/advisory record before adding any crypto dependency.
- Synthetic fixtures with complete manifest provenance and a clean secret scan.

The accepted GO created only `gpsx-protocol`. `gpsx-transport` later received a
separate GO and is now present. The registration-only `gpsx-crypto` slice was
authorized on 2026-10-26 and is awaiting implementation review;
`gpsx-session` remains absent pending a later explicit review.
