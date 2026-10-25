# Planned cryptography dependency register

Status: candidates only; no dependency below is present in `Cargo.lock` or authorized for implementation. Reviewed 2026-10-25 for CR3.

| Crate | Candidate | License | MSRV evidence | Intended use | Advisory status |
|---|---:|---|---:|---|---|
| [`aes`](https://crates.io/crates/aes/0.8.4) | 0.8.4 | MIT OR Apache-2.0 | 1.56 | AES block primitive | NOT_TESTED until locked |
| AES-GCM/GHASH family | Unresolved | Unresolved | Unresolved | Future authenticated encryption/GHASH required by later protocol evidence | BLOCKED pending algorithm and crate review |
| [`cfb-mode`](https://crates.io/crates/cfb-mode/0.8.2) | 0.8.2 | MIT OR Apache-2.0 | 1.56 | CFB composition | NOT_TESTED until locked |
| [`hmac`](https://crates.io/crates/hmac/0.12.1) | 0.12.1 | MIT OR Apache-2.0 | Not declared | HMAC construction | NOT_TESTED until locked |
| [`sha2`](https://crates.io/crates/sha2/0.10.9) | 0.10.9 | MIT OR Apache-2.0 | Not declared | SHA-256 | NOT_TESTED until locked |
| [`subtle`](https://crates.io/crates/subtle/2.6.1) | 2.6.1 | BSD-3-Clause | Not declared | Constant-time comparison | NOT_TESTED until locked |
| [`zeroize`](https://crates.io/crates/zeroize/1.9.1) | 1.9.1 | Apache-2.0 OR MIT | 1.85 | Secret erasure | NOT_TESTED until locked |
| [`secrecy`](https://crates.io/crates/secrecy/0.10.3) | 0.10.3 | Apache-2.0 OR MIT | 1.60 | Secret wrapper | NOT_TESTED until locked |
| [`getrandom`](https://crates.io/crates/getrandom/0.4.3) | 0.4.3 | MIT OR Apache-2.0 | 1.85 | Platform entropy | NOT_TESTED until locked |
| secp256k1 ECDH family | Unresolved | Unresolved | Unresolved | Future registration key agreement required by later protocol evidence | BLOCKED pending algorithm and crate review |

The current latest `aes` 0.9.3 requires Rust 1.89 and `sha2` 0.11.1 requires Rust 1.88, so they are not candidates while GPSx declares Rust 1.85. “MSRV not declared” is an unresolved compatibility check, not an assumption of compatibility.

The AES-GCM/GHASH and secp256k1 ECDH rows close the inventory gap identified by
CR3, but deliberately select no implementation. They must be resolved before
any `gpsx-crypto` or registration work can be proposed.

## Security and acceptance gate

The [RustSec database](https://rustsec.org/advisories/) was consulted on 2026-10-25, but search results are not a substitute for auditing the resolved dependency graph. Advisory status therefore remains `NOT_TESTED`.

Before `gpsx-crypto` can be created:

1. CR3 must accept the algorithms and crate families.
2. Exact versions, features and checksums must be resolved into `Cargo.lock`.
3. `cargo audit` must return no unaccepted vulnerability, unsoundness or unmaintained-package advisory for the complete transitive graph.
4. `cargo deny check advisories licenses bans sources` must pass with an reviewed policy.
5. Android and Windows cross-builds plus public standards known-answer tests must pass.
6. Any exception requires a dated owner, rationale, expiry and independent re-review.

No crate in this table authorizes custom cryptographic primitives or protocol implementation.
