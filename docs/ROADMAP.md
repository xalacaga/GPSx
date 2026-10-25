# Roadmap

Phase 0 audit completed on 2026-10-25. Human review is required before implementation.

## Recommended first milestone — M0.5 Evidence Baseline and License Gate

### Goal

Choose a legally and technically defensible protocol strategy and establish reproducible known-good behavior against the user's PS5, Windows machine, DualSense, and GP520 before building a Rust protocol implementation.

### Scope

- Develop without connecting to the GP520, following `OFFLINE_FIRST.md`.
- Decide and record one path from `LICENSE_AUDIT.md`: A (AGPL reuse/wrap of the C core), B (clean-room Rust), or C (GPSx under AGPL-3.0 with a declared-derivative Rust port).
- Prepare collectors and explicit UNKNOWN fields for the later GP520 hardware gate; do not run them yet.
- Pin immutable upstream revisions and generate a provenance/SBOM baseline.
- Build and run one known-good Chiaki-family desktop client on Windows.
- Build/run Android TV behavior on an emulator and platform fakes; label it emulator-only evidence.
- Capture sanitized discovery, session-state, media, error, codec, audio-route, and controller-inspector evidence.
- Record the full DualSense mapping matrix without storing secrets.
- Define the future GP520 codec, audio, firmware, and input evidence schema without collecting it.
- Define redacted, reusable test fixtures and acceptance baselines for the later core.

### Non-goals

- No Rust protocol port.
- No production Android UI.
- No claim of improved performance.
- No advanced haptics, adaptive triggers, motion, or multitouch implementation.

### Files/modules affected

- `docs/UPSTREAM_ANALYSIS.md`, `docs/LICENSE_AUDIT.md`, `docs/PROTOCOL.md`, `docs/DUALSENSE_MAPPING.md`, `docs/PERFORMANCE.md`, and `docs/TESTING.md`.
- `tests/fixtures/` for sanitized, redistribution-safe fixtures.
- `tools/python/` for redaction and baseline reports.
- `.vscode/` tasks for approved baseline commands and ADB diagnostics.

### Tests

- Secret-redaction unit tests with adversarial samples.
- Fixture provenance and checksum validation.
- Windows discovery/register/connect/clean-disconnect checklist.
- Emulator codec enumeration, synthetic 720p60/1080p60 playback, Surface recreation, and audio-route tests.
- Complete controller inspector matrix and reconnect scenarios.
- Ten-minute baseline stream, then 30- and 60-minute runs if the shorter gate passes.

### Acceptance criteria

- Human-approved license strategy written in `LICENSE_AUDIT.md`.
- Pinned upstream revision and reproducible Windows build/run instructions.
- At least one successful PS5 session baseline or a classified, evidence-backed blocker.
- No credentials or session secrets in artifacts.
- The deferred GP520 evidence fields remain explicitly UNKNOWN and cannot block the offline software gates.
- Every required DualSense control is PASS, FAIL, OS_LIMITATION, or NOT_TESTED with raw evidence.
- Baseline metrics and JSONL are retained with environment metadata.

### Risks

- AGPL may be incompatible with the desired distribution model.
- Authentication or PS5 firmware changes may block the selected upstream build.
- The later GP520 hardware gate may invalidate emulator assumptions and require adapter changes.
- Captures may contain secrets without a tested redaction pipeline.
- A working baseline may expose latency or Bluetooth limits application code cannot remove.

## M1 entry criteria

Added 2026-10-25 after independent review. M1 starts only when all of these hold:

- The license option (A, B, or C) is recorded with an owner and a date.
- `CONCURRENCY.md` has been approved by a human.
- The DualSense wire-exact model, mapping-profile approach, and stream-exit design in `DUALSENSE_ANALYSIS.md` have been approved.
- The CI plan includes the core dependency-purity check, the Android cross-compile, and the `unsafe` lints from `AGENTS.md`.
- The offline plan and hardware-abstraction seams are approved; GP520 ADB/sideload remains intentionally UNKNOWN until the physical-hardware gate.

Before the license decision, only the path-independent crates (`model`, `input`, `telemetry`) may start.

## Later milestones

- M1: portable core foundations after the license gate.
- M2: Windows protocol validation and controller inspector.
- M3: Android TV shell, JNI boundary, MediaCodec/audio/input adapters.
- M4: optional physical GP520 validation, opened only with explicit user approval.
- M5: Bluetooth coexistence experiments.
- M6: measured optimization.
- M7: soak, fuzzing, failure injection, security/license hardening, release readiness.

## Independent cross-review gates

Claude Code cross-review is required before accepting the M1 entry decisions,
before merging each milestone, after introducing protocol/crypto/JNI/media or
concurrency boundaries, before performance claims, and before release. The
agent must stop at each gate and give the user the exact prompt and file scope
defined in `CROSS_REVIEW.md`.
