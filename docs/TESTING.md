# Testing strategy

## Automated gates

`tools/powershell/check.ps1` is the local gate:

1. `cargo fmt --all --check`
2. `cargo clippy --workspace --all-targets -- -D warnings`
3. `cargo test --workspace`
4. core dependency purity (`cargo tree`: no `jni`, `ndk`, `android*`, `windows*`, `winapi`)
5. `cargo check --locked --target aarch64-linux-android` on the core when the
   target is installed; otherwise a `NOT_TESTED` warning (`-RequireAndroid`
   makes it fatal)
6. Python 3.12+ check and `unittest` discovery for evidence tooling
7. secret scan of `tests/fixtures/` and fixture manifest verification
   (checksums plus complete provenance)

The Android gate pins NDK `28.2.13676358` and API 24. It discovers that exact
NDK through `ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT`, an SDK named by
`ANDROID_SDK_ROOT`/`ANDROID_HOME`, or `.tools/android-sdk` in the repository,
then configures `aarch64-linux-android24-clang` and `llvm-ar` for Cargo. The
selected version, API, and path are logged. `-RequireAndroid` fails with a
dedicated message when either the Rust target or pinned NDK is absent.

Current deterministic coverage: an exhaustive 9 × 9 session transition table and
idempotent stop; internal button-mask uniqueness; edge derivation (press and
release in one interval, trigger crossings, neutral state on disconnect);
all-or-nothing bounded queue with overflow counting; multi-controller isolation;
nearest-rank percentiles (n = 1, small unsorted series with duplicates, n = 100);
`Redacted<T>` formatting; adversarial redaction cases; a key-classification
corpus shared by Rust and Python (`tests/redaction/key_cases.tsv`); manifest
verification and planted-secret detection; exact deterministic diagnostic JSONL,
closed metric/unit names, all evidence classes, sample and byte limits, and
rejection of non-finite values, invalid revisions, and pre-Unix wall clocks.

Not covered yet: float-to-`i16`/`u8` axis normalization (no mapping code exists),
stick sign convention (UNKNOWN until verified against PS5 behavior), and
concurrency models (`loom`), which arrive with the first threaded code.

## Local connection simulation

The Windows desktop suite exercises real loopback sockets without contacting a
console or the LAN. UDP discovery sends the exact search datagram to a bounded
local responder. The ctrl smoke test opens a TCP listener on `127.0.0.1`, sends
the encrypted PS4 10 request through the public API, receives a bounded HTTP
response with trailing payload bytes, and completes the ctrl state transition.
All socket operations have two-second timeouts.

This is desktop simulation evidence only. It does not simulate registration,
Takion/RUDP, media, audio, controller packets, reconnect, or a console's full
behavior, and it is never PS4/PS5 interoperability evidence.

## Android virtual-LAN simulation

Android is the primary platform gate. From Windows, the following command
cross-compiles a native Rust integration test for `x86_64-linux-android`, starts
the repository Google TV API 36 AVD when needed, starts a bounded synthetic
console on the host, and executes the test inside Android:

```text
powershell -NoProfile -ExecutionPolicy Bypass -File tools/powershell/test-android-simulation.ps1
```

The test uses QEMU user-mode NAT to reach the host loopback gateway at
`10.0.2.2`. Discovery is unicast UDP to the test-only port `19878`; ctrl connects
to the advertised test-only port `19295`, although the canonical request keeps
the protocol `Host` header port `9295`. It sends the exact PS4 discovery request,
parses the fixed READY response, sends a reviewed encrypted PS4 10 ctrl request,
and completes the ctrl response state transition. Broadcast discovery and the
standard discovery/ctrl ports are `NOT_TESTED` in this simulation. The script
accepts only an ADB serial named `emulator-*`; it never selects a physical
Android device. All I/O is bounded and the remote test binary is removed during
cleanup.

The synthetic console sends the trailing ctrl marker without a guard byte and
keeps the persistent ctrl connection open until the Android client closes. The
client frames the response as the bounded HTTP header plus the known first ctrl
message; it does not use TCP EOF as a message boundary. **OPERATOR_REPORT:** an
emulator `-tcpdump` diagnostic showed a passing 80-byte response payload followed
by a separate FIN, while EOF-framed runs intermittently delivered only 79 bytes
to the guest. The Android assertion requires exact trailing-byte equality; TCP
FIN is not treated as protocol framing or permission to lose a final payload
byte.

FACT (2026-10-28): the Google TV API 36 x86_64 AVD passed this discovery + ctrl
simulation. This is native Android userspace and virtual-network evidence. It is
not APK/JNI integration, registration, Takion/RUDP, media, controller, physical
LAN, real-console interoperability, GP520, latency, or performance evidence.
**FACT (2026-10-28):** `-Iterations 20` passed 20 consecutive exact-trailing-byte
runs on the same AVD without tcpdump. The script retained per-run fake-console
stdout/stderr logs and their SHA-256 hashes in
`target/android-simulation/run-summary.json`; that summary is 20/20 with SHA-256
`6c9274920982a34ab7079dcc0ea603c56bbea5708cb8cc09fc3596278fb901fd`.
The Android libtest output itself was not retained; each recorded PASS therefore
combines the checked adb exit status with clean fake-console termination within
five seconds.
These ignored scratch artifacts are emulator evidence available for independent
review. The earlier tcpdump diagnosis remains an operator report because its
capture was not retained.

## Ctrl-response fuzzing

The `fuzz/control_response` libFuzzer target drives the public prepared
ctrl-response path with arbitrary bytes. On success it asserts that the reported
header length is bounded, ends at the first `\r\n\r\n`, and leaves later bytes
outside the header. On a supported Unix-like host, run:

```text
cargo +nightly fuzz run control_response -- -max_total_time=60 -max_len=1024
```

`cargo +nightly fuzz build control_response` is also a useful compile gate.
The upstream `cargo-fuzz` tool does not support execution on Windows. On
Windows, the deterministic arbitrary-input unit test and the bounded loopback
test run in `check.ps1`; a successful Windows fuzz build is compilation
evidence only, while an actual fuzz campaign remains `NOT_TESTED` until run on
a Unix-like gate.

## Hardware gates

Compilation is not hardware validation. Report these independently:

- Windows baseline client;
- Android emulator lifecycle;
- physical GP520 codec/audio/lifecycle;
- DualSense USB and Bluetooth;
- PS5 discovery, registration, streaming, disconnect, and reconnect.

The project is offline-first with respect to the GP520. Until the user opens the
physical-hardware gate, GP520 rows remain `NOT_TESTED`; emulator, fake-codec,
replay, and desktop results may not promote them to PASS.

## Failure and security testing

- malformed and truncated fixtures;
- packet reorder/loss/duplication once transport work is authorized;
- Surface destruction and recreation;
- controller disconnect/reconnect;
- network transition and timeout;
- adversarial secret-redaction samples;
- fuzzing for every parser that receives untrusted bytes.

