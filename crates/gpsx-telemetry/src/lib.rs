// SPDX-License-Identifier: AGPL-3.0-only
#![forbid(unsafe_code)]

//! Measurement primitives and redaction. Serialization and platform sinks remain adapters.

use std::fmt::{self, Write as _};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

pub const MAX_DIAGNOSTIC_SAMPLES: usize = 4_096;
const MAX_U64_BYTES: usize = 20;
const MAX_SCIENTIFIC_F64_BYTES: usize = "-2.2250738585072014e-308".len();
const MAX_RUN_LINE_BYTES: usize =
    "{\"schema\":\"gpsx.diagnostics.v1\",\"record\":\"run\",\"source_revision\":\"".len()
        + 40
        + "\",\"environment\":\"".len()
        + "android_emulator".len()
        + "\",\"run_kind\":\"".len()
        + "synthetic".len()
        + "\",\"sample_count\":".len()
        + "4096".len()
        + ",\"started_unix_ms\":".len()
        + MAX_U64_BYTES
        + "}".len();
const MAX_METRIC_LINE_BYTES: usize =
    "{\"schema\":\"gpsx.diagnostics.v1\",\"record\":\"metric\",\"metric\":\"".len()
        + "network.packets_reordered".len()
        + "\",\"value\":".len()
        + MAX_SCIENTIFIC_F64_BYTES
        + ",\"unit\":\"".len()
        + "celsius".len()
        + "\",\"monotonic_ns\":".len()
        + MAX_U64_BYTES
        + ",\"wall_unix_ms\":".len()
        + MAX_U64_BYTES
        + ",\"evidence\":\"".len()
        + "not_tested".len()
        + "\"}".len();
pub const MAX_DIAGNOSTIC_JSONL_BYTES: usize =
    (MAX_RUN_LINE_BYTES + 1) + MAX_DIAGNOSTIC_SAMPLES * (MAX_METRIC_LINE_BYTES + 1);

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum EvidenceClass {
    Fact,
    Inference,
    Hypothesis,
    Unknown,
    NotTested,
}

impl EvidenceClass {
    const fn as_str(self) -> &'static str {
        match self {
            Self::Fact => "fact",
            Self::Inference => "inference",
            Self::Hypothesis => "hypothesis",
            Self::Unknown => "unknown",
            Self::NotTested => "not_tested",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum MetricName {
    NetworkRtt,
    NetworkJitter,
    NetworkPacketsLost,
    NetworkPacketsReordered,
    NetworkBitrate,
    VideoFramesPerSecond,
    VideoReceiveToSubmit,
    VideoSubmitToPresent,
    VideoDroppedFrames,
    VideoQueueDepth,
    AudioBufferDepth,
    AudioUnderruns,
    AudioDrift,
    AudioRouteChanges,
    InputEventInterval,
    InputToResponse,
    RuntimeCpuPercent,
    RuntimeMemoryBytes,
    RuntimeJniCalls,
    RuntimeThermalCelsius,
}

impl MetricName {
    const fn as_str(self) -> &'static str {
        match self {
            Self::NetworkRtt => "network.rtt",
            Self::NetworkJitter => "network.jitter",
            Self::NetworkPacketsLost => "network.packets_lost",
            Self::NetworkPacketsReordered => "network.packets_reordered",
            Self::NetworkBitrate => "network.bitrate",
            Self::VideoFramesPerSecond => "video.frames_per_second",
            Self::VideoReceiveToSubmit => "video.receive_to_submit",
            Self::VideoSubmitToPresent => "video.submit_to_present",
            Self::VideoDroppedFrames => "video.dropped_frames",
            Self::VideoQueueDepth => "video.queue_depth",
            Self::AudioBufferDepth => "audio.buffer_depth",
            Self::AudioUnderruns => "audio.underruns",
            Self::AudioDrift => "audio.drift",
            Self::AudioRouteChanges => "audio.route_changes",
            Self::InputEventInterval => "input.event_interval",
            Self::InputToResponse => "input.to_response",
            Self::RuntimeCpuPercent => "runtime.cpu_percent",
            Self::RuntimeMemoryBytes => "runtime.memory_bytes",
            Self::RuntimeJniCalls => "runtime.jni_calls",
            Self::RuntimeThermalCelsius => "runtime.thermal_celsius",
        }
    }

    const fn unit(self) -> &'static str {
        match self {
            Self::NetworkRtt
            | Self::NetworkJitter
            | Self::VideoReceiveToSubmit
            | Self::VideoSubmitToPresent
            | Self::AudioBufferDepth
            | Self::AudioDrift
            | Self::InputEventInterval
            | Self::InputToResponse => "ms",
            Self::NetworkPacketsLost
            | Self::NetworkPacketsReordered
            | Self::VideoDroppedFrames
            | Self::AudioUnderruns
            | Self::AudioRouteChanges
            | Self::RuntimeJniCalls => "count",
            Self::NetworkBitrate => "bit/s",
            Self::VideoFramesPerSecond => "fps",
            Self::VideoQueueDepth => "frames",
            Self::RuntimeCpuPercent => "percent",
            Self::RuntimeMemoryBytes => "bytes",
            Self::RuntimeThermalCelsius => "celsius",
        }
    }
}

#[derive(Clone, Debug, PartialEq)]
pub struct MetricSample {
    pub name: MetricName,
    pub value: f64,
    /// Monotonic capture time from the adapter clock; use this for ordering and intervals.
    pub monotonic_ns: u64,
    /// Wall-clock time as metadata only; it can jump (NTP, manual changes).
    pub wall_clock: Option<SystemTime>,
    pub class: EvidenceClass,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DiagnosticEnvironment {
    Windows,
    AndroidEmulator,
    AndroidGeneric,
    Gp520,
}

impl DiagnosticEnvironment {
    const fn as_str(self) -> &'static str {
        match self {
            Self::Windows => "windows",
            Self::AndroidEmulator => "android_emulator",
            Self::AndroidGeneric => "android_generic",
            Self::Gp520 => "gp520",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DiagnosticRunKind {
    Synthetic,
    Baseline,
    Candidate,
}

impl DiagnosticRunKind {
    const fn as_str(self) -> &'static str {
        match self {
            Self::Synthetic => "synthetic",
            Self::Baseline => "baseline",
            Self::Candidate => "candidate",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct DiagnosticRunMetadata<'a> {
    /// `development` for local builds, otherwise a lowercase 40-character Git SHA.
    pub source_revision: &'a str,
    pub environment: DiagnosticEnvironment,
    pub run_kind: DiagnosticRunKind,
    pub started_unix_ms: Option<u64>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DiagnosticExportError {
    TooManySamples,
    InvalidSourceRevision,
    SyntheticHardwareEnvironment,
    SyntheticFactEvidence,
    NonFiniteValue,
    NonMonotonicSamples,
    WallClockBeforeUnixEpoch,
    WallClockOverflow,
    OutputTooLarge,
    Formatting,
}

impl fmt::Display for DiagnosticExportError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::TooManySamples => "too many diagnostic samples",
            Self::InvalidSourceRevision => "invalid source revision",
            Self::SyntheticHardwareEnvironment => {
                "synthetic diagnostics cannot claim a hardware environment"
            }
            Self::SyntheticFactEvidence => "synthetic diagnostics cannot contain fact evidence",
            Self::NonFiniteValue => "non-finite diagnostic value",
            Self::NonMonotonicSamples => "diagnostic sample times are not monotonic",
            Self::WallClockBeforeUnixEpoch => "diagnostic wall clock precedes Unix epoch",
            Self::WallClockOverflow => "diagnostic wall clock exceeds supported range",
            Self::OutputTooLarge => "diagnostic JSONL exceeds byte limit",
            Self::Formatting => "diagnostic JSONL formatting failed",
        })
    }
}

impl std::error::Error for DiagnosticExportError {}

/// Encodes a bounded, deterministic JSONL diagnostic report.
///
/// The format accepts no free-form field names, identifiers, URLs, credentials, or device
/// metadata. Adapters persist or share the returned bytes; this portable layer performs no I/O.
/// Semantic input is validated before output construction. Defensive size checks continue while
/// formatting, and every failure returns no partial report. The function is intended for offline
/// reports, never for a streaming hot path.
pub fn encode_diagnostic_jsonl(
    metadata: DiagnosticRunMetadata<'_>,
    samples: &[MetricSample],
) -> Result<String, DiagnosticExportError> {
    validate_diagnostic_input(metadata, samples)?;

    let mut output = String::new();
    let mut line = String::new();
    write!(
        line,
        "{{\"schema\":\"gpsx.diagnostics.v1\",\"record\":\"run\",\"source_revision\":\"{}\",\"environment\":\"{}\",\"run_kind\":\"{}\",\"sample_count\":{},\"started_unix_ms\":",
        metadata.source_revision,
        metadata.environment.as_str(),
        metadata.run_kind.as_str(),
        samples.len(),
    )
    .map_err(|_| DiagnosticExportError::Formatting)?;
    write_optional_u64(&mut line, metadata.started_unix_ms)?;
    line.push('}');
    append_bounded_line(&mut output, &line, MAX_RUN_LINE_BYTES)?;

    for sample in samples {
        line.clear();
        write_metric_line(&mut line, sample, wall_clock_millis(sample.wall_clock)?)?;
        append_bounded_line(&mut output, &line, MAX_METRIC_LINE_BYTES)?;
    }

    Ok(output)
}

fn validate_diagnostic_input(
    metadata: DiagnosticRunMetadata<'_>,
    samples: &[MetricSample],
) -> Result<(), DiagnosticExportError> {
    if samples.len() > MAX_DIAGNOSTIC_SAMPLES {
        return Err(DiagnosticExportError::TooManySamples);
    }
    let revision_is_sha = metadata.source_revision.len() == 40
        && metadata
            .source_revision
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte));
    if metadata.source_revision != "development" && !revision_is_sha {
        return Err(DiagnosticExportError::InvalidSourceRevision);
    }
    if metadata.run_kind == DiagnosticRunKind::Synthetic
        && matches!(
            metadata.environment,
            DiagnosticEnvironment::AndroidGeneric | DiagnosticEnvironment::Gp520
        )
    {
        return Err(DiagnosticExportError::SyntheticHardwareEnvironment);
    }
    if metadata.run_kind == DiagnosticRunKind::Synthetic
        && samples
            .iter()
            .any(|sample| sample.class == EvidenceClass::Fact)
    {
        return Err(DiagnosticExportError::SyntheticFactEvidence);
    }
    if samples
        .windows(2)
        .any(|pair| pair[1].monotonic_ns < pair[0].monotonic_ns)
    {
        return Err(DiagnosticExportError::NonMonotonicSamples);
    }
    for sample in samples {
        if !sample.value.is_finite() {
            return Err(DiagnosticExportError::NonFiniteValue);
        }
        wall_clock_millis(sample.wall_clock)?;
    }
    Ok(())
}

fn wall_clock_millis(wall_clock: Option<SystemTime>) -> Result<Option<u64>, DiagnosticExportError> {
    wall_clock
        .map(|time| {
            let duration = time
                .duration_since(UNIX_EPOCH)
                .map_err(|_| DiagnosticExportError::WallClockBeforeUnixEpoch)?;
            u64::try_from(duration.as_millis())
                .map_err(|_| DiagnosticExportError::WallClockOverflow)
        })
        .transpose()
}

fn write_optional_u64(
    output: &mut String,
    value: Option<u64>,
) -> Result<(), DiagnosticExportError> {
    match value {
        Some(value) => write!(output, "{value}").map_err(|_| DiagnosticExportError::Formatting),
        None => {
            output.push_str("null");
            Ok(())
        }
    }
}

fn write_metric_line(
    line: &mut String,
    sample: &MetricSample,
    wall_unix_ms: Option<u64>,
) -> Result<(), DiagnosticExportError> {
    write!(
        line,
        "{{\"schema\":\"gpsx.diagnostics.v1\",\"record\":\"metric\",\"metric\":\"{}\",\"value\":{:e},\"unit\":\"{}\",\"monotonic_ns\":{},\"wall_unix_ms\":",
        sample.name.as_str(),
        sample.value,
        sample.name.unit(),
        sample.monotonic_ns,
    )
    .map_err(|_| DiagnosticExportError::Formatting)?;
    write_optional_u64(line, wall_unix_ms)?;
    write!(line, ",\"evidence\":\"{}\"}}", sample.class.as_str())
        .map_err(|_| DiagnosticExportError::Formatting)
}

fn append_bounded_line(
    output: &mut String,
    line: &str,
    max_line_bytes: usize,
) -> Result<(), DiagnosticExportError> {
    if line.len() > max_line_bytes {
        return Err(DiagnosticExportError::OutputTooLarge);
    }
    let required = output
        .len()
        .checked_add(line.len())
        .and_then(|length| length.checked_add(1))
        .ok_or(DiagnosticExportError::OutputTooLarge)?;
    if required > MAX_DIAGNOSTIC_JSONL_BYTES {
        return Err(DiagnosticExportError::OutputTooLarge);
    }
    output.push_str(line);
    output.push('\n');
    Ok(())
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct LatencySummary {
    pub p50: Duration,
    pub p95: Duration,
    pub p99: Duration,
}

/// Computes nearest-rank percentiles over a copy of the samples.
///
/// Allocates and sorts: intended for offline reports, not for hot paths, which record
/// into fixed histograms instead.
#[must_use]
pub fn summarize_latency(samples: &[Duration]) -> Option<LatencySummary> {
    if samples.is_empty() {
        return None;
    }
    let mut sorted = samples.to_vec();
    sorted.sort_unstable();
    Some(LatencySummary {
        p50: percentile(&sorted, 50),
        p95: percentile(&sorted, 95),
        p99: percentile(&sorted, 99),
    })
}

fn percentile(sorted: &[Duration], percentile: usize) -> Duration {
    let rank = (percentile * sorted.len()).div_ceil(100);
    sorted[rank.saturating_sub(1)]
}

pub const REDACTED: &str = "[REDACTED]";

/// Wraps a secret so that `Debug` and `Display` never print it.
#[derive(Clone, Copy, Eq, PartialEq)]
pub struct Redacted<T>(pub T);

impl<T> fmt::Debug for Redacted<T> {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(REDACTED)
    }
}

impl<T> fmt::Display for Redacted<T> {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(REDACTED)
    }
}

// Keep these rules identical to tools/python/gpsx_evidence/redaction.py; both test suites
// check tests/redaction/key_cases.tsv.
const STRONG_PARTS: &[&str] = &[
    "token",
    "password",
    "passwd",
    "passphrase",
    "secret",
    "cookie",
    "credential",
    "bearer",
];
const EXACT_TOKENS: &[&str] = &[
    "pin",
    "nonce",
    "serial",
    "uniq",
    "mac",
    "otp",
    "psk",
    "authorization",
    "signature",
    "sessionkey",
    "rpkey",
    "registkey",
    "registrationkey",
    "accountid",
    "onlineid",
    "hostid",
    "macaddress",
    "authcode",
];
const PAIRS: &[(&str, &str)] = &[
    ("session", "key"),
    ("rp", "key"),
    ("regist", "key"),
    ("registration", "key"),
    ("private", "key"),
    ("api", "key"),
    ("account", "id"),
    ("online", "id"),
    ("host", "id"),
    ("psn", "id"),
    ("user", "id"),
    ("auth", "code"),
    ("authorization", "code"),
];
const WHOLE_KEYS: &[&str] = &["code", "key", "auth", "sid"];

/// Splits a key into lowercase words at camelCase boundaries and non-alphanumerics.
fn key_tokens(key: &str) -> Vec<String> {
    let chars: Vec<char> = key.chars().collect();
    let mut tokens = Vec::new();
    let mut current = String::new();
    for (index, &character) in chars.iter().enumerate() {
        if !character.is_alphanumeric() {
            if !current.is_empty() {
                tokens.push(std::mem::take(&mut current));
            }
            continue;
        }
        let previous = index.checked_sub(1).map(|i| chars[i]);
        let following = chars.get(index + 1);
        let boundary = character.is_uppercase()
            && previous.is_some_and(|p| {
                p.is_lowercase()
                    || p.is_ascii_digit()
                    || (p.is_uppercase() && following.is_some_and(|f| f.is_lowercase()))
            });
        if boundary && !current.is_empty() {
            tokens.push(std::mem::take(&mut current));
        }
        current.extend(character.to_lowercase());
    }
    if !current.is_empty() {
        tokens.push(current);
    }
    tokens
}

/// Returns whether a field or parameter name denotes a secret or identifying value.
#[must_use]
pub fn is_sensitive_key(key: &str) -> bool {
    let tokens = key_tokens(key);
    if matches!(tokens.as_slice(), [only] if WHOLE_KEYS.contains(&only.as_str())) {
        return true;
    }
    tokens.iter().any(|token| {
        EXACT_TOKENS.contains(&token.as_str()) || STRONG_PARTS.iter().any(|p| token.contains(p))
    }) || tokens
        .windows(2)
        .any(|pair| PAIRS.contains(&(pair[0].as_str(), pair[1].as_str())))
}

#[cfg(test)]
mod tests {
    use super::{
        DiagnosticEnvironment, DiagnosticExportError, DiagnosticRunKind, DiagnosticRunMetadata,
        Duration, EvidenceClass, MAX_DIAGNOSTIC_JSONL_BYTES, MAX_DIAGNOSTIC_SAMPLES,
        MAX_METRIC_LINE_BYTES, MAX_RUN_LINE_BYTES, MetricName, MetricSample, Redacted, UNIX_EPOCH,
        append_bounded_line, encode_diagnostic_jsonl, is_sensitive_key, summarize_latency,
        write_metric_line,
    };

    fn ms(values: &[u64]) -> Vec<Duration> {
        values.iter().copied().map(Duration::from_millis).collect()
    }

    fn metadata() -> DiagnosticRunMetadata<'static> {
        DiagnosticRunMetadata {
            source_revision: "development",
            environment: DiagnosticEnvironment::AndroidEmulator,
            run_kind: DiagnosticRunKind::Synthetic,
            started_unix_ms: Some(1_000),
        }
    }

    fn sample() -> MetricSample {
        MetricSample {
            name: MetricName::NetworkRtt,
            value: 4.8,
            monotonic_ns: 42,
            wall_clock: Some(UNIX_EPOCH + Duration::from_millis(1_234)),
            class: EvidenceClass::Hypothesis,
        }
    }

    #[test]
    fn empty_latency_series_has_no_summary() {
        assert_eq!(summarize_latency(&[]), None);
    }

    #[test]
    fn reports_nearest_rank_percentiles() {
        let samples = (1..=100)
            .rev()
            .map(Duration::from_millis)
            .collect::<Vec<_>>();
        let summary = summarize_latency(&samples).expect("non-empty samples");
        assert_eq!(summary.p50, Duration::from_millis(50));
        assert_eq!(summary.p95, Duration::from_millis(95));
        assert_eq!(summary.p99, Duration::from_millis(99));
    }

    #[test]
    fn single_sample_is_every_percentile() {
        let summary = summarize_latency(&ms(&[7])).expect("non-empty");
        assert_eq!(
            (summary.p50, summary.p95, summary.p99),
            (ms(&[7])[0], ms(&[7])[0], ms(&[7])[0])
        );
    }

    #[test]
    fn small_unsorted_series_with_duplicates_uses_nearest_rank() {
        // Sorted: 1 2 2 3 4 5 6 7 8 100. Nearest rank: p50 -> 5th, p95/p99 -> 10th.
        let summary = summarize_latency(&ms(&[8, 2, 100, 4, 1, 7, 2, 6, 3, 5])).expect("non-empty");
        assert_eq!(summary.p50, Duration::from_millis(4));
        assert_eq!(summary.p95, Duration::from_millis(100));
        assert_eq!(summary.p99, Duration::from_millis(100));
    }

    #[test]
    fn redacted_never_formats_its_value() {
        let secret = Redacted("hunter2");
        assert_eq!(format!("{secret} {secret:?}"), "[REDACTED] [REDACTED]");
    }

    #[test]
    fn key_classification_matches_shared_corpus() {
        let corpus = include_str!("../../../tests/redaction/key_cases.tsv");
        let mut checked = 0;
        for line in corpus
            .lines()
            .filter(|l| !l.is_empty() && !l.starts_with('#'))
        {
            let (key, expected) = line.split_once('\t').expect("key<TAB>class");
            let sensitive = match expected {
                "sensitive" => true,
                "safe" => false,
                other => panic!("unknown class {other}"),
            };
            assert_eq!(is_sensitive_key(key), sensitive, "{key}");
            checked += 1;
        }
        assert!(checked > 40, "corpus unexpectedly small");
    }

    #[test]
    fn diagnostic_jsonl_is_exact_and_deterministic() {
        let expected = concat!(
            "{\"schema\":\"gpsx.diagnostics.v1\",\"record\":\"run\",\"source_revision\":\"development\",\"environment\":\"android_emulator\",\"run_kind\":\"synthetic\",\"sample_count\":1,\"started_unix_ms\":1000}\n",
            "{\"schema\":\"gpsx.diagnostics.v1\",\"record\":\"metric\",\"metric\":\"network.rtt\",\"value\":4.8e0,\"unit\":\"ms\",\"monotonic_ns\":42,\"wall_unix_ms\":1234,\"evidence\":\"hypothesis\"}\n",
        );
        let first = encode_diagnostic_jsonl(metadata(), &[sample()]).expect("valid report");
        let second = encode_diagnostic_jsonl(metadata(), &[sample()]).expect("valid report");
        assert_eq!(first, expected);
        assert_eq!(first, second);
    }

    #[test]
    fn diagnostic_jsonl_serializes_every_evidence_class() {
        let classes = [
            EvidenceClass::Fact,
            EvidenceClass::Inference,
            EvidenceClass::Hypothesis,
            EvidenceClass::Unknown,
            EvidenceClass::NotTested,
        ];
        let samples = classes.map(|class| MetricSample { class, ..sample() });
        let report = encode_diagnostic_jsonl(
            DiagnosticRunMetadata {
                run_kind: DiagnosticRunKind::Baseline,
                ..metadata()
            },
            &samples,
        )
        .expect("valid report");
        for label in ["fact", "inference", "hypothesis", "unknown", "not_tested"] {
            assert!(report.contains(&format!("\"evidence\":\"{label}\"")));
        }
    }

    #[test]
    fn diagnostic_jsonl_uses_closed_metric_names_and_units() {
        let samples = [
            MetricSample {
                name: MetricName::VideoFramesPerSecond,
                value: 59.9,
                ..sample()
            },
            MetricSample {
                name: MetricName::RuntimeMemoryBytes,
                value: 1024.0,
                ..sample()
            },
        ];
        let report = encode_diagnostic_jsonl(metadata(), &samples).expect("valid report");
        assert!(
            report.contains(
                "\"metric\":\"video.frames_per_second\",\"value\":5.99e1,\"unit\":\"fps\""
            )
        );
        assert!(
            report.contains(
                "\"metric\":\"runtime.memory_bytes\",\"value\":1.024e3,\"unit\":\"bytes\""
            )
        );
    }

    #[test]
    fn diagnostic_jsonl_enforces_sample_and_byte_bounds() {
        let worst_sample = MetricSample {
            name: MetricName::NetworkPacketsReordered,
            value: -f64::MIN_POSITIVE,
            monotonic_ns: u64::MAX,
            wall_clock: Some(UNIX_EPOCH + Duration::from_secs(253_402_300_799)),
            class: EvidenceClass::NotTested,
        };
        let samples = vec![worst_sample; MAX_DIAGNOSTIC_SAMPLES];
        let worst_metadata = DiagnosticRunMetadata {
            source_revision: "0123456789abcdef0123456789abcdef01234567",
            run_kind: DiagnosticRunKind::Candidate,
            started_unix_ms: Some(u64::MAX),
            ..metadata()
        };
        let report =
            encode_diagnostic_jsonl(worst_metadata, &samples).expect("maximum is accepted");
        assert!(report.len() <= MAX_DIAGNOSTIC_JSONL_BYTES);
        let mut lines = report.lines();
        assert!(lines.next().expect("run line").len() <= MAX_RUN_LINE_BYTES);
        assert!(lines.all(|line| line.len() <= MAX_METRIC_LINE_BYTES));

        let mut maximum_numeric_line = String::new();
        write_metric_line(&mut maximum_numeric_line, &samples[0], Some(u64::MAX))
            .expect("maximum numeric fields are formattable");
        assert!(maximum_numeric_line.len() <= MAX_METRIC_LINE_BYTES);

        let too_many = vec![sample(); MAX_DIAGNOSTIC_SAMPLES + 1];
        assert_eq!(
            encode_diagnostic_jsonl(metadata(), &too_many),
            Err(DiagnosticExportError::TooManySamples),
        );

        let mut output = String::new();
        assert_eq!(
            append_bounded_line(
                &mut output,
                &"x".repeat(MAX_METRIC_LINE_BYTES + 1),
                MAX_METRIC_LINE_BYTES,
            ),
            Err(DiagnosticExportError::OutputTooLarge),
        );
    }

    #[test]
    fn diagnostic_jsonl_line_bound_covers_every_closed_label() {
        for name in [
            MetricName::NetworkRtt,
            MetricName::NetworkJitter,
            MetricName::NetworkPacketsLost,
            MetricName::NetworkPacketsReordered,
            MetricName::NetworkBitrate,
            MetricName::VideoFramesPerSecond,
            MetricName::VideoReceiveToSubmit,
            MetricName::VideoSubmitToPresent,
            MetricName::VideoDroppedFrames,
            MetricName::VideoQueueDepth,
            MetricName::AudioBufferDepth,
            MetricName::AudioUnderruns,
            MetricName::AudioDrift,
            MetricName::AudioRouteChanges,
            MetricName::InputEventInterval,
            MetricName::InputToResponse,
            MetricName::RuntimeCpuPercent,
            MetricName::RuntimeMemoryBytes,
            MetricName::RuntimeJniCalls,
            MetricName::RuntimeThermalCelsius,
        ] {
            assert!(name.as_str().len() <= "network.packets_reordered".len());
            assert!(name.unit().len() <= "celsius".len());
        }
        for class in [
            EvidenceClass::Fact,
            EvidenceClass::Inference,
            EvidenceClass::Hypothesis,
            EvidenceClass::Unknown,
            EvidenceClass::NotTested,
        ] {
            assert!(class.as_str().len() <= "not_tested".len());
        }
    }

    #[test]
    fn scientific_values_round_trip_exactly_including_negative_zero() {
        for (value, expected) in [
            (-0.0, "-0e0"),
            (0.0, "0e0"),
            (f64::from_bits(1), "5e-324"),
            (-f64::from_bits(1), "-5e-324"),
            (f64::MIN_POSITIVE, "2.2250738585072014e-308"),
            (-f64::MIN_POSITIVE, "-2.2250738585072014e-308"),
            (f64::MAX, "1.7976931348623157e308"),
            (f64::MIN, "-1.7976931348623157e308"),
            (0.1 + 0.2, "3.0000000000000004e-1"),
            (1e21, "1e21"),
            (1e-7, "1e-7"),
            (1.0, "1e0"),
            (-1.0, "-1e0"),
        ] {
            let encoded = format!("{value:e}");
            assert_eq!(encoded, expected);
            let decoded = encoded.parse::<f64>().expect("valid scientific number");
            assert_eq!(decoded.to_bits(), value.to_bits(), "{encoded}");
        }
    }

    #[test]
    fn diagnostic_jsonl_rejects_non_finite_values() {
        for value in [f64::NAN, f64::INFINITY, f64::NEG_INFINITY] {
            assert_eq!(
                encode_diagnostic_jsonl(metadata(), &[MetricSample { value, ..sample() }]),
                Err(DiagnosticExportError::NonFiniteValue),
            );
        }
    }

    #[test]
    fn diagnostic_jsonl_rejects_invalid_revision_without_echoing_it() {
        for source_revision in [
            "SECRET-TOKEN",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "Development",
        ] {
            let invalid = DiagnosticRunMetadata {
                source_revision,
                ..metadata()
            };
            let error =
                encode_diagnostic_jsonl(invalid, &[]).expect_err("revision must be rejected");
            assert_eq!(error, DiagnosticExportError::InvalidSourceRevision);
            assert!(!error.to_string().contains(source_revision));
        }

        let valid = DiagnosticRunMetadata {
            source_revision: "0123456789abcdef0123456789abcdef01234567",
            ..metadata()
        };
        assert!(encode_diagnostic_jsonl(valid, &[]).is_ok());
    }

    #[test]
    fn diagnostic_jsonl_rejects_wall_clock_before_unix_epoch() {
        let wall_clock = UNIX_EPOCH
            .checked_sub(Duration::from_millis(1))
            .expect("representable");
        assert_eq!(
            encode_diagnostic_jsonl(
                metadata(),
                &[MetricSample {
                    wall_clock: Some(wall_clock),
                    ..sample()
                }],
            ),
            Err(DiagnosticExportError::WallClockBeforeUnixEpoch),
        );
    }

    #[test]
    fn diagnostic_jsonl_enforces_evidence_environment_combinations() {
        for environment in [
            DiagnosticEnvironment::Windows,
            DiagnosticEnvironment::AndroidEmulator,
            DiagnosticEnvironment::AndroidGeneric,
            DiagnosticEnvironment::Gp520,
        ] {
            for run_kind in [
                DiagnosticRunKind::Synthetic,
                DiagnosticRunKind::Baseline,
                DiagnosticRunKind::Candidate,
            ] {
                for class in [
                    EvidenceClass::Fact,
                    EvidenceClass::Inference,
                    EvidenceClass::Hypothesis,
                    EvidenceClass::Unknown,
                    EvidenceClass::NotTested,
                ] {
                    let result = encode_diagnostic_jsonl(
                        DiagnosticRunMetadata {
                            environment,
                            run_kind,
                            started_unix_ms: None,
                            ..metadata()
                        },
                        &[MetricSample { class, ..sample() }],
                    );
                    let expected_error = if run_kind == DiagnosticRunKind::Synthetic
                        && matches!(
                            environment,
                            DiagnosticEnvironment::AndroidGeneric | DiagnosticEnvironment::Gp520
                        ) {
                        Some(DiagnosticExportError::SyntheticHardwareEnvironment)
                    } else if run_kind == DiagnosticRunKind::Synthetic
                        && class == EvidenceClass::Fact
                    {
                        Some(DiagnosticExportError::SyntheticFactEvidence)
                    } else {
                        None
                    };
                    match expected_error {
                        Some(error) => assert_eq!(result, Err(error)),
                        None => assert!(result.is_ok()),
                    }
                }
            }
        }
    }

    #[test]
    fn diagnostic_jsonl_requires_non_decreasing_monotonic_times() {
        let first = MetricSample {
            monotonic_ns: 2,
            ..sample()
        };
        let second = MetricSample {
            monotonic_ns: 1,
            ..sample()
        };
        assert_eq!(
            encode_diagnostic_jsonl(metadata(), &[first.clone(), second]),
            Err(DiagnosticExportError::NonMonotonicSamples),
        );
        assert!(encode_diagnostic_jsonl(metadata(), &[first.clone(), first]).is_ok());
    }
}
