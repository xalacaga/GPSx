# LaunchSpec and session-rpcrypt implementation contract

Status: **IMPLEMENTATION CANDIDATE — INDEPENDENT CR3 IMPLEMENTATION REVIEW
REQUIRED**. The plan remediation received independent `GO` on 2026-10-28. The
Rust implementation, closed external oracle, exact binding tests and local
gates now exist; integration remains blocked on the independent implementation
re-review required below. The integrated session-crypto slice remains the
baseline until that review returns `GO`.

## Purpose and evidence

This slice closes the only remaining raw handshake-key boundary. It formats the
canonical LaunchSpec, includes the per-attempt handshake key as RFC 4648 base64,
appends the terminal NUL, generates the pinned session-rpcrypt counter-zero
keystream by CFB-encrypting an equal-length zero buffer, XORs that keystream
with the JSON-plus-NUL, and returns only the base64 encoding of that ciphertext.
It performs no network, protobuf, Takion, logging, telemetry, storage, thread,
media, controller, Android, Windows, or GP520 work.

**FACT:** at pinned chiaki-ng commit
`c8bea2642d0e95402f373af55584a3b98cd89417`, `lib/src/launchspec.c` defines the
field order and target/codec-dependent additions. `lib/src/streamconnection.c`
uses a 1,024-byte JSON buffer, includes the terminal NUL, CFB-encrypts an
equal-length zero buffer at session-rpcrypt counter zero, XORs that keystream
with those bytes, then base64-encodes the ciphertext before protobuf
construction. `lib/src/session.c` initializes that rpcrypt
state from the negotiated target, 16-byte nonce, and 16-byte registration
material called `morning`.

**FACT:** the implementation removes the former public raw-key egress.
`HandshakeSession::write_encrypted_launch_spec` is now the only public path: it
writes ciphertext base64 into caller storage, and `finish` rejects a session
that has not completed this step. Secret owners remain non-`Clone`, redacted,
and zeroized.

**HYPOTHESIS:** reproducing the pinned formatter and session-rpcrypt behavior is
sufficient for console interoperability. No console connection has tested this
slice. Emulator results would not establish console or GP520 behavior.

## Source and license boundary

The only behavioral port sources proposed for this slice are:

- `lib/include/chiaki/common.h`, limited to `ChiakiTarget`, `ChiakiCodec`,
  `chiaki_target_is_ps5`, `chiaki_codec_is_h265`, and
  `chiaki_codec_is_hdr`;
- `lib/include/chiaki/launchspec.h` and `lib/src/launchspec.c` for fields,
  ordering, punctuation, target additions, and codec additions;
- `lib/include/chiaki/rpcrypt.h` and only
  `bright_ambassador_ps4_pre10`, `bright_ambassador`,
  `chiaki_rpcrypt_bright_ambassador`, `chiaki_rpcrypt_init_auth`,
  `rpcrypt_hmac_key`, `chiaki_rpcrypt_generate_iv`,
  `chiaki_rpcrypt_crypt`, and `chiaki_rpcrypt_encrypt` in
  `lib/src/rpcrypt.c`, including the `echo_a`, `keys_a_ps4`, `keys_a_ps5`,
  `keys_b_ps4`, and `keys_b_ps5` tables they require;
- `lib/src/session.c` only for the target/nonce/`morning` initialization order;
- `lib/src/streamconnection.c` only for the 1,024-byte bound, terminal-NUL,
  encryption, and outer-base64 ordering;
- RFC 4648 for GPSx-authored base64.

All paths use the pinned commit and
`LicenseRef-AGPL-3.0-only-OpenSSL` already accepted under project Option C.
GPSx will not copy the formatter, base64 implementation, logging, protobuf, or
transport code. The external oracle may compile the pinned sources, but its
harness, patches, logs, and closed build manifest stay outside the repository.

## Proposed ownership and API

Implementation stays in the portable `gpsx-crypto` crate, in a new private
`launch_spec` module. This is intentional: moving formatting to
`gpsx-protocol` would require retaining a public raw-key exit across crates.
The crate remains `#![forbid(unsafe_code)]`, performs no I/O, and adds no
platform dependency.

The reviewed public surface is expected to be equivalent to:

```text
SessionNonce::from_session_response_bytes([u8; 16]) -> SessionNonce
SessionMorning::take_from_registration_buffer(&mut Zeroizing<[u8; 16]>)
  -> SessionMorning
SessionRpcrypt::new(SessionTarget, SessionNonce, SessionMorning)
  -> Result<SessionRpcrypt>
HandshakeSession<AwaitingPeer>::write_encrypted_launch_spec(
  &mut self,
  &mut SessionRpcrypt,
  LaunchSpec,
  &mut [u8],
) -> Result<EncryptedLaunchSpecLength>
HandshakeSession<AwaitingPeer>::finish(self, UnverifiedPeerKey)
  -> Result<TrafficKeyPair>
```

Names may change during implementation, but these invariants may not:

1. `SessionMorning`, `SessionRpcrypt`, and every temporary plaintext/key buffer
   are non-`Clone`, non-`Copy`, redacted, and zeroized on every return path.
   `SessionNonce` is protocol-public but remains an owned fixed-size value.
   `SessionTarget` and validated public video parameters may be `Copy`.
2. The target is one closed enum: PS4 before firmware 10, PS4 10+, or PS5. The
   same target selects rpcrypt derivation and LaunchSpec additions. A caller
   cannot pass independent crypto and formatting targets.
3. The former public `write_launch_spec_handshake_key` method is absent.
   A crate-private helper writes once into `Zeroizing<[u8; 16]>`; no raw key,
   reference, general accessor, callback, serializer, or construction from key
   bytes becomes public.
4. `write_encrypted_launch_spec` is the only consumer of that helper. It first
   validates every public parameter and output capacity, builds plaintext in a
   fixed 1,024-byte zeroizing buffer, appends one NUL, CFB-encrypts the same
   number of zero bytes at rpcrypt counter zero to obtain the pinned OFB-like
   keystream, XORs plaintext with that keystream, and writes only RFC 4648
   ciphertext-base64 to caller output. It does not directly CFB-encrypt the
   plaintext.
5. Caller output is unchanged on every error. A successful call commits both
   `launch_spec_written` on the `HandshakeSession` and a single-use
   `launch_spec_counter_zero_consumed` slot owned by `SessionRpcrypt`. The
   mutable rpcrypt owner rejects every later LaunchSpec attempt even through a
   different `HandshakeSession`. `finish` fails before peer verification/ECDH
   if its LaunchSpec write did not succeed. Neither state rolls back after
   success.
6. The output length excludes any convenience terminator and is at most 1,368
   bytes (`4 * ceil(1024 / 3)`). No owned plaintext or ciphertext collection is
   returned. Bytes after the returned range are not part of the result.
7. Errors identify only public classes such as invalid parameters, output too
   small, already written, not written, arithmetic overflow, or internal crypto
   failure. They never contain nonce, `morning`, handshake key, plaintext,
   ciphertext, or secret-derived bytes.

`SessionRpcrypt`, rather than `HandshakeSession`, owns the one-success slot
because it owns the counter namespace. This prevents two handshake sessions
from requesting the LaunchSpec counter-zero keystream from one rpcrypt owner.
It does not prevent a caller from constructing two rpcrypt owners from copied
inputs; the type system cannot prove freshness or correct nonce/registration
material pairing. `SessionMorning::take_from_registration_buffer` therefore
borrows a `Zeroizing` source, copies once into its owner, and immediately
zeroizes the source instead of accepting a by-value `[u8; 16]` that is `Copy`.
Freshness and pairing remain session-orchestrator obligations.

**INFERENCE / residual risk:** the pinned protocol also starts both future ctrl
directions at rpcrypt counter zero. LaunchSpec's zero-buffer CFB construction
and ctrl's direct CFB construction therefore share `E(IV)` for their first
block; their feedback and subsequent blocks differ. The current slice neither
implements nor authorizes ctrl encryption. Access to the two ctrl counter-zero
slots is reserved for a separately reviewed ctrl slice. This protocol-imposed
sharing is not claimed to be eliminated by the LaunchSpec one-success state.

## Canonical formatting contract

`LaunchSpec` contains only bounded typed numeric/enum inputs: width, height,
maximum FPS, sent bitrate in kbit/s, MTU, RTT in milliseconds, and codec. There
is no caller-provided JSON string and therefore no escaping or injection path.
Checked decimal formatting is allocation-free.

The byte-exact oracle must bind the pinned field order, punctuation, numeric
spelling, fixed placeholder strings, fixed arrays, the handshake-key base64,
and the target/codec additions. PS4 has no PS5 additions. PS5 adds adaptive
stream mode, selects AVC or HEVC, and selects SDR or HDR exactly as pinned.
The pinned formatter ignores codec for both PS4 families. GPSx nevertheless
rejects PS4 with HEVC or HEVC-HDR as an explicit product policy rather than
silently downgrading to an unlabelled AVC request. That rejection is tested
outside the upstream oracle; it is not presented as upstream behavior.

All six numeric inputs have the following byte-compatibility boundary. The
pinned formatter accepts `unsigned int` without semantic validation, so zero is
accepted here. A future video-profile/session orchestrator may impose narrower
negotiated-profile policy before constructing this value.

| Field | Rust type | Accepted range | Zero policy |
|---|---:|---:|---|
| width | `u32` | `0..=u32::MAX` | accepted, matching the formatter |
| height | `u32` | `0..=u32::MAX` | accepted, matching the formatter |
| maximum FPS | `u32` | `0..=u32::MAX` | accepted, matching the formatter |
| sent bitrate (kbit/s) | `u32` | `0..=u32::MAX` | accepted, matching the formatter |
| MTU | `u32` | `0..=u32::MAX` | accepted, matching the formatter |
| RTT (ms) | `u32` | `0..=u32::MAX` | accepted, matching the formatter |

With every decimal field at `u32::MAX`, the pinned template is 879 bytes for
PS4 and 951 bytes for PS5 before the NUL. The largest encrypted input is thus
952 bytes and its base64 is 1,272 bytes. The fixed defensive capacities remain
1,024 bytes for plaintext-plus-NUL and 1,368 bytes for base64
(`4 * ceil(1024 / 3)`). Tests cover `0`, `1`, `u32::MAX - 1`, and `u32::MAX` for
each field; negative and above-`u32` values are unrepresentable and rejected by
compile-fail tests. Formatting rejects conversion truncation, unchecked
arithmetic, locale dependence, caller floats, and any internal length that
would exceed either fixed capacity.

## Cryptographic contract

Session rpcrypt owns the derived 16-byte AES key and 16-byte ambassador. Its IV
is the first 16 bytes of HMAC-SHA-256 over `ambassador || counter_be64`, using
the pinned target-family HMAC key. For a LaunchSpec of `n` bytes including its
terminal NUL, the implementation zeroes an `n`-byte `Zeroizing` buffer, applies
the existing AES-128-CFB encryptor at counter zero to that zero buffer, and
computes `ciphertext = (JSON || NUL) XOR resulting_keystream`. This reproduces
the pinned OFB-like feedback; direct CFB encryption of the plaintext is wrong
from the second block onward. Existing audited RustCrypto dependencies are
reused; no new AES, CFB, OFB, HMAC, SHA-256, or base64 primitive is introduced.

The PS4-before-10 and modern PS4/PS5 nonce/`morning` derivations remain separate
closed branches. Production accepts negotiated material only through the owned
`SessionNonce` and consuming `SessionMorning` boundaries described above; this
does not authenticate their origin or prevent a caller from duplicating inputs
before ownership transfer. Test-only convenience constructors for complete
fixed tuples exist only under `#[cfg(test)]`. Debug and Display are redacted for
all secret owners and the builder state.

## Required closed oracle

Before integration, an external scratch build of the pinned C behavior must
produce `tests/fixtures/launch_spec_oracle_v1.json` from public synthetic
nonce, `morning`, and handshake-key inputs. The repository keeps only the
canonical fixture, its manifest/provenance entries, and hashes. The closed
external build record captures clean checkout status, exact commands, compiler,
all translation units and headers, patches if any, and SHA-256 for every input
and output.

The fixture must cover at least:

- PS4 before 10, PS4 10+, and PS5;
- AVC, HEVC-SDR, and HEVC-HDR where valid;
- decimal boundaries for every numeric field and the largest accepted JSON;
- the exact 24-byte base64 handshake-key representation;
- plaintext both without and with its terminal NUL, rpcrypt IV, zero-buffer CFB
  keystream, XOR ciphertext, and final ciphertext-base64 as separately bound
  fields;
- multi-block cases of at least 32 bytes and a final block whose length is not
  a multiple of 16;
- exact length calculations and an output-capacity boundary.

The Rust test reconstructs the entire fixture document and compares it by exact
bytes. It checks the exact case count and all top-level/input names. Mutations
of every input, target, codec, numeric field, JSON byte, terminal NUL, IV,
ciphertext byte, keystream byte, base64 byte, length, case count, and content
after the closing brace must fail. Dedicated mutations replace the construction
with direct plaintext CFB, omit the XOR, and use a nonzero counter; each must
fail. Fixture literals must be derived from production constants where a
production constant exists.

## Test and implementation gate

Implementation may start only after an independent CR3 `GO` on this plan and
the matching French plan/provenance entries. The implementation review then
requires:

1. compile-fail tests proving the raw handshake-key exit is gone and all secret
   owners are non-`Clone`;
2. exact formatter, RFC 4648, rpcrypt, and end-to-end oracle vectors;
3. one-success per `SessionRpcrypt` across multiple handshake sessions,
   second-call, missing-write-before-`finish`, output-unchanged, zeroization,
   redaction, numeric boundary, invalid-combination, and capacity tests;
4. mutation tests that fail if direct plaintext CFB replaces zero-buffer
   CFB-plus-XOR, the XOR is omitted, encryption/base64 is reordered, the
   terminal NUL is omitted, counter is nonzero, or plaintext reaches output;
5. `cargo +1.85.0 fmt --all --check`, locked Clippy with warnings denied,
   locked workspace tests, Windows and Android core checks, audit, deny,
   duplicate-dependency review, fixture scan/manifest validation, and
   ProvenanceGuard;
6. `graphify update .` and an independent implementation re-review.

Hardware evidence is **NONE**. Console interoperability and GP520 performance
remain **UNKNOWN / NOT_TESTED**. N1, the complete third-party licence bundle,
remains mandatory before distributing any binary.
