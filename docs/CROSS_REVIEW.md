# Independent Claude Code cross-review gates

> **Retired 2026-10-31.** The project owner stopped the independent cross-review
> gates. This page is kept as a historical record of CR0–CR4. No further gate
> blocks work; tests, licensing, security and the physical-hardware gate remain.


The purpose is adversarial validation, not duplicate implementation. Codex owns
the active change; Claude Code reviews the frozen diff, evidence, tests, and
assumptions. A gate is not passed merely because code compiles.

## Gates

| Gate | When to stop | Required review focus |
|---|---|---|
| CR0 | Phase 0 architecture | Completed: independent review recorded in `PHASE0_REVIEW.md` |
| CR1 | Before accepting M1 entry | License A/B/C, concurrency budgets, DualSense model, exit behavior, and sound deferral of GP520 evidence |
| CR2 | Before merging M1 | Core purity, state invariants, bounded queues, edge preservation, tests and CI |
| CR3 | Before merging the first protocol slice | Provenance, license, parser limits, crypto use, secret handling, fixture legality |
| CR4 | Before the first GP520 streaming build, after the owner authorizes the review package | JNI ownership, Surface/codec lifecycle, audio callback safety, shutdown, native safety invariants and emulator-vs-device gaps |
| CR5 | Before accepting an optimization | Comparable baselines, P50/P95/P99, queue depths, thermal and Bluetooth confounders |
| CR6 | Before release | Threat model, dependency/license audit, fuzz/soak results, recovery, packaging and notices |

An extra review is mandatory whenever a change introduces custom cryptographic
logic, a new `unsafe` block, an untrusted parser, an unbounded or blocking hot
path, authentication storage, a protocol-derived implementation, or a change
to the JNI/media ownership boundary.

Authorization to prepare or review CR4 is not authorization to access physical
hardware. After CR4 returns `GO`, the owner must separately confirm the date,
commands and bounded physical scope before any GP520 connection or installation.

## Review package

Freeze the work long enough for a coherent review. Provide the milestone and
intended behavior, complete relevant diff, architecture/ADR and provenance,
commands and test results, separated hardware evidence, known unknowns and
rejected alternatives, and Graphify output for affected relationships. Never
include credentials, session secrets, private captures, or unsanitized URLs.

## Prompt template to give the user

```text
Act as an independent adversarial reviewer for GPSx gate <GATE>.
Do not implement changes. Inspect the listed files and the current git diff.
Challenge correctness, license/provenance, security, concurrency/ownership,
failure recovery, test sufficiency, platform assumptions, and performance
claims. Separate FACT, INFERENCE, HYPOTHESIS, UNKNOWN, and NOT_TESTED.
For every finding give severity, evidence, affected files, a concrete required
change, and the test that would close it. Finish with GO, GO WITH CHANGES, or
NO-GO and list the exact blocking conditions.

Scope: <FILES AND MODULES>
Commands already run: <COMMANDS AND RESULTS>
Hardware evidence: <EVIDENCE OR NONE>
Known open decisions: <DECISIONS>
```

## Handling findings

Codex classifies every finding as accepted, rejected with evidence, or deferred
with an owner and gate. Material accepted findings update architecture and
tests. The same reviewer then checks only the remediation diff. Disagreement is
shown to the user; it is never silently averaged away.

