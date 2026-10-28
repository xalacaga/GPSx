# Cryptography dependency register

Status: the registration subset was authorized on 2026-10-26. The session plan
received `GO` and short-tag owner acceptance on 2026-10-28. The implementation
graph now passes the locked Windows/Android, RustSec and license gates. The
security-driven switch from `k256` to `rust-secp256k1` received independent
implementation `GO`; the closed external oracle and its canonical binding also
received independent `GO`. The session dependency graph is integrated.

The registration-only direct subset proposed on 2026-10-26 is `aes`,
`cfb-mode`, `hmac`, `sha2`, and `zeroize`. It was authorized by the independent
remediation review of `REGISTRATION_CRYPTO_PLAN.md`. `subtle` is
present transitively through `digest/mac`; `secrecy`, `getrandom`,
AES-GCM/GHASH, and secp256k1 ECDH are not part of this narrow slice.

| Crate | Resolution | License | MSRV evidence | Intended use | Advisory status |
|---|---:|---|---:|---|---|
| [`aes`](https://crates.io/crates/aes/0.8.4) | 0.8.4 | MIT OR Apache-2.0 | 1.56; Rust 1.85 Windows/Android PASS | AES block primitive, `zeroize` feature | PASS in audited scratch graph |
| [`aes-gcm`](https://crates.io/crates/aes-gcm/0.10.3) | 0.10.3 with `aes`, `zeroize`; defaults off; full 16-byte tag | MIT OR Apache-2.0 | 1.56 metadata; Rust 1.85 Windows/Android PASS | GMAC with 16-byte IV; constant-time verification of `full_tag[..4]`; no `hazmat` | PASS in integrated, independently reviewed graph |
| [`cfb-mode`](https://crates.io/crates/cfb-mode/0.8.2) | 0.8.2 | MIT OR Apache-2.0 | 1.56; Rust 1.85 Windows/Android PASS | CFB composition, `zeroize` feature | PASS in audited scratch graph |
| [`hmac`](https://crates.io/crates/hmac/0.12.1) | 0.12.1 | MIT OR Apache-2.0 | Not declared; Rust 1.85 Windows/Android PASS | HMAC construction | PASS in audited scratch graph |
| [`sha2`](https://crates.io/crates/sha2/0.10.9) | 0.10.9 | MIT OR Apache-2.0 | Not declared; Rust 1.85 Windows/Android PASS | SHA-256 | PASS in audited scratch graph |
| [`subtle`](https://crates.io/crates/subtle/2.6.1) | 2.6.1, defaults off | BSD-3-Clause | Not declared; Rust 1.85 Windows/Android PASS | Direct constant-time 4-byte tag comparison | PASS in implementation graph |
| [`zeroize`](https://crates.io/crates/zeroize/1.9.1) | 1.9.1 | Apache-2.0 OR MIT | 1.85; Rust 1.85 Windows/Android PASS | Secret erasure | PASS in audited scratch graph |
| [`secrecy`](https://crates.io/crates/secrecy/0.10.3) | 0.10.3 | Apache-2.0 OR MIT | 1.60 | Secret wrapper | NOT_TESTED until locked |
| [`getrandom`](https://crates.io/crates/getrandom/0.4.3) | 0.4.3 | MIT OR Apache-2.0 | 1.85 | Platform entropy | NOT_TESTED until locked |
| [`ctr`](https://crates.io/crates/ctr/0.9.2) | 0.9.2 with `zeroize`; defaults off; `Ctr128LE<Aes128>` | MIT OR Apache-2.0 | 1.56 metadata; Rust 1.85 Windows/Android PASS | Maintained little-endian AES-CTR over existing `aes` 0.8.4 | PASS in integrated, independently reviewed graph |
| [`secp256k1`](https://crates.io/crates/secp256k1/0.33.1) | 0.33.1 with `alloc`; defaults off; `secp256k1-sys` 0.14.1 | Crate metadata CC0-1.0; vendored patched libsecp256k1 C is MIT | 1.63 metadata; Rust 1.85 Windows/Android PASS | SEC1 validation and raw-point secp256k1 ECDH | PASS in audit and independent CR3 implementation review |
| [`k256`](https://crates.io/crates/k256/0.13.4) | rejected | MIT OR Apache-2.0 | 1.65 | Original secp256k1 candidate | REJECTED: `RUSTSEC-2026-0387`; corrected 0.14 chain requires Rust 1.87 |

The current latest `aes` 0.9.3 requires Rust 1.89 and `sha2` 0.11.1 requires
Rust 1.88. `k256` 0.14.0 declares Rust 1.85, but its non-yanked corrected
`crypto-bigint` chain requires Rust 1.87. GPSx therefore keeps the AES/digest
generations and the workspace MSRV unchanged and uses the separately reviewed
secp256k1 family above. “MSRV not declared” is an unresolved compatibility
check, not an assumption of compatibility.

The locked session graph is the final integrated dependency decision. Its exact
resolution is recorded in
`tests/fixtures/session_crypto_dependency_audit_v1.json`. Security, provenance,
build, implementation and closed-oracle integration gates all pass.

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

### Session implementation graph — 2026-10-28

**FACT:** Cargo 1.85 resolves 30 registry packages for `gpsx-crypto`. The exact
names, versions, crates.io checksums and direct features are bound by
`tests/fixtures/session_crypto_dependency_audit_v1.json` and checked from
`tools/powershell/check.ps1`. There are no duplicate `aes`, `cipher`, or
`digest` generations.

| Command | Result |
|---|---|
| `cargo +1.85.0 check -p gpsx-crypto --locked --all-targets` | PASS, Windows x64 |
| `cargo +1.85.0 check -p gpsx-crypto --locked --target aarch64-linux-android --all-targets` | PASS, NDK 28.2.13676358 |
| `cargo +1.85.0 clippy -p gpsx-crypto --all-targets --locked -- -D warnings` | PASS |
| `cargo-audit 0.22.2 audit --file Cargo.lock` | PASS, advisory DB `40f5be6fb12ed93fc591bf4ab23b7d793b3f2239` |
| `cargo-deny 0.20.2 check advisories licenses bans sources` | PASS; crate-metadata CC0 exceptions are limited to `secp256k1` and `secp256k1-sys`; vendored libsecp256k1 MIT is tracked separately |
| `cargo tree -p gpsx-crypto -d` | PASS, no duplicates |

**FACT:** the first resolution with `k256` 0.13.4 failed cargo-audit on
`RUSTSEC-2026-0387`. No advisory exception was added. The replacement passes
the automated gates, but its C/FFI ownership, build portability, CC0 crate and
vendored MIT notices, and documented best-effort secret erasure are explicit
CR3 review subjects.

The implementation-review gate requires:

1. CR3 must accept the algorithms and crate families.
2. The audited exact versions, features and checksums must be reproduced in the repository `Cargo.lock`.
3. `cargo audit` must still return no unaccepted vulnerability, unsoundness or unmaintained-package advisory for the repository graph.
4. `cargo deny check advisories licenses bans sources` must still pass with the reviewed policy.
5. Android and Windows cross-builds plus public standards known-answer tests must pass.
6. Any exception requires a dated owner, rationale, expiry and independent re-review.

No crate in this table authorizes custom cryptographic primitives or protocol implementation.
