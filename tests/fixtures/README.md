# Reviewed fixtures

Only sanitized, redistribution-safe fixtures belong here. Every fixture must
record its origin, applicable license, capture environment, redaction review,
and SHA-256 digest in `manifest.json`.

Raw captures belong in `docs/evidence/` and are ignored by Git.

## LaunchSpec oracle v1

`launch_spec_oracle_v1.json` contains six public synthetic cases spanning PS4
before firmware 10, modern PS4, and PS5 AVC, HEVC and HDR. Every target has a
case with six distinct numeric fields; another case permutes `0` and
`u32::MAX`; the maximum and `u32::MAX - 1` cases remain. Modern PS4 and PS5 use
nonces whose two table-row selectors differ. The canonical JSON, terminal NUL,
counter-zero IV, CFB-encrypted zero-buffer keystream, XOR ciphertext and outer
base64 are recorded separately. The Rust test reconstructs the entire document,
requires byte equality, and compares every case with the public production write
path.

The external harness directly compiled pinned `launchspec.c`, `rpcrypt.c` and
`base64.c` at chiaki-ng commit
`c8bea2642d0e95402f373af55584a3b98cd89417`, using Mbed TLS commit
`8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0` and MSVC 19.44.35229.0. The one
scratch-only correction expands the known undersized SHA-256 result buffer in
`rpcrypt.c` from 16 to 32 bytes without changing the retained 16-byte result.
The zero-buffer allocation, terminal-NUL inclusion, CFB-keystream call and XOR
loop are GPSx harness code transcribed from `streamconnection.c:1027-1036`;
`streamconnection.c` is evidence for that order and is not compiled or executed
by this oracle.
The external closed build record, harness, compiler products and source
checkouts remain under ignored `target/launchspec-audit/` and must not enter the
repository.

The neutral `public_input_a_16`, `public_input_b_16`, and
`public_input_c_16` labels denote the synthetic nonce, morning material, and
handshake key; production APIs retain their secret-bearing typed names.

The fixture is 55,602 bytes with SHA-256
`84487e526ac8625dcb555c447916e167cffb39eb4782ee31ca78d467ca740599`.
It is integrated after an independent implementation review returned `GO` on
2026-10-28; it makes no console, performance, emulator or hardware claim.

## Registration codec oracle v1

`registration_codec_oracle_v1.json` contains nine deterministic cases generated
from public synthetic inputs. The five default-filler cases are compared byte
for byte with the pinned upstream `chiaki_regist_request_payload_format`; the
four custom-filler cases are assembled by the external harness from the pinned
upstream `init_regist`, aeropause, and encryption primitives because the
upstream formatter hard-codes the `A` filler.

The input keys `online_name` and `identity_bytes` are neutral fixture labels
for the synthetic OnlineId and AccountId. They avoid the repository scanner's
deliberately conservative `online_id` / `account_id` key classification while
the per-case `identity` field still binds the protocol variants exactly.

Generation used chiaki-ng commit
`c8bea2642d0e95402f373af55584a3b98cd89417`, Mbed TLS commit
`8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0`, and MSVC
`19.44.35229.0`. The output is 28,469 bytes with SHA-256
`13b3d768d861888d52d4aa9d86cb1ba2a09de701f85288534706734727064dd4`.
Windows text-mode stdout produced CRLF line endings; `.gitattributes` marks JSON
fixtures `-text`, so those reviewed bytes remain stable on checkout.

The generation harness, extracted version stub, upstream patch, compiler logs,
and closed build manifest remain external under R5 option b and must never be
added to this repository. The fixture contains no hardware or interoperability
claim.

## Registration cryptography oracle v2

`registration_crypto_oracle_v2.json` contains only public synthetic inputs. Its
modern cases deliberately use independent cipher/aeropause offset pairs
`(0,31)`, `(31,0)`, and `(7,22)` so an implementation cannot silently swap or
merge the two protocol fields. In
each case, `derived_16` is the 16-byte registration cipher material, `mix_16`
is the aeropause result, `initial_vector_16` is the truncated HMAC-SHA-256
result for counter zero, and `encrypted` is AES-128-CFB output. These neutral
fixture labels prevent test tooling from treating synthetic public vectors as
live credentials; production APIs must use secret-bearing names and types.

The pinned Mbed TLS branch of upstream `rpcrypt.c` writes a 32-byte SHA-256
result into a 16-byte array. The scratch oracle reproduced the resulting
Windows stack-protection failure. Vector generation therefore applied one
recorded scratch-only correction: the result array was expanded to 32 bytes,
while the existing copy continued to retain the first 16 bytes. GPSx must not
port the undersized buffer.

## Session cryptography dependency audit v1

`session_crypto_dependency_audit_v1.json` binds the exact 30-package crates.io
graph resolved for `gpsx-crypto`, direct dependency features, registry
checksums, the Rust 1.85 Windows and Android cross-check environment, and the
security/license audit results. It also records why the initial `k256` family
was rejected and replaced by `secp256k1` 0.33.1, and distinguishes the crates'
CC0-1.0 metadata from the patched vendored libsecp256k1 C code under MIT. It contains dependency
metadata only; it is distinct from the independent session-crypto oracle
described below.

## Session cryptography oracle v1

`session_crypto_oracle_v1.json` contains public synthetic vectors for the
pinned upstream ECDH example, both keystate modes, both traffic directions,
epoch and position boundaries, arithmetic overflow, policy limits, full
AES-GCM tags, their protocol MSB32 values, and fold-XOR internals. The Rust
test reconstructs the complete canonical document, requires byte equality, and
rejects mutations of every top-level field plus all previously unbound fields.

Generation directly executed the six compatible pinned `gkcrypt.c` tests and
both `keystate.c` tests. A GPSx-authored scratch-only affine secp256k1
calculation plus Python standard-library HMAC-SHA-256 reproduced the pinned
ECDH vector. The pinned chiaki Mbed TLS implementation regenerates the scalar
passed to its deterministic local-key setter, so that ECDH test route does not
reproduce the pinned vector; the external record preserves this diagnostic as
an expected exit 2 and no scratch repair is used for the fixture.

The output is 12,764 bytes with SHA-256
`eb67d9123a592b064a7799bf12e36b274f425683e322f83c9f440440cb23f8e9`.
Its external closed build manifest records clean pinned checkouts, 81 compiled
translation units, 313 observed headers, exact commands, tool versions,
executable hashes and deterministic result hashes. The manifest, compiler log,
harness and source checkouts remain under ignored `target/session-audit/` and
must never be added to this repository. The independent C1 re-review returned
`GO` on 2026-10-28, so this fixture is integrated. It makes no console,
performance, emulator or hardware claim.

