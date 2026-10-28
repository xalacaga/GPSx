// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

use std::fmt::{self, Write};
use std::net::Ipv4Addr;

use rand_core::{CryptoRng, RngCore};
use zeroize::{Zeroize, Zeroizing};

use crate::launch_spec::{
    LaunchSpecError, SessionCodec, SessionRpcrypt, SessionTarget, encode_base64,
};

const REGISTRATION_KEY_SIZE: usize = 16;
const DID_SIZE: usize = 32;
const DID_PREFIX: [u8; 10] = [0x00, 0x18, 0x00, 0x00, 0x00, 0x07, 0x00, 0x40, 0x00, 0x80];
const DID_RANDOM_RANGE: std::ops::Range<usize> = 10..26;
const OS_TYPE: &[u8; 10] = b"Win10.0.0\0";
const CONTROL_PORT: u16 = 9295;
const MAX_CONTROL_LINES: usize = 32;
const MAX_CONTROL_LINE_SIZE: usize = 256;
const MAX_DECODED_HEADER_VALUE_SIZE: usize = 192;

pub const MAX_CONTROL_REQUEST_SIZE: usize = 512;
pub const MAX_CONTROL_HEADER_SIZE: usize = 512;

macro_rules! redacted_format {
    ($type:ty, $label:literal) => {
        impl fmt::Debug for $type {
            fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
                formatter.write_str(concat!($label, "([REDACTED])"))
            }
        }
        impl fmt::Display for $type {
            fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
                formatter.write_str(concat!($label, "([REDACTED])"))
            }
        }
    };
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ControlProfile {
    Ps4_8,
    Ps4_9,
    Ps4_10,
    Ps5,
}

impl ControlProfile {
    const fn target(self) -> SessionTarget {
        match self {
            Self::Ps4_8 | Self::Ps4_9 => SessionTarget::Ps4Pre10,
            Self::Ps4_10 => SessionTarget::Ps4Modern,
            Self::Ps5 => SessionTarget::Ps5,
        }
    }

    const fn path(self) -> &'static str {
        match self {
            Self::Ps4_8 | Self::Ps4_9 => "/sce/rp/session/ctrl",
            Self::Ps4_10 => "/sie/ps4/rp/sess/ctrl",
            Self::Ps5 => "/sie/ps5/rp/sess/ctrl",
        }
    }

    const fn rp_version(self) -> &'static str {
        match self {
            Self::Ps4_8 => "8.0",
            Self::Ps4_9 => "9.0",
            Self::Ps4_10 => "10.0",
            Self::Ps5 => "1.0",
        }
    }

    const fn next_local_counter(self) -> u64 {
        match self {
            Self::Ps4_8 | Self::Ps4_9 => 3,
            Self::Ps4_10 => 4,
            Self::Ps5 => 5,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ControlEndpoint(Ipv4Addr);

impl ControlEndpoint {
    #[must_use]
    pub const fn local_ipv4(address: Ipv4Addr) -> Self {
        Self(address)
    }
}

/// Registration key transferred into the ctrl boundary and erased at source.
///
/// ```compile_fail
/// use gpsx_crypto::ControlRegistrationKey;
/// fn require_clone<T: Clone>() {}
/// require_clone::<ControlRegistrationKey>();
/// ```
pub struct ControlRegistrationKey([u8; REGISTRATION_KEY_SIZE]);

impl ControlRegistrationKey {
    pub fn take_from_registration_buffer(
        source: &mut Zeroizing<[u8; REGISTRATION_KEY_SIZE]>,
    ) -> Self {
        let mut value = Self([0; REGISTRATION_KEY_SIZE]);
        value.0.copy_from_slice(source.as_ref());
        source.zeroize();
        value
    }
}

impl Drop for ControlRegistrationKey {
    fn drop(&mut self) {
        self.0.zeroize();
    }
}

redacted_format!(ControlRegistrationKey, "ControlRegistrationKey");

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ControlRequestLength(usize);

impl ControlRequestLength {
    #[must_use]
    pub const fn get(self) -> usize {
        self.0
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ControlHeaderLength(usize);

impl ControlHeaderLength {
    #[must_use]
    pub const fn get(self) -> usize {
        self.0
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ControlServerType {
    Ps4,
    Ps4Pro,
    Ps5,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ControlHandshakeResponse {
    pub server_type: Option<ControlServerType>,
    pub prohibit: bool,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ControlHandshakeError {
    AlreadyStarted,
    ProfileTargetMismatch,
    EntropyUnavailable,
    RequestAlreadyPrepared,
    RequestNotPrepared,
    UnsupportedCodec,
    OutputTooSmall,
    FormattingFailed,
    CryptoInitialization,
    IncompleteHeader,
    HeaderTooLarge,
    TooManyHeaderLines,
    HeaderLineTooLong,
    InvalidStatusLine,
    InvalidHeader,
    DuplicateHeader,
    InvalidProhibit,
    InvalidBase64,
    InvalidServerType,
    ControlNotFinished,
    LaunchSpecNotWritten,
}

impl fmt::Display for ControlHandshakeError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::AlreadyStarted => "control counter namespace was already opened",
            Self::ProfileTargetMismatch => "control profile and rpcrypt target do not match",
            Self::EntropyUnavailable => "control DID entropy was unavailable",
            Self::RequestAlreadyPrepared => "control request was already prepared",
            Self::RequestNotPrepared => "control request must be prepared before the response",
            Self::UnsupportedCodec => "control profile and codec combination is unsupported",
            Self::OutputTooSmall => "control request output buffer is too small",
            Self::FormattingFailed => "control request formatting failed",
            Self::CryptoInitialization => "control cryptographic primitive could not initialize",
            Self::IncompleteHeader => "control response header is incomplete",
            Self::HeaderTooLarge => "control response header exceeds its byte bound",
            Self::TooManyHeaderLines => "control response has too many header lines",
            Self::HeaderLineTooLong => "control response line exceeds its byte bound",
            Self::InvalidStatusLine => "control response status line is invalid",
            Self::InvalidHeader => "control response header grammar is invalid",
            Self::DuplicateHeader => "control response repeats a security-sensitive header",
            Self::InvalidProhibit => "control response RP-Prohibit value is invalid",
            Self::InvalidBase64 => "control response RP-Server-Type base64 is invalid",
            Self::InvalidServerType => "control response server type is invalid",
            Self::ControlNotFinished => "control handshake has not finished",
            Self::LaunchSpecNotWritten => "LaunchSpec has not consumed its counter-zero slot",
        })
    }
}

impl std::error::Error for ControlHandshakeError {}

pub struct ControlHandshake<'a> {
    rpcrypt: &'a mut SessionRpcrypt,
    profile: ControlProfile,
    did: Zeroizing<[u8; DID_SIZE]>,
    request_prepared: bool,
}

redacted_format!(ControlHandshake<'_>, "ControlHandshake");

/// Opaque owner reserved for the separately reviewed continuous ctrl codec.
///
/// ```compile_fail
/// use gpsx_crypto::ControlChannelCrypto;
/// fn require_clone<T: Clone>() {}
/// require_clone::<ControlChannelCrypto>();
/// ```
pub struct ControlChannelCrypto {
    rpcrypt: SessionRpcrypt,
}

redacted_format!(ControlChannelCrypto, "ControlChannelCrypto");

impl SessionRpcrypt {
    pub fn begin_control<R>(
        &mut self,
        profile: ControlProfile,
        entropy: &mut R,
    ) -> Result<ControlHandshake<'_>, ControlHandshakeError>
    where
        R: CryptoRng + RngCore,
    {
        if self.control_started {
            return Err(ControlHandshakeError::AlreadyStarted);
        }
        if profile.target() != self.target {
            return Err(ControlHandshakeError::ProfileTargetMismatch);
        }

        let mut did = Zeroizing::new([0_u8; DID_SIZE]);
        did[..DID_PREFIX.len()].copy_from_slice(&DID_PREFIX);
        entropy
            .try_fill_bytes(&mut did[DID_RANDOM_RANGE])
            .map_err(|_| ControlHandshakeError::EntropyUnavailable)?;

        self.control_started = true;
        Ok(ControlHandshake {
            rpcrypt: self,
            profile,
            did,
            request_prepared: false,
        })
    }

    pub fn into_control_channel(self) -> Result<ControlChannelCrypto, ControlHandshakeError> {
        if !self.control_finished {
            return Err(ControlHandshakeError::ControlNotFinished);
        }
        if !self.launch_spec_counter_zero_consumed {
            return Err(ControlHandshakeError::LaunchSpecNotWritten);
        }
        Ok(ControlChannelCrypto { rpcrypt: self })
    }
}

impl ControlHandshake<'_> {
    pub fn write_request(
        &mut self,
        endpoint: ControlEndpoint,
        registration_key: &ControlRegistrationKey,
        codec: SessionCodec,
        output: &mut [u8],
    ) -> Result<ControlRequestLength, ControlHandshakeError> {
        if self.request_prepared {
            return Err(ControlHandshakeError::RequestAlreadyPrepared);
        }
        validate_request_codec(self.profile, codec)?;

        let auth_placeholder = [b'A'; 24];
        let did_placeholder = [b'A'; 44];
        let os_placeholder = [b'A'; 16];
        let word_placeholder = [b'A'; 8];
        let mut preflight = Zeroizing::new([0_u8; MAX_CONTROL_REQUEST_SIZE]);
        let required = format_request(
            self.profile,
            endpoint,
            &auth_placeholder,
            &did_placeholder,
            &os_placeholder,
            &word_placeholder,
            &word_placeholder,
            preflight.as_mut(),
        )?;
        if output.len() < required {
            return Err(ControlHandshakeError::OutputTooSmall);
        }

        let mut auth = Zeroizing::new([0_u8; REGISTRATION_KEY_SIZE]);
        auth.copy_from_slice(&registration_key.0);
        let mut did = Zeroizing::new(*self.did);
        let mut os_type = Zeroizing::new(*OS_TYPE);
        let mut bitrate = Zeroizing::new([0_u8; 4]);
        let mut streaming_type = Zeroizing::new(match codec {
            SessionCodec::Avc => 1_u32.to_le_bytes(),
            SessionCodec::Hevc => 2_u32.to_le_bytes(),
            SessionCodec::HevcHdr => 3_u32.to_le_bytes(),
        });

        self.rpcrypt.encrypt_in_place(0, auth.as_mut())?;
        self.rpcrypt.encrypt_in_place(1, did.as_mut())?;
        self.rpcrypt.encrypt_in_place(2, os_type.as_mut())?;
        if matches!(self.profile, ControlProfile::Ps4_10 | ControlProfile::Ps5) {
            self.rpcrypt.encrypt_in_place(3, bitrate.as_mut())?;
        }
        if self.profile == ControlProfile::Ps5 {
            self.rpcrypt.encrypt_in_place(4, streaming_type.as_mut())?;
        }

        let mut auth_b64 = Zeroizing::new([0_u8; 24]);
        let mut did_b64 = Zeroizing::new([0_u8; 44]);
        let mut os_b64 = Zeroizing::new([0_u8; 16]);
        let mut bitrate_b64 = Zeroizing::new([0_u8; 8]);
        let mut streaming_b64 = Zeroizing::new([0_u8; 8]);
        encode_exact(auth.as_ref(), auth_b64.as_mut())?;
        encode_exact(did.as_ref(), did_b64.as_mut())?;
        encode_exact(os_type.as_ref(), os_b64.as_mut())?;
        encode_exact(bitrate.as_ref(), bitrate_b64.as_mut())?;
        encode_exact(streaming_type.as_ref(), streaming_b64.as_mut())?;

        let mut request = Zeroizing::new([0_u8; MAX_CONTROL_REQUEST_SIZE]);
        let request_len = format_request(
            self.profile,
            endpoint,
            auth_b64.as_ref(),
            did_b64.as_ref(),
            os_b64.as_ref(),
            bitrate_b64.as_ref(),
            streaming_b64.as_ref(),
            request.as_mut(),
        )?;
        debug_assert_eq!(request_len, required);
        output[..request_len].copy_from_slice(&request[..request_len]);
        self.rpcrypt.control_local_counter = self.profile.next_local_counter();
        self.request_prepared = true;
        Ok(ControlRequestLength(request_len))
    }

    pub fn finish(
        self,
        input: &[u8],
    ) -> Result<(ControlHandshakeResponse, ControlHeaderLength), ControlHandshakeError> {
        if !self.request_prepared {
            return Err(ControlHandshakeError::RequestNotPrepared);
        }
        let parsed = parse_response(input)?;
        let mut server_type = None;
        let mut remote_counter = 0;
        if let Some(encoded) = parsed.server_type {
            let mut decoded = Zeroizing::new([0_u8; MAX_DECODED_HEADER_VALUE_SIZE]);
            let decoded_len = decode_base64_canonical(encoded, decoded.as_mut())?;
            match decoded_len {
                16 => {
                    self.rpcrypt.decrypt_in_place(0, &mut decoded[..16])?;
                    server_type = Some(match decoded[0] {
                        0 => ControlServerType::Ps4,
                        1 => ControlServerType::Ps4Pro,
                        2 => ControlServerType::Ps5,
                        _ => return Err(ControlHandshakeError::InvalidServerType),
                    });
                    remote_counter = 1;
                }
                1 | 2 | 4 | 5 | 7 | 8 | 10 | 11 | 13 | 14 => {}
                _ => return Err(ControlHandshakeError::InvalidServerType),
            }
        }

        self.rpcrypt.control_server_type = server_type;
        self.rpcrypt.control_remote_counter = remote_counter;
        self.rpcrypt.control_finished = true;
        Ok((
            ControlHandshakeResponse {
                server_type,
                prohibit: parsed.prohibit,
            },
            ControlHeaderLength(parsed.header_len),
        ))
    }
}

impl From<LaunchSpecError> for ControlHandshakeError {
    fn from(_: LaunchSpecError) -> Self {
        Self::CryptoInitialization
    }
}

fn validate_request_codec(
    profile: ControlProfile,
    codec: SessionCodec,
) -> Result<(), ControlHandshakeError> {
    if profile != ControlProfile::Ps5 && codec != SessionCodec::Avc {
        Err(ControlHandshakeError::UnsupportedCodec)
    } else {
        Ok(())
    }
}

fn encode_exact(input: &[u8], output: &mut [u8]) -> Result<(), ControlHandshakeError> {
    let len = encode_base64(input, output).map_err(ControlHandshakeError::from)?;
    if len == output.len() {
        Ok(())
    } else {
        Err(ControlHandshakeError::FormattingFailed)
    }
}

#[allow(clippy::too_many_arguments)]
fn format_request(
    profile: ControlProfile,
    endpoint: ControlEndpoint,
    auth: &[u8],
    did: &[u8],
    os_type: &[u8],
    bitrate: &[u8],
    streaming_type: &[u8],
    output: &mut [u8],
) -> Result<usize, ControlHandshakeError> {
    let auth = std::str::from_utf8(auth).map_err(|_| ControlHandshakeError::FormattingFailed)?;
    let did = std::str::from_utf8(did).map_err(|_| ControlHandshakeError::FormattingFailed)?;
    let os_type =
        std::str::from_utf8(os_type).map_err(|_| ControlHandshakeError::FormattingFailed)?;
    let bitrate =
        std::str::from_utf8(bitrate).map_err(|_| ControlHandshakeError::FormattingFailed)?;
    let streaming_type =
        std::str::from_utf8(streaming_type).map_err(|_| ControlHandshakeError::FormattingFailed)?;

    let mut writer = FixedWriter::new(output);
    write!(&mut writer, "GET {} HTTP/1.1\r\n", profile.path())?;
    write!(&mut writer, "Host: {}:{}\r\n", endpoint.0, CONTROL_PORT)?;
    writer.write_str("User-Agent: remoteplay Windows\r\n")?;
    writer.write_str("Connection: keep-alive\r\n")?;
    writer.write_str("Content-Length: 0\r\n")?;
    write!(&mut writer, "RP-Auth: {auth}\r\n")?;
    write!(&mut writer, "RP-Version: {}\r\n", profile.rp_version())?;
    write!(&mut writer, "RP-Did: {did}\r\n")?;
    writer.write_str("RP-ControllerType: 3\r\n")?;
    writer.write_str("RP-ClientType: 11\r\n")?;
    write!(&mut writer, "RP-OSType: {os_type}\r\n")?;
    writer.write_str("RP-ConPath: 1\r\n")?;
    if matches!(profile, ControlProfile::Ps4_10 | ControlProfile::Ps5) {
        write!(&mut writer, "RP-StartBitrate: {bitrate}\r\n")?;
    }
    if profile == ControlProfile::Ps5 {
        write!(&mut writer, "RP-StreamingType: {streaming_type}\r\n")?;
    }
    writer.write_str("\r\n")?;
    Ok(writer.len())
}

struct FixedWriter<'a> {
    output: &'a mut [u8],
    cursor: usize,
}

impl<'a> FixedWriter<'a> {
    const fn new(output: &'a mut [u8]) -> Self {
        Self { output, cursor: 0 }
    }

    const fn len(&self) -> usize {
        self.cursor
    }
}

impl Write for FixedWriter<'_> {
    fn write_str(&mut self, value: &str) -> fmt::Result {
        let end = self.cursor.checked_add(value.len()).ok_or(fmt::Error)?;
        let destination = self.output.get_mut(self.cursor..end).ok_or(fmt::Error)?;
        destination.copy_from_slice(value.as_bytes());
        self.cursor = end;
        Ok(())
    }
}

impl From<fmt::Error> for ControlHandshakeError {
    fn from(_: fmt::Error) -> Self {
        Self::FormattingFailed
    }
}

struct ParsedResponse<'a> {
    header_len: usize,
    server_type: Option<&'a [u8]>,
    prohibit: bool,
}

fn parse_response(input: &[u8]) -> Result<ParsedResponse<'_>, ControlHandshakeError> {
    let scan_len = input.len().min(MAX_CONTROL_HEADER_SIZE);
    let terminator = input[..scan_len]
        .windows(4)
        .position(|window| window == b"\r\n\r\n")
        .ok_or({
            if input.len() > MAX_CONTROL_HEADER_SIZE {
                ControlHandshakeError::HeaderTooLarge
            } else {
                ControlHandshakeError::IncompleteHeader
            }
        })?;
    let header_len = terminator
        .checked_add(4)
        .ok_or(ControlHandshakeError::HeaderTooLarge)?;
    if header_len > MAX_CONTROL_HEADER_SIZE {
        return Err(ControlHandshakeError::HeaderTooLarge);
    }
    let content = &input[..terminator];
    if content.contains(&0) {
        return Err(ControlHandshakeError::InvalidHeader);
    }

    let (status, mut remaining) = take_line(content);
    validate_status(status)?;
    let mut line_count = 0;
    let mut server_type = None;
    let mut prohibit = None;
    while !remaining.is_empty() {
        let (line, rest) = take_line(remaining);
        remaining = rest;
        line_count += 1;
        if line_count > MAX_CONTROL_LINES {
            return Err(ControlHandshakeError::TooManyHeaderLines);
        }
        if line.len() > MAX_CONTROL_LINE_SIZE {
            return Err(ControlHandshakeError::HeaderLineTooLong);
        }
        if line.is_empty() || line.contains(&b'\r') || line.contains(&b'\n') {
            return Err(ControlHandshakeError::InvalidHeader);
        }
        let colon = line
            .iter()
            .position(|byte| *byte == b':')
            .ok_or(ControlHandshakeError::InvalidHeader)?;
        let name = &line[..colon];
        if name.is_empty() || !name.iter().copied().all(is_token_byte) {
            return Err(ControlHandshakeError::InvalidHeader);
        }
        let suffix = &line[colon + 1..];
        if suffix.len() < 2 || suffix[0] != b' ' || suffix[1] == b' ' {
            return Err(ControlHandshakeError::InvalidHeader);
        }
        let value = &suffix[1..];
        if !value.iter().all(|byte| (0x20..=0x7e).contains(byte)) {
            return Err(ControlHandshakeError::InvalidHeader);
        }

        if name.eq_ignore_ascii_case(b"RP-Server-Type") {
            if name != b"RP-Server-Type" {
                return Err(ControlHandshakeError::InvalidHeader);
            }
            if server_type.replace(value).is_some() {
                return Err(ControlHandshakeError::DuplicateHeader);
            }
        } else if name.eq_ignore_ascii_case(b"RP-Prohibit") {
            if name != b"RP-Prohibit" {
                return Err(ControlHandshakeError::InvalidHeader);
            }
            if prohibit.is_some() {
                return Err(ControlHandshakeError::DuplicateHeader);
            }
            prohibit = Some(match value {
                b"0" => false,
                b"1" => true,
                _ => return Err(ControlHandshakeError::InvalidProhibit),
            });
        }
    }

    Ok(ParsedResponse {
        header_len,
        server_type,
        prohibit: prohibit.unwrap_or(false),
    })
}

fn take_line(input: &[u8]) -> (&[u8], &[u8]) {
    if let Some(position) = input.windows(2).position(|window| window == b"\r\n") {
        (&input[..position], &input[position + 2..])
    } else {
        (input, &[])
    }
}

fn validate_status(line: &[u8]) -> Result<(), ControlHandshakeError> {
    if line.len() > MAX_CONTROL_LINE_SIZE {
        return Err(ControlHandshakeError::HeaderLineTooLong);
    }
    const PREFIX: &[u8] = b"HTTP/1.1 200";
    if line == PREFIX {
        return Ok(());
    }
    let reason = line
        .strip_prefix(PREFIX)
        .and_then(|suffix| suffix.strip_prefix(b" "))
        .ok_or(ControlHandshakeError::InvalidStatusLine)?;
    if reason
        .first()
        .is_none_or(|byte| !(0x21..=0x7e).contains(byte))
        || !reason[1..]
            .iter()
            .all(|byte| *byte == b' ' || (0x21..=0x7e).contains(byte))
    {
        return Err(ControlHandshakeError::InvalidStatusLine);
    }
    Ok(())
}

fn is_token_byte(byte: u8) -> bool {
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

fn decode_base64_canonical(
    input: &[u8],
    output: &mut [u8],
) -> Result<usize, ControlHandshakeError> {
    if input.is_empty() || input.len() % 4 != 0 {
        return Err(ControlHandshakeError::InvalidBase64);
    }
    let mut output_cursor: usize = 0;
    for (group_index, group) in input.chunks_exact(4).enumerate() {
        let final_group = group_index + 1 == input.len() / 4;
        let a = decode_sextet(group[0])?;
        let b = decode_sextet(group[1])?;
        let padding = match (group[2], group[3]) {
            (b'=', b'=') if final_group && b & 0x0f == 0 => 2,
            (third, b'=') if final_group => {
                let c = decode_sextet(third)?;
                if c & 0x03 != 0 {
                    return Err(ControlHandshakeError::InvalidBase64);
                }
                1
            }
            (b'=', _) => return Err(ControlHandshakeError::InvalidBase64),
            (_, b'=') => return Err(ControlHandshakeError::InvalidBase64),
            _ => 0,
        };
        if !final_group && padding != 0 {
            return Err(ControlHandshakeError::InvalidBase64);
        }
        let c = if padding == 2 {
            0
        } else {
            decode_sextet(group[2])?
        };
        let d = if padding != 0 {
            0
        } else {
            decode_sextet(group[3])?
        };
        let bytes_to_write = 3 - padding;
        let end = output_cursor
            .checked_add(bytes_to_write)
            .ok_or(ControlHandshakeError::InvalidBase64)?;
        let destination = output
            .get_mut(output_cursor..end)
            .ok_or(ControlHandshakeError::InvalidBase64)?;
        let decoded = [(a << 2) | (b >> 4), (b << 4) | (c >> 2), (c << 6) | d];
        destination.copy_from_slice(&decoded[..bytes_to_write]);
        output_cursor = end;
    }
    Ok(output_cursor)
}

fn decode_sextet(byte: u8) -> Result<u8, ControlHandshakeError> {
    match byte {
        b'A'..=b'Z' => Ok(byte - b'A'),
        b'a'..=b'z' => Ok(byte - b'a' + 26),
        b'0'..=b'9' => Ok(byte - b'0' + 52),
        b'+' => Ok(62),
        b'/' => Ok(63),
        _ => Err(ControlHandshakeError::InvalidBase64),
    }
}

impl Drop for ControlChannelCrypto {
    fn drop(&mut self) {
        self.rpcrypt.control_local_counter = 0;
        self.rpcrypt.control_remote_counter = 0;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::num::NonZeroU32;

    use crate::{LaunchSpec, SessionMorning, SessionNonce};

    const NONCE: [u8; 16] = [
        0x43, 0x09, 0x67, 0xae, 0x36, 0x4b, 0x1c, 0x45, 0x26, 0x62, 0x37, 0x7a, 0xbf, 0x3f, 0xe9,
        0x1d,
    ];
    const MORNING: [u8; 16] = [
        0x30, 0x71, 0x19, 0x22, 0xe8, 0x41, 0x9d, 0x70, 0x4f, 0x29, 0x64, 0x35, 0x56, 0x18, 0xc3,
        0x8a,
    ];

    struct PatternRng;

    impl RngCore for PatternRng {
        fn next_u32(&mut self) -> u32 {
            0x0302_0100
        }

        fn next_u64(&mut self) -> u64 {
            0x0706_0504_0302_0100
        }

        fn fill_bytes(&mut self, destination: &mut [u8]) {
            for (index, byte) in destination.iter_mut().enumerate() {
                *byte = 0xa0_u8.wrapping_add(index as u8);
            }
        }

        fn try_fill_bytes(&mut self, destination: &mut [u8]) -> Result<(), rand_core::Error> {
            self.fill_bytes(destination);
            Ok(())
        }
    }

    impl CryptoRng for PatternRng {}

    struct FailingRng;

    impl RngCore for FailingRng {
        fn next_u32(&mut self) -> u32 {
            0
        }

        fn next_u64(&mut self) -> u64 {
            0
        }

        fn fill_bytes(&mut self, _destination: &mut [u8]) {}

        fn try_fill_bytes(&mut self, _destination: &mut [u8]) -> Result<(), rand_core::Error> {
            Err(rand_core::Error::from(NonZeroU32::new(1).unwrap()))
        }
    }

    impl CryptoRng for FailingRng {}

    fn rpcrypt(target: SessionTarget) -> SessionRpcrypt {
        let mut morning_source = Zeroizing::new(MORNING);
        SessionRpcrypt::new(
            target,
            SessionNonce::from_session_response_bytes(NONCE),
            SessionMorning::take_from_registration_buffer(&mut morning_source),
        )
    }

    fn registration_key() -> ControlRegistrationKey {
        let mut source = Zeroizing::new([0x5a; 16]);
        let key = ControlRegistrationKey::take_from_registration_buffer(&mut source);
        assert_eq!(*source, [0; 16]);
        key
    }

    fn encrypted_server_type(rpcrypt: &SessionRpcrypt, first_byte: u8) -> [u8; 24] {
        let mut block = Zeroizing::new([0_u8; 16]);
        block[0] = first_byte;
        rpcrypt.encrypt_in_place(0, block.as_mut()).unwrap();
        let mut encoded = [0_u8; 24];
        assert_eq!(encode_base64(block.as_ref(), &mut encoded).unwrap(), 24);
        encoded
    }

    fn prepare_request(handshake: &mut ControlHandshake<'_>, codec: SessionCodec) -> Vec<u8> {
        let mut output = vec![0xa5; MAX_CONTROL_REQUEST_SIZE];
        let len = handshake
            .write_request(
                ControlEndpoint::local_ipv4(Ipv4Addr::new(255, 255, 255, 255)),
                &registration_key(),
                codec,
                &mut output,
            )
            .unwrap()
            .get();
        output.truncate(len);
        output
    }

    #[test]
    fn canonical_requests_have_exact_lengths_counters_and_did_layout() {
        for (profile, target, codec, expected_len, expected_counter) in [
            (
                ControlProfile::Ps4_8,
                SessionTarget::Ps4Pre10,
                SessionCodec::Avc,
                331,
                3,
            ),
            (
                ControlProfile::Ps4_9,
                SessionTarget::Ps4Pre10,
                SessionCodec::Avc,
                331,
                3,
            ),
            (
                ControlProfile::Ps4_10,
                SessionTarget::Ps4Modern,
                SessionCodec::Avc,
                360,
                4,
            ),
            (
                ControlProfile::Ps5,
                SessionTarget::Ps5,
                SessionCodec::HevcHdr,
                387,
                5,
            ),
        ] {
            let mut owner = rpcrypt(target);
            let mut rng = PatternRng;
            let mut handshake = owner.begin_control(profile, &mut rng).unwrap();
            let request = prepare_request(&mut handshake, codec);
            assert_eq!(request.len(), expected_len);
            assert!(request.ends_with(b"\r\n\r\n"));
            assert_eq!(handshake.rpcrypt.control_local_counter, expected_counter);

            let text = std::str::from_utf8(&request).unwrap();
            let encoded_did = text
                .lines()
                .find_map(|line| line.strip_prefix("RP-Did: "))
                .unwrap()
                .as_bytes();
            let mut encrypted_did = Zeroizing::new([0_u8; 32]);
            assert_eq!(
                decode_base64_canonical(encoded_did, encrypted_did.as_mut()).unwrap(),
                32
            );
            handshake
                .rpcrypt
                .decrypt_in_place(1, encrypted_did.as_mut())
                .unwrap();
            assert_eq!(&encrypted_did[..10], &DID_PREFIX);
            assert_eq!(
                &encrypted_did[10..26],
                &(0xa0_u8..=0xaf).collect::<Vec<_>>()
            );
            assert_eq!(&encrypted_did[26..], &[0; 6]);
        }
    }

    #[test]
    fn prepared_request_bytes_are_retryable_without_reencryption_or_counter_change() {
        let mut owner = rpcrypt(SessionTarget::Ps4Modern);
        let mut handshake = owner
            .begin_control(ControlProfile::Ps4_10, &mut PatternRng)
            .unwrap();
        let prepared = prepare_request(&mut handshake, SessionCodec::Avc);
        let counter_after_prepare = handshake.rpcrypt.control_local_counter;

        let first_send = prepared.clone();
        let retry_send = prepared.clone();
        assert_eq!(first_send, retry_send);
        assert_eq!(
            handshake.rpcrypt.control_local_counter,
            counter_after_prepare
        );

        let mut replacement = [0xa5; MAX_CONTROL_REQUEST_SIZE];
        assert_eq!(
            handshake.write_request(
                ControlEndpoint::local_ipv4(Ipv4Addr::LOCALHOST),
                &registration_key(),
                SessionCodec::Avc,
                &mut replacement,
            ),
            Err(ControlHandshakeError::RequestAlreadyPrepared)
        );
        assert_eq!(replacement, [0xa5; MAX_CONTROL_REQUEST_SIZE]);
        assert_eq!(
            handshake.rpcrypt.control_local_counter,
            counter_after_prepare
        );
    }

    #[test]
    fn begin_control_is_atomic_and_profile_bound() {
        let mut owner = rpcrypt(SessionTarget::Ps5);
        assert!(matches!(
            owner.begin_control(ControlProfile::Ps4_10, &mut PatternRng),
            Err(ControlHandshakeError::ProfileTargetMismatch)
        ));
        assert!(!owner.control_started);
        assert!(matches!(
            owner.begin_control(ControlProfile::Ps5, &mut FailingRng),
            Err(ControlHandshakeError::EntropyUnavailable)
        ));
        assert!(!owner.control_started);
        let _handshake = owner
            .begin_control(ControlProfile::Ps5, &mut PatternRng)
            .unwrap();
    }

    #[test]
    fn response_preserves_trailing_bytes_and_classification_rejection_is_atomic() {
        let mut owner = rpcrypt(SessionTarget::Ps4Modern);
        let encoded = encrypted_server_type(&owner, 0);
        let encoded = std::str::from_utf8(&encoded).unwrap();
        let mut rng = PatternRng;
        let mut handshake = owner
            .begin_control(ControlProfile::Ps4_10, &mut rng)
            .unwrap();
        let _request = prepare_request(&mut handshake, SessionCodec::Avc);
        let header = format!(
            "HTTP/1.1 200 Connection Established\r\nRP-Server-Type: {encoded}\r\nRP-Prohibit: 1\r\n\r\n"
        );
        let mut response_bytes = header.as_bytes().to_vec();
        let trailing = [0x00, 0xff, 0x0d, 0x0a, 0x42];
        response_bytes.extend_from_slice(&trailing);
        let snapshot = response_bytes.clone();
        let (response, header_len) = handshake.finish(&response_bytes).unwrap();
        assert_eq!(response.server_type, Some(ControlServerType::Ps4));
        assert!(response.prohibit);
        assert_eq!(header_len.get(), header.len());
        assert_eq!(&response_bytes[header_len.get()..], &trailing);
        assert_eq!(response_bytes, snapshot);

        let mut output = [0xa5; 1_368];
        let mismatch = LaunchSpec::new(1281, 720, 60, 15_000, 1454, 4, SessionCodec::Avc);
        assert_eq!(
            crate::launch_spec::write_encrypted_launch_spec(
                &[0x11; 16],
                &mut owner,
                mismatch,
                &mut output,
            ),
            Err(LaunchSpecError::ServerClassificationMismatch)
        );
        assert_eq!(output, [0xa5; 1_368]);
        assert!(!owner.launch_spec_counter_zero_consumed);

        let compatible = LaunchSpec::new(1280, 720, 60, 15_000, 1454, 4, SessionCodec::Avc);
        crate::launch_spec::write_encrypted_launch_spec(
            &[0x11; 16],
            &mut owner,
            compatible,
            &mut output,
        )
        .unwrap();
        let _channel = owner.into_control_channel().unwrap();
    }

    #[test]
    fn strict_parser_rejects_documented_upstream_permissiveness() {
        assert!(parse_response(b"HTTP/1.1 200\r\n\r\n").is_ok());
        assert!(parse_response(b"HTTP/1.1 200 Connection Established\r\n\r\n").is_ok());
        for invalid in [
            &b"HTTP/1.1 200\n\n"[..],
            &b"HTTP/1.1 200\r\r"[..],
            &b"HTTP/1.1 200\0\r\n\r\n"[..],
            &b"HTTP/1.1 0200 OK\r\n\r\n"[..],
            &b" HTTP/1.1 200 OK\r\n\r\n"[..],
            &b"HTTP/1.1 200 \r\n\r\n"[..],
            &b"HTTP/1.1 200 OK\r\nrp-server-type: QQ==\r\n\r\n"[..],
            &b"HTTP/1.1 200 OK\r\nRP-Prohibit : 1\r\n\r\n"[..],
            &b"HTTP/1.1 200 OK\r\nRP-Prohibit: 1\r\nRP-Prohibit: 1\r\n\r\n"[..],
        ] {
            assert!(parse_response(invalid).is_err(), "accepted {invalid:?}");
        }
        let mut output = [0_u8; 4];
        assert_eq!(
            decode_base64_canonical(b"AB==", &mut output),
            Err(ControlHandshakeError::InvalidBase64)
        );
        assert_eq!(
            decode_base64_canonical(b"QQ==junk", &mut output),
            Err(ControlHandshakeError::InvalidBase64)
        );
        assert_eq!(
            decode_base64_canonical(b"QUJ=", &mut output),
            Err(ControlHandshakeError::InvalidBase64)
        );

        for invalid in [
            &b"HTTP/1.1 200 OK\r\nX:  two-spaces\r\n\r\n"[..],
            &b"HTTP/1.1 200 OK\r\nX: \x80\r\n\r\n"[..],
            &b"HTTP/1.1 200 OK\r\nX: a\0b\r\n\r\n"[..],
            &b"HTTP/1.1 200 OK\r\nRP-Prohibit: 01\r\n\r\n"[..],
        ] {
            assert!(parse_response(invalid).is_err(), "accepted {invalid:?}");
        }

        let mut oversized = b"HTTP/1.1 200 OK\r\nX: ".to_vec();
        oversized.resize(MAX_CONTROL_HEADER_SIZE + 8, b'a');
        oversized.extend_from_slice(b"\r\n\r\n");
        assert_eq!(
            parse_response(&oversized).map(|_| ()),
            Err(ControlHandshakeError::HeaderTooLarge)
        );

        for size in 0..=1_024 {
            let mut arbitrary = vec![0_u8; size];
            for (index, byte) in arbitrary.iter_mut().enumerate() {
                *byte = (index as u8).wrapping_mul(37).wrapping_add(size as u8);
            }
            let _ = parse_response(&arbitrary);
        }
    }

    #[test]
    fn header_ows_empty_values_and_duplicate_server_type_are_rejected() {
        for (invalid, expected) in [
            (
                &b"HTTP/1.1 200 OK\r\nX:\tvalue\r\n\r\n"[..],
                ControlHandshakeError::InvalidHeader,
            ),
            (
                &b"HTTP/1.1 200 OK\r\nX:value\r\n\r\n"[..],
                ControlHandshakeError::InvalidHeader,
            ),
            (
                &b"HTTP/1.1 200 OK\r\nX: \r\n\r\n"[..],
                ControlHandshakeError::InvalidHeader,
            ),
            (
                &b"HTTP/1.1 200 OK\r\nRP-Server-Type: QQ==\r\nRP-Server-Type: QQ==\r\n\r\n"[..],
                ControlHandshakeError::DuplicateHeader,
            ),
        ] {
            assert_eq!(
                parse_response(invalid).map(|_| ()),
                Err(expected),
                "accepted {invalid:?}"
            );
        }
    }

    #[test]
    fn header_line_count_and_length_bounds_are_exact() {
        let response_with_lines = |count: usize| {
            let mut response = b"HTTP/1.1 200 OK\r\n".to_vec();
            for _ in 0..count {
                response.extend_from_slice(b"X: a\r\n");
            }
            response.extend_from_slice(b"\r\n");
            response
        };
        assert!(parse_response(&response_with_lines(32)).is_ok());
        assert_eq!(
            parse_response(&response_with_lines(33)).map(|_| ()),
            Err(ControlHandshakeError::TooManyHeaderLines)
        );

        let response_with_line_size = |size: usize| {
            let mut response = b"HTTP/1.1 200 OK\r\nX: ".to_vec();
            response.resize(response.len() + size - 3, b'a');
            response.extend_from_slice(b"\r\n\r\n");
            response
        };
        assert!(parse_response(&response_with_line_size(256)).is_ok());
        assert_eq!(
            parse_response(&response_with_line_size(257)).map(|_| ()),
            Err(ControlHandshakeError::HeaderLineTooLong)
        );
    }

    #[test]
    fn decoded_length_policy_and_unknown_server_bytes_are_exact() {
        for length in 1..=19 {
            let input = vec![0x5a; length];
            let mut encoded = vec![0_u8; length.div_ceil(3) * 4];
            encode_base64(&input, &mut encoded).unwrap();
            let mut decoded = [0_u8; 32];
            assert_eq!(
                decode_base64_canonical(&encoded, &mut decoded).unwrap(),
                length
            );
            if encoded.ends_with(b"=") {
                let unpadded_len = encoded.iter().rposition(|byte| *byte != b'=').unwrap() + 1;
                assert_eq!(
                    decode_base64_canonical(&encoded[..unpadded_len], &mut decoded),
                    Err(ControlHandshakeError::InvalidBase64)
                );
            }
            let accepted_short = matches!(length, 1 | 2 | 4 | 5 | 7 | 8 | 10 | 11 | 13 | 14);
            assert_eq!(accepted_short, length < 16 && length % 3 != 0);
        }

        for first_byte in [3, 255] {
            let mut owner = rpcrypt(SessionTarget::Ps5);
            let encoded = encrypted_server_type(&owner, first_byte);
            let encoded = std::str::from_utf8(&encoded).unwrap();
            let mut handshake = owner
                .begin_control(ControlProfile::Ps5, &mut PatternRng)
                .unwrap();
            let _request = prepare_request(&mut handshake, SessionCodec::Avc);
            let header = format!("HTTP/1.1 200 OK\r\nRP-Server-Type: {encoded}\r\n\r\n");
            assert_eq!(
                handshake.finish(header.as_bytes()),
                Err(ControlHandshakeError::InvalidServerType)
            );
        }
    }

    #[test]
    fn request_and_channel_state_are_one_shot_and_redacted() {
        let mut owner = rpcrypt(SessionTarget::Ps5);
        assert_eq!(format!("{owner:?}"), "SessionRpcrypt([REDACTED])");
        let mut handshake = owner
            .begin_control(ControlProfile::Ps5, &mut PatternRng)
            .unwrap();
        assert_eq!(format!("{handshake:?}"), "ControlHandshake([REDACTED])");
        let _request = prepare_request(&mut handshake, SessionCodec::Avc);
        let mut untouched = [0xa5; MAX_CONTROL_REQUEST_SIZE];
        assert_eq!(
            handshake.write_request(
                ControlEndpoint::local_ipv4(Ipv4Addr::LOCALHOST),
                &registration_key(),
                SessionCodec::Avc,
                &mut untouched,
            ),
            Err(ControlHandshakeError::RequestAlreadyPrepared)
        );
        assert_eq!(untouched, [0xa5; MAX_CONTROL_REQUEST_SIZE]);
        let _ = handshake.finish(b"HTTP/1.1 200\r\n\r\n").unwrap();
        assert!(matches!(
            owner.into_control_channel(),
            Err(ControlHandshakeError::LaunchSpecNotWritten)
        ));
    }

    #[test]
    fn one_byte_short_request_output_is_atomic() {
        let mut reference_owner = rpcrypt(SessionTarget::Ps5);
        let mut reference = reference_owner
            .begin_control(ControlProfile::Ps5, &mut PatternRng)
            .unwrap();
        let request = prepare_request(&mut reference, SessionCodec::Hevc);

        let mut owner = rpcrypt(SessionTarget::Ps5);
        let mut handshake = owner
            .begin_control(ControlProfile::Ps5, &mut PatternRng)
            .unwrap();
        let registration_key = registration_key();
        let mut short = vec![0xa5; request.len() - 1];
        assert_eq!(
            handshake.write_request(
                ControlEndpoint::local_ipv4(Ipv4Addr::new(255, 255, 255, 255)),
                &registration_key,
                SessionCodec::Hevc,
                &mut short,
            ),
            Err(ControlHandshakeError::OutputTooSmall)
        );
        assert!(short.iter().all(|byte| *byte == 0xa5));
        assert_eq!(handshake.rpcrypt.control_local_counter, 0);
        assert!(!handshake.request_prepared);

        let mut retry = vec![0xa5; request.len()];
        let retry_len = handshake
            .write_request(
                ControlEndpoint::local_ipv4(Ipv4Addr::new(255, 255, 255, 255)),
                &registration_key,
                SessionCodec::Hevc,
                &mut retry,
            )
            .unwrap()
            .get();
        assert_eq!(&retry[..retry_len], request.as_slice());
    }

    #[test]
    fn codec_and_classification_mismatches_are_rejected_before_state_changes() {
        let mut ps4_owner = rpcrypt(SessionTarget::Ps4Modern);
        let mut ps4_handshake = ps4_owner
            .begin_control(ControlProfile::Ps4_10, &mut PatternRng)
            .unwrap();
        let key = registration_key();
        let mut output = [0xa5; MAX_CONTROL_REQUEST_SIZE];
        assert_eq!(
            ps4_handshake.write_request(
                ControlEndpoint::local_ipv4(Ipv4Addr::LOCALHOST),
                &key,
                SessionCodec::Hevc,
                &mut output,
            ),
            Err(ControlHandshakeError::UnsupportedCodec)
        );
        assert_eq!(output, [0xa5; MAX_CONTROL_REQUEST_SIZE]);
        assert_eq!(ps4_handshake.rpcrypt.control_local_counter, 0);
        assert!(!ps4_handshake.request_prepared);
        drop(ps4_handshake);

        let spec_hevc = LaunchSpec::new(1920, 1080, 60, 15_000, 1454, 4, SessionCodec::Hevc);
        assert_eq!(
            crate::launch_spec::validate_server_classification(
                Some(ControlServerType::Ps4Pro),
                SessionTarget::Ps4Modern,
                spec_hevc,
            ),
            Err(LaunchSpecError::ServerClassificationMismatch)
        );
        assert_eq!(
            crate::launch_spec::validate_server_classification(
                Some(ControlServerType::Ps4),
                SessionTarget::Ps5,
                LaunchSpec::new(1280, 720, 60, 15_000, 1454, 4, SessionCodec::Avc),
            ),
            Err(LaunchSpecError::ServerClassificationMismatch)
        );

        let mut unfinished = rpcrypt(SessionTarget::Ps4Modern);
        let mut launch_output = [0xa5; 1_368];
        assert_eq!(
            crate::launch_spec::write_encrypted_launch_spec(
                &[0x11; 16],
                &mut unfinished,
                LaunchSpec::new(1280, 720, 60, 15_000, 1454, 4, SessionCodec::Avc),
                &mut launch_output,
            ),
            Err(LaunchSpecError::ControlHandshakeNotFinished)
        );
        assert_eq!(launch_output, [0xa5; 1_368]);
    }

    #[test]
    fn type_two_rejects_ps4_and_boundaries_are_exact() {
        for (server_type, target, spec, expected) in [
            (
                Some(ControlServerType::Ps4),
                SessionTarget::Ps4Modern,
                LaunchSpec::new(1280, 721, 60, 1, 1, 1, SessionCodec::Avc),
                false,
            ),
            (
                Some(ControlServerType::Ps4),
                SessionTarget::Ps4Modern,
                LaunchSpec::new(1281, 720, 60, 1, 1, 1, SessionCodec::Avc),
                false,
            ),
            (
                Some(ControlServerType::Ps4),
                SessionTarget::Ps4Modern,
                LaunchSpec::new(1280, 720, 60, 1, 1, 1, SessionCodec::Avc),
                true,
            ),
            (
                Some(ControlServerType::Ps5),
                SessionTarget::Ps4Modern,
                LaunchSpec::new(1280, 720, 60, 1, 1, 1, SessionCodec::Avc),
                false,
            ),
        ] {
            assert_eq!(
                crate::launch_spec::validate_server_classification(server_type, target, spec)
                    .is_ok(),
                expected
            );
        }
    }
}

#[cfg(test)]
#[path = "control_handshake_oracle_tests.rs"]
mod oracle_tests;
