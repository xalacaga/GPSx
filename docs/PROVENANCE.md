# Provenance ledger

Status: mandatory compliance record. Updated 2026-10-25.

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
| Current use | Behavioral Rust port of the bounded SRCH discovery slice listed below |
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
| `crates/gpsx-protocol/src/lib.rs` | N/A - GPSx-authored | `c8bea2642d0e95402f373af55584a3b98cd89417` | GPSx contributors | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Public surface restricted to pure SRCH formatting and parsing |
| `crates/gpsx-protocol/src/discovery.rs` | `lib/include/chiaki/discovery.h`; `lib/src/discovery.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | chiaki-ng contributors; pinned Git history | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Behavioral Rust port of SRCH formatting and response mapping; strict bounded parsing, redacted formatting, no I/O or WAKEUP |
| `crates/gpsx-protocol/src/http.rs` | `lib/include/chiaki/http.h`; `lib/src/http.c` | `c8bea2642d0e95402f373af55584a3b98cd89417` | chiaki-ng contributors; pinned Git history | `LicenseRef-AGPL-3.0-only-OpenSSL` | GPSx / 2026-10-25 | Bounded HTTP/1.1 response parser with ASCII names, controlled UTF-8 values, classified errors and deterministic property tests |

## CR3 planned-file entries (not yet implemented)

The CR3 GO of 2026-10-25 authorized exactly the four rows now recorded above.
No additional protocol file is planned or authorized in this slice.

| Planned local path | Exact upstream path(s) | Commit | Original copyright | Planned SPDX | Owner/date | Planned work |
|---|---|---|---|---|---|---|
