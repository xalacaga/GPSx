# ADR-0001: Protocol implementation and license strategy

- Status: **ACCEPTED — OPTION C**
- Date opened: 2026-10-25
- Accepted by: project owner, 2026-10-25
- Blocks: protocol, transport, registration, authentication, media framing

## Context

The useful Chiaki-family implementations inspected during phase 0 are AGPL-3.0
with an OpenSSL exception. Translating, wrapping, or mechanically rewriting that
implementation does not establish independence.

## Option A — AGPL-compatible reuse

Reuse or adapt an immutable upstream revision and distribute GPSx in a manner
that satisfies the applicable AGPL obligations and exception terms.

## Option B — separated clean-room implementation

Use separate roles and an auditable specification/evidence boundary. The
implementation side receives only approved behavioral specifications and
redistribution-safe fixtures, not upstream implementation details.

## Option C — declared-derivative AGPL Rust port

License GPSx under AGPL-3.0, explicitly treat the Rust protocol port as a
derivative of the selected upstream implementation, preserve notices and source
availability, and document the OpenSSL-exception treatment.

## Owner input and recommendation

- **FACT (owner, 2026-10-25):** distribution model is personal use on the owner's own hardware first, then possibly a free public GitHub release if it works well.
- **INFERENCE (engineering, not legal advice):** private use without conveying copies triggers no AGPL source obligation; a free public AGPL-3.0-only release is compatible with the Chiaki family. Option B's cost only buys escape from AGPL, which this model does not need.
- **Recommendation:** Option C; Option A if schedule outweighs the Rust preference. Analysis in `../LICENSE_AUDIT.md`.
- Keep the provenance ledger from the first commit; add `LICENSE` and notices before the first public push.

## Accepted decision record

- **Selected strategy:** Option C. GPSx is AGPL-3.0-only with the Chiaki-family
  OpenSSL additional permission. Protocol work is an openly declared derivative
  Rust port, not a clean-room implementation.
- **Distribution model:** personal use first; a possible later public release is
  free and accompanied by the complete corresponding source.
- **Approver/date:** project owner, 2026-10-25.
- **Primary upstream baseline:** `chiaki-ng/chiaki-ng` commit
  `c8bea2642d0e95402f373af55584a3b98cd89417`. A later baseline change requires
  an ADR amendment and provenance review.
- **Legal assumption:** this is an engineering compliance choice, not legal
  advice. It assumes the owner accepts AGPL source, notice and attribution
  obligations for every conveyed APK or desktop build. Trademark, PSN terms and
  store-policy review remain release gates.
- **Protocol-bearing scope:** only portable Rust crates explicitly named
  `gpsx-protocol`, `gpsx-transport`, `gpsx-crypto`, `gpsx-session`, and their
  tests/fixtures may contain derived protocol behavior. Kotlin UI and platform
  adapters call stable ports and must not duplicate protocol logic.
- **Provenance:** every adapted file, constant, fixture and algorithm records the
  upstream repository, immutable commit, original path/copyright, SPDX identifier,
  adaptation owner/date and modifications. Original notices are preserved.
- **Required notices:** the repository and distributed application must carry the
  AGPL-3.0-only text, the OpenSSL additional permission, upstream attributions,
  warranty notice, source link and modification notices before public distribution.

Acceptance removes the license-decision block on protocol crates. It does not
authorize implementation before the CR3 provenance/security review package, and
it does not open the physical GP520 gate.

