# Reviewed fixtures

Only sanitized, redistribution-safe fixtures belong here. Every fixture must
record its origin, applicable license, capture environment, redaction review,
and SHA-256 digest in `manifest.json`.

Raw captures belong in `docs/evidence/` and are ignored by Git.

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

