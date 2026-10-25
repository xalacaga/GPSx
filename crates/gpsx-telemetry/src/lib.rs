#![forbid(unsafe_code)]

//! Measurement primitives and redaction. Serialization and platform sinks remain adapters.

use std::fmt;
use std::time::{Duration, SystemTime};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum EvidenceClass {
    Fact,
    Inference,
    Hypothesis,
    Unknown,
    NotTested,
}

#[derive(Clone, Debug, PartialEq)]
pub struct MetricSample {
    pub name: &'static str,
    pub value: f64,
    pub unit: &'static str,
    /// Monotonic capture time from the adapter clock; use this for ordering and intervals.
    pub monotonic_ns: u64,
    /// Wall-clock time as metadata only; it can jump (NTP, manual changes).
    pub wall_clock: Option<SystemTime>,
    pub class: EvidenceClass,
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
    use super::{Duration, Redacted, is_sensitive_key, summarize_latency};

    fn ms(values: &[u64]) -> Vec<Duration> {
        values.iter().copied().map(Duration::from_millis).collect()
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
}
