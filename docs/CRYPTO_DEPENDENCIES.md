# Cryptography dependency register

Status: the registration-only subset was authorized and added to `Cargo.lock`
on 2026-10-26; all session-cryptography candidates remain blocked.

The registration-only direct subset proposed on 2026-10-26 is `aes`,
`cfb-mode`, `hmac`, `sha2`, and `zeroize`. It was authorized by the independent
remediation review of `REGISTRATION_CRYPTO_PLAN.md`. `subtle` is
present transitively through `digest/mac`; `secrecy`, `getrandom`,
AES-GCM/GHASH, and secp256k1 ECDH are not part of this narrow slice.

| Crate | Candidate | License | MSRV evidence | Intended use | Advisory status |
|---|---:|---|---:|---|---|
| [`aes`](https://crates.io/crates/aes/0.8.4) | 0.8.4 | MIT OR Apache-2.0 | 1.56; Rust 1.85 Windows/Android PASS | AES block primitive, `zeroize` feature | PASS in audited scratch graph |
| AES-GCM/GHASH family | Unresolved | Unresolved | Unresolved | Future authenticated encryption/GHASH required by later protocol evidence | BLOCKED pending algorithm and crate review |
| [`cfb-mode`](https://crates.io/crates/cfb-mode/0.8.2) | 0.8.2 | MIT OR Apache-2.0 | 1.56; Rust 1.85 Windows/Android PASS | CFB composition, `zeroize` feature | PASS in audited scratch graph |
| [`hmac`](https://crates.io/crates/hmac/0.12.1) | 0.12.1 | MIT OR Apache-2.0 | Not declared; Rust 1.85 Windows/Android PASS | HMAC construction | PASS in audited scratch graph |
| [`sha2`](https://crates.io/crates/sha2/0.10.9) | 0.10.9 | MIT OR Apache-2.0 | Not declared; Rust 1.85 Windows/Android PASS | SHA-256 | PASS in audited scratch graph |
| [`subtle`](https://crates.io/crates/subtle/2.6.1) | 2.6.1 | BSD-3-Clause | Not declared; Rust 1.85 Windows/Android PASS | Transitive `digest/mac` constant-time support | PASS in audited scratch graph |
| [`zeroize`](https://crates.io/crates/zeroize/1.9.1) | 1.9.1 | Apache-2.0 OR MIT | 1.85; Rust 1.85 Windows/Android PASS | Secret erasure | PASS in audited scratch graph |
| [`secrecy`](https://crates.io/crates/secrecy/0.10.3) | 0.10.3 | Apache-2.0 OR MIT | 1.60 | Secret wrapper | NOT_TESTED until locked |
| [`getrandom`](https://crates.io/crates/getrandom/0.4.3) | 0.4.3 | MIT OR Apache-2.0 | 1.85 | Platform entropy | NOT_TESTED until locked |
| secp256k1 ECDH family | Unresolved | Unresolved | Unresolved | Future registration key agreement required by later protocol evidence | BLOCKED pending algorithm and crate review |

The current latest `aes` 0.9.3 requires Rust 1.89 and `sha2` 0.11.1 requires Rust 1.88, so they are not candidates while GPSx declares Rust 1.85. “MSRV not declared” is an unresolved compatibility check, not an assumption of compatibility.

The AES-GCM/GHASH and secp256k1 ECDH rows close the inventory gap identified by
CR3, but deliberately select no implementation. They must be resolved before
authenticated session cryptography is proposed; they are outside the isolated
registration-only slice.

## Security and acceptance gate

### Registration-only resolved graph — 2026-10-26

**FACT:** Cargo 1.85 resolved 17 registry packages. The direct features are
`aes/zeroize` and `cfb-mode/zeroize`; all five direct dependencies disable
default features. The full package/version/checksum list is stored in
`tests/fixtures/registration_crypto_dependency_audit_v1.json`; the scratch
lockfile SHA-256 is
`d312587e656ced976d4eb20c24178a4101640f548049328b5d8ce063c161d72f`.

| Command | Result |
|---|---|
| `cargo +1.85.0 check --locked --all-targets` | PASS, Windows x64 |
| `cargo +1.85.0 check --locked --target aarch64-linux-android --all-targets` | PASS |
| `cargo-audit 0.22.2 audit --file Cargo.lock` | PASS, advisory DB `a78282a1414d6c28cc228c4eae3570451affb0dc` |
| `cargo-deny 0.20.2 check advisories licenses bans sources` | PASS with MIT, Apache-2.0, BSD-3-Clause and the scratch package’s AGPL-3.0-only policy |

The repository implementation must resolve the identical dependency subset and
features. A checksum, source, version, or feature difference invalidates this
evidence and requires another audit.

**FACT (2026-10-26):** the repository `Cargo.lock` resolves that identical
17-package subset and the automated GPSx gate compares every package name,
version, source and checksum with the fixture. `cargo-audit` passed on the full
repository lockfile, and cargo-deny passed advisories, licenses, bans and
sources with `gpsx-crypto` as the graph root. The unrelated pre-existing local
path dependency in `gpsx-transport` is outside that crypto-rooted deny graph.

The implementation-review gate requires:

1. CR3 must accept the algorithms and crate families.
2. The audited exact versions, features and checksums must be reproduced in the repository `Cargo.lock`.
3. `cargo audit` must still return no unaccepted vulnerability, unsoundness or unmaintained-package advisory for the repository graph.
4. `cargo deny check advisories licenses bans sources` must still pass with the reviewed policy.
5. Android and Windows cross-builds plus public standards known-answer tests must pass.
6. Any exception requires a dated owner, rationale, expiry and independent re-review.

No crate in this table authorizes custom cryptographic primitives or protocol implementation.
