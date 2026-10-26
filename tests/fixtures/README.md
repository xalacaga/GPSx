# Reviewed fixtures

Only sanitized, redistribution-safe fixtures belong here. Every fixture must
record its origin, applicable license, capture environment, redaction review,
and SHA-256 digest in `manifest.json`.

Raw captures belong in `docs/evidence/` and are ignored by Git.

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

