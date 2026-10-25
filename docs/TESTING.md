# Testing strategy

## Automated gates

`tools/powershell/check.ps1` is the local gate:

1. `cargo fmt --all --check`
2. `cargo clippy --workspace --all-targets -- -D warnings`
3. `cargo test --workspace`
4. core dependency purity (`cargo tree`: no `jni`, `ndk`, `android*`, `windows*`, `winapi`)
5. `cargo check --target aarch64-linux-android` on the core when the target is
   installed; otherwise a `NOT_TESTED` warning (`-RequireAndroid` makes it fatal)
6. Python 3.12+ check and `unittest` discovery for evidence tooling
7. secret scan of `tests/fixtures/` and fixture manifest verification
   (checksums plus complete provenance)

Current deterministic coverage: an exhaustive 9 × 9 session transition table and
idempotent stop; internal button-mask uniqueness; edge derivation (press and
release in one interval, trigger crossings, neutral state on disconnect);
all-or-nothing bounded queue with overflow counting; multi-controller isolation;
nearest-rank percentiles (n = 1, small unsorted series with duplicates, n = 100);
`Redacted<T>` formatting; adversarial redaction cases; a key-classification
corpus shared by Rust and Python (`tests/redaction/key_cases.tsv`); manifest
verification and planted-secret detection.

Not covered yet: float-to-`i16`/`u8` axis normalization (no mapping code exists),
stick sign convention (UNKNOWN until verified against PS5 behavior), and
concurrency models (`loom`), which arrive with the first threaded code.

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

