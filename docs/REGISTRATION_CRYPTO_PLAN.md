# Registration cryptography plan

Status: implementation authorized on 2026-10-26; awaiting independent review
before commit.

This slice prepares the smallest portable cryptographic boundary needed by
future local PS4/PS5 registration. It does not format registration requests,
parse replies, open sockets, contact a console, accept a real PIN or account
identifier, persist credentials, wake a console, or establish a session.

## Evidence and scope

- **FACT:** the pinned chiaki-ng registration path derives a 16-byte AES key,
  derives a 16-byte IV from HMAC-SHA-256, and encrypts/decrypts registration
  material with AES-128-CFB.
- **FACT:** local registration also requires the `aeropause` transformation.
  PS4 10+ and PS5 select both key-table columns with offsets in `0..=31`;
  PS4 before 10 uses its separate fixed transformation without an offset.
- **FACT:** the registration cipher IV is
  `HMAC-SHA-256(target_key, ambassador || counter_be64)[0..16]`. Three HMAC
  constants distinguish PS5, PS4 10+, and PS4 before 10. Registration uses
  counter zero only.
- **FACT:** the exact upstream behavior and compatibility vectors are confined
  in this slice to `lib/include/chiaki/rpcrypt.h`, `lib/src/rpcrypt.c`, and
  `test/rpcrypt.c` at commit
  `c8bea2642d0e95402f373af55584a3b98cd89417`.
- **FACT:** ECDH, AES-GCM/GHASH and GKCrypt belong to later authenticated
  session work and are outside this slice.
- **INFERENCE:** isolating registration primitives before the codec and network
  adapter makes secret ownership, zeroization, and compatibility failures
  independently reviewable.
- **UNKNOWN:** interoperability with the discovered PS5 remains untested until
  the later live-registration gate is explicitly opened by the user.
- **FACT:** the pinned Mbed TLS branch allocates 16 bytes for a 32-byte HMAC
  result and triggers Windows stack protection. The reviewed oracle fixture
  records a scratch-only 32-byte-buffer repair before the existing 16-byte
  truncation. GPSx must use the safe Rust HMAC output size and never port the
  undersized buffer.

## Implementation files

Only these new implementation files are present:

| Local path | Purpose |
|---|---|
| `crates/gpsx-crypto/Cargo.toml` | Exact dependency and feature declarations |
| `crates/gpsx-crypto/src/lib.rs` | Minimal public surface and error types |
| `crates/gpsx-crypto/src/registration.rs` | Registration key schedule, IV derivation and AES-CFB operation |

The workspace manifest, lockfile, provenance registers, notice, CI checks and
dependency policy changed only as support files for these three files. No
registration codec or transport file is included.

## Adopted dependency subset

The audited direct dependencies are `aes 0.8.4` with `zeroize`, `cfb-mode
0.8.2` with `zeroize`, `hmac 0.12.1`, `sha2 0.10.9`, and `zeroize 1.9.1`, all
with default features disabled. `subtle 2.6.1` is an expected transitive
dependency through `digest/mac`; it is not a direct GPSx dependency. No
randomness dependency is present in this crate.

On 2026-10-26 the exact scratch graph passed Cargo 1.85 checks on Windows and
`aarch64-linux-android`, cargo-audit 0.22.2, and cargo-deny 0.20.2 checks for
advisories, licenses, bans, and sources. The advisory database commit, complete
resolved package/checksum list, feature set, command outcomes, and scratch-lock
SHA-256 are frozen in
`tests/fixtures/registration_crypto_dependency_audit_v1.json`. Implementation
must produce the same dependency names, versions, sources, checksums, and
features in the repository lockfile; any difference reopens the audit gate.

## API and secret invariants

- The crate is portable, performs no I/O, forbids `unsafe`, and has no platform
  dependency.
- Secret material has fixed sizes: 16-byte ambassador/key/IV values. Payloads
  are borrowed byte slices; this primitive does not impose a maximum length,
  so the future codec must enforce its reviewed payload bound.
- A closed target enum has exactly `Ps4Pre10`, `Ps4Modern`, and `Ps5`. Unknown
  or raw upstream target numbers are rejected before cryptographic work.
- The PIN enters through a non-`Copy`, non-`Clone`, redacted wrapper built from
  exactly eight ASCII decimal digits. Its owned bytes are zeroized, and the
  temporary four-byte big-endian representation is held in a zeroizing scope;
  no public `u32` PIN API is permitted.
- The ambassador is a non-`Copy`, non-`Clone`, redacted 16-byte owner. The
  future platform adapter must fill it from the OS CSPRNG, abort on entropy
  failure, never accept it from user input, and never reuse it across attempts.
- The registration-only API fixes the IV counter to zero internally and does
  not expose a caller-controlled counter. Encrypting a request consumes the
  context and returns a one-shot response decryptor, which is itself consumed
  by decryption: one context encrypts one request and its decryptor decrypts one
  response. The type system does not enforce freshness across independently
  constructed contexts. Keys and IVs are unique across attempts only when the
  platform adapter supplies the fresh CSPRNG ambassador required above.
- Both modern key-table offsets are validated before indexing: values 0 through
  31 are accepted and 32 or greater returns a static error. PS4 before 10
  exposes no offset.
- Secret-bearing owners are non-`Clone`, redact `Debug` and `Display`, and
  zeroize owned buffers on drop.
- **HYPOTHESIS / residual risk:** moving an owned Rust value may leave
  compiler-dependent copies of key or PIN-derived bytes in vacated stack slots
  or registers, because `Drop` runs only for the final owner. The API minimizes
  explicit copies and zeroizes every live owner, but does not claim to erase
  unobservable copies introduced by moves or optimization.
- Errors are closed enums with static messages and never contain inputs,
  derived keys, offsets, counters or ciphertext.
- The API never logs and exposes no serialization for secret-bearing types.
- `aes/zeroize` and `cfb-mode/zeroize` are mandatory so AES round-key schedules
  and CFB IV state implement zeroization on drop; cipher contexts are scoped to
  one operation.
- Key-table offsets are validated before indexing; fixed table dimensions and
  the validated `0..=31` range prove every computed index is in bounds. The
  wrapping additions in `aeropause` are protocol-defined byte arithmetic.
- Encryption and decryption may operate on arbitrary lengths because CFB is a
  stream mode; callers own and must enforce the maximum payload limit.

The upstream session and remote-registration functions `init_auth`,
`bright_ambassador`, `init_regist_psn`, `aeropause_psn`, and
`ambassador_from_aeropause` are explicitly excluded. So are ECDH, GKCrypt,
AES-GCM/GHASH, sockets, codecs, storage, JNI, Android APIs, and GP520 access.

## Tests required before review

1. NIST SP 800-38A AES-128-CFB128 vectors F.3.13 and F.3.14.
2. RFC 4231 HMAC-SHA-256 cases 1–4, 6 and 7, plus explicit truncation of the
   32-byte result to the first 16 bytes for IV construction.
3. Every applicable pinned `test/rpcrypt.c` registration vector—specifically
   `test_iv_regist_ps4`, `test_iv_regist_ps5`, `test_bright_regist_ps4`, and
   `test_bright_regist_ps5`—plus every case in
   `tests/fixtures/registration_crypto_oracle_v2.json`: PS4 before 10,
   plus PS4 10+ and PS5 with independent cipher/aeropause pairs `(0,31)`,
   `(31,0)`, and `(7,22)`. Every derived value,
   aeropause value, IV, ciphertext, and decrypt round trip must match bitwise.
   The other pinned `test/rpcrypt.c` vectors exercise explicitly excluded
   authentication/session helpers and are not applicable to this crate.
4. Boundary tests accepting every key-table offset from 0 through 31 and
   rejecting 32 before indexing with a static error.
5. One-shot request/response round trips for zero, sub-block, block, and
   multi-block payload lengths.
6. Determinism across distinct contexts with identical synthetic inputs,
   redacted formatting, and static error messages.
7. Compile-fail or structural tests proving secret owners do not implement
   `Clone`, the request context cannot encrypt twice, and formatting does not
   reveal secrets.
8. `cargo fmt --all --check`, workspace Clippy with warnings denied, workspace
   tests, Rust 1.85 checks, Windows build, and Android core cross-build.

Test vectors are public synthetic compatibility data. They must contain no
live PIN, account identifier, console identifier, registration key, session
key, private capture, or user-derived value.

During implementation, `NOTICE` must be updated for the three pinned chiaki-ng
sources and every adopted third-party dependency before the slice can commit.

## Later gates

After this slice receives an independent GO and is implemented:

1. a separately reviewed pure registration codec may format bounded requests
   and parse bounded replies;
2. platform credential stores must receive their own authentication-storage
   review (Windows protected storage and Android Keystore adapters);
3. a bounded Windows TCP adapter may be added without embedding storage in the
   portable core;
4. only then may the user explicitly authorize one live registration attempt,
   with the console-generated PIN entered interactively and never logged.

The live attempt must report only classified outcomes and aggregate timing. It
must never print or retain the PIN, PSN account identifier, registration key,
session key, decrypted payload, raw response, or secret-bearing URL.
