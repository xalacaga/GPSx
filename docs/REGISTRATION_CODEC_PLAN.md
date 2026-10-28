# Registration codec plan

Status: the codec remediation A1/A2 received `GO`. Oracle integration received
`GO WITH CHANGES`; B1-B4 are implemented in the uncommitted review tree and
must receive the required independent check before the fixture is committed.

This document scopes a pure, bounded codec for local console registration. It excludes sockets, retries, persistence, platform APIs, remote PSN, RUDP, hole punching, live credentials, and hardware access.

## Evidence and provenance (C5)

- **FACT:** `gpsx-protocol` is the portable protocol layer and must remain independent of `gpsx-crypto`, transport, and platform crates.
- **FACT:** the pinned chiaki-ng baseline is commit `c8bea2642d0e95402f373af55584a3b98cd89417`, under `LicenseRef-AGPL-3.0-only-OpenSSL`.
- **FACT:** the approved upstream inventory is `lib/include/chiaki/regist.h`, `lib/src/regist.c`, `lib/src/session.c`, and `test/regist.c`.
- **FACT:** base64 will be GPSx-written from the RFC 4648 section 4 alphabet/algorithm and section 10 vectors, and strict hexadecimal/decimal parsing will be GPSx-written. `lib/src/base64.c` and `lib/src/utils.h` are therefore not port sources for the implementation.
- **FACT:** no code from the proposed upstream paths may be copied, translated, or adapted before the revised plan and proposed provenance entries receive an independent `GO`.
- **UNKNOWN:** interoperability with a real PS4 or PS5, including the PS4_8 and PS4_9 RP targets.

The proposed derived-code ledger row covers `request_head_fmt`, `request_path`, `request_header_format`, the non-hole-punching branch of `chiaki_regist_request_payload_format`, `regist_parse_response_payload`, and only the `chiaki_rp_version_string` constants needed by the four profiles. It explicitly excludes UDP `regist_search`, sockets, threads, stop pipes, timeouts, logging, PSN registration, hole punching, RUDP, persistence, and all other session code.

The profile-to-firmware observations in `gui/src/qmlbackend.cpp` and `android/app/src/main/java/com/metallic/chiaki/regist/RegistActivity.kt` are reference-only evidence at the pinned commit. Those files are neither port sources nor proposed inventory entries.

## Layer boundary and allocation policy

The implemented module is `crates/gpsx-protocol/src/registration.rs`.

It performs no I/O and exposes only deterministic operations over caller-provided fixed buffers:

1. format a bounded plaintext inner request and return its length;
2. prepare the `0x1e0` request prefix and return its derived layout;
3. place opaque aeropause bytes and an already encrypted inner block;
4. format the outer HTTP request header around the completed opaque payload;
5. frame and validate an outer HTTP response while borrowing its encrypted body;
6. parse the decrypted registration field block as a borrowed view.

No formatter creates or grows a `Vec`, `String`, `Box`, `Cow`, map, or other heap collection, and the module does not use `vec!`, `format!`, or `collect`. No parser copies a secret-bearing value. The existing discovery parser in `http.rs` remains unchanged; registration uses a separate strict CRLF framing function because discovery intentionally accepts other line endings and has no binary body.

The codec does not encrypt or decrypt. A later orchestration layer combines it with the one-shot `gpsx-crypto` registration typestate and a separately reviewed transport.

## Ownership and secret handling (C6)

- `OnlineId<'a>(&'a str)` and `AccountId<'a>(&'a [u8; 8])` are validated wrappers that borrow caller-owned storage and contain references only. They do not copy identity bytes and do not implement `Debug`, `Display`, `Clone`, serialization, or conversion to an owned string.
- The plaintext formatter writes directly into a caller-provided fixed buffer and returns its length. Insufficient capacity returns a static redacted error and no partial trusted result.
- The outer parser returns a view borrowing the caller-owned response buffer. The decrypted parser returns borrowed encoded secret slices, borrowed validated text, and scalar values.
- Secret-bearing result types do not implement `Debug`, `Display`, `Clone`, or serialization.
- Errors are static redacted categories. They never contain input bytes, header values, keys, identifiers, nickname, application text, or redirect data.
- The caller owns and zeroizes plaintext and decrypted storage. Secret-owning types belong in the crypto/session boundary, not in this dependency-free codec.

The account ID, online ID, plaintext block, aeropause, and encrypted body never enter logs, panic text, telemetry, live-data fixtures, or diagnostic archives. Compile-fail/structural tests cover forbidden traits on the borrowed identity wrappers and every public secret-bearing result type. A forbidden-symbol scan of `registration.rs` for `Vec`, `String`, `ToOwned`, `to_owned`, `to_string`, `vec!`, `Box`, `format!`, `collect`, `Cow`, `HashMap`, and `BTreeMap`, followed by code review of every formatter/parser allocation site, enforces the no-growable-allocation rule.

**INFERENCE:** borrowed views and fixed output buffers minimize avoidable secret duplication while preserving the zero-dependency protocol crate. Compiler-generated register or stack copies cannot be ruled out and are not claimed to be zeroized.

## Request API and layout invariants (C1)

The request is built without an offset side channel:

1. `prepare_request_prefix(profile, filler, plaintext_len, output)` takes exactly `0x1e0` caller-provided filler bytes, copies them into a fixed caller-provided output buffer, and returns an opaque prepared-prefix token carrying the plaintext length. For the PS4_10 protocol target and PS5, the token exposes `cipher_offset = filler[0x18d] & 0x1f` and `aeropause_offset = filler[0] >> 3` as plain `u8` values. For PS4_8 and PS4_9 targets, it exposes the fixed pre-10 layout and no table offsets.
2. `finish_request_payload(prepared, aeropause, encrypted_inner)` consumes that token and rejects an encrypted block whose length differs from the carried plaintext length. For PS4_8/PS4_9 targets it writes all 16 opaque aeropause bytes at `0x11c`. For the PS4_10 target/PS5 it writes `aeropause[8..16]` at `0xc7` and `aeropause[0..8]` at `0x191`, then appends the opaque encrypted inner block at `0x1e0`.

The opaque token keeps the exclusive borrow of the prepared output, so safe Rust cannot change its filler between offset derivation and final placement. The modern aeropause regions `[0xc7, 0xcf)` and `[0x191, 0x199)` do not overlap the offset-source bytes `0` and `0x18d`; the pre-10 region `[0x11c, 0x12c)` does not overlap them either. These invariants are compile-time constants with assertions and tests.

The codec supports arbitrary fixture filler so offset derivation can be tested. The first integration policy passes `[0x41; 0x1e0]`, the only filler covered by the pinned request vector. Random filler is not enabled merely because the upstream comment says it “can be random”; console acceptance is **UNKNOWN** and requires later hardware evidence.

Only bytes after `0x1e0` are encrypted. The prefix stays clear. The outer `Content-Length` is exactly `0x1e0 + encrypted_inner.len()`, using checked arithmetic, and equals the completed payload length.

## Profiles and fixed request constants (C2)

The codec uses four RP protocol-target profiles. Their names do not denote the console firmware version. PS4_8 is retained rather than hidden inside a pre-10 profile.

| Codec profile | POST path | `RP-Version` | Inner identity | `Client-Type` | `gpsx_crypto::RegistrationTarget` |
| --- | --- | --- | --- | --- | --- |
| `Ps4_8` | `/sce/rp/regist` | absent | `Np-Online-Id` | `Windows` | `Ps4Pre10` |
| `Ps4_9` | `/sce/rp/regist` | `9.0` | RFC 4648 base64 `Np-AccountId`; test-only `Np-Online-Id` for the pinned vector | `Windows` | `Ps4Pre10` |
| `Ps4_10Target` | `/sie/ps4/rp/sess/rgst` | `10.0` | RFC 4648 base64 `Np-AccountId` | 64-character constant below | `Ps4Modern` |
| `Ps5` | `/sie/ps5/rp/sess/rgst` | `1.0` | RFC 4648 base64 `Np-AccountId` | 64-character constant below | `Ps5` |

The orchestrator must match exhaustively over these four values and reject any codec-profile/crypto-target mismatch before formatting or encryption. The inner formatter and prefix-preparation calls are not tied by one type token; until a separately reviewed API change does so, the orchestrator must carry one selected profile through both calls and test that no mixed-profile sequence is possible.

| Item | Exact value | Evidence |
| --- | --- | --- |
| Request line | `POST <profile-path> HTTP/1.1\r\n` | pinned `request_head_fmt` |
| Compatibility quirk | ` HTTP/1.1\r\n` including the leading space | pinned `request_head_fmt` |
| Host | `HOST: 10.0.2.15\r\n` | fixed GPSx value matching the upstream caller; no LAN address is input |
| User agent | `User-Agent: remoteplay Windows\r\n` | pinned `request_head_fmt` |
| Connection | `Connection: close\r\n` | pinned `request_head_fmt` |
| Length | `Content-Length: <canonical decimal>\r\n` | completed payload length |
| Optional version | `RP-Version: <profile value>\r\n` | absent only for `Ps4_8` |
| Header terminator | `\r\n` | pinned request tail |
| Modern client type | `dabfa2ec873de5839bee8d3f4c0239c4282c07c25c6077a2931afcf0adc0d34f` | pinned `client_type` |
| PS4_8 / PS4_9 compatibility inner block | `Client-Type: Windows\r\nNp-Online-Id: <validated-id>\r\n` | pinned `request_inner_online_id_fmt` |
| PS4_9 normal inner block | `Client-Type: Windows\r\nNp-AccountId: <12 base64 characters>\r\n` | pinned account-ID formatter with the pre-10 client type |
| Modern inner block | `Client-Type: <modern-client-type>\r\nNp-AccountId: <12 base64 characters>\r\n` | pinned `request_inner_account_id_fmt` |
| Clear prefix size | `0x1e0` bytes | pinned inner-header offset |
| Pre-10 aeropause placement | 16 bytes at `0x11c` | pinned non-hole-punching formatter |
| Modern aeropause placement | second half `[8..16]` at `0xc7`, first half `[0..8]` at `0x191` | pinned non-hole-punching formatter |
| Inner crypto counter | `0` | pinned encrypt/decrypt calls |

GPSx reproduces the malformed-looking compatibility line byte for byte. Whether consoles tolerate replacing it with valid HTTP is **UNKNOWN**; it will not be changed without a new oracle vector and later hardware evidence.

`Ps4_8` is online-ID only. Production `Ps4_9` uses the eight-byte account ID with `Client-Type: Windows`; a private `#[cfg(test)]` compatibility constructor accepts its pinned OnlineId vector and is absent from production builds. `Ps4_10Target` and `Ps5` are account-ID only. The Online ID grammar is 1 through 16 ASCII bytes from `[A-Za-z0-9_-]`; this is a local hardening policy, while the complete set accepted by real consoles is **UNKNOWN**. Account IDs encode as exactly 12 RFC 4648 base64 characters with padding. `Ps4_8` plus AccountId, production `Ps4_9` plus OnlineId, and modern target plus OnlineId are rejected.

Reference-only callers map firmware below 7.0 to the PS4_8 target, firmware from 7.0 up to but excluding 8.0 to PS4_9, and firmware 8.0 or later to the PS4_10 target. The GUI likewise uses Online ID only for PS4_8 and Account ID for every other target. This mapping is evidence for target selection, not code to port.

The prior session-authenticator row is removed: it belongs to a different flow and has no source in local registration.

## Registration framing and limits (C4)

All limits are checked before allocation, indexing, arithmetic, or conversion. Arithmetic uses checked operations.

| Element | Hard limit or rule | Failure behavior |
| --- | --- | --- |
| Outer request header | 512 bytes including `\r\n\r\n` | reject before writing past caller capacity |
| Outer response header block | 1,024 bytes including the first `\r\n\r\n` | close attempt |
| Outer response body | 1,024 bytes | close attempt; caller zeroizes secret storage |
| Complete outer response | 1,500 bytes for header plus body | close attempt |
| Decrypted field block | exactly the body length, at most 1,024 bytes | reject complete block |
| Registration field count | at most 128 | reject before examining field 129 |
| Registration field line | at most 512 bytes including CRLF | reject complete block |
| Request prefix | exactly `0x1e0` bytes | reject incompatible capacity/layout |

The 1,500-byte aggregate matches the pinned upstream receive buffer while giving each sub-parser an explicit lower bound. These values replace the contradictory 32 KiB/1 MiB proposal in `CR3_SECURITY_PLAN.md`.

The registration framer splits at the first `\r\n\r\n` wholly inside the 1,024-byte header limit. Bare LF, bare CR, obsolete folding, NUL, and data before a complete separator are rejected. Any `Transfer-Encoding` header, regardless of name case, is rejected.

For HTTP 200, exactly one case-insensitive `Content-Length` is mandatory. Its value is ASCII decimal digits only, without sign, whitespace, or leading zero, and is between 1 and 1,024. The available body must equal it exactly; truncation and trailing bytes are rejected. `+5`, `05`, `0`, whitespace, duplicates, and `u64` overflow are invalid.

A non-200 response becomes a redacted status error. It may omit `Content-Length` only when input ends exactly at the header terminator. If present, the length obeys the same 1..1,024 grammar and the body must be exact, but its contents are not surfaced. A non-200 response without a length followed by any byte is rejected. A bounded `RP-Application-Reason` may be classified numerically, but server text is never included in errors.

## Decrypted response fields (C3)

The field block uses strict CRLF-terminated `Name: Value` lines. A field name is 1..64 ASCII bytes from the RFC 7230 `tchar` set: letters, digits, or ``!#$%&'*+-.^_`|~``. This applies to unknown fields too. The separator is exactly colon plus one ASCII space. `Name:Value`, `Name:  Value`, empty names, and invalid name characters are rejected. Values are non-empty, are never trimmed, and retain every byte after the single required space. The first implementation requires the final line to end in CRLF and rejects NUL or padding. Whether real consoles instead use a missing final CRLF, NUL bytes, or padding is **UNKNOWN** and requires fixture or hardware evidence before broadening the grammar.

“Bounded text” means valid UTF-8 without ASCII control characters, measured in bytes. That is a GPSx hardening policy; upstream supplies only fixed C-array bounds.

| Exact field name | Profiles | Required | Grammar and length | Result handling / evidence |
| --- | --- | --- | --- | --- |
| `AP-Ssid` | all | optional | bounded text, 1..47 bytes | recognized but not exposed; upstream array bound is **FACT** |
| `AP-Bssid` | all | optional | bounded text, 1..31 bytes | recognized but not exposed; upstream array bound is **FACT** |
| `AP-Key` | all | optional | bounded text, 1..79 bytes | secret; recognized but not exposed; upstream array bound is **FACT** |
| `AP-Name` | all | optional | bounded text, 1..31 bytes | recognized but not exposed; upstream array bound is **FACT** |
| `PS4-Nickname` | PS4 only | optional | bounded text, 1..31 bytes | borrowed view; upstream array bound is **FACT** |
| `PS5-Nickname` | PS5 only | optional | bounded text, 1..31 bytes | borrowed view; upstream array bound is **FACT** |
| `PS4-RegistKey` | PS4 only | mandatory | 2..32 ASCII hex characters, even length; 1..16 decoded bytes | borrowed encoded secret; upstream range is **FACT**, real-console length is **UNKNOWN** |
| `PS5-RegistKey` | PS5 only | mandatory | 2..32 ASCII hex characters, even length; 1..16 decoded bytes | borrowed encoded secret; upstream range is **FACT**, real-console length is **UNKNOWN** |
| `RP-KeyType` | all | optional | canonical unsigned `u32`: `0`, nonzero decimal without leading zero, or `0x` plus 1..8 hex digits | scalar; both `a-f` and `A-F` accepted after lowercase `0x`; optionality is upstream **FACT**, actual wire radix is **UNKNOWN** |
| `RP-Key` | all | mandatory | exactly 32 ASCII hex characters / 16 decoded bytes | borrowed encoded secret; exact upstream size is **FACT** |
| `PS4-Mac` | PS4 only | mandatory | exactly 12 ASCII hex characters / 6 decoded bytes | borrowed encoded value; exact upstream size is **FACT** |
| `PS5-Mac` | PS5 only | mandatory | exactly 12 ASCII hex characters / 6 decoded bytes | borrowed encoded value; exact upstream size is **FACT** |
| `RP-SupportCmd` | all | optional | same canonical `u32` grammar as `RP-KeyType` | scalar; actual wire radix is **UNKNOWN** |

All hexadecimal byte fields accept both `a-f` and `A-F`, reject non-hex data, and remain borrowed. The later secret owner performs the accepted copy/decode and pads a short RegistKey with zeros as required by the session boundary.

Every known field, including optional fields, may occur at most once. Names are exact and case-sensitive for acceptance. A case-insensitive match with a non-exact spelling is an error, and a generation-specific known name in the wrong profile is an error. Other bounded unknown fields are ignored but count toward line, field-count, aggregate-size, and duplicate-comparison limits.

No AP field is returned. In particular, `AP-Key` remains only in caller-owned decrypted storage and is treated as secret until that storage is zeroized.

## Deterministic evidence plan (C7)

After R5 approval, a scratch C harness built from the pinned sources generates `tests/fixtures/registration_codec_oracle_v1.json`. The generation checkout, harness, and every GPSx-authored stub remain in a temporary scratch directory outside the repository; no checkout, stub, or vendored upstream header is committed. Only their hashes are recorded. The generation record includes `git rev-parse HEAD` and `git status --porcelain` from that external checkout. For every modified upstream file it records the exact diff, SHA-256, and justification, and marks every translation unit differing from the pinned commit as `patched` in the input list. Its manifest entry and provenance row record the exact commit, compiler, crypto backend and version or commit, linked dependencies, generation command, synthetic-only inputs, redistribution status, byte count, SHA-256, and secret scan. The closed build-input list is generated from compiler dependency output (`/showIncludes` or `-MD`, according to toolchain) and the exact link command rather than written by hand. It lists every compiled upstream translation unit, consumed upstream header, linked archive/library, crypto backend, and scratch GPSx stub, each with path or stable scratch label, role, and SHA-256. `lib/src/base64.c` is an oracle input only and is not a GPSx port source. A reviewer, not ProvenanceGuard, verifies this oracle manifest against the external checkout state and diffs, compiler output, and link command.

The oracle contains the pinned compatibility-only PS4_9 plus OnlineId payload from `test/regist.c` byte for byte; a separate PS4_9 plus AccountId request with `Client-Type: Windows` from the C formatter; exact outer-header and payload vectors for all four profiles; and PS4_10-target plus PS5 cases with at least three synthetic fillers whose pairs differ and include `(0, 31)` and `(31, 0)`. Plaintext inner bytes, offsets, opaque aeropause, encrypted inner bytes, completed payload, and outer request are separately bound per case.

Tests must cover:

- swapping aeropause halves, swapping the typed offsets, or changing any filler byte makes the per-case oracle match fail;
- an exhaustive four-profile mapping to `RegistrationTarget`, plus every mismatched pair rejected;
- `Ps4_8` plus AccountId, production `Ps4_9` plus OnlineId, and either modern target plus OnlineId rejected; a compile-fail doctest calls the private test-only PS4_9 compatibility constructor from outside the crate and proves it is absent from production builds;
- a positive and negative test for every response-field table row;
- `Name: Value` accepted; `Name:Value`, `Name:  Value`, `Name: `, empty/overlong names, invalid `tchar`, and empty values rejected; a one-byte text value accepted;
- wrong-generation names, case variants of known names, every duplicate known optional field, and RegistKey decoded lengths 0, 1, 16, and 17;
- limit minus one, exact limit, and limit plus one for request header, response header, body, total response, decrypted block, field count, and line length;
- `Transfer-Encoding`; `Content-Length` values `+5`, `5`, `05`, `0`, whitespace, duplicates, and `u64` overflow;
- bare LF rejected by registration while existing discovery tests remain unchanged;
- RFC 4648 section 10 base64 vectors and the exact eight-byte account-ID encoding shape;
- structural or compile-fail checks proving the identity wrappers contain references only and lack forbidden traits; the documented forbidden-symbol scan plus code review proving no growable allocation;
- redaction tests for every public result and error type;
- `cargo tree -p gpsx-protocol` showing no dependency;
- `cargo fmt --check`, Clippy with warnings denied, and `cargo test --workspace`.

Fuzzing here means deterministic dependency-free property tests inside `gpsx-protocol`, not `cargo-fuzz`. They enumerate structured mutations and arbitrary byte vectors at every length from 0 through each parser limit. “Bounded work” means one linear scan of bounded input plus at most `128 × 128` field-name comparisons for duplicate detection; there is no recursion, backtracking, unbounded rescan, or input-sized allocation. A future `cargo-fuzz` crate requires separate dependency and provenance review.

Compilation, the C oracle, and synthetic vectors are not hardware evidence. Emulator, Android device, LAN, and GP520 results remain separate.

Response-success tests are GPSx-authored synthetic cases and provide only **HYPOTHESIS**-level interoperability evidence until a reviewed fixture or hardware result exists.

## Failure contract and deferred UDP search

Every parse operation is all-or-error: it returns no partially trusted registration structure. Malformed framing, limit excess, inconsistent length, invalid known field, duplicate, wrong-generation field, or missing mandatory material closes the attempt. No recovery scans beyond the bounded message.

**FACT:** the pinned implementation sends a UDP `SRC2`/`SRC3` search on port 9295 before TCP registration. That step is excluded from this codec and deferred to a separately reviewed orchestrator/transport slice.

**UNKNOWN:** whether a real PS4 or PS5 requires that UDP step before accepting registration.

The future orchestrator owns this sequence:

1. obtain a fresh CSPRNG ambassador from a platform adapter;
2. select one of four codec profiles and its exhaustively matched crypto target;
3. format identity into caller-owned fixed plaintext storage;
4. prepare the prefix and initialize crypto with the returned typed offsets;
5. encrypt once, obtain opaque aeropause, finish the payload, and format its outer header;
6. send and receive through a separately reviewed bounded transport, including any separately authorized UDP pre-step;
7. frame the response, decrypt once, and parse the borrowed field view;
8. copy only accepted secrets into zeroizing owners and destroy temporary plaintext.

The type system enforces one request encryption and one response decryption per context. Cross-attempt key/IV uniqueness still depends on the adapter supplying fresh CSPRNG input.

## Review gate

R5 authorized external generation, and the integration review returned `GO WITH
CHANGES`. B1 now records 76 backend and 5 oracle translation units, every
observed header, and every explicitly linked library with roles and SHA-256;
B2 recomputes crypto intermediates; B3 records bilingual provenance; and B4
binds every field of all nine cases with mutation tests.

The fixture and tests remain uncommitted until an independent reviewer checks
B1-B4. Under R5 option b, the harness, extracted stub, upstream patch, build
logs, and closed build manifest must never enter this repository.
