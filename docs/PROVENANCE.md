# Provenance ledger

Status: mandatory compliance record. Updated 2026-10-28.

This ledger is the source of truth for every external artifact copied, adapted,
translated, generated or used as a protocol fixture. `NOTICE` contains the
notices shipped with the product. The accepted strategy is ADR-0001 Option C.

## Rules

- Every derived Rust file starts with
  `SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL`.
- Before a derived file enters the repository, this ledger records its local
  path, upstream repository, immutable commit, original path and copyright,
  SPDX identifier, adaptation owner/date and a modification summary.
- A GPSx-authored protocol file uses the exact upstream field
  `N/A - GPSx-authored`; an adapted file lists only paths present in the pinned
  upstream inventory under `tools/provenance/`.
- Fixtures additionally record whether redistribution is permitted and contain
  no credentials, account identifiers, session secrets or raw private captures.
- A baseline change amends ADR-0001. An upstream reference does not authorize copying.
- `tools/powershell/check.ps1` parses this table structurally. It requires a complete
  seven-field row at the pinned commit for every file in an authorized protocol
  crate, rejects undeclared crates, and requires the derived-work SPDX header on Rust files.

## Primary derivative baseline

| Field | Value |
|---|---|
| Project | chiaki-ng |
| Repository | `https://github.com/chiaki-ng/chiaki-ng` |
| Immutable commit | `c8bea2642d0e95402f373af55584a3b98cd89417` |
| License | `LicenseRef-AGPL-3.0-only-OpenSSL` |
| License source | `COPYING` at the pinned commit |
| Current use | Behavioral Rust ports of the bounded SRCH discovery and registration-only cryptography slices listed below |
| Approved by/date | Project owner, 2026-10-25 |

## Reference-only projects

| Project | Use | Copy status |
|---|---|---|
| Chiaki | Historical architecture and regression archaeology | No copied source |
| Pylux | Android lifecycle and product behavior reference | No copied source |
| Linux `hid-playstation.c` | DualSense behavior reference under GPL-2.0-or-later | No copied source |
| Android/AOSP documentation | Platform API behavior | No copied source |

## Derived-file entries

| Local path | Upstream path | Commit | Original copyright | SPDX | Owner/date | Modifications |
|---|---|---|---|---|---|---|
| `crates/gpsx-protocol/Cargo.toml` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | GPSx contributors | `AGPL-3.0-only` | GPSx / 2026-10-25 | Dependency-free package manifest; no build script |
| `crates/gpsx-protocol/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | GPSx contributors | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Public surface for pure bounded SRCH and local-registration formatting/parsing; no I/O or cryptography |
| `crates/gpsx-protocol/src/discovery.rs` | `lib/include/chiaki/discovery.h`; `lib/src/discovery.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | chiaki-ng contributors; pinned Git history | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Behavioral Rust port of SRCH formatting and response mapping; strict bounded parsing, redacted formatting, no I/O or WAKEUP |
| `crates/gpsx-protocol/src/http.rs` | `lib/include/chiaki/http.h`; `lib/src/http.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | chiaki-ng contributors; pinned Git history | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Bounded HTTP/1.1 response parser with ASCII names, controlled UTF-8 values, classified errors and deterministic property tests |
| `crates/gpsx-protocol/src/registration.rs` | `lib/include/chiaki/regist.h`; `lib/src/regist.c`; `lib/src/session.c`; `test/regist.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | chiaki-ng contributors; pinned Git history | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Behavioral Rust port of the reviewed local-registration request constants/layout and response-field mapping; GPSx-authored RFC 4648 base64 and strict bounded framing, borrowed identity/secret views, fixed caller buffers, no I/O, transport, crypto, remote PSN, RUDP or hole punching |
| `crates/gpsx-transport/Cargo.toml` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | GPSx contributors | `AGPL-3.0-only` | GPSx / 2026-10-26 | Package manifest for the platform transport adapter; depends only on gpsx-protocol |
| `crates/gpsx-transport/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | GPSx contributors | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | GPSx-authored bounded IPv4 UDP adapter around the reviewed pure SRCH API; loopback-tested, no copied transport implementation |
| `crates/gpsx-transport/src/bin/gpsx-discover.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | GPSx contributors | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Opt-in French Windows discovery probe; redacted summaries only, no wake-up, credentials or session setup |
| `crates/gpsx-crypto/Cargo.toml` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | GPSx contributors | `AGPL-3.0-only` | GPSx / 2026-10-26 | Exact reviewed registration-only primitive dependencies; no I/O, platform dependency or build script |
| `crates/gpsx-crypto/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | GPSx contributors | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Minimal redacted public surface for registration cryptography only |
| `crates/gpsx-crypto/src/registration.rs` | `lib/include/chiaki/rpcrypt.h`; `lib/src/rpcrypt.c`; `test/rpcrypt.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | chiaki-ng contributors; pinned Git history | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-26 | Behavioral Rust port of PS4/PS5 registration key schedules, aeropause, fixed-counter IV and AES-CFB behavior; independently typed table offsets, redacted zeroizing owners, no session or I/O |

## CR3 planned-file entries

The codec implementation and its four upstream paths were authorized and now
appear above and in the inventory. R5 authorized external oracle generation;
the integration review returned `GO WITH CHANGES`, its B1-B4 remediation then
passed the required independent review on 2026-10-28, and integration is `GO`.

| Proposed local artifact | Proposed upstream paths or standard | Commit / license | Planned scope | Explicit exclusions | Gate |
|---|---|---|---|---|---|
| `tests/fixtures/registration_codec_oracle_v1.json` and its `tests/fixtures/manifest.json` entry | Same four pinned paths plus already approved `lib/include/chiaki/rpcrypt.h`, `lib/src/rpcrypt.c`, and `test/rpcrypt.c`; `lib/src/base64.c` as oracle-build input only | same pinned commit and license | Scratch C-oracle output for four RP target profiles, both PS4_9 identity variants, modern filler/offset cases, exact headers and separately bound intermediate fields; synthetic public inputs only | No response capture, live account, live key, LAN data, emulator data, hardware claim, or adaptation from `base64.c` | R5 generation and integration `GO`; B1-B4 independently verified on 2026-10-28. Harness, stub, patch, logs, and closed build manifest remain external under option b |

The oracle generation record is a closed build manifest, not a narrative summary. The generation checkout, harness, and GPSx-authored stubs remain in a temporary scratch directory outside the repository; only their hashes are retained. The record captures `git rev-parse HEAD` and `git status --porcelain` from that external checkout. For every modified upstream file it records the exact diff, SHA-256, and justification; every translation unit differing from the pinned commit is marked `patched` in the input list. `/showIncludes` and the exact link command generated a closed list of 76 Mbed TLS and 5 oracle translation units, 313 observed headers, and 12 explicitly linked libraries, each with a stable label, role, byte count, and SHA-256. `regist.c` and `rpcrypt.c` are marked patched; `session_version.c` is marked as the exact extracted stub from pinned `session.c:58-73`. The byte-exact patch has no BOM and passes `git apply --check -R`. The record identifies Mbed TLS `8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0`, MSVC `19.44.35229.0`, complete flags, link command, and harness/output hashes. `lib/src/base64.c` is compiled only for independent C-oracle output; production base64 is GPSx-authored from RFC 4648. ProvenanceGuard validates repository ledger/source policy but not this external manifest; the required independent review verified B1-B4 on 2026-10-28.

Firmware-to-target evidence from `gui/src/qmlbackend.cpp` and `android/app/src/main/java/com/metallic/chiaki/regist/RegistActivity.kt` is reference-only. Neither path is a port source or proposed upstream-inventory entry.

## Reviewed fixture entries

| Local fixture | Origin | Commit or resolution | License | Generation | Environment/review | Redistribution |
|---|---|---|---|---|---|---|
| `tests/fixtures/registration_codec_oracle_v1.json` | `lib/src/regist.c`; `lib/src/rpcrypt.c`; `lib/src/base64.c`; exact `chiaki_rp_version_string` extraction from `lib/src/session.c:58-73` | chiaki-ng `c8bea2642d0e95402f373af55584a3b98cd89417`; Mbed TLS `8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0` | `LicenseRef-AGPL-3.0-only-OpenSSL` | Five default-filler cases compared byte-for-byte with the pinned upstream formatter; four custom-filler cases assembled from pinned upstream init, aeropause, and encryption primitives. Output: 28,469 bytes, SHA-256 `13b3d768d861888d52d4aa9d86cb1ba2a09de701f85288534706734727064dd4`, CRLF preserved by `-text`. | Windows x64, MSVC `19.44.35229.0`; synthetic-only input review and secret scan on 2026-10-28; closed build manifest remains external | Permitted under project Option C; fixture only. Harness, stub, patch, logs, and closed build manifest are forbidden from the repository by R5 option b |
| `tests/fixtures/registration_crypto_oracle_v2.json` | `lib/include/chiaki/rpcrypt.h`; `lib/src/rpcrypt.c`; `test/rpcrypt.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | `LicenseRef-AGPL-3.0-only-OpenSSL` | Built the pinned `rpcrypt.c` with Mbed TLS `8b3f26a5ac38d4fdccbc5c5366229f3e01dafcc0`, then ran a scratch harness over public synthetic inputs for PS4 pre-10 plus PS4 10+ and PS5 with independent cipher/aeropause offset pairs `(0,31)`, `(31,0)` and `(7,22)`. The pinned Mbed TLS branch crashed because its 16-byte result array received a 32-byte SHA-256 HMAC; generation used the recorded one-line scratch repair `hmac[16]` → `hmac[32]` and retained the upstream 16-byte truncation. | Windows x64, MSVC 19.44, manual review and secret scan on 2026-10-26 | Permitted under project Option C; no private capture or live value |
| `tests/fixtures/registration_crypto_dependency_audit_v1.json` | GPSx-authored dependency resolution and repository re-audit | Scratch lock SHA-256 `d312587e656ced976d4eb20c24178a4101640f548049328b5d8ce063c161d72f` | `AGPL-3.0-only` | Exact versions/features resolved with Cargo 1.85; checked on Windows and `aarch64-linux-android`; repository lock re-audited structurally by package name, version, and crates.io checksum with cargo-audit 0.22.2 and crypto-rooted cargo-deny 0.20.2. | Advisory database `a78282a1414d6c28cc228c4eae3570451affb0dc`; manual review and secret scan on 2026-10-26 | Permitted; factual dependency metadata only |
