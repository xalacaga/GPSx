# Offline-first development before GP520 access

Status: accepted owner constraint on 2026-10-25; the physical-hardware gate is
closed until the owner confirms the date and scope after the evidence package
and CR4 review are complete. No physical access has been run.

GPSx is developed first without connecting to, probing, installing on, or
depending on the BenQ GP520. Physical access is a later, explicit gate controlled
by the owner. Each physical test needs the bounded procedure, evidence package,
and CR4 `GO` defined below before the owner may open that scope.

## Allowed before the hardware gate

- Rust unit, property, fuzz, concurrency, and replay tests.
- Windows desktop validation and PS5 interoperability when separately approved.
- Android TV emulator builds and lifecycle/focus/navigation tests.
- Fake platform adapters for decoder, audio sink, clock, network, and input.
- Synthetic H.264/HEVC/Opus fixtures whose provenance permits redistribution.
- Recorded, sanitized Android input replays from approved external fixtures.
- Static capability schemas and failure-injection tests.

## Evidence boundaries

Emulator and fake-adapter results are `FACT` only about those test environments.
They are never evidence for GP520 codec availability, latency, Bluetooth,
thermal behavior, key layouts, sideloading, or ADB access. Those fields remain
`UNKNOWN` or `NOT_TESTED`.

## Architecture consequence

Every platform dependency sits behind an interface. The core receives clocks,
transport, decoder/audio readiness, surfaces, and input events through adapters.
Tests must be deterministic without Android hardware. No production branch may
special-case an unobserved GP520 behavior; device work starts as characterization
tests, then the smallest adapter change closes each measured gap.

## Physical-hardware gate

Before asking the owner to open a bounded physical-access scope, Codex must
provide the purpose, exact commands, collected fields, redaction behavior,
duration, rollback/uninstall steps, and a Claude Code CR4 review prompt. The
evidence package and a CR4 `GO` must precede physical access. Merely reaching M3
does not authorize device access.

