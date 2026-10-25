// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

use core::fmt;

use crate::http::{Header, HttpParseError, parse_http_response};

pub const PS4_DISCOVERY_PORT: u16 = 987;
pub const PS5_DISCOVERY_PORT: u16 = 9_302;
pub const PS4_PROTOCOL_VERSION: &str = "00020020";
pub const PS5_PROTOCOL_VERSION: &str = "00030010";

const PS4_SEARCH: &[u8] = b"SRCH * HTTP/1.1\ndevice-discovery-protocol-version:00020020\n\0";
const PS5_SEARCH: &[u8] = b"SRCH * HTTP/1.1\ndevice-discovery-protocol-version:00030010\n\0";

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ConsoleGeneration {
    Ps4,
    Ps5,
}

/// Formats the complete chiaki-ng-compatible SRCH datagram, including its
/// terminal NUL. This function performs no network I/O.
#[must_use]
pub const fn format_search_request(generation: ConsoleGeneration) -> &'static [u8] {
    match generation {
        ConsoleGeneration::Ps4 => PS4_SEARCH,
        ConsoleGeneration::Ps5 => PS5_SEARCH,
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DiscoveryState {
    Ready,
    Standby,
    Unknown,
}

#[derive(Clone, Eq, PartialEq)]
pub struct DiscoveryHost<'a> {
    state: DiscoveryState,
    status_code: u16,
    host_request_port: Option<u16>,
    system_version: Option<&'a str>,
    protocol_version: Option<&'a str>,
    host_name: Option<&'a str>,
    host_type: Option<&'a str>,
    host_id: Option<&'a str>,
    running_app_title_id: Option<&'a str>,
    running_app_name: Option<&'a str>,
}

impl<'a> DiscoveryHost<'a> {
    #[must_use]
    pub const fn state(&self) -> DiscoveryState {
        self.state
    }

    #[must_use]
    pub const fn status_code(&self) -> u16 {
        self.status_code
    }

    #[must_use]
    pub const fn host_request_port(&self) -> Option<u16> {
        self.host_request_port
    }

    #[must_use]
    pub const fn system_version(&self) -> Option<&'a str> {
        self.system_version
    }

    #[must_use]
    pub const fn protocol_version(&self) -> Option<&'a str> {
        self.protocol_version
    }

    #[must_use]
    pub const fn host_name(&self) -> Option<&'a str> {
        self.host_name
    }

    #[must_use]
    pub const fn host_type(&self) -> Option<&'a str> {
        self.host_type
    }

    #[must_use]
    pub const fn host_id(&self) -> Option<&'a str> {
        self.host_id
    }

    #[must_use]
    pub const fn running_app_title_id(&self) -> Option<&'a str> {
        self.running_app_title_id
    }

    #[must_use]
    pub const fn running_app_name(&self) -> Option<&'a str> {
        self.running_app_name
    }
}

impl fmt::Debug for DiscoveryHost<'_> {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("DiscoveryHost")
            .field("state", &self.state)
            .field("status_code", &self.status_code)
            .field("host_request_port", &self.host_request_port)
            .field("system_version", &self.system_version)
            .field("protocol_version", &self.protocol_version)
            .field("host_name", &self.host_name.map(|_| "[redacted]"))
            .field("host_type", &self.host_type)
            .field("host_id", &self.host_id.map(|_| "[redacted]"))
            .field("running_app_title_id", &self.running_app_title_id)
            .field("running_app_name", &self.running_app_name)
            .finish()
    }
}

impl fmt::Display for DiscoveryHost<'_> {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "discovery host: state={:?}, status={}, port={:?}, host-name=[redacted], host-id=[redacted]",
            self.state, self.status_code, self.host_request_port
        )
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DiscoveryParseError {
    Http(HttpParseError),
    InvalidHostRequestPort,
}

impl fmt::Display for DiscoveryParseError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Http(error) => write!(formatter, "invalid discovery response: {error}"),
            Self::InvalidHostRequestPort => {
                formatter.write_str("discovery host request port is not a decimal u16")
            }
        }
    }
}

impl std::error::Error for DiscoveryParseError {}

impl From<HttpParseError> for DiscoveryParseError {
    fn from(value: HttpParseError) -> Self {
        Self::Http(value)
    }
}

/// Parses a bounded SRCH response without I/O.
///
/// Deliberate hardening differences from the pinned C implementation:
/// one terminal NUL is accepted but embedded/repeated NUL is rejected instead of
/// truncating; UTF-8 values are accepted while control characters are rejected;
/// a valid final header and a status-only response without EOL are accepted;
/// ports are strict decimal `1..=65535`; sensitive duplicates are rejected.
/// Header names are ASCII and matched case-insensitively. A double space before
/// the status code, LF-CR followed by data, and trailing port whitespace are
/// rejected. For non-sensitive duplicate known fields, the first value wins.
pub fn parse_search_response(input: &[u8]) -> Result<DiscoveryHost<'_>, DiscoveryParseError> {
    let response = parse_http_response(input)?;
    let mut host = DiscoveryHost {
        state: match response.status {
            200 => DiscoveryState::Ready,
            620 => DiscoveryState::Standby,
            _ => DiscoveryState::Unknown,
        },
        status_code: response.status,
        host_request_port: None,
        system_version: None,
        protocol_version: None,
        host_name: None,
        host_type: None,
        host_id: None,
        running_app_title_id: None,
        running_app_name: None,
    };

    for header in response.headers {
        set_first_known_header(&mut host, header)?;
    }
    Ok(host)
}

fn set_first_known_header<'a>(
    host: &mut DiscoveryHost<'a>,
    header: Header<'a>,
) -> Result<(), DiscoveryParseError> {
    if header.name.eq_ignore_ascii_case("system-version") {
        host.system_version.get_or_insert(header.value);
    } else if header
        .name
        .eq_ignore_ascii_case("device-discovery-protocol-version")
    {
        host.protocol_version.get_or_insert(header.value);
    } else if header.name.eq_ignore_ascii_case("host-request-port") {
        if host.host_request_port.is_none() {
            host.host_request_port = Some(parse_port(header.value)?);
        }
    } else if header.name.eq_ignore_ascii_case("host-name") {
        host.host_name.get_or_insert(header.value);
    } else if header.name.eq_ignore_ascii_case("host-type") {
        host.host_type.get_or_insert(header.value);
    } else if header.name.eq_ignore_ascii_case("host-id") {
        host.host_id.get_or_insert(header.value);
    } else if header.name.eq_ignore_ascii_case("running-app-titleid") {
        host.running_app_title_id.get_or_insert(header.value);
    } else if header.name.eq_ignore_ascii_case("running-app-name") {
        host.running_app_name.get_or_insert(header.value);
    }
    Ok(())
}

fn parse_port(value: &str) -> Result<u16, DiscoveryParseError> {
    if value.is_empty() || !value.bytes().all(|byte| byte.is_ascii_digit()) {
        return Err(DiscoveryParseError::InvalidHostRequestPort);
    }
    let port = value
        .parse::<u16>()
        .map_err(|_| DiscoveryParseError::InvalidHostRequestPort)?;
    if port == 0 {
        return Err(DiscoveryParseError::InvalidHostRequestPort);
    }
    Ok(port)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats_exact_deterministic_search_datagrams() {
        assert_eq!(
            format_search_request(ConsoleGeneration::Ps4),
            b"SRCH * HTTP/1.1\ndevice-discovery-protocol-version:00020020\n\0"
        );
        assert_eq!(
            format_search_request(ConsoleGeneration::Ps5),
            b"SRCH * HTTP/1.1\ndevice-discovery-protocol-version:00030010\n\0"
        );
        assert!(core::ptr::eq(
            format_search_request(ConsoleGeneration::Ps5),
            format_search_request(ConsoleGeneration::Ps5)
        ));
    }

    #[test]
    fn maps_status_codes() {
        assert_eq!(
            parse_search_response(b"HTTP/1.1 200").unwrap().state(),
            DiscoveryState::Ready
        );
        assert_eq!(
            parse_search_response(b"HTTP/1.1 620").unwrap().state(),
            DiscoveryState::Standby
        );
        let unknown = parse_search_response(b"HTTP/1.1 404").unwrap();
        assert_eq!(unknown.state(), DiscoveryState::Unknown);
        assert_eq!(unknown.status_code(), 404);
    }

    #[test]
    fn parses_known_headers_and_ignores_unknown_ones() {
        let input = b"HTTP/1.1 200\r\nsystem-version:10000000\r\n\
            device-discovery-protocol-version:00030010\r\nhost-request-port:9295\r\n\
            host-name:Living Room\r\nhost-type:PS5\r\nhost-id:synthetic-id\r\n\
            running-app-titleid:CUSA00000\r\nrunning-app-name:Synthetic Game\r\nx-unknown:value";
        let host = parse_search_response(input).unwrap();
        assert_eq!(host.system_version(), Some("10000000"));
        assert_eq!(host.protocol_version(), Some("00030010"));
        assert_eq!(host.host_request_port(), Some(9_295));
        assert_eq!(host.host_name(), Some("Living Room"));
        assert_eq!(host.host_type(), Some("PS5"));
        assert_eq!(host.host_id(), Some("synthetic-id"));
        assert_eq!(host.running_app_title_id(), Some("CUSA00000"));
        assert_eq!(host.running_app_name(), Some("Synthetic Game"));
    }

    #[test]
    fn validates_decimal_port_boundaries() {
        for accepted in ["1", "9295", "65535"] {
            let input = format!("HTTP/1.1 200\nhost-request-port:{accepted}");
            assert_eq!(
                parse_search_response(input.as_bytes())
                    .unwrap()
                    .host_request_port(),
                accepted.parse::<u16>().ok()
            );
        }
        for rejected in ["0", "65536", "0x2457", "+1", "-1", "", " ", "  ", "1 "] {
            let input = format!("HTTP/1.1 200\nhost-request-port:{rejected}");
            assert!(
                parse_search_response(input.as_bytes()).is_err(),
                "{rejected:?}"
            );
        }
    }

    #[test]
    fn first_non_sensitive_duplicate_wins() {
        let host =
            parse_search_response(b"HTTP/1.1 200\nhost-name:first\nhost-name:second").unwrap();
        assert_eq!(host.host_name(), Some("first"));
    }

    #[test]
    fn debug_and_display_redact_host_identity() {
        let host = parse_search_response(
            b"HTTP/1.1 200\nhost-name:private-console\nhost-id:private-identifier",
        )
        .unwrap();
        let debug = format!("{host:?}");
        let display = host.to_string();
        for output in [debug, display] {
            assert!(!output.contains("private-console"));
            assert!(!output.contains("private-identifier"));
            assert!(output.contains("[redacted]"));
        }
    }

    #[test]
    fn parse_errors_do_not_echo_identifiers() {
        let input = b"HTTP/1.1 200\nhost-id:private-identifier\nhost-id:second";
        let error = parse_search_response(input).unwrap_err();
        let rendered = format!("{error:?} {error}");
        assert!(!rendered.contains("private-identifier"));
    }
}
