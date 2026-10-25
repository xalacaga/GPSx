# GPSx

GPSx is an evidence-first Remote Play client project targeting Windows and
Android TV, with the BenQ GP520 as the first physical Android TV target.

## Current status

The project is in **M0.5 — Evidence Baseline and License Gate**. Portable state,
input, and telemetry foundations may evolve, but protocol code must not be
copied, translated, or implemented until the protocol license strategy is
approved in `docs/decisions/0001-protocol-license-strategy.md`.

## Prerequisites

- Rust stable with `rustfmt` and `clippy`
- Python 3.12+
- Android Platform Tools (`adb`) for GP520 evidence collection. A project-local
  copy may be placed in `.tools/platform-tools/`.
- A physical PS5, DualSense, and GP520 for hardware validation

## Validation

```powershell
./tools/powershell/check.ps1
```

The command runs formatting, Clippy with warnings denied, the Rust workspace
tests, and the Python evidence-tooling tests.

## Evidence handling

Never commit raw credentials, tokens, cookies, registration keys, session keys,
or secret-bearing URLs. Put local captures in `docs/evidence/`; generated
capture files are ignored by Git. Only reviewed, sanitized fixtures belong in
`tests/fixtures/`.

