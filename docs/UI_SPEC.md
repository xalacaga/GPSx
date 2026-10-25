# Android TV interface specification

The first GPSx interface is a remote-first, offline prototype. It validates the
navigation model, information hierarchy and explicit session state before any
console or GP520 connection is permitted.

## Visual direction

- Dark cinematic canvas with restrained blue depth, mint focus and amber warnings.
- Large type and high-contrast focus rings intended for television distance.
- A persistent six-item rail: Home, Consoles, Play, Controllers, Diagnostics and Settings.
- Rounded panels and sparse motion; no decorative animation may obscure focus or add stream latency.
- Synthetic values always carry a visible `SIMULATED DATA` label.

## Interaction contract

- Every action is focusable and operable using only D-pad, Select and Back.
- Back returns to Home from a primary destination; stream exit will later require confirmation.
- Focus styling does not depend on color alone and remains visible on every panel.
- The stream captures controller input only when the future stream input mode is active.
- Touch is optional and never required.

## Current screens

Home provides the primary action, synthetic health summary and shortcuts. Consoles
reserves local discovery, manual registration and remote connection. Play exercises
the session state machine and performance overlay. Controllers exposes the planned
capability model. Diagnostics separates network, video, audio and input evidence.
Settings provides safe presets before advanced subsystem pages.

## Acceptance for this slice

- The Android TV application compiles at API 30 compatibility and launches through Leanback.
- Navigation and actions can be completed using keyboard D-pad events in an emulator.
- The demo follows `READY → CONNECTING → STREAMING → STOPPED`.
- Invalid state transitions are ignored and reducer tests pass.
- No ADB connection, device discovery, network protocol or GP520 access occurs.

