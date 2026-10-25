// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

use core::fmt;

pub const MAX_DATAGRAM_SIZE: usize = 1_024;
pub const MAX_HEADER_COUNT: usize = 32;
pub const MAX_LINE_SIZE: usize = 512;

const SENSITIVE_HEADERS: [&str; 4] = [
    "host-id",
    "host-request-port",
    "device-discovery-protocol-version",
    "host-type",
];

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum HttpParseError {
    DatagramTooLarge,
    LineTooLong,
    TooManyHeaders,
    EmbeddedNul,
    InvalidUtf8,
    ControlCharacter,
    MissingStatus,
    InvalidStatus,
    HeaderAfterTerminator,
    MissingHeaderColon,
    InvalidHeaderName,
    EmptyHeaderValue,
    DuplicateSensitiveHeader,
}

impl fmt::Display for HttpParseError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::DatagramTooLarge => "discovery datagram exceeds 1024 bytes",
            Self::LineTooLong => "discovery line exceeds 512 bytes",
            Self::TooManyHeaders => "discovery response exceeds 32 headers",
            Self::EmbeddedNul => "discovery response contains a non-terminal or repeated NUL",
            Self::InvalidUtf8 => "discovery header value is not valid UTF-8",
            Self::ControlCharacter => "discovery header value contains a control character",
            Self::MissingStatus => "discovery response has no status line",
            Self::InvalidStatus => "discovery response has an invalid HTTP/1.1 status",
            Self::HeaderAfterTerminator => "discovery response has data after its terminator",
            Self::MissingHeaderColon => "discovery header has no colon",
            Self::InvalidHeaderName => "discovery header name is invalid",
            Self::EmptyHeaderValue => "discovery header value is empty",
            Self::DuplicateSensitiveHeader => {
                "discovery response repeats a security-sensitive header"
            }
        })
    }
}

impl std::error::Error for HttpParseError {}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct Header<'a> {
    pub(crate) name: &'a str,
    pub(crate) value: &'a str,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct HttpResponse<'a> {
    pub(crate) status: u16,
    pub(crate) headers: Vec<Header<'a>>,
}

pub(crate) fn parse_http_response(input: &[u8]) -> Result<HttpResponse<'_>, HttpParseError> {
    if input.len() > MAX_DATAGRAM_SIZE {
        return Err(HttpParseError::DatagramTooLarge);
    }
    let input = if let Some(without_terminal_nul) = input.strip_suffix(&[0]) {
        if without_terminal_nul.contains(&0) {
            return Err(HttpParseError::EmbeddedNul);
        }
        without_terminal_nul
    } else if input.contains(&0) {
        return Err(HttpParseError::EmbeddedNul);
    } else {
        input
    };

    let mut lines = BoundedLines::new(input);
    let status_line = lines
        .next()
        .transpose()?
        .ok_or(HttpParseError::MissingStatus)?;
    if status_line.is_empty() {
        return Err(HttpParseError::MissingStatus);
    }
    let status = parse_status(status_line)?;

    let mut headers = Vec::with_capacity(MAX_HEADER_COUNT);
    let mut terminated = false;
    for line in lines {
        let line = line?;
        if line.is_empty() {
            terminated = true;
            continue;
        }
        if terminated {
            return Err(HttpParseError::HeaderAfterTerminator);
        }
        if headers.len() == MAX_HEADER_COUNT {
            return Err(HttpParseError::TooManyHeaders);
        }

        let colon = line
            .iter()
            .position(|byte| *byte == b':')
            .ok_or(HttpParseError::MissingHeaderColon)?;
        let name_bytes = &line[..colon];
        if name_bytes.is_empty() || !name_bytes.iter().all(|byte| is_header_name_byte(*byte)) {
            return Err(HttpParseError::InvalidHeaderName);
        }
        let mut value_bytes = &line[colon + 1..];
        if value_bytes.first() == Some(&b' ') {
            value_bytes = &value_bytes[1..];
        }
        if value_bytes.is_empty() {
            return Err(HttpParseError::EmptyHeaderValue);
        }

        let name =
            std::str::from_utf8(name_bytes).map_err(|_| HttpParseError::InvalidHeaderName)?;
        let value = std::str::from_utf8(value_bytes).map_err(|_| HttpParseError::InvalidUtf8)?;
        if value.chars().any(char::is_control) {
            return Err(HttpParseError::ControlCharacter);
        }
        if SENSITIVE_HEADERS.iter().any(|sensitive| {
            name.eq_ignore_ascii_case(sensitive)
                && headers
                    .iter()
                    .any(|header: &Header<'_>| header.name.eq_ignore_ascii_case(sensitive))
        }) {
            return Err(HttpParseError::DuplicateSensitiveHeader);
        }
        headers.push(Header { name, value });
    }

    Ok(HttpResponse { status, headers })
}

struct BoundedLines<'a> {
    input: &'a [u8],
    cursor: usize,
}

impl<'a> BoundedLines<'a> {
    const fn new(input: &'a [u8]) -> Self {
        Self { input, cursor: 0 }
    }
}

impl<'a> Iterator for BoundedLines<'a> {
    type Item = Result<&'a [u8], HttpParseError>;

    fn next(&mut self) -> Option<Self::Item> {
        if self.cursor >= self.input.len() {
            return None;
        }
        let start = self.cursor;
        while self.cursor < self.input.len()
            && self.input[self.cursor] != b'\r'
            && self.input[self.cursor] != b'\n'
        {
            self.cursor += 1;
        }
        let line = &self.input[start..self.cursor];
        if self.cursor < self.input.len() {
            if self.input[self.cursor] == b'\r'
                && self.cursor + 1 < self.input.len()
                && self.input[self.cursor + 1] == b'\n'
            {
                self.cursor += 2;
            } else {
                self.cursor += 1;
            }
        }
        Some(if line.len() > MAX_LINE_SIZE {
            Err(HttpParseError::LineTooLong)
        } else {
            Ok(line)
        })
    }
}

fn parse_status(line: &[u8]) -> Result<u16, HttpParseError> {
    const PREFIX: &[u8] = b"HTTP/1.1 ";
    if !line.is_ascii() || line.iter().any(|byte| byte.is_ascii_control()) {
        return Err(HttpParseError::InvalidStatus);
    }
    let remainder = line
        .strip_prefix(PREFIX)
        .ok_or(HttpParseError::InvalidStatus)?;
    let code_bytes = remainder
        .split(|byte| *byte == b' ')
        .next()
        .ok_or(HttpParseError::InvalidStatus)?;
    if code_bytes.is_empty() || !code_bytes.iter().all(u8::is_ascii_digit) {
        return Err(HttpParseError::InvalidStatus);
    }
    let code = code_bytes.iter().try_fold(0_u16, |current, byte| {
        current
            .checked_mul(10)?
            .checked_add(u16::from(*byte - b'0'))
    });
    let code = code.ok_or(HttpParseError::InvalidStatus)?;
    if code == 0 {
        return Err(HttpParseError::InvalidStatus);
    }
    Ok(code)
}

fn is_header_name_byte(byte: u8) -> bool {
    byte.is_ascii_alphanumeric()
        || matches!(
            byte,
            b'!' | b'#'
                | b'$'
                | b'%'
                | b'&'
                | b'\''
                | b'*'
                | b'+'
                | b'-'
                | b'.'
                | b'^'
                | b'_'
                | b'`'
                | b'|'
                | b'~'
        )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn response_with_headers(count: usize) -> Vec<u8> {
        let mut input = b"HTTP/1.1 200\n".to_vec();
        for index in 0..count {
            input.extend_from_slice(format!("x-{index}:value\n").as_bytes());
        }
        input
    }

    fn response_of_size(size: usize) -> Vec<u8> {
        assert!((526..=1_024).contains(&size));
        let mut input = b"HTTP/1.1 200\n".to_vec();
        input.extend_from_slice(b"x:");
        input.extend(std::iter::repeat_n(b'a', 510));
        input.push(b'\n');
        let remaining = size - input.len();
        input.extend_from_slice(b"y:");
        input.extend(std::iter::repeat_n(b'b', remaining - 2));
        input
    }

    #[test]
    fn enforces_datagram_boundaries() {
        assert_eq!(parse_http_response(b""), Err(HttpParseError::MissingStatus));
        assert!(parse_http_response(&response_of_size(1_023)).is_ok());
        assert!(parse_http_response(&response_of_size(1_024)).is_ok());
        let mut oversized = response_of_size(1_024);
        oversized.push(b'x');
        assert_eq!(
            parse_http_response(&oversized),
            Err(HttpParseError::DatagramTooLarge)
        );
    }

    #[test]
    fn enforces_header_count_boundaries() {
        assert!(parse_http_response(&response_with_headers(31)).is_ok());
        assert!(parse_http_response(&response_with_headers(32)).is_ok());
        assert_eq!(
            parse_http_response(&response_with_headers(33)),
            Err(HttpParseError::TooManyHeaders)
        );
    }

    #[test]
    fn enforces_line_length_boundaries() {
        for length in [511, 512] {
            let mut input = b"HTTP/1.1 200 ".to_vec();
            input.extend(std::iter::repeat_n(b'x', length - input.len()));
            assert!(parse_http_response(&input).is_ok());
        }
        let mut input = b"HTTP/1.1 200 ".to_vec();
        input.extend(std::iter::repeat_n(b'x', 513 - input.len()));
        assert_eq!(
            parse_http_response(&input),
            Err(HttpParseError::LineTooLong)
        );
    }

    #[test]
    fn defines_status_only_and_status_validation() {
        assert_eq!(parse_http_response(b"HTTP/1.1 200").unwrap().status, 200);
        assert_eq!(parse_http_response(b"HTTP/1.1 0200").unwrap().status, 200);
        assert_eq!(
            parse_http_response(b"HTTP/1.1 620\r\n").unwrap().status,
            620
        );
        assert_eq!(parse_http_response(b"HTTP/1.1 404\n").unwrap().status, 404);
        for invalid in [
            &b"HTTP/1.1 0"[..],
            &b"HTTP/1.1  200"[..],
            &b"HTTP/1.1 nope"[..],
            &b"HTTP/1.0 200"[..],
            &b"garbage"[..],
        ] {
            assert_eq!(
                parse_http_response(invalid),
                Err(HttpParseError::InvalidStatus)
            );
        }
    }

    #[test]
    fn accepts_all_line_endings_and_final_line_without_eol() {
        for separator in ["\r\n", "\n", "\r"] {
            let input = format!("HTTP/1.1 200{separator}x:value");
            let parsed = parse_http_response(input.as_bytes()).unwrap();
            assert_eq!(parsed.headers[0].value, "value");
        }
    }

    #[test]
    fn applies_strict_header_grammar() {
        assert_eq!(
            parse_http_response(b"HTTP/1.1 200\nx:value\0hidden"),
            Err(HttpParseError::EmbeddedNul)
        );
        assert_eq!(
            parse_http_response(b"HTTP/1.1 200\nx:"),
            Err(HttpParseError::EmptyHeaderValue)
        );
        assert_eq!(
            parse_http_response(b"HTTP/1.1 200\nx"),
            Err(HttpParseError::MissingHeaderColon)
        );
        let parsed = parse_http_response(b"HTTP/1.1 200\nx: one:two").unwrap();
        assert_eq!(parsed.headers[0].value, "one:two");
        let parsed = parse_http_response(b"HTTP/1.1 200\nx:  two").unwrap();
        assert_eq!(parsed.headers[0].value, " two");
    }

    #[test]
    fn accepts_utf8_values_and_rejects_invalid_or_control_values() {
        let input = "HTTP/1.1 200\nhost-name:PS5 Séjour\nrunning-app-name:Pokémon™";
        let parsed = parse_http_response(input.as_bytes()).unwrap();
        assert_eq!(parsed.headers[0].value, "PS5 Séjour");
        assert_eq!(parsed.headers[1].value, "Pokémon™");

        let invalid_utf8 = b"HTTP/1.1 200\nhost-name:\xff";
        assert_eq!(
            parse_http_response(invalid_utf8),
            Err(HttpParseError::InvalidUtf8)
        );
        assert_eq!(
            parse_http_response(b"HTTP/1.1 200\nhost-name:bad\x1fvalue"),
            Err(HttpParseError::ControlCharacter)
        );
    }

    #[test]
    fn accepts_one_terminal_nul_only() {
        assert!(parse_http_response(b"HTTP/1.1 200\r\n\0").is_ok());
        assert_eq!(
            parse_http_response(b"HTTP/1.1 200\nx:a\0b"),
            Err(HttpParseError::EmbeddedNul)
        );
        assert_eq!(
            parse_http_response(b"HTTP/1.1 200\0\0"),
            Err(HttpParseError::EmbeddedNul)
        );
    }

    #[test]
    fn empty_lines_do_not_expand_parser_storage() {
        let mut input = b"HTTP/1.1 200\n".to_vec();
        input.extend(std::iter::repeat_n(b'\n', 1_000));
        assert!(parse_http_response(&input).is_ok());
        assert_eq!(
            parse_http_response(b"HTTP/1.1 200\n\rx:value"),
            Err(HttpParseError::HeaderAfterTerminator)
        );
    }

    #[test]
    fn rejects_sensitive_duplicates_case_insensitively() {
        for name in SENSITIVE_HEADERS {
            let input = format!("HTTP/1.1 200\n{name}:one\n{}:two", name.to_uppercase());
            assert_eq!(
                parse_http_response(input.as_bytes()),
                Err(HttpParseError::DuplicateSensitiveHeader)
            );
        }
        assert!(parse_http_response(b"HTTP/1.1 200\nx:one\nx:two").is_ok());
    }

    #[test]
    fn arbitrary_inputs_never_panic_and_are_deterministic() {
        let mut state = 0x91e1_0da5_u32;
        for length in 0..=1_025 {
            let mut input = Vec::with_capacity(length);
            for _ in 0..length {
                state = state.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
                input.push((state >> 24) as u8);
            }
            assert_eq!(parse_http_response(&input), parse_http_response(&input));
        }
    }
}
