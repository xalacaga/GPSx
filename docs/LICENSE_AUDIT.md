# License Audit

Audit date: 2026-10-25. This is an engineering compliance analysis, not legal advice.

## Project status

**FACT:** the owner accepted Option C on 2026-10-25. GPSx is AGPL-3.0-only with the Chiaki-family OpenSSL additional permission, and its future Rust protocol port is a declared derivative. No upstream production code has yet been copied into this repository. ADR-0001 is the source of truth.

## Upstream summary

| Project/component | Observed license | Reuse status |
|---|---|---|
| Chiaki | AGPL-3.0 lineage with OpenSSL exception | Blocked pending product-license decision |
| chiaki-ng | AGPL-3.0; core files use `LicenseRef-AGPL-3.0-only-OpenSSL` | Blocked pending decision |
| Pylux | AGPL-3.0 with the same OpenSSL exception | Blocked pending decision |
| Linux `hid-playstation.c` | GPL-2.0-or-later | Behavioral reference only; do not copy without review |
| Android APIs/AOSP artifacts | License varies; commonly Apache-2.0 for relevant source | API use allowed; copied code still needs review |
| Graphify | Apache-2.0 per project documentation | Tooling only; no runtime dependency |
| `libfuzzer-sys` 0.4.13 | `(MIT OR Apache-2.0) AND NCSA` | Test-only standalone fuzz crate; NCSA is allowed only as a crate-specific `cargo-deny` exception |

chiaki-ng and Pylux include AGPL v3 plus an additional OpenSSL linking permission and corresponding-source language. [chiaki-ng COPYING](https://github.com/chiaki-ng/chiaki-ng/blob/main/COPYING), [Pylux COPYING](https://github.com/ForWard-Technologies-LLC/Pylux/blob/master/COPYING)

## Option A — reuse or wrap the Chiaki family

Assumption: the distributed application and corresponding source can comply with AGPL-3.0 and all notice/source obligations.

- A C ABI/FFI wrapper does not itself avoid copyleft obligations.
- Exact upstream revision, patches, build scripts, notices, and corresponding source must be retained.
- This route has the lowest protocol-correctness and schedule risk.

## Option B — Rust reimplementation

A mechanical translation, line-by-line rewrite, or adaptation from AGPL source remains a derivative-work risk. Renaming types or changing language does not establish independence.

A defensible clean-room route would require:

1. a behavior/protocol specification produced without copying implementation expression;
2. a separate implementation process based on that specification, lawful observations, and independently licensed sources;
3. provenance for every constant, packet field, algorithm, and fixture;
4. avoidance of upstream-specific names, comments, structure, and non-obvious implementation choices unless independently derived;
5. legal review for reverse-engineering and interoperability rules in target jurisdictions.

This route is substantially slower and riskier and cannot be justified merely by a preference for Rust.

## Option C — GPSx licensed AGPL-3.0, Rust port declared as derivative

Added 2026-10-25 after independent review; the original audit omitted this option.

GPSx itself is distributed under AGPL-3.0-only with the same OpenSSL additional permission. Upstream protocol logic is ported to Rust as an openly declared derivative work, with copyright notices, attribution, and per-file provenance.

- **FACT:** chiaki-ng files carry `LicenseRef-AGPL-3.0-only-OpenSSL`. GPSx therefore cannot use "or-later" for combined work.
- Fits the Rust preference without clean-room cost, and keeps upstream behavior as a direct reference.
- Obligations are the same as Option A: corresponding source for every distributed APK and Windows build, notices, build scripts, and source offers.
- The OpenSSL permission matters only if OpenSSL is linked. A Rust stack using RustCrypto/rustls needs no exception, but keeping it preserves the option of linking upstream C.
- This differs from Option A in engineering terms only. Option A keeps the C core behind FFI, which means a dual threading model and `unsafe` FFI. Option C has a single Rust ownership model but carries translation-correctness risk, which upstream fixtures mitigate.

## Choosing between options

**FACT (owner statement, 2026-10-25):** GPSx is first for the owner's personal use on their own hardware. Later, if it works well, it may be published for free on GitHub.

Consequences (engineering reading, not legal advice):

- **Personal phase:** AGPL-3.0 obligations are triggered by conveying copies to others. Private modification and use on the owner's own devices with no distribution trigger no source-offer obligation. §13 (remote network interaction) concerns users interacting with a modified program over a network, not a private client.
- **Publication phase:** a free, public GitHub release under AGPL-3.0-only (with the OpenSSL permission) is fully compatible with the Chiaki family, provided notices, attribution, provenance, and corresponding source are present. Public source satisfies the source obligation for binaries published alongside it.
- **Option B (clean-room) is therefore not justified:** its cost exists only to escape AGPL, which this distribution model does not need.
- **Recommendation:** Option C (GPSx AGPL-3.0-only with a declared-derivative Rust port), which matches the Rust preference. Option A remains valid if schedule matters more than Rust.
- Keep the provenance ledger from the first commit, so publication later needs no retroactive audit. Add the `LICENSE` file and notices before the first public push.
- Trademark and PSN items below become relevant at publication time.

**Accepted:** the owner selected Option C on 2026-10-25. The decision and pinned upstream baseline are recorded only in `decisions/0001-protocol-license-strategy.md` (ADR-0001), the single source of truth. The protocol, transport and session layers will therefore be Rust implementations with explicit derivative provenance.

## Non-copyright considerations

Engineering flags only, not legal advice; not yet assessed:

- Sony trademarks (PlayStation, PS5, DualSense, Remote Play) in the app name, icon, store listing, and UI. Use descriptive compatibility wording only, pending review.
- PlayStation Network terms of service, and the OAuth flow used to obtain the PSN account ID for registration. That flow handles secret-bearing redirect URLs and tokens (see `AGENTS.md`).
- Store distribution policy for an unofficial Remote Play client.

## Compatible third-party candidates (not yet adopted)

| Component | License | AGPL-3.0 compatibility |
|---|---|---|
| Oboe | Apache-2.0 | Compatible with GPLv3/AGPLv3 (FACT, FSF compatibility list) |
| libopus | BSD-3-Clause | Compatible |
| RustCrypto crates | MIT/Apache-2.0 | Compatible |
| rust-secp256k1 crates / vendored libsecp256k1 | Crate metadata: CC0-1.0; vendored and patched C library: MIT, Copyright (c) 2013 Pieter Wuille | Permissive; the MIT notice is now present in `NOTICE`, while the complete N1 third-party license bundle/SBOM remains required before binary distribution |

Each one still needs an entry in the provenance ledger and the SBOM before adoption.

## Dependency considerations

The chiaki-ng desktop manifest includes SDL2, protobuf, OpenSSL, json-c, libevent, miniupnpc, Opus, lcms, SpeexDSP, and Vulkan. Each pinned transitive dependency needs its own license record if reused. [chiaki-ng vcpkg manifest](https://github.com/chiaki-ng/chiaki-ng/blob/main/vcpkg.json)

The eventual Rust/Kotlin stack must produce an SBOM and license report in CI. Vetted crypto libraries are mandatory; protocol-specific composition still requires security review.

## Provenance ledger

For every imported or adapted artifact record:

- source repository and immutable revision;
- original path and copyright notice;
- SPDX identifier and license text;
- copy, patch, wrapper, generated artifact, or clean-room status;
- modifications and local owner;
- binary notice/source obligations;
- reviewer and approval date.

## Phase 0 decision

Option C is accepted. No Chiaki-family source may nevertheless be copied, translated or adapted until the CR3 review package verifies notices, the file-level provenance process, parser bounds, cryptography and fixture handling.

