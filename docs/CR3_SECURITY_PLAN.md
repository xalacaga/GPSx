# CR3 protocol security plan

Historical status of the first CR3 gate: limited GO accepted 2026-10-25 for the
pure SRCH slice only. Later transport, registration-crypto, and registration-codec
gates are recorded at the end of this document.

The first slice proposed at that time was a pure, allocation-bounded discovery
formatter and response parser in `gpsx-protocol`. It performed no socket I/O,
registration, authentication, cryptography, media handling, or GP520 access.
The exact chiaki-ng files were frozen in `PROVENANCE.md` at commit
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
| Registration HTTP header block | 1,024 bytes | Close attempt; retain no partial result | IMPLEMENTED; remediation `GO` |
| Registration body / decrypted field block | 1,024 bytes / 128 fields | Close attempt; zeroize caller-owned secret buffers | IMPLEMENTED; remediation `GO` |
| Complete registration response | 1,500 bytes for header plus body | Close attempt | IMPLEMENTED; remediation `GO` |
| Future encrypted UDP datagram | 2,048 bytes | Drop and count classified error | CRYPTO INTEGRATED; transport outside slice |
| Future media fragment set | 64 fragments / 4 MiB aggregate | Drop frame and request recovery when supported | PLANNED, outside first slice |
| Future reorder window | 128 packets / 250 ms | Evict oldest and record bounded loss | PLANNED, outside first slice |

The first slice keeps status lines and header names ASCII-oriented, accepts
valid UTF-8 header values without control characters, rejects embedded or
repeated NUL, duplicate security-relevant fields, numeric overflow and trailing
unparsed bytes, and never logs raw datagrams or identifiers. One terminal NUL
is accepted and removed as an explicit interoperability precaution.

## Historical authorization — first SRCH slice

At that first gate, only `gpsx-protocol` and its four then-current provenance
rows were authorized. The crate had zero dependencies, no build script, forbade
unsafe code, and performed no I/O. WAKEUP, credentials, sockets, registration,
authentication, cryptography, sessions, Takion, media, JNI, Kotlin protocol
code, real captures, and GP520 work were outside that authorization. This is a
historical scope statement, not the current project boundary; later transport,
registration-crypto, and registration-codec decisions are summarized at the end.

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
zeroization primitives itself. The integrated register covers `aes`,
`cfb-mode`, `hmac`, `sha2`, `subtle`, `zeroize`, `secp256k1` 0.33.1, `ctr`
0.9.2, and `aes-gcm` 0.10.3. `k256` was rejected because its Rust-1.85
resolution is affected by `RUSTSEC-2026-0387`; `secrecy` and direct `getrandom`
were not selected. Exact versions, licenses, MSRV evidence and completed gates
are recorded in `CRYPTO_DEPENDENCIES.md`; session-specific invariants are in
`SESSION_CRYPTO_PLAN.md`. If the
required protocol mode cannot be represented safely by maintained libraries,
implementation stops for another security review; no custom fallback is allowed.

The session protocol's 32-bit GMAC is a documented residual risk. GPSx accepts
at most 2,048 authenticated bytes per packet, claims SP 800-38D Table 1 conformance only
through 1,024 bytes, limits authenticated-decryption invocations to 2,048 per
remote GMAC epoch key, and keeps
direction/session-global failure budgets. A 4 MiB forward resynchronization
ceiling permits one direct target-epoch derivation; farther-forward input is
rejected before cryptography, and stale input below the backward window is
discarded without consuming the authentication-failure budget. Exact limits,
bounded epoch-counter state, owner sign-off, silent-discard policy, and no-reset
rules are normative in `SESSION_CRYPTO_PLAN.md`; implementation and closed
external oracle integration received independent `GO` on 2026-10-28.

The session handshake is a consuming state chain: one `HandshakeSession` owns
the handshake key and fallibly generated ephemeral scalar, verifies the peer,
performs ECDH, and derives both directions without exposing an independently
rebindable authenticated-key or shared-secret value. Production entropy uses
`try_fill_bytes` plus at most eight unbiased `secp256k1::SecretKey` validation attempts;
fixed-scalar construction exists only under `#[cfg(test)]`.

Exactly one public egress writes the handshake key into a caller-owned
`Zeroizing<[u8; 16]>` for the future reviewed LaunchSpec/rpcrypt builder. There
is no production import-from-bytes path. Confidentiality still depends on that
future slice base64-encoding the key and rpcrypt-encrypting the complete
LaunchSpec before network I/O; this remains a design assumption, not current
implementation evidence.

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

The accepted GO first created `gpsx-protocol`; `gpsx-transport` then received a
separate GO. The registration cryptography and bounded codec remediations, the
registration oracle integration, and the session-cryptography implementation
and oracle have now all received their required independent `GO` reviews. The
external harnesses, stubs, patches, logs, and closed build manifests remain
permanently outside the repository. Portable session cryptography is integrated
in `gpsx-crypto`; no separate `gpsx-session` crate was introduced.
