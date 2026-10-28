# Control-channel HTTP handshake implementation contract

Status: **IMPLEMENTATION CANDIDATE — EXTERNAL ORACLE AND IMPLEMENTATION REVIEW
REQUIRED**. Findings C1–C7 and D1–D4 received independent `GO`. The portable
Rust implementation and deterministic local tests now exist. The closed
external oracle, exact fixture binding, mutation evidence, full gates, and an
independent implementation re-review remain mandatory before integration.

## Purpose and evidence

This slice prepares and validates only the local-LAN TCP control-channel HTTP
handshake. It runs before LaunchSpec, as the pinned implementation does, so the
classified server type can constrain the video profile and codec subsequently
written into LaunchSpec. It formats one bounded request, encrypts its fields,
parses only the bounded HTTP header, and records opaque ctrl counters for a
separately reviewed future message-codec slice.

**FACT:** `lib/src/session.c:522-598` starts ctrl and waits for its session ID
before `lib/src/session.c:682` starts the stream connection that formats
LaunchSpec. `lib/src/ctrl.c:1442-1461` uses the returned server type to
downgrade base-PS4 1080p to 720p and force PS4/PS4 Pro to H.264. GPSx therefore
orders the states as ctrl handshake, server classification, validated
LaunchSpec, then ctrl-channel crypto.

**FACT:** at pinned chiaki-ng commit
`c8bea2642d0e95402f373af55584a3b98cd89417`, `lib/src/ctrl.c` initializes the
local and remote ctrl counters to zero. Its local TCP request encrypts, in
order, the 16-byte registration key at counter 0, the 32-byte device identifier
at counter 1, and `Win10.0.0` plus its NUL at counter 2. PS4 10 and PS5 add a
zero little-endian start bitrate at counter 3. PS5 adds the little-endian
streaming type at counter 4 (`1` AVC, `2` HEVC, `3` HEVC HDR).

**FACT:** the pinned request uses `/sce/rp/session/ctrl` for PS4 8/9,
`/sie/ps4/rp/sess/ctrl` for PS4 10, and `/sie/ps5/rp/sess/ctrl` for PS5. The RP
versions are respectively `8.0`, `9.0`, `10.0`, and `1.0`. For a maximum-length
numeric IPv4 address and local port 9295, the canonical requests are 331 bytes
for PS4 8/9, 360 bytes for PS4 10, and 387 bytes for PS5.

**FACT:** `lib/src/session.c:252-256` creates the 32-byte device identifier per
session from the ten-byte prefix `00 18 00 00 00 07 00 40 00 80`, sixteen
bytes from `chiaki_random_bytes_crypt`, and six zero bytes. It is not
registration output. GPSx generates it inside `gpsx-crypto` from an injected
`CryptoRng`.

**FACT:** the upstream receive helper may return bytes beyond the first
`\r\n\r\n`; `lib/src/ctrl.c:1469-1472` preserves them as the beginning of the
first ctrl message. The parser therefore returns the exact header length and
leaves every following byte uninterpreted and untouched.

**HYPOTHESIS:** reproducing the pinned local TCP request and response behavior
is sufficient for this stage of console interoperability. No console has
tested it. Emulator results would not establish console or GP520 behavior.

## Scope and explicit exclusions

The implementation scope is deliberately limited to:

- one local TCP control-handshake request;
- one bounded HTTP header while preserving any already-read following bytes;
- one per-session DID generated from an injected CSPRNG;
- exact target/profile and codec selection;
- monotonic local and remote session-rpcrypt counters;
- an opaque post-handshake crypto owner for the next reviewed slice.

This slice excludes sockets, DNS, IPv6, port selection, retries, timers,
threads, queues, RUDP and PSN remote connection, continuous ctrl message
framing, session ID, login PIN, heartbeat, keyboard, microphone, display
messages, sleep/home commands, Takion, protobuf, media, controller feedback,
Android, Windows adapters, logging, telemetry, storage, and hardware access.
Transport retries must resend the exact bytes already produced; they must not
invoke encryption or request preparation again.

## Source and license boundary

The only proposed behavioral port sources are:

- `lib/include/chiaki/common.h`, limited to the four concrete RP target
  profiles and target predicates;
- `lib/include/chiaki/session.h` for the 32-byte device-identifier size;
- `lib/src/session.c:58-72` for target-to-RP-version mapping,
  `lib/src/session.c:252-256` for DID construction, and
  `lib/src/session.c:522-598,682` for ctrl-before-stream ordering;
- `lib/src/ctrl.c:1125-1154,1195-1309,1424-1472` for the static request
  layout, counter ordering, response handling, classification,
  trailing-byte preservation, and profile adjustment;
- the already integrated session-rpcrypt behavior from `lib/src/rpcrypt.c`;
- RFC 4648 for GPSx-authored production base64.

All chiaki-ng paths use the pinned commit and
`LicenseRef-AGPL-3.0-only-OpenSSL` accepted under Option C. GPSx will not copy
the C formatter, parser, base64, networking, logging, thread, queue, or retry
code. The external oracle must compile and execute pinned `http.c` and
`base64.c` and record the decoder-only diagnostic separately from the upstream
ctrl verdict. Those files are executed-oracle-only, not production port
sources. Request formatting remains a GPSx harness transcription recorded as
`compiled=false, executed=false`; the labeled `ctrl.c:1138-1153` response-loop
transcription is compiled and executed with `strlen(value) + 1` and a 16-byte
output buffer.

## Proposed ownership and API

The implementation remains in portable `gpsx-crypto`, in a private
`control_handshake` module. Keeping formatting, parsing, and encryption behind
one boundary prevents raw registration keys, device identifiers, rpcrypt key
material, or mutable counters from crossing crates. The crate remains
`#![forbid(unsafe_code)]` and performs no I/O.

The reviewed public surface is expected to be equivalent to:

```text
ControlProfile::{Ps4_8, Ps4_9, Ps4_10, Ps5}
ControlEndpoint::local_ipv4(Ipv4Addr) -> ControlEndpoint
ControlRegistrationKey::take_from_registration_buffer(
    &mut Zeroizing<[u8; 16]>,
) -> ControlRegistrationKey
SessionRpcrypt::begin_control(
    &mut self,
    ControlProfile,
    &mut (impl CryptoRng + RngCore),
) -> Result<ControlHandshake<'_>>
ControlHandshake::write_request(
    &mut self,
    ControlEndpoint,
    &ControlRegistrationKey,
    SessionCodec,
    &mut [u8],
) -> Result<ControlRequestLength>
ControlHandshake::finish(self, &[u8])
  -> Result<(ControlHandshakeResponse, ControlHeaderLength)>
HandshakeSession<AwaitingPeer>::write_encrypted_launch_spec(
    &mut self,
    &mut SessionRpcrypt,
    LaunchSpec,
    &mut [u8],
) -> Result<EncryptedLaunchSpecLength>
SessionRpcrypt::into_control_channel(self) -> Result<ControlChannelCrypto>
```

Names may change, but these invariants may not:

1. `SessionRpcrypt` owns separate single-use ctrl and LaunchSpec slots.
   `begin_control` borrows it mutably, opens the ctrl namespace once, and does
   not require or consume the LaunchSpec slot. RNG failure produces no DID or
   request output and does not open or advance either slot.
2. `finish` records the classified server type in the borrowed owner and ends
   the ctrl borrow. LaunchSpec then requires a completed ctrl response and
   validates target, codec, width, and height against that classification.
   `into_control_channel` consumes the owner and succeeds only after both ctrl
   and LaunchSpec complete. This enforces ctrl → LaunchSpec → channel crypto.
3. **GPSx fail-closed policy / intentional divergence with UNKNOWN
   compatibility risk:** known type `0` (base PS4) permits only a PS4 profile,
   AVC, `width <= 1280`, and `height <= 720`. Known type `1` (PS4 Pro) permits
   only a PS4 profile and AVC. Known type `2` permits only PS5 and its reviewed
   AVC/HEVC/HEVC-HDR choices. A decrypted first byte `>= 3` is an error. An
   absent or accepted short server type is `Unknown` and preserves the already
   selected compatible target and codec. Upstream instead conditionally
   downgrades exactly 1080p only when `video_profile_auto_downgrade` is set,
   unconditionally forces H.264 for types 0/1, and takes no action for type 2.
   Every classification rejection is atomic: output is unchanged and the
   LaunchSpec slot remains available for a compatible retry.
4. `ControlProfile` distinguishes PS4 8 and PS4 9 even though both map to the
   same `SessionTarget::Ps4Pre10` crypto tables. `begin_control` rejects a
   profile whose crypto target differs from the borrowed rpcrypt owner.
5. The DID is created internally for each `begin_control` from the fixed
   prefix, exactly 16 CSPRNG bytes, and six zero bytes. No DID constructor or
   raw DID input is public. The registration key is copied once from caller
   `Zeroizing` storage, which is immediately cleared.
6. The registration-key owner, rpcrypt owner, handshake/channel states, DID,
   and all plaintext/keystream temporaries are non-`Clone`, non-`Copy`,
   redacted, and zeroized. The endpoint is a numeric IPv4 address with fixed
   local port 9295. No caller
   string can enter the request, preventing request-line or header injection.
7. `write_request` borrows the registration-key owner and validates profile,
   codec, endpoint, output capacity, and all arithmetic before encryption.
   Caller output and counters remain unchanged on every error, and the same key
   owner remains available for retry without reloading secure storage. One
   successful call writes the complete request, commits the
   exact next local counter (`3`, `4`, or `5`), and permanently closes request
   preparation for that owner.
8. `finish` consumes the handshake and parses only through the first
   `\r\n\r\n`. The header itself is at most 512 bytes, 32 lines, and 256 bytes
   per line. Input may contain any number of following bytes; they are not
   counted against the header bound, parsed, rejected, copied, or modified.
   `ControlHeaderLength` identifies their exact starting offset.
9. The parser rejects bare LF, lone CR, a NUL before the header terminator,
   obs-fold, whitespace before a colon, invalid names/values, and malformed
   known headers. Security-sensitive headers are
   matched case-insensitively for
   duplicate detection but must use canonical spelling. Duplicate or
   case-variant `RP-Server-Type` and `RP-Prohibit` are rejected. Unknown valid
   headers are ignored within the same count and line bounds.
10. Status is exactly `HTTP/1.1 200`, followed either by CRLF or by one SP, a
    reason phrase whose first byte is VCHAR and whose remaining bytes are
    VCHAR or SP, and CRLF. Both
    `HTTP/1.1 200\r\n` and `HTTP/1.1 200 OK\r\n` are valid.
11. `RP-Prohibit` accepts only canonical `0` or `1`; every other value is
    rejected instead of inheriting C `atoi` behavior. This is an intentional
    parser hardening, not an upstream behavior claim.
12. `RP-Server-Type` follows the exact base64 policy below. A 16-byte value
    consumes remote counter 0 once; every accepted short or absent value leaves
    it unconsumed.
13. `ControlChannelCrypto` exposes no raw key, IV, counter getter, arbitrary
    encrypt/decrypt method, serializer, constructor from bytes, or `Clone`.
    Its next local/remote counters are available only to a future reviewed ctrl
    message API.
14. Errors contain only public classes and byte offsets. They never contain
    credentials, device identifiers, header values, ciphertext, plaintext,
    derived bytes, host addresses, or response fragments.

## Canonical request contract

The request is ASCII and uses CRLF only, in this order:

```text
GET <target path> HTTP/1.1
Host: <numeric IPv4>:9295
User-Agent: remoteplay Windows
Connection: keep-alive
Content-Length: 0
RP-Auth: <base64 encrypted 16-byte registration key>
RP-Version: <target version>
RP-Did: <base64 encrypted per-session 32-byte DID>
RP-ControllerType: 3
RP-ClientType: 11
RP-OSType: <base64 encrypted "Win10.0.0\0">
RP-ConPath: 1
[RP-StartBitrate: <base64 encrypted LE32 zero>]
[RP-StreamingType: <base64 encrypted LE32 codec value>]

```

The formatter uses a fixed 512-byte zeroizing scratch buffer and reports a
length excluding any convenience terminator. The output capacity must equal or
exceed the exact request length; bytes after the returned range are untouched.

Production decoding deliberately accepts only canonical RFC 4648 spelling.
Padding and zero padding bits are required whenever RFC 4648 requires them.
Decoded length 16 is
decrypted and classified. The upstream-accepted canonical padded short lengths
`1, 2, 4, 5, 7, 8, 10, 11, 13, 14` become `Unknown` without counter use.
Every other decoded length, unpadded spelling, embedded newline, whitespace,
non-alphabet byte, and junk after `=` is an error. The external oracle covers
decoded lengths 1 through 19 in padded and unpadded forms plus trailing junk.

**FACT / intentional GPSx divergences with UNKNOWN compatibility risk:** the
pinned header parser treats names case-sensitively (so variants are ignored),
treats whitespace before `:` as part of another name, prepends duplicates and
therefore reverses their order, and strips only one post-colon space. Its
`strtol` status parse accepts forms including `0200`, leading whitespace, and a
missing reason phrase. It accepts bare LF and lone CR as line endings and
silently stops at NUL. Its base64 decoder skips only newline, receives
`strlen + 1` including NUL, accepts non-zero padding bits such as `AB==`, and
stops at `=` while ignoring later junk. GPSx instead applies the strict rules
above. It also rejects horizontal-tab or absent post-colon whitespace, empty
header values, and duplicate `RP-Server-Type` or `RP-Prohibit` fields. These
strictness choices have dedicated tests and remain interoperability risks until
console evidence exists.

## Counter and residual-risk contract

The local TCP request consumes counters exactly as follows:

| Profile | Counter 0 | Counter 1 | Counter 2 | Counter 3 | Counter 4 | Next local |
|---|---|---|---|---|---|---:|
| PS4 8/9 | auth | device ID | OS type | — | — | 3 |
| PS4 10 | auth | device ID | OS type | bitrate | — | 4 |
| PS5 | auth | device ID | OS type | bitrate | streaming type | 5 |

The response consumes remote counter 0 only for a valid decoded 16-byte
`RP-Server-Type`. A transport timeout does not alter either counter because the
transport resends the previously prepared bytes.

**FACT:** the first 16 LaunchSpec plaintext bytes are always
`{"sessionId":"se`. LaunchSpec's zero-buffer CFB construction and both ctrl
directions reuse the session-rpcrypt counter-zero first keystream block. An
observer who can see the LaunchSpec ciphertext can therefore recover that
keystream block and reveal the first ctrl plaintext block: the complete
`RP-Auth` registration key and the server-type block.

**INFERENCE:** the registration key is already sent as plaintext hexadecimal in
the earlier session request, so this recovery probably creates no new exposure
for that key. **UNKNOWN:** whether LaunchSpec ciphertext is visible to a LAN
observer; this plan has not verified Takion transport protection. The type
system prevents extra uses within one owner but cannot repair protocol-imposed
reuse or prevent reconstruction from copied inputs.

## Required closed oracle

Implementation requires a tracked
`tests/fixtures/control_handshake_oracle_v1.json` generated outside the
repository with a closed build record. It must contain only synthetic public
inputs and cover:

- PS4 8, PS4 9, PS4 10, and PS5 with AVC, plus PS5 HEVC and HEVC HDR;
- distinct registration keys, injected CSPRNG DID bytes, nonces, and `morning`
  values that discriminate every case and counter;
- every plaintext field, counter, IV, ciphertext, base64 value, complete
  request, request length, and next local counter;
- responses for server types 0, 1, and 2, both prohibit values, an absent
  server type, all decoded lengths 1 through 19 in padded/unpadded form, and
  junk after `=`;
- the decrypted 16-byte server-type block, classified first byte, and exact
  next remote counter.

For every response vector, the external record must compile and execute pinned
`http.c` and `base64.c`; the fixture records decoder-only diagnostics separately
from upstream ctrl success, server-type validity and prohibit. It marks request
formatting as GPSx harness code with `compiled=false, executed=false` and the
labeled `ctrl.c:1138-1153` response-loop transcription as
`compiled=true, executed=true`. It hashes every input, translation unit,
compiler product, command, checkout, patch, harness, raw output, and fixture.
Harnesses, logs, source checkouts, build products, and the closed manifest stay
ignored under `target/control-handshake-audit/`.

Rust must rebuild the complete canonical JSON document and require exact byte
equality. Every fixture case must separately call the public request path and
compare its length and full request bytes. Mutations must cover every field,
counter, profile, codec mapping, header order, CRLF, NUL, base64 padding,
request length, response classification, prohibit value, next counter, an
extra case, and bytes appended after the closing JSON brace.

## Test and implementation gate

Before integration, all of the following are required:

1. Focused independent re-check and `GO` on D1–D4 in both languages, the
   source boundary, and the three newly inventoried paths.
2. Exact request tests for all six oracle cases, maximum and one-byte-short
   output capacities, and atomic failure behavior.
3. State tests enforcing ctrl → LaunchSpec → channel crypto; boundaries
   720/721 and 1280/1281; type-2 with a PS4 LaunchSpec; first bytes 3 and 255;
   successful compatible retry on the same owner after atomic rejection; and
   mutants removing or relaxing every ordering/classification rule.
4. Retry tests proving the same prepared bytes may be sent twice without
   another encryption or counter change.
5. DID tests for the exact prefix/random/suffix, RNG failure with no output or
   state change, and a killed prefix mutant.
6. Parser tests for header plus 1..N arbitrary trailing bytes, exact returned
   header length, every bound/divergence, bare LF, lone CR, embedded NUL,
   multi-word reason phrases, decoded lengths 1..19 padded and unpadded,
   non-zero padding bits, junk after `=`, deterministic arbitrary inputs, and
   a fuzz target that proves successful header length ends at the first
   terminator and excludes following bytes, and never panics. Matching
   oracle vectors must record the upstream verdict.
7. Redaction/zeroization inspection and tests for credentials, temporaries,
   channel state, errors, `Debug`, and `Display`.
8. ProvenanceGuard, fixture secret scan, sorted manifest, a closed record that
   executes `http.c` and `base64.c` for every response, and independent oracle
   recomputation.
9. `cargo +1.85.0 fmt --all --check`, Clippy with warnings denied,
   `cargo +1.85.0 test --workspace --locked`, Android cross-compile,
   `cargo audit`, `cargo deny check`, and duplicate-dependency inspection.
10. Independent implementation re-review with mutant results before marking
    the slice integrated.

N1, the complete third-party licence bundle, remains mandatory before binary
distribution. Console interoperability and GP520 performance remain
`UNKNOWN / NOT_TESTED`; hardware evidence is `NONE`.
