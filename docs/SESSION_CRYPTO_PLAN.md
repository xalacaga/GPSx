# Session cryptography plan

Status: integrated on 2026-10-28. The third plan remediation, implementation
and R1/R2 follow-ups, closed external oracle, and C1 canonical-fixture binding
all received independent `GO`. The project owner accepted the protocol-imposed
32-bit-tag residual risk. N1, the complete third-party licence bundle, remains
mandatory before distributing any binary.

## Scope and evidence

**FACT:** the pinned chiaki-ng baseline at
`c8bea2642d0e95402f373af55584a3b98cd89417` uses secp256k1 ECDH, a 16-byte
handshake key, a 32-byte shared secret, HMAC-SHA-256 key derivation, an
AES-128 counter-mode keystream, and AES-128-GCM as GMAC with a 16-byte IV and a
4-byte tag. The observed sources are `lib/include/chiaki/ecdh.h`,
`lib/src/ecdh.c`, `lib/include/chiaki/gkcrypt.h`, `lib/src/gkcrypt.c`,
`lib/include/chiaki/seqnum.h`, `lib/src/streamconnection.c`, and
`test/gkcrypt.c`. They remain reference-only until provenance review authorizes
specific derived files.

**FACT:** the pinned `chiaki_ecdh_derive_secret` accepts a handshake key and a
remote 32-byte signature but does not read either value. Its caller requires a
non-empty remote signature and then passes it to that function. GPSx must not
silently reproduce this unauthenticated behavior.

**FACT:** the upstream vector proves that the remote signature is
`HMAC-SHA-256(handshake_key, complete 65-byte uncompressed SEC1 key)`, including
the `0x04` prefix. ECDH produces the 32-byte big-endian x-coordinate. The
traffic derivation is `key || iv = HMAC-SHA-256(ecdh_secret,
01 || direction || 00 || handshake_key || 01 || 00)`, with local direction `2`
and remote direction `3`.

**FACT:** the keystream is AES-128 over
`little_endian_u128(iv) + floor(key_pos / 16)`. GMAC uses a 16-byte IV advanced
by `floor(key_pos / 16)`, the packet as AAD, empty plaintext, and the first four
bytes of the full GCM tag. Epoch is `(key_pos > 0 ? key_pos - 1 : 0) / 45000`.
Later epoch keys use SHA-256, not HMAC, over `key_gmac_base` and the IV advanced
by checked `epoch * 44910`, then XOR-fold the two digest halves.

**UNKNOWN:** no console, emulator, LAN, PS4, PS5, or GP520 session exchange has
been tested by GPSx. Console interoperability, the accepted failure response,
the outbound position correction below, and all target-specific differences
remain unverified.

## Integrated dependency decision

The integrated direct additions are exact versions with default features off:

| Crate | Candidate features | Purpose | Current evidence |
|---|---|---|---|
| `secp256k1` 0.33.1 | `alloc`; defaults off; `secp256k1-sys` 0.14.1 | secp256k1 public-key validation and raw-point ECDH | Crate metadata CC0-1.0; vendored, patched libsecp256k1 C is MIT; declared MSRV 1.63; C/FFI remains inside the reviewed dependency |
| `ctr` 0.9.2 | `zeroize`; concrete `Ctr128LE<aes::Aes128>` | maintained little-endian AES-CTR composition | MIT OR Apache-2.0; declared MSRV 1.56 |
| `aes-gcm` 0.10.3 | `aes`, `zeroize`; `AesGcm<Aes128, U16>` | full 16-byte GMAC with a 16-byte nonce | MIT OR Apache-2.0; declared MSRV 1.56 |
| `subtle` 2.6.1 | defaults off; direct dependency | constant-time comparison of the received tag with `full_tag[..4]` | BSD-3-Clause; already present transitively |

There is no `hazmat` feature. GPSx computes the full tag, takes its most
significant four bytes as required by GCM, and verifies those bytes in constant
time. The existing exact `hmac`, `sha2`, `aes`, and `zeroize` dependencies remain
in scope. `getrandom` is not a direct portable-core dependency in this proposal:
production entropy is injected through a platform adapter implementing the
required cryptographic RNG traits. Deterministic RNGs are test-only.

**FACT (2026-10-28):** the originally approved `k256` 0.13.4 resolution pulled
`crypto-bigint` 0.5.5, now affected by `RUSTSEC-2026-0387`. `k256` 0.14.0 itself
declares Rust 1.85, but its corrected transitive chain requires Rust 1.87. GPSx
therefore rejected both resolutions instead of suppressing the advisory and
resolved `secp256k1` 0.33.1/`secp256k1-sys` 0.14.1. Rust 1.85 Windows and Android
checks, Clippy, cargo-audit 0.22.2, cargo-deny 0.20.2, and `cargo tree -d` pass;
the exact graph is recorded in `session_crypto_dependency_audit_v1.json`.
Because this introduces a maintained C/FFI dependency with CC0-1.0 crate
metadata and vendored, patched MIT libsecp256k1 C code in place of the approved
pure-Rust family, it required and received independent CR3 acceptance on
2026-10-28.

## Required API and state invariants

- The portable `gpsx-crypto` crate remains `#![forbid(unsafe_code)]` and has no
  platform, JNI, networking, logging, or telemetry dependency.
- `HandshakeKey` owns exactly 16 bytes. It is non-`Clone`, non-`Copy`, redacted,
  zeroized on drop, and generated once per attempt by a fresh CSPRNG. It is
  transmitted only inside the rpcrypt-encrypted LaunchSpec. The consuming
  `HandshakeSession` signs the local key, verifies the peer key, performs ECDH,
  and derives both traffic directions. Its only key egress is the narrow
  LaunchSpec method below; no general key accessor exists.
- An ephemeral private key is non-`Clone`, redacted, and zeroized on drop. A
  session attempt consumes it exactly once when deriving the shared secret.
- The public key is exactly one validated, uncompressed 65-byte SEC1 point.
  Invalid length, prefix, off-curve point, point at infinity, or invalid
  signature length fails before ECDH.
- Before point parsing or ECDH, GPSx verifies the remote 32-byte HMAC-SHA-256
  over the exact received 65 bytes using `Mac::verify_slice`. Authentication
  failure is terminal, has no fallback, and produces no secret.
- The raw ECDH x-coordinate never becomes a cipher key directly. The observed
  protocol HMAC-SHA-256 derivation is reproduced only after an oracle binds its
  exact input bytes and output split into the 16-byte key and 16-byte IV.
- Encryption and GMAC contexts own their secret material, are non-`Clone`, have
  redacted `Debug`/`Display`, and zeroize all stored key material on drop.
- Counter position, checked addition, GMAC key epoch, and inbound verification
  count are explicit typed state. Inbound decryption accepts any byte position; only the
  internal CTR block start is aligned and leading keystream bytes are skipped.
  Overflow, an out-of-window position, epoch overflow, or limit exhaustion
  returns a classified error without partial inbound output. For outbound
  protection, an internal error after CTR starts may leave the selected range
  transformed; the caller discards the whole packet and the monotonic position
  is deliberately not rolled back, preventing nonce reuse.
- The type system separates unauthenticated input, authenticated peer key,
  derived secret, and ready traffic keys. No caller can initialize traffic
  cryptography from unverified bytes.

The concrete public typestate is:

```text
HandshakeSession<AwaitingPeer>::new(entropy) -> Result<HandshakeSession<AwaitingPeer>>
HandshakeSession<AwaitingPeer>::local_public_key_and_tag(&self) -> LocalHandshakeMaterial
HandshakeSession<AwaitingPeer>::write_encrypted_launch_spec(
  &mut self, &mut SessionRpcrypt, LaunchSpec, &mut [u8])
  -> Result<EncryptedLaunchSpecLength, LaunchSpecError>
HandshakeSession<AwaitingPeer>::finish(self, UnverifiedPeerKey)
  -> Result<TrafficKeyPair { local: LocalTrafficKeys, remote: RemoteTrafficKeys }>
LocalTrafficKeys::protect_in_place(&mut packet, encrypted_range)
  -> Result<(OutboundPacketPositions, [u8; 4])>
RemoteTrafficKeys::verify_and_decrypt_in_place(
  key_pos_low, &mut packet, encrypted_range, &[u8; 4]) -> Result<ReceiveOutcome>
```

`finish` verifies the peer HMAC with the session-owned `HandshakeKey`, parses the
point, consumes the session-owned ephemeral scalar for ECDH, and derives both
directions with that same handshake key. No `AuthenticatedPeerKey`, shared
secret, handshake-key reference, public `From`/`TryFrom`, deserializer, or
unchecked construction path escapes this chain. A private derivation primitive
uses the closed `Direction::{Local, Remote}` enum mapped only to `2` and `3`; the
public pair returns distinct local and remote types. Compile-fail tests reject
derivation outside `finish`, reuse of the consumed session, fixed-scalar
construction in a non-test build, swapping directions, and cloning any secret
owner. A structural test proves that no public API can verify with one
`HandshakeKey` and derive with another.

The later reviewed LaunchSpec candidate removes the temporary raw-key exit.
`write_encrypted_launch_spec` is now the only public consumer of the 16 bytes;
it returns only the length of ciphertext base64 written to caller storage, and
`finish` rejects a session until that write succeeds. No owned key array or key
reference escapes. Production exposes no `HandshakeKey` or `HandshakeSession`
constructor from bytes; a fixed-key constructor exists only under `#[cfg(test)]`.

**HYPOTHESIS:** confidentiality of this boundary depends on the reviewed
session-rpcrypt and LaunchSpec slice. Until the independent implementation
review returns `GO`, the transport boundary remains a candidate rather than an
integrated guarantee; plaintext key material must never reach I/O, logs, errors,
or telemetry.

The pinned `test/gkcrypt.c::test_ecdh` vector is mandatory. Every one-byte
mutation of the peer key, signature, or handshake key must fail before ECDH. If
hardware contradicts this construction, work stops for another security review.

The traffic API authenticates the complete packet and encrypts only the bounded
`encrypted_range` within that packet. On send, CTR is applied to the range first
and GMAC is then computed over the complete resulting packet. On receive, GMAC
over the complete packet is verified before any byte is decrypted. The caller
owns Takion layout and must zero the MAC field and, for CONTROL/CONGESTION, the
position field before calling this portable primitive. The 2,048-byte maximum
applies to the complete authenticated packet, not only to the encrypted range.

## Key-position and outbound-allocation policy

`KeyPositionReconstructor` brings the 32-to-64-bit behavior of
`chiaki_key_state_request_pos` into scope and reproduces every
`test/keystate.c` vector. Candidate positions are reconstructed and checked
before SHA-256, AES, or GHASH.

The fixed receive policy is:

- maximum authenticated packet length: 2,048 bytes;
- forward resynchronization ceiling from the highest authenticated position:
  4,194,304 bytes (4 MiB);
- backward tolerance: 262,144 bytes (the planned 128-packet reorder window);
- before the first authenticated packet, the position is at most 4,194,304;
- only successful authentication advances the highest position;
- a forward candidate within the 4 MiB ceiling triggers at most one direct
  target-epoch derivation and one GMAC attempt; success resynchronizes the high
  water mark, while failure consumes the global and per-epoch failure budgets;
- a farther-forward or initial position beyond the ceiling performs no SHA-256,
  AES, or GHASH and consumes one global failure; 32 such failures terminate the
  session;
- a position older than the backward window performs no cryptography and is
  silently discarded as stale without consuming the authentication-failure
  budget, so ordinary late traffic cannot terminate the session;
- exactly one current GMAC epoch/key may be retained. A forward transition
  derives the target once. A lower in-window epoch uses one temporary zeroizing
  key without changing current state. No packet triggers an iterative epoch walk.

**HYPOTHESIS:** the 4 MiB ceiling tolerates the required 64 KiB, 256 KiB, and
1 MiB simulated loss bursts with margin while bounding attacker-selected epochs;
at 15 Mbit/s it represents about 2.2 seconds of encrypted stream. The 256 KiB
backward window is only a stale/reorder acceptance bound. Replay/oracle evidence
must confirm both; changing either bound requires independent re-review. A loss
gap greater than 4 MiB cannot resynchronize under this policy: later positions
remain too far forward and the 32nd rejection terminates the session, requiring
a clean reconnect.

`epoch * 44910`, `key_pos + 16`, reconstruction, block conversion, and 128-bit
IV addition use checked arithmetic. Carry beyond 128 bits is an error, never a
wrap.

`OutboundKeyPositionAllocator` is the sole source of emitted tag positions. For
encrypted-range length `n`, it allocates GMAC at `p`, encryption at `p + 16`, then moves
to `round_up_16(p + 16 + n)`. This intentionally corrects the pinned rounding
quirk and proves that no two emitted tags share `floor(p / 16)` under one key.
Console acceptance is UNKNOWN; contradiction stops the slice rather than
weakening IV uniqueness.

## Short-tag and counter safety gate

The 32-bit GMAC tag is a protocol constraint, not a GPSx security choice. NIST
SP 800-38D Appendix C Table 1 gives these limits
([official publication](https://doi.org/10.6028/NIST.SP.800-38D)):

| Maximum Combined Length of the Ciphertext and AAD In a Single Packet (bytes) | Maximum Invocations of the Authenticated Decryption Function |
|---:|---:|
| 2^5 | 2^22 |
| 2^6 | 2^20 |
| 2^7 | 2^18 |
| 2^8 | 2^15 |
| 2^9 | 2^13 |
| 2^10 | 2^11 |

There is no row above 1,024 bytes. GPSx makes no SP 800-38D conformance claim
for authenticated packets from 1,025 through 2,048 bytes. This is a
protocol-imposed residual risk. NIST has announced that a future revision will
remove support for tags shorter than 96 bits. Project-owner acceptance of this
risk was recorded as **ACCEPTED on 2026-10-28**. This acceptance does not claim
SP 800-38D conformance or console interoperability.

The fixed runtime receive policy is per remote direction. Authentication
generation on the monotonic local/send direction is not charged against these
authenticated-decryption invocation limits; IV uniqueness remains enforced by
the outbound allocator:

- every GMAC verification counts, success or failure;
- the invocation limit is 2,048 for each GMAC epoch key, indexed by epoch; a
  verification using a temporary lower-epoch key counts against that epoch;
- through 1,024 bytes this conservative limit enforces the matching Table 1
  row; packets through 2,048 bytes remain non-conformant residual risk;
- authentication, length, far-forward, or initial-position failures count
  toward a direction/session-global failure limit of 32; GMAC failures also
  count toward a per-epoch limit of 8; neither failure counter ever resets;
- reaching any limit ends the session before another attempt, with no reset,
  response, retry, or silent rekey;
- failed packets are silently discarded. Only bounded numeric counters may be
  recorded; packet bytes, addresses, keys, positions, and tags are never logged.

Invocation state is a bounded, non-secret map from epoch to count. The fixed
receive interval can intersect at most 101 epochs, so capacity is 102 entries.
An entry may be removed only after its epoch is permanently below the monotonic
high-water mark's backward window; an epoch that can still be accepted or become
current retains its count. Advancing to a new epoch therefore does not reset an
invocation count, while a session may process at least 1,000,000 valid packets
across many epoch keys without hitting a session-global success limit.

The bound uses 45,000-position epochs as defined above by
`(key_pos > 0 ? key_pos - 1 : 0) / 45000`: the receive span is
`262,144 + 4,194,304 = 4,456,448` bytes, so it intersects at most
`ceil(4,456,448 / 45,000) + 1 = 101` epochs. The distinct constant `44,910` is
only the checked IV-offset multiplier and is not the epoch length.

**HYPOTHESIS:** 2,048 invocations fit in one 45,000-position epoch only when the
average advance is at most about 21.97 bytes, or when retransmissions accumulate.
The replay oracle must demonstrate whether valid traffic can reach this cap; it
must not silently raise or reset the cap if it can.

The observed counter and GMAC constants require byte-exact oracle binding.
`Ctr128LE` is accepted only if width, endianness, byte seeking, and overflow
match every vector. Otherwise work stops; no custom cipher-mode fallback exists.

## Entropy, caching, performance, and provenance

Production injects a platform-owned CSPRNG implementing `rand_core` 0.6
`CryptoRng + RngCore`. GPSx calls fallible `try_fill_bytes` upstream into a
zeroizing 32-byte candidate and makes at most eight attempts to construct a
validated `secp256k1::SecretKey` by unbiased rejection; entropy failure or eight
rejected candidates returns a classified `Err` without panic. A GPSx-owned,
non-`Clone`, consuming wrapper passes the key by reference to
`secp256k1::ecdh::shared_secret_point`, retains only the big-endian x-coordinate,
zeroizes the temporary point, and calls the dependency's `non_secure_erase` in
`Drop`. The dependency documents that compiler-created secret copies cannot be
guaranteed erased; this residual memory-copy risk is explicit and part of the
new CR3 implementation review.
Only `#[cfg(test)]` exposes a fixed-scalar constructor for the pinned vector; it
is absent from production builds.

The portable core has no thread, worker, or keystream cache. It may retain only
the single current 16-byte GMAC epoch key described above; temporary lower-epoch
keys and cipher contexts are zeroized after use. Any larger cache or
precomputation requires a separate review.

On-demand CTR/GMAC performance is NOT_TESTED on GP520/ARMv8. No performance
claim is allowed. After the user explicitly opens the physical-hardware gate,
the first build depending on this path requires comparable P50/P95/P99 CPU-time
and queue-depth evidence before any cache or thread is considered.

`PROVENANCE.md` records the proposed upstream inventory and exact functions.
Only ECDH/HMAC behavior, key/IV and GMAC derivation, CTR positioning, direction
selection, outbound positions, 32-to-64-bit reconstruction, and the single
LaunchSpec-key egress contract may be ported.
Networking, protobuf, Takion parsing, threading, the circular keystream cache,
actual LaunchSpec formatting/rpcrypt encryption, media, and logging are
explicitly excluded for a future reviewed slice.

## Test and evidence gate

Implementation may begin only after this plan receives an independent CR3 GO.
The implementation review must then require:

1. exact dependency and feature resolution recorded in a new audit fixture;
2. `cargo +1.85.0 check --locked --all-targets` on Windows and for
   `aarch64-linux-android`, plus audit and deny gates;
3. `cargo tree -d` with no duplicated `aes`, `cipher`, or `digest` generation,
   unless a separate signed exception exists;
4. public secp256k1 ECDH and NIST AES-GCM/CTR known-answer tests;
5. a closed oracle covering all seven `test/gkcrypt.c` tests, every
   `test/keystate.c` vector, both directions, unaligned positions, and positions
   `0`, `1`, `44999` (epoch 0), `45000` (epoch 0), `45001` (epoch 1), `90000`,
   `90001`, `2^32 + 0x420`, and
   `0x6b1de0`, plus the RFC 1982 comparison case at an exact distance of `2^31`;
6. oracle cases for a lower-epoch return, multi-epoch jump, `ff..ff` IV carry,
   error at 2^128, checked `epoch * 44910`, `key_gmac_base` versus `key_base`,
   SHA-256 fold-XOR, `key_pos + 16`, and full-tag/MSB32 equality;
7. every one-byte mutation of every field, malformed SEC1 points, bad HMAC,
   checked-overflow, and redaction; a failing RNG returns a classified `Err`
   without panic; eight rejected scalar candidates return the exhaustion error;
   the fixed-scalar test constructor reproduces `test_ecdh`; and compile-fail
   tests prove that production exposes no fixed-scalar, `HandshakeKey`-from-bytes,
   or `HandshakeSession`-from-bytes constructor;
8. at least 1,000,000 complete packets through the real protect/verify/decrypt
   API across at least 1,000 epochs and multiple encrypted-range sizes without termination, the
   2,049th verification under one epoch terminating the session, temporary-key
   verifications charged to their own epoch, and global/per-epoch failure
   counters never resetting; replay evidence must cover the under-22-byte average
   advance/retransmission case that could legitimately reach the per-epoch cap;
9. simulated 64 KiB, 256 KiB, and 1 MiB loss bursts followed by valid traffic;
   rejection beyond 4 MiB before SHA-256/GHASH; 32 forged far-forward positions
   terminating the session; stale backward traffic not consuming the failure
   budget; a greater-than-4-MiB gap requiring clean reconnect; and every boundary;
10. structural/public-API tests proving exactly one handshake-key egress, no
    verification/derivation with different keys, and that deterministic exported
    bytes equal the same key used for traffic-key derivation; plus a property test
    proving the outbound allocator never repeats a GMAC IV and a structural test
    that `gpsx-crypto` contains no thread or keystream cache;
11. an independently generated oracle with a closed R5-equivalent provenance
    record and no live credential, key, capture, LAN, emulator, or hardware data.

The session-cryptography slice is integrated after independent `GO` for the
implementation, external build record, exact canonical fixture binding,
provenance entries and full gate results. The short-tag risk has been accepted
since 2026-10-28. N1 still blocks binary distribution. No console or
performance claim is permitted until its separate evidence gate is opened.
