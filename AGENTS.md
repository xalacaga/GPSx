## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.

## GPSx engineering contract

- Keep the Remote Play engine portable; Android, Windows, Compose, JNI, MediaCodec, AudioTrack, and GP520 behavior stay behind platform adapters.
- Use explicit state machines, bounded queues with documented depth and overflow policy (see `docs/CONCURRENCY.md`), documented ownership, and coarse JNI calls. Never cross JNI per packet, sample, pixel, or frame when ownership can remain native.
- Represent controller input once as a platform-neutral, multi-controller state keyed by an internal ID.
- Label significant conclusions FACT, INFERENCE, HYPOTHESIS, or UNKNOWN. Compilation is not validation; keep core, Windows, Android, emulator, and physical GP520 evidence separate.
- Rust gates are `cargo fmt --check`, Clippy with warnings denied, and `cargo test --workspace`. Add deterministic parsing, state, queue, input, telemetry, and redaction tests; fuzz untrusted parsers.
- Never log credentials, cookies, tokens, secret-bearing redirect URLs, registration keys, session keys, or Remote Play secrets. Do not disable TLS validation or implement custom cryptographic primitives.
- Do not claim performance improvement without comparable measurements. Prefer P50/P95/P99 and queue-depth evidence.
- Do not copy, translate, or adapt upstream code until its license and provenance are recorded and accepted. Treat a rewrite of AGPL source as derivative unless legal review establishes otherwise.
- Rust owns portable protocol/session/input/telemetry logic. Kotlin owns Android lifecycle, UI, Surface hand-off, input-event capture, and system integration. A native Android adapter (outside the portable core) owns media I/O: NDK `AMediaCodec`, `ANativeWindow`, AAudio/Oboe, and their threads. Compressed video and PCM never transit the JVM. Python is tooling only.
- Portable core crates must not depend on `jni`, `ndk`, Android, Windows, or other platform crates; CI enforces this with a dependency-graph check and cross-compiles the core for `aarch64-linux-android` in addition to Windows.
- Portable core crates use `#![forbid(unsafe_code)]`. `unsafe` is allowed only in FFI/adapter crates; every `unsafe` block carries a `// SAFETY:` comment stating its invariants (enforced with `clippy::undocumented_unsafe_blocks`). Run Miri where feasible and ASan/HWASan on Android native code.
- Controller button edges and trigger engage/release crossings (0 ↔ non-zero) are never coalesced or dropped between capture and the wire. Stick, motion, and intermediate trigger values are continuous state and may be coalesced (latest wins).
- Every authored Markdown document exists in English (`NAME.md`, the reference version) and French (`NAME.fr.md`). Update both in the same change. Third-party tooling under `.codex/` and generated files under `graphify-out/` are excluded.
- Request an independent Claude Code cross-review at the gates in `docs/CROSS_REVIEW.md`. Stop and tell the user when a gate is reached; provide the exact review scope and prompt before advancing.
- Follow `docs/OFFLINE_FIRST.md`: do not connect to, probe, sideload, or require the GP520 before the user explicitly opens the physical-hardware gate. Emulator and replay results are never GP520 evidence.
- A change is complete only when relevant tests pass, failure behavior is documented, and performance-sensitive changes have before/after evidence.
