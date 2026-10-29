# Concurrency, Queues, and Ownership

Status: proposed 2026-10-25 after independent review; human approval required before M1. Numbers marked HYPOTHESIS are starting budgets to be replaced by GP520 measurements.

## Principles

- Hot paths (network receive, video feed, audio callback, input) run on explicit, named threads. An async runtime may be used only on the control path (discovery, registration, session/HTTP control). It is never used on media or input paths.
- Every queue has a fixed capacity, a single documented owner on each end, and an overflow policy. Unbounded channels are forbidden.
- Hot threads do not allocate per packet, per frame, or per event in steady state; they use pre-allocated pools. Hot threads never take a lock held by a control or telemetry thread.
- The audio callback never locks, allocates, logs, or calls JNI.

## Threads (Android)

| Thread | Owner | Work | Blocks on |
|---|---|---|---|
| Main (Kotlin) | Kotlin | UI, lifecycle, input capture → JNI submit | never on native queues (non-blocking push) |
| Control | core | discovery, registration, session control, reconnect state machine | sockets, timers |
| Net receive | core | UDP recv, decrypt/validate, reorder, FEC, frame assembly, Opus decode (moved to a separate thread only if measurement shows it is needed) | socket recv |
| Feedback sender | core | input history + state packets, tail resends | condvar/timer |
| Video feeder | Android adapter | pop AU → `dequeueInputBuffer` → memcpy → queue | codec input buffer |
| Video output | Android adapter | dequeue output → release-to-render, timing metrics | codec output |
| Audio callback | AAudio/Oboe | pop PCM ring, drift control | none |
| Telemetry writer | core | drain per-thread rings → JSONL | low priority |

Windows uses the same core threads. Its adapter supplies its own decoder and audio threads; Windows thread behavior is not evidence for Android.

The current `gpsx-transport` discovery call is a synchronous control-path
operation, not a hot network thread. The caller owns the socket, the single
65,535-byte receive allocation and the returned report. It sends one datagram,
blocks for at most five seconds, creates no background thread and has no queue.
The Windows probe invokes it serially for PS5 and PS4, for at most 1.5 seconds
with default settings. A future GUI must call it from its control worker, never
from the UI thread. ICMP connection resets/refusals are counted and do not end
the collection; the deadline is checked before every receive, including under a
continuous packet flood.

Thread priorities: AAudio sets the callback priority. Raising the net receive and video threads' priority is a HYPOTHESIS to evaluate with measurements; never assume it.

## Queues

| Queue | Producer → Consumer | Capacity | Overflow policy |
|---|---|---|---|
| Input events | Main (JNI) → feedback/input stage | 128 transitions (Android adapter FACT) | One raw Android event produces at most 18 transitions and is normalized synchronously through `gpsx-input`. The whole batch enters the queue or none does; the adapter drains it before returning and refuses output if `overflows()` is non-zero. Button edges and trigger 0 ↔ non-zero crossings are submitted immediately; sticks and intermediate trigger values are latest-wins state. |
| Protocol input history | input stage → feedback sender | per protocol (upstream: 16 events, 8 kept for resend) | Protocol-defined; tail resend 20/50/100/200 ms (upstream FACT) |
| Compressed video AUs | net receive → video feeder | 2 AUs (HYPOTHESIS) | On overflow drop queued non-IDR units, set `need_idr`, request IDR, discard until the next IDR. Never block net receive. |
| Video buffer pool | feeder returns buffers to assembler | fixed N buffers sized for the max AU | Pool empty → same as video overflow |
| PCM ring | net receive (Opus) → audio callback | target T, max 2T; T initial 40 ms (HYPOTHESIS), excluding device/BT latency | Overflow: drop oldest. Underrun: emit silence, count it. |
| Telemetry | each hot thread → writer | fixed per-thread ring | Drop the newest record, count drops; never block. |
| Events to Kotlin | core → main | small fixed batch | Lifecycle/error events are never dropped (bounded and small). Metric snapshots are coalesced (latest wins). |

### Audio clock drift

The PS5's audio clock and the sink's clock differ. If ring depth stays outside [T/2, 3T/2] for longer than a window W, the core corrects by dropping or inserting a single frame at a low-energy point, or by applying a bounded resampling ratio. T and W, and the choice between drop/insert and resampling, are decided from measurements. Depth must be recorded continuously in soak tests.

## Ownership

- Compressed AU buffers belong to the core pool. Ownership moves to the feeder by value through the queue and returns to the pool after the memcpy into the codec input buffer. Exactly one copy is allowed between frame assembly and codec input.
- PCM blocks are written in place into the ring; there is no per-block allocation.
- The native adapter owns `ANativeWindow`, tagged with a Surface generation. A stale generation is ignored.
- JNI global references are created once, owned by the adapter, and released last.

## Lifecycle and shutdown order

Shutdown is idempotent and can be called from any state:

1. Kotlin stops submitting input and marks the session as stopping.
2. The input stage sends a neutral controller state and stops the feedback sender, which flushes and is joined.
3. The control thread sends an orderly session teardown when possible, then closes sockets to unblock net receive. Net receive is joined.
4. The video feeder is drained and joined. The codec receives end-of-stream; the output thread is joined; the codec is stopped and deleted; then `ANativeWindow_release` is called.
5. The audio stream is stopped and closed.
6. The telemetry writer flushes and is joined.
7. JNI global references are released and the engine handle is invalidated.

Surface loss (backgrounding): the feeder pauses. Use `setOutputSurface` if a new generation arrives; otherwise stop the codec. The network session stays alive within a bounded grace period. Request an IDR on resume.

## Required tests

- `loom` (or equivalent) models for queue handoff and the shutdown order.
- Overflow tests for each queue that check the stated policy, including a press and release under input pressure.
- An engine create/destroy stress test ×1000, and Surface churn during decode (Android).
- A ≥60-minute audio drift soak that records ring depth.
- Queue-depth P50/P95/P99 exported for every queue.
