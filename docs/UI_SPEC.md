# Android TV interface specification

The first GPSx interface is a remote-first, offline prototype. It validates the
navigation model, information hierarchy and explicit session state before any
console or GP520 connection is permitted.

## Visual direction

- Arcade direction: near-black canvas, warm off-white text and one signal orange
  for focus and the main action. No gradients, tinted glass or rounded cards.
- Very large, tight black-weight titles readable at three metres from a projector.
- A horizontal tab bar at the top (one tab per destination) with a live controller
  count; it hides while streaming, and moving up from a screen returns to the
  active tab.
- Focus: tiles and rows gain a 3 dp orange frame, buttons and tabs fill orange;
  the Home "Jouer" tile fills orange.
- Only real data is shown: no simulated metrics or placeholder features.
- The wordmark's G is orange; the About screen opens with the owner's line
  "Gaby's game", kept verbatim.

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
Settings provides safe presets before advanced subsystem pages. Controllers shows
two explicit physical DualSense slots whose local identity and reconnect state
remain independent before their inputs are merged into one Remote Play player.
Diagnostics reserves a Bluetooth coexistence view for two controllers, the TV
remote and an audio route, with correlated stream-impact events.

## Acceptance for this slice

- The Android TV application compiles at API 30 compatibility and launches through Leanback.
- Navigation and actions can be completed using keyboard D-pad events in an emulator.
- The demo follows `READY → CONNECTING → STREAMING → STOPPED`.
- Invalid state transitions are ignored and reducer tests pass.
- No ADB connection, device discovery, network protocol or GP520 access occurs.

