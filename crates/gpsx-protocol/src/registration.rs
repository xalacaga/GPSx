// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

use core::fmt;

pub const REGISTRATION_REQUEST_PREFIX_SIZE: usize = 0x1e0;
pub const MAX_REGISTRATION_REQUEST_HEADER_SIZE: usize = 512;
pub const MAX_REGISTRATION_RESPONSE_HEADER_SIZE: usize = 1_024;
pub const MAX_REGISTRATION_BODY_SIZE: usize = 1_024;
pub const MAX_REGISTRATION_RESPONSE_SIZE: usize = 1_500;
pub const MAX_REGISTRATION_FIELD_COUNT: usize = 128;
pub const MAX_REGISTRATION_FIELD_LINE_SIZE: usize = 512;
pub const MAX_REGISTRATION_FIELD_NAME_SIZE: usize = 64;

const PRE10_AEROPAUSE_OFFSET: usize = 0x11c;
const MODERN_AEROPAUSE_SECOND_HALF_OFFSET: usize = 0xc7;
const MODERN_AEROPAUSE_FIRST_HALF_OFFSET: usize = 0x191;
const MODERN_CIPHER_OFFSET_SOURCE: usize = 0x18d;
const AEROPAUSE_SIZE: usize = 16;
const ACCOUNT_ID_SIZE: usize = 8;
const ACCOUNT_ID_BASE64_SIZE: usize = 12;
const MODERN_CLIENT_TYPE: &[u8] =
    b"dabfa2ec873de5839bee8d3f4c0239c4282c07c25c6077a2931afcf0adc0d34f";

const _: () = assert!(PRE10_AEROPAUSE_OFFSET + AEROPAUSE_SIZE <= REGISTRATION_REQUEST_PREFIX_SIZE);
const _: () = assert!(
    MODERN_AEROPAUSE_SECOND_HALF_OFFSET + AEROPAUSE_SIZE / 2 <= REGISTRATION_REQUEST_PREFIX_SIZE
);
const _: () = assert!(
    MODERN_AEROPAUSE_FIRST_HALF_OFFSET + AEROPAUSE_SIZE / 2 <= REGISTRATION_REQUEST_PREFIX_SIZE
);
const _: () = assert!(MODERN_AEROPAUSE_SECOND_HALF_OFFSET > 0);
const _: () = assert!(
    MODERN_CIPHER_OFFSET_SOURCE < MODERN_AEROPAUSE_SECOND_HALF_OFFSET
        || MODERN_CIPHER_OFFSET_SOURCE >= MODERN_AEROPAUSE_SECOND_HALF_OFFSET + 8
);
const _: () = assert!(MODERN_AEROPAUSE_FIRST_HALF_OFFSET > 0);
const _: () = assert!(
    MODERN_CIPHER_OFFSET_SOURCE < MODERN_AEROPAUSE_FIRST_HALF_OFFSET
        || MODERN_CIPHER_OFFSET_SOURCE >= MODERN_AEROPAUSE_FIRST_HALF_OFFSET + 8
);

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RegistrationCodecError {
    OutputTooSmall,
    ArithmeticOverflow,
    InvalidOnlineId,
    IdentityProfileMismatch,
    CryptoTargetMismatch,
    EncryptedLengthMismatch,
    RequestHeaderTooLarge,
    ResponseTooLarge,
    ResponseHeaderTooLarge,
    MissingHeaderTerminator,
    InvalidLineEnding,
    LineTooLong,
    TooManyFields,
    InvalidStatus,
    MissingHeaderColon,
    InvalidFieldName,
    InvalidFieldNameCase,
    InvalidSeparator,
    EmptyValue,
    InvalidUtf8,
    ControlCharacter,
    TransferEncodingUnsupported,
    MissingContentLength,
    DuplicateContentLength,
    InvalidContentLength,
    BodyTooLarge,
    BodyLengthMismatch,
    HttpStatus,
    DuplicateField,
    WrongProfileField,
    InvalidFieldValue,
    MissingRegistrationKey,
    MissingRpKey,
    MissingMac,
}

impl fmt::Display for RegistrationCodecError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::OutputTooSmall => "registration output buffer is too small",
            Self::ArithmeticOverflow => "registration size arithmetic overflowed",
            Self::InvalidOnlineId => "registration online identifier is invalid",
            Self::IdentityProfileMismatch => "registration identity does not match the profile",
            Self::CryptoTargetMismatch => "registration crypto target does not match the profile",
            Self::EncryptedLengthMismatch => {
                "registration encrypted block length differs from plaintext length"
            }
            Self::RequestHeaderTooLarge => "registration request header exceeds its limit",
            Self::ResponseTooLarge => "registration response exceeds its aggregate limit",
            Self::ResponseHeaderTooLarge => "registration response header exceeds its limit",
            Self::MissingHeaderTerminator => "registration response has no header terminator",
            Self::InvalidLineEnding => "registration message has an invalid line ending",
            Self::LineTooLong => "registration field line exceeds its limit",
            Self::TooManyFields => "registration message exceeds its field limit",
            Self::InvalidStatus => "registration response has an invalid HTTP status",
            Self::MissingHeaderColon => "registration field has no colon",
            Self::InvalidFieldName => "registration field name is invalid",
            Self::InvalidFieldNameCase => "registration known field name has invalid casing",
            Self::InvalidSeparator => "registration field separator is invalid",
            Self::EmptyValue => "registration field value is empty",
            Self::InvalidUtf8 => "registration field value is not valid UTF-8",
            Self::ControlCharacter => "registration field value contains a control character",
            Self::TransferEncodingUnsupported => {
                "registration response uses unsupported transfer encoding"
            }
            Self::MissingContentLength => "registration response has no content length",
            Self::DuplicateContentLength => "registration response repeats content length",
            Self::InvalidContentLength => "registration response content length is invalid",
            Self::BodyTooLarge => "registration response body exceeds its limit",
            Self::BodyLengthMismatch => "registration response body length is inconsistent",
            Self::HttpStatus => "registration response has a non-success HTTP status",
            Self::DuplicateField => "registration response repeats a known field",
            Self::WrongProfileField => "registration response field belongs to another profile",
            Self::InvalidFieldValue => "registration response field value is invalid",
            Self::MissingRegistrationKey => "registration response is missing its registration key",
            Self::MissingRpKey => "registration response is missing its RP key",
            Self::MissingMac => "registration response is missing its console MAC",
        })
    }
}

impl std::error::Error for RegistrationCodecError {}

/// RP protocol target used by the local-registration wire codec.
///
/// The PS4_9 plus OnlineId compatibility formatter is compiled only under
/// `cfg(test)` and is private. Those two properties remove it from production;
/// this doctest additionally confirms that no public export exists:
///
/// ```compile_fail
/// use gpsx_protocol::format_ps4_9_online_id_compatibility;
/// ```
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RegistrationProfile {
    Ps4_8,
    Ps4_9,
    Ps4_10Target,
    Ps5,
}

impl RegistrationProfile {
    #[must_use]
    pub const fn crypto_target(self) -> RegistrationCryptoTarget {
        match self {
            Self::Ps4_8 | Self::Ps4_9 => RegistrationCryptoTarget::Ps4Pre10,
            Self::Ps4_10Target => RegistrationCryptoTarget::Ps4Modern,
            Self::Ps5 => RegistrationCryptoTarget::Ps5,
        }
    }

    const fn request_path(self) -> &'static [u8] {
        match self {
            Self::Ps4_8 | Self::Ps4_9 => b"/sce/rp/regist",
            Self::Ps4_10Target => b"/sie/ps4/rp/sess/rgst",
            Self::Ps5 => b"/sie/ps5/rp/sess/rgst",
        }
    }

    const fn rp_version(self) -> Option<&'static [u8]> {
        match self {
            Self::Ps4_8 => None,
            Self::Ps4_9 => Some(b"9.0"),
            Self::Ps4_10Target => Some(b"10.0"),
            Self::Ps5 => Some(b"1.0"),
        }
    }

    const fn is_modern(self) -> bool {
        matches!(self, Self::Ps4_10Target | Self::Ps5)
    }

    const fn is_ps5(self) -> bool {
        matches!(self, Self::Ps5)
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RegistrationCryptoTarget {
    Ps4Pre10,
    Ps4Modern,
    Ps5,
}

pub fn validate_registration_crypto_target(
    profile: RegistrationProfile,
    target: RegistrationCryptoTarget,
) -> Result<(), RegistrationCodecError> {
    if profile.crypto_target() == target {
        Ok(())
    } else {
        Err(RegistrationCodecError::CryptoTargetMismatch)
    }
}

/// Borrowed, validated online identifier.
///
/// ```compile_fail
/// use gpsx_protocol::OnlineId;
/// let value = OnlineId::new("Player_1").unwrap();
/// let duplicate = value.clone();
/// ```
#[repr(transparent)]
pub struct OnlineId<'a>(&'a str);

impl<'a> OnlineId<'a> {
    pub fn new(value: &'a str) -> Result<Self, RegistrationCodecError> {
        let bytes = value.as_bytes();
        if !(1..=16).contains(&bytes.len())
            || !bytes
                .iter()
                .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-'))
        {
            return Err(RegistrationCodecError::InvalidOnlineId);
        }
        Ok(Self(value))
    }

    const fn as_bytes(&self) -> &[u8] {
        self.0.as_bytes()
    }
}

/// Borrowed eight-byte account identifier.
///
/// ```compile_fail
/// use gpsx_protocol::AccountId;
/// let bytes = [0_u8; 8];
/// let value = AccountId::new(&bytes);
/// let duplicate = value.clone();
/// ```
#[repr(transparent)]
pub struct AccountId<'a>(&'a [u8; ACCOUNT_ID_SIZE]);

impl<'a> AccountId<'a> {
    #[must_use]
    pub const fn new(value: &'a [u8; ACCOUNT_ID_SIZE]) -> Self {
        Self(value)
    }

    const fn as_bytes(&self) -> &[u8; ACCOUNT_ID_SIZE] {
        self.0
    }
}

/// Borrowed identity selected for one registration request.
///
/// ```compile_fail
/// use gpsx_protocol::RegistrationIdentity;
/// fn assert_clone<T: Clone>() {}
/// assert_clone::<RegistrationIdentity<'static>>();
/// ```
///
/// ```compile_fail
/// use gpsx_protocol::RegistrationIdentity;
/// fn assert_debug<T: core::fmt::Debug>() {}
/// assert_debug::<RegistrationIdentity<'static>>();
/// ```
pub enum RegistrationIdentity<'a> {
    OnlineId(OnlineId<'a>),
    AccountId(AccountId<'a>),
}

pub fn format_registration_inner_request(
    profile: RegistrationProfile,
    identity: RegistrationIdentity<'_>,
    output: &mut [u8],
) -> Result<usize, RegistrationCodecError> {
    match (profile, identity) {
        (RegistrationProfile::Ps4_8, RegistrationIdentity::OnlineId(online_id)) => {
            format_online_id_inner(online_id, output)
        }
        (RegistrationProfile::Ps4_9, RegistrationIdentity::AccountId(account_id)) => {
            format_account_id_inner(false, account_id, output)
        }
        (
            RegistrationProfile::Ps4_10Target | RegistrationProfile::Ps5,
            RegistrationIdentity::AccountId(account_id),
        ) => format_account_id_inner(true, account_id, output),
        _ => Err(RegistrationCodecError::IdentityProfileMismatch),
    }
}

#[cfg(test)]
fn format_ps4_9_online_id_compatibility(
    online_id: OnlineId<'_>,
    output: &mut [u8],
) -> Result<usize, RegistrationCodecError> {
    format_online_id_inner(online_id, output)
}

fn format_online_id_inner(
    online_id: OnlineId<'_>,
    output: &mut [u8],
) -> Result<usize, RegistrationCodecError> {
    const PREFIX: &[u8] = b"Client-Type: Windows\r\nNp-Online-Id: ";
    const SUFFIX: &[u8] = b"\r\n";
    let required = checked_sum(&[PREFIX.len(), online_id.as_bytes().len(), SUFFIX.len()])?;
    ensure_output(output, required)?;
    let mut cursor = 0;
    write_bytes(output, &mut cursor, PREFIX);
    write_bytes(output, &mut cursor, online_id.as_bytes());
    write_bytes(output, &mut cursor, SUFFIX);
    Ok(cursor)
}

fn format_account_id_inner(
    modern: bool,
    account_id: AccountId<'_>,
    output: &mut [u8],
) -> Result<usize, RegistrationCodecError> {
    const CLIENT_PREFIX: &[u8] = b"Client-Type: ";
    const ACCOUNT_PREFIX: &[u8] = b"\r\nNp-AccountId: ";
    const SUFFIX: &[u8] = b"\r\n";
    let client_type = if modern {
        MODERN_CLIENT_TYPE
    } else {
        &b"Windows"[..]
    };
    let required = checked_sum(&[
        CLIENT_PREFIX.len(),
        client_type.len(),
        ACCOUNT_PREFIX.len(),
        ACCOUNT_ID_BASE64_SIZE,
        SUFFIX.len(),
    ])?;
    ensure_output(output, required)?;
    let mut cursor = 0;
    write_bytes(output, &mut cursor, CLIENT_PREFIX);
    write_bytes(output, &mut cursor, client_type);
    write_bytes(output, &mut cursor, ACCOUNT_PREFIX);
    let encoded_end = cursor
        .checked_add(ACCOUNT_ID_BASE64_SIZE)
        .ok_or(RegistrationCodecError::ArithmeticOverflow)?;
    let encoded_len = encode_base64(account_id.as_bytes(), &mut output[cursor..encoded_end])?;
    cursor += encoded_len;
    write_bytes(output, &mut cursor, SUFFIX);
    Ok(cursor)
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct RegistrationOffsets {
    pub cipher_offset: u8,
    pub aeropause_offset: u8,
}

/// Exclusive preparation token for one request payload.
///
/// ```compile_fail
/// use gpsx_protocol::PreparedRegistrationPrefix;
/// fn assert_clone<T: Clone>() {}
/// assert_clone::<PreparedRegistrationPrefix<'static>>();
/// ```
///
/// ```compile_fail
/// use gpsx_protocol::PreparedRegistrationPrefix;
/// fn assert_debug<T: core::fmt::Debug>() {}
/// assert_debug::<PreparedRegistrationPrefix<'static>>();
/// ```
pub struct PreparedRegistrationPrefix<'a> {
    profile: RegistrationProfile,
    output: &'a mut [u8],
    plaintext_len: usize,
    total_len: usize,
    offsets: Option<RegistrationOffsets>,
}

impl PreparedRegistrationPrefix<'_> {
    #[must_use]
    pub const fn offsets(&self) -> Option<RegistrationOffsets> {
        self.offsets
    }
}

pub fn prepare_registration_request_prefix<'a>(
    profile: RegistrationProfile,
    filler: &[u8; REGISTRATION_REQUEST_PREFIX_SIZE],
    plaintext_len: usize,
    output: &'a mut [u8],
) -> Result<PreparedRegistrationPrefix<'a>, RegistrationCodecError> {
    let total_len = REGISTRATION_REQUEST_PREFIX_SIZE
        .checked_add(plaintext_len)
        .ok_or(RegistrationCodecError::ArithmeticOverflow)?;
    ensure_output(output, total_len)?;
    output[..REGISTRATION_REQUEST_PREFIX_SIZE].copy_from_slice(filler);
    let offsets = profile.is_modern().then_some(RegistrationOffsets {
        cipher_offset: filler[MODERN_CIPHER_OFFSET_SOURCE] & 0x1f,
        aeropause_offset: filler[0] >> 3,
    });
    Ok(PreparedRegistrationPrefix {
        profile,
        output,
        plaintext_len,
        total_len,
        offsets,
    })
}

/// Borrowed completed registration payload.
///
/// ```compile_fail
/// use gpsx_protocol::RegistrationPayload;
/// fn assert_clone<T: Clone>() {}
/// assert_clone::<RegistrationPayload<'static>>();
/// ```
///
/// ```compile_fail
/// use gpsx_protocol::RegistrationPayload;
/// fn assert_debug<T: core::fmt::Debug>() {}
/// assert_debug::<RegistrationPayload<'static>>();
/// ```
pub struct RegistrationPayload<'a> {
    profile: RegistrationProfile,
    bytes: &'a [u8],
}

impl RegistrationPayload<'_> {
    #[must_use]
    pub const fn as_bytes(&self) -> &[u8] {
        self.bytes
    }

    #[must_use]
    pub const fn profile(&self) -> RegistrationProfile {
        self.profile
    }
}

pub fn finish_registration_request_payload<'a>(
    prepared: PreparedRegistrationPrefix<'a>,
    aeropause: &[u8; AEROPAUSE_SIZE],
    encrypted_inner: &[u8],
) -> Result<RegistrationPayload<'a>, RegistrationCodecError> {
    if encrypted_inner.len() != prepared.plaintext_len {
        return Err(RegistrationCodecError::EncryptedLengthMismatch);
    }
    if prepared.profile.is_modern() {
        prepared.output
            [MODERN_AEROPAUSE_SECOND_HALF_OFFSET..MODERN_AEROPAUSE_SECOND_HALF_OFFSET + 8]
            .copy_from_slice(&aeropause[8..16]);
        prepared.output[MODERN_AEROPAUSE_FIRST_HALF_OFFSET..MODERN_AEROPAUSE_FIRST_HALF_OFFSET + 8]
            .copy_from_slice(&aeropause[0..8]);
    } else {
        prepared.output[PRE10_AEROPAUSE_OFFSET..PRE10_AEROPAUSE_OFFSET + AEROPAUSE_SIZE]
            .copy_from_slice(aeropause);
    }
    prepared.output[REGISTRATION_REQUEST_PREFIX_SIZE..prepared.total_len]
        .copy_from_slice(encrypted_inner);
    Ok(RegistrationPayload {
        profile: prepared.profile,
        bytes: &prepared.output[..prepared.total_len],
    })
}

pub fn format_registration_http_request_header(
    payload: &RegistrationPayload<'_>,
    output: &mut [u8],
) -> Result<usize, RegistrationCodecError> {
    const METHOD: &[u8] = b"POST ";
    const QUIRK_AND_HOST: &[u8] = b" HTTP/1.1\r\n HTTP/1.1\r\nHOST: 10.0.2.15\r\n";
    const USER_AGENT: &[u8] = b"User-Agent: remoteplay Windows\r\n";
    const CONNECTION: &[u8] = b"Connection: close\r\n";
    const LENGTH_PREFIX: &[u8] = b"Content-Length: ";
    const VERSION_PREFIX: &[u8] = b"RP-Version: ";
    const LINE_END: &[u8] = b"\r\n";

    let mut decimal = [0_u8; 20];
    let decimal_len = format_decimal(payload.bytes.len(), &mut decimal);
    let path = payload.profile.request_path();
    let version_len = payload.profile.rp_version().map_or(0, |version| {
        VERSION_PREFIX.len() + version.len() + LINE_END.len()
    });
    let required = checked_sum(&[
        METHOD.len(),
        path.len(),
        QUIRK_AND_HOST.len(),
        USER_AGENT.len(),
        CONNECTION.len(),
        LENGTH_PREFIX.len(),
        decimal_len,
        LINE_END.len(),
        version_len,
        LINE_END.len(),
    ])?;
    if required > MAX_REGISTRATION_REQUEST_HEADER_SIZE {
        return Err(RegistrationCodecError::RequestHeaderTooLarge);
    }
    ensure_output(output, required)?;
    let mut cursor = 0;
    write_bytes(output, &mut cursor, METHOD);
    write_bytes(output, &mut cursor, path);
    write_bytes(output, &mut cursor, QUIRK_AND_HOST);
    write_bytes(output, &mut cursor, USER_AGENT);
    write_bytes(output, &mut cursor, CONNECTION);
    write_bytes(output, &mut cursor, LENGTH_PREFIX);
    write_bytes(output, &mut cursor, &decimal[..decimal_len]);
    write_bytes(output, &mut cursor, LINE_END);
    if let Some(version) = payload.profile.rp_version() {
        write_bytes(output, &mut cursor, VERSION_PREFIX);
        write_bytes(output, &mut cursor, version);
        write_bytes(output, &mut cursor, LINE_END);
    }
    write_bytes(output, &mut cursor, LINE_END);
    Ok(cursor)
}

/// Borrowed registration HTTP response body.
///
/// ```compile_fail
/// use gpsx_protocol::RegistrationHttpResponse;
/// fn assert_clone<T: Clone>() {}
/// assert_clone::<RegistrationHttpResponse<'static>>();
/// ```
///
/// ```compile_fail
/// use gpsx_protocol::RegistrationHttpResponse;
/// fn assert_debug<T: core::fmt::Debug>() {}
/// assert_debug::<RegistrationHttpResponse<'static>>();
/// ```
pub struct RegistrationHttpResponse<'a> {
    body: &'a [u8],
}

impl RegistrationHttpResponse<'_> {
    #[must_use]
    pub const fn body(&self) -> &[u8] {
        self.body
    }
}

pub fn parse_registration_http_response(
    input: &[u8],
) -> Result<RegistrationHttpResponse<'_>, RegistrationCodecError> {
    if input.len() > MAX_REGISTRATION_RESPONSE_SIZE {
        return Err(RegistrationCodecError::ResponseTooLarge);
    }
    let search_len = input.len().min(MAX_REGISTRATION_RESPONSE_HEADER_SIZE);
    let separator = find_bytes(&input[..search_len], b"\r\n\r\n");
    let separator = match separator {
        Some(position) => position,
        None if input.len() >= MAX_REGISTRATION_RESPONSE_HEADER_SIZE => {
            return Err(RegistrationCodecError::ResponseHeaderTooLarge);
        }
        None => return Err(RegistrationCodecError::MissingHeaderTerminator),
    };
    let header_len = separator
        .checked_add(4)
        .ok_or(RegistrationCodecError::ArithmeticOverflow)?;
    if header_len > MAX_REGISTRATION_RESPONSE_HEADER_SIZE {
        return Err(RegistrationCodecError::ResponseHeaderTooLarge);
    }
    let header_block = &input[..separator];
    validate_crlf_only(header_block)?;
    let mut cursor = 0;
    let status_line = next_crlf_or_end(header_block, &mut cursor);
    if status_line.len() + 2 > MAX_REGISTRATION_FIELD_LINE_SIZE {
        return Err(RegistrationCodecError::LineTooLong);
    }
    let status = parse_http_status(status_line)?;
    let mut field_count = 0;
    let mut content_length = None;
    while cursor < header_block.len() {
        let line = next_crlf_or_end(header_block, &mut cursor);
        if line.len() + 2 > MAX_REGISTRATION_FIELD_LINE_SIZE {
            return Err(RegistrationCodecError::LineTooLong);
        }
        field_count += 1;
        if field_count > MAX_REGISTRATION_FIELD_COUNT {
            return Err(RegistrationCodecError::TooManyFields);
        }
        let (name, value) = parse_name_value(line)?;
        if name.eq_ignore_ascii_case(b"Transfer-Encoding") {
            return Err(RegistrationCodecError::TransferEncodingUnsupported);
        }
        if name.eq_ignore_ascii_case(b"Content-Length") {
            if content_length.is_some() {
                return Err(RegistrationCodecError::DuplicateContentLength);
            }
            content_length = Some(parse_content_length(value)?);
        }
    }

    let body = &input[header_len..];
    match content_length {
        Some(length) => {
            if length > MAX_REGISTRATION_BODY_SIZE {
                return Err(RegistrationCodecError::BodyTooLarge);
            }
            if body.len() != length {
                return Err(RegistrationCodecError::BodyLengthMismatch);
            }
        }
        None if status == 200 => return Err(RegistrationCodecError::MissingContentLength),
        None if !body.is_empty() => return Err(RegistrationCodecError::BodyLengthMismatch),
        None => {}
    }
    if status != 200 {
        return Err(RegistrationCodecError::HttpStatus);
    }
    Ok(RegistrationHttpResponse { body })
}

/// Borrowed view of validated decrypted registration fields.
///
/// ```compile_fail
/// use gpsx_protocol::RegistrationResponseView;
/// fn assert_clone<T: Clone>() {}
/// assert_clone::<RegistrationResponseView<'static>>();
/// ```
///
/// ```compile_fail
/// use gpsx_protocol::RegistrationResponseView;
/// fn assert_debug<T: core::fmt::Debug>() {}
/// assert_debug::<RegistrationResponseView<'static>>();
/// ```
pub struct RegistrationResponseView<'a> {
    nickname: Option<&'a str>,
    registration_key_hex: &'a [u8],
    rp_key_type: Option<u32>,
    rp_key_hex: &'a [u8],
    mac_hex: &'a [u8],
    support_command: Option<u32>,
}

impl<'a> RegistrationResponseView<'a> {
    #[must_use]
    pub const fn nickname(&self) -> Option<&'a str> {
        self.nickname
    }

    #[must_use]
    pub const fn registration_key_hex(&self) -> &'a [u8] {
        self.registration_key_hex
    }

    #[must_use]
    pub const fn rp_key_type(&self) -> Option<u32> {
        self.rp_key_type
    }

    #[must_use]
    pub const fn rp_key_hex(&self) -> &'a [u8] {
        self.rp_key_hex
    }

    #[must_use]
    pub const fn mac_hex(&self) -> &'a [u8] {
        self.mac_hex
    }

    #[must_use]
    pub const fn support_command(&self) -> Option<u32> {
        self.support_command
    }
}

#[derive(Default)]
struct SeenFields {
    ap_ssid: bool,
    ap_bssid: bool,
    ap_key: bool,
    ap_name: bool,
    nickname: bool,
    registration_key: bool,
    rp_key_type: bool,
    rp_key: bool,
    mac: bool,
    support_command: bool,
}

pub fn parse_registration_response_fields<'a>(
    profile: RegistrationProfile,
    input: &'a [u8],
) -> Result<RegistrationResponseView<'a>, RegistrationCodecError> {
    if input.len() > MAX_REGISTRATION_BODY_SIZE {
        return Err(RegistrationCodecError::BodyTooLarge);
    }
    if input.is_empty() || !input.ends_with(b"\r\n") {
        return Err(RegistrationCodecError::InvalidLineEnding);
    }
    validate_crlf_only(input)?;
    let mut cursor = 0;
    let mut field_count = 0;
    let mut seen = SeenFields::default();
    let mut nickname = None;
    let mut registration_key_hex = None;
    let mut rp_key_type = None;
    let mut rp_key_hex = None;
    let mut mac_hex = None;
    let mut support_command = None;

    while cursor < input.len() {
        let line = next_required_crlf(input, &mut cursor)?;
        if line.len() + 2 > MAX_REGISTRATION_FIELD_LINE_SIZE {
            return Err(RegistrationCodecError::LineTooLong);
        }
        field_count += 1;
        if field_count > MAX_REGISTRATION_FIELD_COUNT {
            return Err(RegistrationCodecError::TooManyFields);
        }
        let (name, value) = parse_name_value(line)?;
        reject_known_case_variant(name)?;
        let value_text = validate_general_value(value)?;

        match name {
            b"AP-Ssid" => {
                mark_once(&mut seen.ap_ssid)?;
                validate_text(value_text, 47)?;
            }
            b"AP-Bssid" => {
                mark_once(&mut seen.ap_bssid)?;
                validate_text(value_text, 31)?;
            }
            b"AP-Key" => {
                mark_once(&mut seen.ap_key)?;
                validate_text(value_text, 79)?;
            }
            b"AP-Name" => {
                mark_once(&mut seen.ap_name)?;
                validate_text(value_text, 31)?;
            }
            b"PS4-Nickname" => {
                ensure_ps4(profile)?;
                mark_once(&mut seen.nickname)?;
                validate_text(value_text, 31)?;
                nickname = Some(value_text);
            }
            b"PS5-Nickname" => {
                ensure_ps5(profile)?;
                mark_once(&mut seen.nickname)?;
                validate_text(value_text, 31)?;
                nickname = Some(value_text);
            }
            b"PS4-RegistKey" => {
                ensure_ps4(profile)?;
                mark_once(&mut seen.registration_key)?;
                validate_hex_range(value, 1, 16)?;
                registration_key_hex = Some(value);
            }
            b"PS5-RegistKey" => {
                ensure_ps5(profile)?;
                mark_once(&mut seen.registration_key)?;
                validate_hex_range(value, 1, 16)?;
                registration_key_hex = Some(value);
            }
            b"RP-KeyType" => {
                mark_once(&mut seen.rp_key_type)?;
                rp_key_type = Some(parse_canonical_u32(value)?);
            }
            b"RP-Key" => {
                mark_once(&mut seen.rp_key)?;
                validate_hex_exact(value, 16)?;
                rp_key_hex = Some(value);
            }
            b"PS4-Mac" => {
                ensure_ps4(profile)?;
                mark_once(&mut seen.mac)?;
                validate_hex_exact(value, 6)?;
                mac_hex = Some(value);
            }
            b"PS5-Mac" => {
                ensure_ps5(profile)?;
                mark_once(&mut seen.mac)?;
                validate_hex_exact(value, 6)?;
                mac_hex = Some(value);
            }
            b"RP-SupportCmd" => {
                mark_once(&mut seen.support_command)?;
                support_command = Some(parse_canonical_u32(value)?);
            }
            _ => {}
        }
    }

    Ok(RegistrationResponseView {
        nickname,
        registration_key_hex: registration_key_hex
            .ok_or(RegistrationCodecError::MissingRegistrationKey)?,
        rp_key_type,
        rp_key_hex: rp_key_hex.ok_or(RegistrationCodecError::MissingRpKey)?,
        mac_hex: mac_hex.ok_or(RegistrationCodecError::MissingMac)?,
        support_command,
    })
}

fn checked_sum(parts: &[usize]) -> Result<usize, RegistrationCodecError> {
    parts.iter().try_fold(0_usize, |sum, part| {
        sum.checked_add(*part)
            .ok_or(RegistrationCodecError::ArithmeticOverflow)
    })
}

fn ensure_output(output: &[u8], required: usize) -> Result<(), RegistrationCodecError> {
    if output.len() < required {
        Err(RegistrationCodecError::OutputTooSmall)
    } else {
        Ok(())
    }
}

fn write_bytes(output: &mut [u8], cursor: &mut usize, bytes: &[u8]) {
    let end = *cursor + bytes.len();
    output[*cursor..end].copy_from_slice(bytes);
    *cursor = end;
}

fn encode_base64(input: &[u8], output: &mut [u8]) -> Result<usize, RegistrationCodecError> {
    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let required = input
        .len()
        .checked_add(2)
        .and_then(|value| value.checked_div(3))
        .and_then(|value| value.checked_mul(4))
        .ok_or(RegistrationCodecError::ArithmeticOverflow)?;
    ensure_output(output, required)?;
    let mut input_cursor = 0;
    let mut output_cursor = 0;
    while input_cursor + 3 <= input.len() {
        let value = (u32::from(input[input_cursor]) << 16)
            | (u32::from(input[input_cursor + 1]) << 8)
            | u32::from(input[input_cursor + 2]);
        output[output_cursor] = ALPHABET[((value >> 18) & 0x3f) as usize];
        output[output_cursor + 1] = ALPHABET[((value >> 12) & 0x3f) as usize];
        output[output_cursor + 2] = ALPHABET[((value >> 6) & 0x3f) as usize];
        output[output_cursor + 3] = ALPHABET[(value & 0x3f) as usize];
        input_cursor += 3;
        output_cursor += 4;
    }
    let remaining = input.len() - input_cursor;
    if remaining == 1 {
        let value = u32::from(input[input_cursor]) << 16;
        output[output_cursor] = ALPHABET[((value >> 18) & 0x3f) as usize];
        output[output_cursor + 1] = ALPHABET[((value >> 12) & 0x3f) as usize];
        output[output_cursor + 2] = b'=';
        output[output_cursor + 3] = b'=';
    } else if remaining == 2 {
        let value =
            (u32::from(input[input_cursor]) << 16) | (u32::from(input[input_cursor + 1]) << 8);
        output[output_cursor] = ALPHABET[((value >> 18) & 0x3f) as usize];
        output[output_cursor + 1] = ALPHABET[((value >> 12) & 0x3f) as usize];
        output[output_cursor + 2] = ALPHABET[((value >> 6) & 0x3f) as usize];
        output[output_cursor + 3] = b'=';
    }
    Ok(required)
}

fn format_decimal(mut value: usize, output: &mut [u8; 20]) -> usize {
    let mut cursor = output.len();
    loop {
        cursor -= 1;
        output[cursor] = b'0' + (value % 10) as u8;
        value /= 10;
        if value == 0 {
            break;
        }
    }
    let length = output.len() - cursor;
    output.copy_within(cursor.., 0);
    length
}

fn find_bytes(input: &[u8], needle: &[u8]) -> Option<usize> {
    input
        .windows(needle.len())
        .position(|window| window == needle)
}

fn validate_crlf_only(input: &[u8]) -> Result<(), RegistrationCodecError> {
    let mut cursor = 0;
    while cursor < input.len() {
        match input[cursor] {
            b'\r' if input.get(cursor + 1) == Some(&b'\n') => cursor += 2,
            b'\r' | b'\n' => return Err(RegistrationCodecError::InvalidLineEnding),
            _ => cursor += 1,
        }
    }
    Ok(())
}

fn next_crlf_or_end<'a>(input: &'a [u8], cursor: &mut usize) -> &'a [u8] {
    let start = *cursor;
    if let Some(relative) = find_bytes(&input[start..], b"\r\n") {
        let end = start + relative;
        *cursor = end + 2;
        &input[start..end]
    } else {
        *cursor = input.len();
        &input[start..]
    }
}

fn next_required_crlf<'a>(
    input: &'a [u8],
    cursor: &mut usize,
) -> Result<&'a [u8], RegistrationCodecError> {
    let start = *cursor;
    let relative =
        find_bytes(&input[start..], b"\r\n").ok_or(RegistrationCodecError::InvalidLineEnding)?;
    let end = start + relative;
    *cursor = end + 2;
    Ok(&input[start..end])
}

fn parse_http_status(line: &[u8]) -> Result<u16, RegistrationCodecError> {
    const PREFIX: &[u8] = b"HTTP/1.1 ";
    let remainder = line
        .strip_prefix(PREFIX)
        .ok_or(RegistrationCodecError::InvalidStatus)?;
    if remainder.len() < 3
        || !remainder[..3].iter().all(u8::is_ascii_digit)
        || (remainder.len() > 3 && remainder[3] != b' ')
        || remainder[3..].iter().any(|byte| byte.is_ascii_control())
    {
        return Err(RegistrationCodecError::InvalidStatus);
    }
    let status = remainder[..3]
        .iter()
        .fold(0_u16, |value, byte| value * 10 + u16::from(*byte - b'0'));
    if status < 100 {
        return Err(RegistrationCodecError::InvalidStatus);
    }
    Ok(status)
}

fn parse_name_value(line: &[u8]) -> Result<(&[u8], &[u8]), RegistrationCodecError> {
    let colon = line
        .iter()
        .position(|byte| *byte == b':')
        .ok_or(RegistrationCodecError::MissingHeaderColon)?;
    let name = &line[..colon];
    if name.is_empty()
        || name.len() > MAX_REGISTRATION_FIELD_NAME_SIZE
        || !name.iter().all(|byte| is_tchar(*byte))
    {
        return Err(RegistrationCodecError::InvalidFieldName);
    }
    if line.get(colon + 1) != Some(&b' ') || line.get(colon + 2) == Some(&b' ') {
        return Err(RegistrationCodecError::InvalidSeparator);
    }
    let value = line
        .get(colon + 2..)
        .ok_or(RegistrationCodecError::EmptyValue)?;
    if value.is_empty() {
        return Err(RegistrationCodecError::EmptyValue);
    }
    validate_general_value(value)?;
    Ok((name, value))
}

fn is_tchar(byte: u8) -> bool {
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

fn validate_general_value(value: &[u8]) -> Result<&str, RegistrationCodecError> {
    let text = std::str::from_utf8(value).map_err(|_| RegistrationCodecError::InvalidUtf8)?;
    if text.chars().any(char::is_control) {
        return Err(RegistrationCodecError::ControlCharacter);
    }
    Ok(text)
}

fn parse_content_length(value: &[u8]) -> Result<usize, RegistrationCodecError> {
    if value.is_empty()
        || !value.iter().all(u8::is_ascii_digit)
        || value == b"0"
        || (value.len() > 1 && value[0] == b'0')
    {
        return Err(RegistrationCodecError::InvalidContentLength);
    }
    let length = value.iter().try_fold(0_u64, |current, byte| {
        current
            .checked_mul(10)?
            .checked_add(u64::from(*byte - b'0'))
    });
    let length = length.ok_or(RegistrationCodecError::InvalidContentLength)?;
    usize::try_from(length).map_err(|_| RegistrationCodecError::InvalidContentLength)
}

fn reject_known_case_variant(name: &[u8]) -> Result<(), RegistrationCodecError> {
    const KNOWN: [&[u8]; 13] = [
        b"AP-Ssid",
        b"AP-Bssid",
        b"AP-Key",
        b"AP-Name",
        b"PS4-Nickname",
        b"PS5-Nickname",
        b"PS4-RegistKey",
        b"PS5-RegistKey",
        b"RP-KeyType",
        b"RP-Key",
        b"PS4-Mac",
        b"PS5-Mac",
        b"RP-SupportCmd",
    ];
    if KNOWN
        .iter()
        .any(|known| name != *known && name.eq_ignore_ascii_case(known))
    {
        Err(RegistrationCodecError::InvalidFieldNameCase)
    } else {
        Ok(())
    }
}

fn mark_once(seen: &mut bool) -> Result<(), RegistrationCodecError> {
    if *seen {
        Err(RegistrationCodecError::DuplicateField)
    } else {
        *seen = true;
        Ok(())
    }
}

fn ensure_ps4(profile: RegistrationProfile) -> Result<(), RegistrationCodecError> {
    if profile.is_ps5() {
        Err(RegistrationCodecError::WrongProfileField)
    } else {
        Ok(())
    }
}

fn ensure_ps5(profile: RegistrationProfile) -> Result<(), RegistrationCodecError> {
    if profile.is_ps5() {
        Ok(())
    } else {
        Err(RegistrationCodecError::WrongProfileField)
    }
}

fn validate_text(value: &str, maximum: usize) -> Result<(), RegistrationCodecError> {
    if value.is_empty() || value.len() > maximum {
        Err(RegistrationCodecError::InvalidFieldValue)
    } else {
        Ok(())
    }
}

fn validate_hex_range(
    value: &[u8],
    minimum_bytes: usize,
    maximum_bytes: usize,
) -> Result<(), RegistrationCodecError> {
    if value.len() % 2 != 0
        || value.len() < minimum_bytes * 2
        || value.len() > maximum_bytes * 2
        || !value.iter().all(u8::is_ascii_hexdigit)
    {
        Err(RegistrationCodecError::InvalidFieldValue)
    } else {
        Ok(())
    }
}

fn validate_hex_exact(value: &[u8], decoded_bytes: usize) -> Result<(), RegistrationCodecError> {
    validate_hex_range(value, decoded_bytes, decoded_bytes)
}

fn parse_canonical_u32(value: &[u8]) -> Result<u32, RegistrationCodecError> {
    if value == b"0" {
        return Ok(0);
    }
    let (digits, radix) = if let Some(hex) = value.strip_prefix(b"0x") {
        if hex.is_empty() || hex.len() > 8 || !hex.iter().all(u8::is_ascii_hexdigit) {
            return Err(RegistrationCodecError::InvalidFieldValue);
        }
        (hex, 16_u32)
    } else {
        if value.is_empty() || value[0] == b'0' || !value.iter().all(u8::is_ascii_digit) {
            return Err(RegistrationCodecError::InvalidFieldValue);
        }
        (value, 10_u32)
    };
    digits
        .iter()
        .try_fold(0_u32, |current, byte| {
            let digit = match byte {
                b'0'..=b'9' => u32::from(*byte - b'0'),
                b'a'..=b'f' => u32::from(*byte - b'a') + 10,
                b'A'..=b'F' => u32::from(*byte - b'A') + 10,
                _ => return None,
            };
            current.checked_mul(radix)?.checked_add(digit)
        })
        .ok_or(RegistrationCodecError::InvalidFieldValue)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[path = "../../../../../tests/support/registration_codec_oracle.rs"]
    mod codec_oracle;

    const ACCOUNT: [u8; 8] = [0, 1, 2, 3, 4, 5, 6, 7];
    const RP_KEY: &[u8] = b"00112233445566778899aabbccddeeff";
    const UPSTREAM_PRE10_AEROPAUSE: [u8; 16] = [
        0x0b, 0xe1, 0x2f, 0xbb, 0x4c, 0x7c, 0x99, 0x4a, 0x41, 0x1e, 0x2d, 0x4c, 0xa4, 0x19, 0xf4,
        0x35,
    ];
    const UPSTREAM_PRE10_ENCRYPTED_INNER: [u8; 54] = [
        0xf2, 0xfd, 0x4d, 0xdc, 0x86, 0x21, 0xda, 0x3d, 0x9f, 0x35, 0xf3, 0xdf, 0x82, 0x58, 0x65,
        0x08, 0x6f, 0xb5, 0xee, 0xd1, 0x1e, 0xdd, 0xdd, 0xaa, 0x5a, 0xce, 0x28, 0xdf, 0x2f, 0x13,
        0x9c, 0x0c, 0x66, 0x6f, 0xec, 0x1c, 0x5d, 0xd1, 0x9f, 0x08, 0x15, 0xdd, 0x5c, 0x61, 0x57,
        0xe6, 0xad, 0x69, 0x60, 0x12, 0x67, 0x2d, 0x4b, 0x64,
    ];

    fn error_of<T>(result: Result<T, RegistrationCodecError>) -> RegistrationCodecError {
        match result {
            Ok(_) => panic!("expected a classified registration error"),
            Err(error) => error,
        }
    }

    fn assert_codec_oracle(fixture: &str) {
        let oracle = codec_oracle::parse(fixture).expect("strict codec oracle JSON");
        assert_eq!(oracle.inputs.synthetic_number, 13_374_201);
        assert_eq!(oracle.inputs.seed_16, "1337deadbeefc0ffee42636869616b69");
        assert_eq!(oracle.inputs.online_id, "ChiakiNanami1337");
        assert_eq!(oracle.inputs.account_id, "0001020304050607");
        assert_eq!(oracle.cases.len(), 9);

        let expected = [
            (
                "ps4_8_online_default",
                "ps4_8",
                "online_id",
                800,
                None,
                None,
            ),
            (
                "ps4_9_online_compat_default",
                "ps4_9",
                "online_id",
                900,
                None,
                None,
            ),
            (
                "ps4_9_account_default",
                "ps4_9",
                "account_id",
                900,
                None,
                None,
            ),
            (
                "ps4_10_account_default",
                "ps4_10_target",
                "account_id",
                1_000,
                Some(1),
                Some(8),
            ),
            (
                "ps4_10_account_0_31",
                "ps4_10_target",
                "account_id",
                1_000,
                Some(0),
                Some(31),
            ),
            (
                "ps4_10_account_31_0",
                "ps4_10_target",
                "account_id",
                1_000,
                Some(31),
                Some(0),
            ),
            (
                "ps5_account_default",
                "ps5",
                "account_id",
                1_000_100,
                Some(1),
                Some(8),
            ),
            (
                "ps5_account_0_31",
                "ps5",
                "account_id",
                1_000_100,
                Some(0),
                Some(31),
            ),
            (
                "ps5_account_31_0",
                "ps5",
                "account_id",
                1_000_100,
                Some(31),
                Some(0),
            ),
        ];

        for (index, (case, expected)) in oracle.cases.iter().zip(expected).enumerate() {
            let (name, profile_name, identity, target, cipher_offset, aeropause_offset) = expected;
            assert_eq!(case.name, name);
            assert_eq!(case.profile, profile_name, "{name} profile");
            assert_eq!(case.identity, identity, "{name} identity");
            assert_eq!(case.target, target, "{name} target");
            assert_eq!(case.cipher_offset, cipher_offset, "{name} cipher offset");
            assert_eq!(
                case.aeropause_offset, aeropause_offset,
                "{name} aeropause offset"
            );
            assert_eq!(case.trailing_comma, index < 8, "{name} case delimiter");

            let profile = match case.profile {
                "ps4_8" => RegistrationProfile::Ps4_8,
                "ps4_9" => RegistrationProfile::Ps4_9,
                "ps4_10_target" => RegistrationProfile::Ps4_10Target,
                "ps5" => RegistrationProfile::Ps5,
                _ => panic!("unexpected oracle profile"),
            };
            let mut formatted_inner = [0_u8; 160];
            let formatted_len = match (profile, case.identity) {
                (RegistrationProfile::Ps4_9, "online_id") => format_ps4_9_online_id_compatibility(
                    OnlineId::new(oracle.inputs.online_id).unwrap(),
                    &mut formatted_inner,
                )
                .unwrap(),
                (_, "online_id") => format_registration_inner_request(
                    profile,
                    RegistrationIdentity::OnlineId(OnlineId::new(oracle.inputs.online_id).unwrap()),
                    &mut formatted_inner,
                )
                .unwrap(),
                (_, "account_id") => format_registration_inner_request(
                    profile,
                    RegistrationIdentity::AccountId(AccountId::new(&ACCOUNT)),
                    &mut formatted_inner,
                )
                .unwrap(),
                _ => panic!("unexpected oracle identity"),
            };
            let plaintext = codec_oracle::decode_hex(case.plaintext_inner).unwrap();
            assert_eq!(
                &formatted_inner[..formatted_len],
                plaintext,
                "{name} plaintext"
            );

            let filler: [u8; REGISTRATION_REQUEST_PREFIX_SIZE] =
                codec_oracle::decode_hex(case.filler)
                    .unwrap()
                    .try_into()
                    .unwrap();
            let encrypted = codec_oracle::decode_hex(case.encrypted_inner).unwrap();
            assert_eq!(encrypted.len(), plaintext.len(), "{name} encrypted length");
            let aeropause: [u8; AEROPAUSE_SIZE] = codec_oracle::decode_hex(case.aeropause)
                .unwrap()
                .try_into()
                .unwrap();
            let mut payload_buffer = [0_u8; MAX_REGISTRATION_BODY_SIZE];
            let prepared = prepare_registration_request_prefix(
                profile,
                &filler,
                plaintext.len(),
                &mut payload_buffer,
            )
            .unwrap();
            assert_eq!(
                prepared.offsets(),
                cipher_offset.map(|cipher_offset| RegistrationOffsets {
                    cipher_offset,
                    aeropause_offset: aeropause_offset.unwrap(),
                }),
                "{name} derived offsets"
            );
            let payload =
                finish_registration_request_payload(prepared, &aeropause, &encrypted).unwrap();
            assert_eq!(
                payload.as_bytes(),
                codec_oracle::decode_hex(case.payload).unwrap(),
                "{name} payload"
            );
            let mut outer = [0_u8; MAX_REGISTRATION_REQUEST_HEADER_SIZE];
            let outer_len = format_registration_http_request_header(&payload, &mut outer).unwrap();
            assert_eq!(
                &outer[..outer_len],
                codec_oracle::decode_hex(case.outer_request).unwrap(),
                "{name} outer request"
            );
        }
    }

    #[test]
    fn codec_oracle_binds_all_nine_complete_cases() {
        assert!(codec_oracle::has_crlf_only(codec_oracle::FIXTURE));
        assert_codec_oracle(codec_oracle::FIXTURE);
    }

    #[test]
    fn codec_oracle_mutations_are_detected() {
        let changed_input = codec_oracle::FIXTURE.replacen(
            "\"synthetic_number\":13374201",
            "\"synthetic_number\":13374202",
            1,
        );
        let changed_offset =
            codec_oracle::FIXTURE.replacen("\"cipher_offset\":1,", "\"cipher_offset\":2,", 1);
        let swapped_aeropause_halves = codec_oracle::FIXTURE.replacen(
            "75064b815c8db1e97999fe4637e35969",
            "7999fe4637e3596975064b815c8db1e9",
            1,
        );
        let extra_case = codec_oracle::duplicate_first_case();

        for mutated in [
            changed_input,
            changed_offset,
            swapped_aeropause_halves,
            extra_case,
        ] {
            assert!(std::panic::catch_unwind(|| assert_codec_oracle(&mutated)).is_err());
        }
    }

    #[test]
    fn profile_mapping_is_exhaustive_and_mismatches_fail() {
        let profiles = [
            RegistrationProfile::Ps4_8,
            RegistrationProfile::Ps4_9,
            RegistrationProfile::Ps4_10Target,
            RegistrationProfile::Ps5,
        ];
        let targets = [
            RegistrationCryptoTarget::Ps4Pre10,
            RegistrationCryptoTarget::Ps4Modern,
            RegistrationCryptoTarget::Ps5,
        ];
        for profile in profiles {
            for target in targets {
                assert_eq!(
                    validate_registration_crypto_target(profile, target).is_ok(),
                    profile.crypto_target() == target
                );
            }
        }
    }

    #[test]
    fn identity_profiles_and_exact_inner_headers_are_bound() {
        let mut output = [0_u8; 160];
        let online = OnlineId::new("ChiakiNanami1337").unwrap();
        let length = format_registration_inner_request(
            RegistrationProfile::Ps4_8,
            RegistrationIdentity::OnlineId(online),
            &mut output,
        )
        .unwrap();
        assert_eq!(
            &output[..length],
            b"Client-Type: Windows\r\nNp-Online-Id: ChiakiNanami1337\r\n"
        );

        let account = AccountId::new(&ACCOUNT);
        let length = format_registration_inner_request(
            RegistrationProfile::Ps4_9,
            RegistrationIdentity::AccountId(account),
            &mut output,
        )
        .unwrap();
        assert_eq!(
            &output[..length],
            b"Client-Type: Windows\r\nNp-AccountId: AAECAwQFBgc=\r\n"
        );

        let account = AccountId::new(&ACCOUNT);
        let length = format_registration_inner_request(
            RegistrationProfile::Ps5,
            RegistrationIdentity::AccountId(account),
            &mut output,
        )
        .unwrap();
        assert_eq!(
            &output[..length],
            b"Client-Type: dabfa2ec873de5839bee8d3f4c0239c4282c07c25c6077a2931afcf0adc0d34f\r\nNp-AccountId: AAECAwQFBgc=\r\n"
        );

        let online = OnlineId::new("Player_1").unwrap();
        assert_eq!(
            format_registration_inner_request(
                RegistrationProfile::Ps4_9,
                RegistrationIdentity::OnlineId(online),
                &mut output,
            ),
            Err(RegistrationCodecError::IdentityProfileMismatch)
        );
        let account = AccountId::new(&ACCOUNT);
        assert_eq!(
            format_registration_inner_request(
                RegistrationProfile::Ps4_8,
                RegistrationIdentity::AccountId(account),
                &mut output,
            ),
            Err(RegistrationCodecError::IdentityProfileMismatch)
        );
        let online = OnlineId::new("Player_1").unwrap();
        assert_eq!(
            format_registration_inner_request(
                RegistrationProfile::Ps5,
                RegistrationIdentity::OnlineId(online),
                &mut output,
            ),
            Err(RegistrationCodecError::IdentityProfileMismatch)
        );
    }

    #[test]
    fn test_only_ps4_9_online_id_uses_the_shared_formatter() {
        let mut output = [0_u8; 96];
        let length = format_ps4_9_online_id_compatibility(
            OnlineId::new("ChiakiNanami1337").unwrap(),
            &mut output,
        )
        .unwrap();
        assert_eq!(
            &output[..length],
            b"Client-Type: Windows\r\nNp-Online-Id: ChiakiNanami1337\r\n"
        );
    }

    #[test]
    fn upstream_ps4_9_pre10_payload_is_reproduced_byte_for_byte() {
        let mut plaintext = [0_u8; 96];
        let plaintext_len = format_ps4_9_online_id_compatibility(
            OnlineId::new("ChiakiNanami1337").unwrap(),
            &mut plaintext,
        )
        .unwrap();
        assert_eq!(plaintext_len, UPSTREAM_PRE10_ENCRYPTED_INNER.len());

        let filler = [0x41_u8; REGISTRATION_REQUEST_PREFIX_SIZE];
        let mut output = [0_u8; 534];
        let prepared = prepare_registration_request_prefix(
            RegistrationProfile::Ps4_9,
            &filler,
            plaintext_len,
            &mut output,
        )
        .unwrap();
        let payload = finish_registration_request_payload(
            prepared,
            &UPSTREAM_PRE10_AEROPAUSE,
            &UPSTREAM_PRE10_ENCRYPTED_INNER,
        )
        .unwrap();

        let mut expected = [0x41_u8; 534];
        expected[0x11c..0x12c].copy_from_slice(&UPSTREAM_PRE10_AEROPAUSE);
        expected[0x1e0..].copy_from_slice(&UPSTREAM_PRE10_ENCRYPTED_INNER);
        assert_eq!(payload.as_bytes(), expected);
    }

    #[test]
    fn rfc4648_base64_vectors_are_reproduced() {
        let cases: [(&[u8], &[u8]); 7] = [
            (b"", b""),
            (b"f", b"Zg=="),
            (b"fo", b"Zm8="),
            (b"foo", b"Zm9v"),
            (b"foob", b"Zm9vYg=="),
            (b"fooba", b"Zm9vYmE="),
            (b"foobar", b"Zm9vYmFy"),
        ];
        for (input, expected) in cases {
            let mut output = [0_u8; 16];
            let length = encode_base64(input, &mut output).unwrap();
            assert_eq!(&output[..length], expected);
        }
    }

    #[test]
    fn modern_offsets_and_aeropause_halves_are_bound() {
        let aeropause = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];
        let verify = |profile, filler: &[u8; REGISTRATION_REQUEST_PREFIX_SIZE], expected| {
            let mut output = [0_u8; 0x1e3];
            let prepared =
                prepare_registration_request_prefix(profile, filler, 3, &mut output).unwrap();
            assert_eq!(prepared.offsets(), Some(expected));
            let payload =
                finish_registration_request_payload(prepared, &aeropause, b"xyz").unwrap();
            assert_eq!(&payload.as_bytes()[0xc7..0xcf], &aeropause[8..16]);
            assert_eq!(&payload.as_bytes()[0x191..0x199], &aeropause[0..8]);
            assert_eq!(&payload.as_bytes()[0x1e0..], b"xyz");
        };

        for profile in [RegistrationProfile::Ps4_10Target, RegistrationProfile::Ps5] {
            let mut default_filler = [0x41_u8; REGISTRATION_REQUEST_PREFIX_SIZE];
            default_filler[0x18c] = 0x12;
            verify(
                profile,
                &default_filler,
                RegistrationOffsets {
                    cipher_offset: 1,
                    aeropause_offset: 8,
                },
            );

            let mut zero_thirty_one = default_filler;
            zero_thirty_one[0] = 0xf8;
            zero_thirty_one[0x18d] = 0;
            verify(
                profile,
                &zero_thirty_one,
                RegistrationOffsets {
                    cipher_offset: 0,
                    aeropause_offset: 31,
                },
            );

            let mut thirty_one_zero = default_filler;
            thirty_one_zero[0] = 0;
            thirty_one_zero[0x18d] = 31;
            verify(
                profile,
                &thirty_one_zero,
                RegistrationOffsets {
                    cipher_offset: 31,
                    aeropause_offset: 0,
                },
            );
        }
    }

    #[test]
    fn encrypted_length_mismatch_fails_before_finalization() {
        let filler = [0x41_u8; REGISTRATION_REQUEST_PREFIX_SIZE];
        let mut output = [0_u8; REGISTRATION_REQUEST_PREFIX_SIZE + 4];
        let prepared = prepare_registration_request_prefix(
            RegistrationProfile::Ps4_8,
            &filler,
            4,
            &mut output,
        )
        .unwrap();
        assert_eq!(
            error_of(finish_registration_request_payload(
                prepared,
                &[0_u8; 16],
                b"bad"
            )),
            RegistrationCodecError::EncryptedLengthMismatch
        );
    }

    #[test]
    fn outer_request_headers_are_exact_for_every_profile() {
        let bytes = [0_u8; 534];
        let cases: [(RegistrationProfile, &[u8]); 4] = [
            (
                RegistrationProfile::Ps4_8,
                b"POST /sce/rp/regist HTTP/1.1\r\n HTTP/1.1\r\nHOST: 10.0.2.15\r\nUser-Agent: remoteplay Windows\r\nConnection: close\r\nContent-Length: 534\r\n\r\n",
            ),
            (
                RegistrationProfile::Ps4_9,
                b"POST /sce/rp/regist HTTP/1.1\r\n HTTP/1.1\r\nHOST: 10.0.2.15\r\nUser-Agent: remoteplay Windows\r\nConnection: close\r\nContent-Length: 534\r\nRP-Version: 9.0\r\n\r\n",
            ),
            (
                RegistrationProfile::Ps4_10Target,
                b"POST /sie/ps4/rp/sess/rgst HTTP/1.1\r\n HTTP/1.1\r\nHOST: 10.0.2.15\r\nUser-Agent: remoteplay Windows\r\nConnection: close\r\nContent-Length: 534\r\nRP-Version: 10.0\r\n\r\n",
            ),
            (
                RegistrationProfile::Ps5,
                b"POST /sie/ps5/rp/sess/rgst HTTP/1.1\r\n HTTP/1.1\r\nHOST: 10.0.2.15\r\nUser-Agent: remoteplay Windows\r\nConnection: close\r\nContent-Length: 534\r\nRP-Version: 1.0\r\n\r\n",
            ),
        ];
        for (profile, expected) in cases {
            let payload = RegistrationPayload {
                profile,
                bytes: &bytes,
            };
            let mut output = [0_u8; MAX_REGISTRATION_REQUEST_HEADER_SIZE];
            let length = format_registration_http_request_header(&payload, &mut output).unwrap();
            assert_eq!(&output[..length], expected);
        }
    }

    #[test]
    fn registration_http_framing_is_strict_and_borrowed() {
        let parsed =
            parse_registration_http_response(b"HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nabcd")
                .unwrap();
        assert_eq!(parsed.body(), b"abcd");

        assert_eq!(
            error_of(parse_registration_http_response(
                b"HTTP/1.1 200 OK\nContent-Length: 4\n\nabcd"
            )),
            RegistrationCodecError::MissingHeaderTerminator
        );
        assert_eq!(
            error_of(parse_registration_http_response(
                b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 4\r\n\r\nabcd"
            )),
            RegistrationCodecError::TransferEncodingUnsupported
        );
        assert_eq!(
            error_of(parse_registration_http_response(
                b"HTTP/1.1 404 Nope\r\n\r\n"
            )),
            RegistrationCodecError::HttpStatus
        );
    }

    #[test]
    fn content_length_grammar_and_exact_body_are_enforced() {
        for (value, expected) in [
            (&b"+5"[..], RegistrationCodecError::InvalidContentLength),
            (&b"05"[..], RegistrationCodecError::InvalidContentLength),
            (&b"0"[..], RegistrationCodecError::InvalidContentLength),
            (&b" 5"[..], RegistrationCodecError::InvalidSeparator),
            (
                &b"18446744073709551616"[..],
                RegistrationCodecError::InvalidContentLength,
            ),
        ] {
            let mut input = [0_u8; 96];
            let mut cursor = 0;
            write_bytes(
                &mut input,
                &mut cursor,
                b"HTTP/1.1 200 OK\r\nContent-Length: ",
            );
            write_bytes(&mut input, &mut cursor, value);
            write_bytes(&mut input, &mut cursor, b"\r\n\r\nabcde");
            assert_eq!(
                error_of(parse_registration_http_response(&input[..cursor])),
                expected
            );
        }
        assert_eq!(
            error_of(parse_registration_http_response(
                b"HTTP/1.1 200 OK\r\nContent-Length: 5\r\nContent-Length: 5\r\n\r\nabcde"
            )),
            RegistrationCodecError::DuplicateContentLength
        );
        assert_eq!(
            error_of(parse_registration_http_response(
                b"HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nabcd"
            )),
            RegistrationCodecError::BodyLengthMismatch
        );
    }

    #[test]
    fn decrypted_fields_are_strict_borrowed_and_complete() {
        let input = b"AP-Key: wifi-secret\r\nPS4-Nickname: Room\r\nPS4-RegistKey: aa\r\nRP-KeyType: 0x1A\r\nRP-Key: 00112233445566778899aabbccddeeff\r\nPS4-Mac: 001122334455\r\nRP-SupportCmd: 7\r\n";
        let parsed = parse_registration_response_fields(RegistrationProfile::Ps4_9, input).unwrap();
        assert_eq!(parsed.nickname(), Some("Room"));
        assert_eq!(parsed.registration_key_hex(), b"aa");
        assert_eq!(parsed.rp_key_type(), Some(0x1a));
        assert_eq!(parsed.rp_key_hex(), RP_KEY);
        assert_eq!(parsed.mac_hex(), b"001122334455");
        assert_eq!(parsed.support_command(), Some(7));
    }

    #[test]
    fn decrypted_field_line_grammar_is_exact() {
        let suffix = b"\r\nPS4-RegistKey: aa\r\nRP-Key: 00112233445566778899aabbccddeeff\r\nPS4-Mac: 001122334455\r\n";
        for (prefix, expected) in [
            (&b"X: a"[..], None),
            (b"X:a", Some(RegistrationCodecError::InvalidSeparator)),
            (b"X:  a", Some(RegistrationCodecError::InvalidSeparator)),
            (b"X: ", Some(RegistrationCodecError::EmptyValue)),
            (b": a", Some(RegistrationCodecError::InvalidFieldName)),
            (
                b"bad name: a",
                Some(RegistrationCodecError::InvalidFieldName),
            ),
        ] {
            let mut input = [0_u8; 192];
            let mut cursor = 0;
            write_bytes(&mut input, &mut cursor, prefix);
            write_bytes(&mut input, &mut cursor, suffix);
            let result =
                parse_registration_response_fields(RegistrationProfile::Ps4_9, &input[..cursor]);
            match expected {
                None => assert!(result.is_ok()),
                Some(error) => assert_eq!(error_of(result), error),
            }
        }
    }

    #[test]
    fn known_duplicates_case_variants_and_wrong_profiles_fail() {
        let duplicate = b"AP-Name: one\r\nAP-Name: two\r\nPS4-RegistKey: aa\r\nRP-Key: 00112233445566778899aabbccddeeff\r\nPS4-Mac: 001122334455\r\n";
        assert_eq!(
            error_of(parse_registration_response_fields(
                RegistrationProfile::Ps4_9,
                duplicate
            )),
            RegistrationCodecError::DuplicateField
        );
        let wrong_case = b"ps4-registkey: aa\r\nRP-Key: 00112233445566778899aabbccddeeff\r\nPS4-Mac: 001122334455\r\n";
        assert_eq!(
            error_of(parse_registration_response_fields(
                RegistrationProfile::Ps4_9,
                wrong_case
            )),
            RegistrationCodecError::InvalidFieldNameCase
        );
        let wrong_profile = b"PS5-RegistKey: aa\r\nRP-Key: 00112233445566778899aabbccddeeff\r\nPS5-Mac: 001122334455\r\n";
        assert_eq!(
            error_of(parse_registration_response_fields(
                RegistrationProfile::Ps4_9,
                wrong_profile
            )),
            RegistrationCodecError::WrongProfileField
        );
    }

    #[test]
    fn registration_key_boundaries_are_exact() {
        for (value, expected) in [
            (&b""[..], RegistrationCodecError::EmptyValue),
            (&b"aa"[..], RegistrationCodecError::MissingRpKey),
            (
                &b"00000000000000000000000000000000"[..],
                RegistrationCodecError::MissingRpKey,
            ),
            (
                &b"0000000000000000000000000000000000"[..],
                RegistrationCodecError::InvalidFieldValue,
            ),
        ] {
            let mut input = [0_u8; 160];
            let mut cursor = 0;
            write_bytes(&mut input, &mut cursor, b"PS4-RegistKey: ");
            write_bytes(&mut input, &mut cursor, value);
            write_bytes(&mut input, &mut cursor, b"\r\n");
            assert_eq!(
                error_of(parse_registration_response_fields(
                    RegistrationProfile::Ps4_9,
                    &input[..cursor]
                )),
                expected
            );
        }
    }

    #[test]
    fn field_count_and_line_length_are_bounded() {
        let mut input = [0_u8; MAX_REGISTRATION_BODY_SIZE];
        let mut cursor = 0;
        for _ in 0..125 {
            write_bytes(&mut input, &mut cursor, b"X: a\r\n");
        }
        write_bytes(&mut input, &mut cursor, b"PS4-RegistKey: aa\r\n");
        write_bytes(
            &mut input,
            &mut cursor,
            b"RP-Key: 00112233445566778899aabbccddeeff\r\n",
        );
        write_bytes(&mut input, &mut cursor, b"PS4-Mac: 001122334455\r\n");
        assert!(
            parse_registration_response_fields(RegistrationProfile::Ps4_9, &input[..cursor])
                .is_ok()
        );
        write_bytes(&mut input, &mut cursor, b"Y: b\r\n");
        assert_eq!(
            error_of(parse_registration_response_fields(
                RegistrationProfile::Ps4_9,
                &input[..cursor]
            )),
            RegistrationCodecError::TooManyFields
        );

        let mut long = [b'a'; MAX_REGISTRATION_FIELD_LINE_SIZE + 1];
        long[0] = b'X';
        long[1] = b':';
        long[2] = b' ';
        long[MAX_REGISTRATION_FIELD_LINE_SIZE - 1] = b'\r';
        long[MAX_REGISTRATION_FIELD_LINE_SIZE] = b'\n';
        assert_eq!(
            error_of(parse_registration_response_fields(
                RegistrationProfile::Ps4_9,
                &long
            )),
            RegistrationCodecError::LineTooLong
        );
    }

    #[test]
    fn arbitrary_inputs_are_deterministic_and_never_panic() {
        let mut input = [0_u8; MAX_REGISTRATION_RESPONSE_SIZE + 1];
        let mut state = 0x91e1_0da5_u32;
        for length in 0..=input.len() {
            if length > 0 {
                state = state.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
                input[length - 1] = (state >> 24) as u8;
            }
            let first = parse_registration_http_response(&input[..length]).err();
            let second = parse_registration_http_response(&input[..length]).err();
            assert_eq!(first, second);
            let first = parse_registration_response_fields(
                RegistrationProfile::Ps4_9,
                &input[..length.min(MAX_REGISTRATION_BODY_SIZE)],
            )
            .err();
            let second = parse_registration_response_fields(
                RegistrationProfile::Ps4_9,
                &input[..length.min(MAX_REGISTRATION_BODY_SIZE)],
            )
            .err();
            assert_eq!(first, second);
        }
    }

    #[test]
    fn public_module_contains_no_growable_allocation_symbols() {
        let source = include_str!("registration.rs");
        for forbidden in [
            concat!("V", "ec"),
            concat!("S", "tring"),
            concat!("To", "Owned"),
            concat!("to_", "owned"),
            concat!("to_", "string"),
            concat!("v", "ec!"),
            concat!("B", "ox"),
            concat!("for", "mat!"),
            concat!("col", "lect"),
            concat!("C", "ow"),
            concat!("Hash", "Map"),
            concat!("BTree", "Map"),
        ] {
            assert!(!source.contains(forbidden));
        }
    }
}
