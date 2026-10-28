// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

use std::fmt::{self, Write};

use aes::Aes128;
use aes::cipher::{AsyncStreamCipher, KeyIvInit, generic_array::GenericArray};
use cfb_mode::Encryptor;
use hmac::{Hmac, Mac};
use sha2::Sha256;
use zeroize::{Zeroize, Zeroizing};

use crate::registration::{ECHO_B, HMAC_KEY_PS4_MODERN, HMAC_KEY_PS4_PRE10, HMAC_KEY_PS5};
use crate::rpcrypt_tables::{KEYS_A_PS4, KEYS_A_PS5, KEYS_B_PS4, KEYS_B_PS5};

const MATERIAL_SIZE: usize = 16;
const AUTH_TABLE_ROW_SIZE: usize = 0x70;
const HANDSHAKE_KEY_BASE64_SIZE: usize = 24;
const ECHO_A: [u8; MATERIAL_SIZE] = [
    0x01, 0x49, 0x87, 0x9b, 0x65, 0x39, 0x8b, 0x39, 0x4b, 0x3a, 0x8d, 0x48, 0xc3, 0x0a, 0xef, 0x51,
];

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

pub const MAX_LAUNCH_SPEC_PLAINTEXT_SIZE: usize = 1_024;
pub const MAX_ENCRYPTED_LAUNCH_SPEC_SIZE: usize = 1_368;

/// Console family selecting both session-rpcrypt derivation and LaunchSpec shape.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SessionTarget {
    Ps4Pre10,
    Ps4Modern,
    Ps5,
}

/// Codec requested by the typed LaunchSpec boundary.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SessionCodec {
    Avc,
    Hevc,
    HevcHdr,
}

/// Public nonce decoded from the authenticated session response.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SessionNonce([u8; MATERIAL_SIZE]);

impl SessionNonce {
    #[must_use]
    pub const fn from_session_response_bytes(bytes: [u8; MATERIAL_SIZE]) -> Self {
        Self(bytes)
    }
}

/// Registration `rp_key` transferred into the session boundary and erased at source.
///
/// ```compile_fail
/// use gpsx_crypto::SessionMorning;
/// fn require_clone<T: Clone>() {}
/// require_clone::<SessionMorning>();
/// ```
pub struct SessionMorning([u8; MATERIAL_SIZE]);

impl SessionMorning {
    pub fn take_from_registration_buffer(source: &mut Zeroizing<[u8; MATERIAL_SIZE]>) -> Self {
        let mut value = Self([0; MATERIAL_SIZE]);
        value.0.copy_from_slice(source.as_ref());
        source.zeroize();
        value
    }
}

impl Drop for SessionMorning {
    fn drop(&mut self) {
        self.0.zeroize();
    }
}

redacted_format!(SessionMorning, "SessionMorning");

/// Session authentication cipher owner and its single-use LaunchSpec counter slot.
///
/// ```compile_fail
/// use gpsx_crypto::SessionRpcrypt;
/// fn require_clone<T: Clone>() {}
/// require_clone::<SessionRpcrypt>();
/// ```
pub struct SessionRpcrypt {
    target: SessionTarget,
    bright: [u8; MATERIAL_SIZE],
    ambassador: [u8; MATERIAL_SIZE],
    launch_spec_counter_zero_consumed: bool,
}

impl SessionRpcrypt {
    #[must_use]
    pub fn new(target: SessionTarget, nonce: SessionNonce, morning: SessionMorning) -> Self {
        let mut value = Self {
            target,
            bright: [0; MATERIAL_SIZE],
            ambassador: [0; MATERIAL_SIZE],
            launch_spec_counter_zero_consumed: false,
        };
        match target {
            SessionTarget::Ps4Pre10 => value.derive_ps4_pre10(&nonce.0, &morning.0),
            SessionTarget::Ps4Modern => {
                value.derive_modern(&nonce.0, &morning.0, &KEYS_A_PS4, &KEYS_B_PS4)
            }
            SessionTarget::Ps5 => {
                value.derive_modern(&nonce.0, &morning.0, &KEYS_A_PS5, &KEYS_B_PS5)
            }
        }
        value
    }

    fn derive_ps4_pre10(&mut self, nonce: &[u8; MATERIAL_SIZE], morning: &[u8; MATERIAL_SIZE]) {
        for index in 0..MATERIAL_SIZE {
            self.ambassador[index] =
                nonce[index].wrapping_sub(index as u8).wrapping_sub(0x27) ^ ECHO_A[index];
            self.bright[index] = morning[index].wrapping_sub(index as u8).wrapping_add(0x34)
                ^ ECHO_B[index]
                ^ nonce[index];
        }
    }

    fn derive_modern(
        &mut self,
        nonce: &[u8; MATERIAL_SIZE],
        morning: &[u8; MATERIAL_SIZE],
        keys_a: &[u8],
        keys_b: &[u8],
    ) {
        let key_a_start = usize::from(nonce[0] >> 3) * AUTH_TABLE_ROW_SIZE;
        let key_b_start = usize::from(nonce[7] >> 3) * AUTH_TABLE_ROW_SIZE;
        let key_a = &keys_a[key_a_start..key_a_start + MATERIAL_SIZE];
        let key_b = &keys_b[key_b_start..key_b_start + MATERIAL_SIZE];
        for index in 0..MATERIAL_SIZE {
            match self.target {
                SessionTarget::Ps5 => {
                    self.ambassador[index] =
                        nonce[index].wrapping_sub(0x2d).wrapping_sub(index as u8) ^ key_a[index];
                    self.bright[index] =
                        morning[index].wrapping_add(0x18).wrapping_add(index as u8)
                            ^ nonce[index]
                            ^ key_b[index];
                }
                SessionTarget::Ps4Modern => {
                    self.ambassador[index] =
                        nonce[index].wrapping_add(0x36).wrapping_add(index as u8) ^ key_a[index];
                    self.bright[index] = (key_b[index] ^ morning[index])
                        .wrapping_add(0x21)
                        .wrapping_add(index as u8)
                        ^ nonce[index];
                }
                SessionTarget::Ps4Pre10 => unreachable!("closed modern target dispatch"),
            }
        }
    }

    fn counter_zero_iv(&self) -> Result<Zeroizing<[u8; MATERIAL_SIZE]>, LaunchSpecError> {
        let key = match self.target {
            SessionTarget::Ps4Pre10 => &HMAC_KEY_PS4_PRE10,
            SessionTarget::Ps4Modern => &HMAC_KEY_PS4_MODERN,
            SessionTarget::Ps5 => &HMAC_KEY_PS5,
        };
        let mut input = Zeroizing::new([0_u8; MATERIAL_SIZE + 8]);
        input[..MATERIAL_SIZE].copy_from_slice(&self.ambassador);
        let mut mac = Hmac::<Sha256>::new_from_slice(key)
            .map_err(|_| LaunchSpecError::CryptoInitialization)?;
        mac.update(input.as_ref());
        let mut digest = mac.finalize().into_bytes();
        let mut iv = Zeroizing::new([0_u8; MATERIAL_SIZE]);
        iv.copy_from_slice(&digest[..MATERIAL_SIZE]);
        digest.zeroize();
        Ok(iv)
    }
}

impl Drop for SessionRpcrypt {
    fn drop(&mut self) {
        self.bright.zeroize();
        self.ambassador.zeroize();
        self.launch_spec_counter_zero_consumed = true;
    }
}

redacted_format!(SessionRpcrypt, "SessionRpcrypt");

/// Public, typed numeric inputs for the canonical LaunchSpec.
///
/// Values outside the protocol's unsigned 32-bit wire domain do not compile:
///
/// ```compile_fail
/// use gpsx_crypto::{LaunchSpec, SessionCodec};
/// let _ = LaunchSpec::new(4_294_967_296, 1, 1, 1, 1, 1, SessionCodec::Avc);
/// ```
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct LaunchSpec {
    pub width: u32,
    pub height: u32,
    pub max_fps: u32,
    pub bitrate_kbps: u32,
    pub mtu: u32,
    pub rtt_ms: u32,
    pub codec: SessionCodec,
}

impl LaunchSpec {
    #[must_use]
    pub const fn new(
        width: u32,
        height: u32,
        max_fps: u32,
        bitrate_kbps: u32,
        mtu: u32,
        rtt_ms: u32,
        codec: SessionCodec,
    ) -> Self {
        Self {
            width,
            height,
            max_fps,
            bitrate_kbps,
            mtu,
            rtt_ms,
            codec,
        }
    }
}

/// Length of the encrypted, outer-base64 LaunchSpec written to caller storage.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct EncryptedLaunchSpecLength(usize);

impl EncryptedLaunchSpecLength {
    #[must_use]
    pub const fn get(self) -> usize {
        self.0
    }
}

/// Static, secret-free LaunchSpec failures.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LaunchSpecError {
    AlreadyWritten,
    OutputTooSmall,
    UnsupportedCodec,
    ArithmeticOverflow,
    FormattingFailed,
    CryptoInitialization,
}

impl fmt::Display for LaunchSpecError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::AlreadyWritten => "LaunchSpec counter-zero slot was already consumed",
            Self::OutputTooSmall => "LaunchSpec output buffer is too small",
            Self::UnsupportedCodec => "LaunchSpec target and codec combination is unsupported",
            Self::ArithmeticOverflow => "LaunchSpec length arithmetic overflowed",
            Self::FormattingFailed => "LaunchSpec canonical formatting failed",
            Self::CryptoInitialization => "LaunchSpec cryptographic primitive could not initialize",
        })
    }
}

impl std::error::Error for LaunchSpecError {}

pub(crate) fn write_encrypted_launch_spec(
    handshake_key: &[u8; MATERIAL_SIZE],
    rpcrypt: &mut SessionRpcrypt,
    spec: LaunchSpec,
    output: &mut [u8],
) -> Result<EncryptedLaunchSpecLength, LaunchSpecError> {
    if rpcrypt.launch_spec_counter_zero_consumed {
        return Err(LaunchSpecError::AlreadyWritten);
    }
    validate_codec(rpcrypt.target, spec.codec)?;

    let mut preflight = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
    let preflight_len =
        format_plaintext(&[0; MATERIAL_SIZE], rpcrypt.target, spec, &mut preflight)?;
    let required = base64_encoded_len(preflight_len)?;
    if output.len() < required {
        return Err(LaunchSpecError::OutputTooSmall);
    }

    let mut plaintext = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
    let plaintext_len = format_plaintext(handshake_key, rpcrypt.target, spec, &mut plaintext)?;
    debug_assert_eq!(plaintext_len, preflight_len);
    encrypt_with_counter_zero_keystream(rpcrypt, &mut plaintext[..plaintext_len])?;

    let mut encoded = Zeroizing::new([0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE]);
    let encoded_len = encode_base64(&plaintext[..plaintext_len], encoded.as_mut())?;
    output[..encoded_len].copy_from_slice(&encoded[..encoded_len]);
    rpcrypt.launch_spec_counter_zero_consumed = true;
    Ok(EncryptedLaunchSpecLength(encoded_len))
}

fn validate_codec(target: SessionTarget, codec: SessionCodec) -> Result<(), LaunchSpecError> {
    if target != SessionTarget::Ps5 && codec != SessionCodec::Avc {
        Err(LaunchSpecError::UnsupportedCodec)
    } else {
        Ok(())
    }
}

fn encrypt_with_counter_zero_keystream(
    rpcrypt: &SessionRpcrypt,
    plaintext: &mut [u8],
) -> Result<(), LaunchSpecError> {
    let iv = rpcrypt.counter_zero_iv()?;
    let mut keystream = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
    Encryptor::<Aes128>::new(
        GenericArray::from_slice(&rpcrypt.bright),
        GenericArray::from_slice(iv.as_ref()),
    )
    .encrypt(&mut keystream[..plaintext.len()]);
    for (byte, key_byte) in plaintext.iter_mut().zip(keystream.iter()) {
        *byte ^= *key_byte;
    }
    Ok(())
}

fn format_plaintext(
    handshake_key: &[u8; MATERIAL_SIZE],
    target: SessionTarget,
    spec: LaunchSpec,
    output: &mut [u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE],
) -> Result<usize, LaunchSpecError> {
    let mut handshake_key_b64 = Zeroizing::new([0_u8; HANDSHAKE_KEY_BASE64_SIZE]);
    let encoded_key_len = encode_base64(handshake_key, handshake_key_b64.as_mut())?;
    if encoded_key_len != HANDSHAKE_KEY_BASE64_SIZE {
        return Err(LaunchSpecError::FormattingFailed);
    }
    let handshake_key_b64 = std::str::from_utf8(handshake_key_b64.as_ref())
        .map_err(|_| LaunchSpecError::FormattingFailed)?;

    let mut writer = FixedWriter::new(output);
    writer.write_str(
        "{\"sessionId\":\"sessionId4321\",\"streamResolutions\":[{\"resolution\":{\"width\":",
    )?;
    write!(&mut writer, "{}", spec.width)?;
    writer.write_str(",\"height\":")?;
    write!(&mut writer, "{}", spec.height)?;
    writer.write_str("},\"maxFps\":")?;
    write!(&mut writer, "{}", spec.max_fps)?;
    writer.write_str(",\"score\":10}],\"network\":{\"bwKbpsSent\":")?;
    write!(&mut writer, "{}", spec.bitrate_kbps)?;
    writer.write_str(",\"bwLoss\":0.001000,\"mtu\":")?;
    write!(&mut writer, "{}", spec.mtu)?;
    writer.write_str(",\"rtt\":")?;
    write!(&mut writer, "{}", spec.rtt_ms)?;
    writer.write_str(",\"ports\":[53,2053]},\"slotId\":1,\"appSpecification\":{\"minFps\":30,\"minBandwidth\":0,\"extTitleId\":\"ps3\",\"version\":1,\"timeLimit\":1,\"startTimeout\":100,\"afkTimeout\":100,\"afkTimeoutDisconnect\":100},\"konan\":{\"ps3AccessToken\":\"accessToken\",\"ps3RefreshToken\":\"refreshToken\"},\"requestGameSpecification\":{\"model\":\"bravia_tv\",\"platform\":\"android\",\"audioChannels\":\"5.1\",\"language\":\"sp\",\"acceptButton\":\"X\",\"connectedControllers\":[\"xinput\",\"ds3\",\"ds4\"],\"yuvCoefficient\":\"bt601\",\"videoEncoderProfile\":\"hw4.1\",\"audioEncoderProfile\":\"audio1\"")?;
    if target == SessionTarget::Ps5 {
        writer.write_str(",\"adaptiveStreamMode\": \"resize\"")?;
    }
    writer.write_str("},\"userProfile\":{\"onlineId\":\"psnId\",\"npId\":\"npId\",\"region\":\"US\",\"languagesUsed\":[\"en\",\"jp\"]},")?;
    if target == SessionTarget::Ps5 {
        writer.write_str(match spec.codec {
            SessionCodec::Avc => "\"videoCodec\":\"avc\",",
            SessionCodec::Hevc | SessionCodec::HevcHdr => "\"videoCodec\":\"hevc\",",
        })?;
        writer.write_str(match spec.codec {
            SessionCodec::HevcHdr => "\"dynamicRange\":\"HDR\",",
            SessionCodec::Avc | SessionCodec::Hevc => "\"dynamicRange\":\"SDR\",",
        })?;
    }
    writer.write_str("\"handshakeKey\":\"")?;
    writer.write_str(handshake_key_b64)?;
    writer.write_str("\"}\0")?;
    Ok(writer.len())
}

struct FixedWriter<'a> {
    output: &'a mut [u8],
    cursor: usize,
}

impl<'a> FixedWriter<'a> {
    fn new(output: &'a mut [u8]) -> Self {
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

impl From<fmt::Error> for LaunchSpecError {
    fn from(_: fmt::Error) -> Self {
        Self::FormattingFailed
    }
}

fn base64_encoded_len(input_len: usize) -> Result<usize, LaunchSpecError> {
    input_len
        .checked_add(2)
        .and_then(|value| value.checked_div(3))
        .and_then(|value| value.checked_mul(4))
        .ok_or(LaunchSpecError::ArithmeticOverflow)
}

fn encode_base64(input: &[u8], output: &mut [u8]) -> Result<usize, LaunchSpecError> {
    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let required = base64_encoded_len(input.len())?;
    if output.len() < required {
        return Err(LaunchSpecError::OutputTooSmall);
    }
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
    match input.len() - input_cursor {
        1 => {
            let value = u32::from(input[input_cursor]) << 16;
            output[output_cursor] = ALPHABET[((value >> 18) & 0x3f) as usize];
            output[output_cursor + 1] = ALPHABET[((value >> 12) & 0x3f) as usize];
            output[output_cursor + 2] = b'=';
            output[output_cursor + 3] = b'=';
        }
        2 => {
            let value =
                (u32::from(input[input_cursor]) << 16) | (u32::from(input[input_cursor + 1]) << 8);
            output[output_cursor] = ALPHABET[((value >> 18) & 0x3f) as usize];
            output[output_cursor + 1] = ALPHABET[((value >> 12) & 0x3f) as usize];
            output[output_cursor + 2] = ALPHABET[((value >> 6) & 0x3f) as usize];
            output[output_cursor + 3] = b'=';
        }
        _ => {}
    }
    Ok(required)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{HandshakeSession, SessionCryptoError, UnverifiedPeerKey};

    const NONCE: [u8; 16] = [
        0x43, 0x09, 0x67, 0xae, 0x36, 0x4b, 0x1c, 0x45, 0x26, 0x62, 0x37, 0x7a, 0xbf, 0x3f, 0xe9,
        0x39,
    ];
    const NONCE_PS4_MODERN: [u8; 16] = [
        0x08, 0x19, 0x2a, 0x3b, 0x4c, 0x5d, 0x6e, 0x48, 0x80, 0x91, 0xa2, 0xb3, 0xc4, 0xd5, 0xe6,
        0xf7,
    ];
    const NONCE_PS5: [u8; 16] = [
        0x10, 0x21, 0x32, 0x43, 0x54, 0x65, 0x76, 0x78, 0x98, 0xa9, 0xba, 0xcb, 0xdc, 0xed, 0xfe,
        0x0f,
    ];
    const MORNING: [u8; 16] = [
        0xd2, 0x78, 0x9f, 0x51, 0x85, 0xa7, 0x99, 0xa2, 0x44, 0x52, 0x77, 0x9c, 0x2b, 0x83, 0xcf,
        0x07,
    ];
    const HANDSHAKE_KEY: [u8; 16] = [
        0xfc, 0x5d, 0x4b, 0xa0, 0x3a, 0x35, 0x3a, 0xbb, 0x6a, 0x7f, 0xac, 0x79, 0x1b, 0x17, 0xbb,
        0x34,
    ];
    const LAUNCH_SPEC_ORACLE: &str =
        include_str!("../../../tests/fixtures/launch_spec_oracle_v1.json");

    struct OracleCase {
        name: &'static str,
        target_name: &'static str,
        target: SessionTarget,
        codec_name: &'static str,
        nonce: [u8; 16],
        spec: LaunchSpec,
    }

    fn hex(bytes: &[u8]) -> String {
        const DIGITS: &[u8; 16] = b"0123456789abcdef";
        let mut encoded = String::with_capacity(bytes.len() * 2);
        for &byte in bytes {
            encoded.push(char::from(DIGITS[usize::from(byte >> 4)]));
            encoded.push(char::from(DIGITS[usize::from(byte & 0x0f)]));
        }
        encoded
    }

    fn decode_hex<const N: usize>(value: &str) -> [u8; N] {
        assert_eq!(value.len(), N * 2);
        let mut output = [0_u8; N];
        for (index, byte) in output.iter_mut().enumerate() {
            *byte = u8::from_str_radix(&value[index * 2..index * 2 + 2], 16).unwrap();
        }
        output
    }

    fn authenticated_test_peer() -> ([u8; 32], UnverifiedPeerKey) {
        let scalar = decode_hex("16e75dcbda9855fb6befdd8aa5f16e7f46fde1d2279703601872d84b1538d900");
        let public = decode_hex::<65>(
            "04dfef08bba856f2b44b8a0e4f44203f8e493feed43ce93afe5c64677720157c5910156794ae5f024aad0ccefa14150aabee080b141276ea3ec0d565f46877a3ca",
        );
        let tag =
            decode_hex::<32>("13c589e23b728524a99f968003a181305968f1bbb64dc4a76ccef6794ceb2d98");
        (scalar, UnverifiedPeerKey::new(&public, &tag).unwrap())
    }

    fn oracle_cases() -> [OracleCase; 6] {
        [
            OracleCase {
                name: "ps4_pre10_avc_distinct",
                target_name: "ps4_pre10",
                target: SessionTarget::Ps4Pre10,
                codec_name: "avc",
                nonce: NONCE,
                spec: LaunchSpec::new(1, 22, 333, 4444, 55555, 666666, SessionCodec::Avc),
            },
            OracleCase {
                name: "ps4_modern_avc_distinct_rows",
                target_name: "ps4_modern",
                target: SessionTarget::Ps4Modern,
                codec_name: "avc",
                nonce: NONCE_PS4_MODERN,
                spec: LaunchSpec::new(2, 33, 444, 5555, 66666, 777777, SessionCodec::Avc),
            },
            OracleCase {
                name: "ps5_avc_distinct_rows",
                target_name: "ps5",
                target: SessionTarget::Ps5,
                codec_name: "avc",
                nonce: NONCE_PS5,
                spec: LaunchSpec::new(3, 44, 555, 6666, 77777, 888888, SessionCodec::Avc),
            },
            OracleCase {
                name: "ps5_hevc_permuted_bounds",
                target_name: "ps5",
                target: SessionTarget::Ps5,
                codec_name: "hevc",
                nonce: NONCE_PS5,
                spec: LaunchSpec::new(u32::MAX, 0, u32::MAX, 0, u32::MAX, 0, SessionCodec::Hevc),
            },
            OracleCase {
                name: "ps5_hevc_max_minus_one",
                target_name: "ps5",
                target: SessionTarget::Ps5,
                codec_name: "hevc",
                nonce: NONCE_PS5,
                spec: LaunchSpec::new(
                    u32::MAX - 1,
                    u32::MAX - 1,
                    u32::MAX - 1,
                    u32::MAX - 1,
                    u32::MAX - 1,
                    u32::MAX - 1,
                    SessionCodec::Hevc,
                ),
            },
            OracleCase {
                name: "ps5_hevc_hdr_max",
                target_name: "ps5",
                target: SessionTarget::Ps5,
                codec_name: "hevc_hdr",
                nonce: NONCE_PS5,
                spec: LaunchSpec::new(
                    u32::MAX,
                    u32::MAX,
                    u32::MAX,
                    u32::MAX,
                    u32::MAX,
                    u32::MAX,
                    SessionCodec::HevcHdr,
                ),
            },
        ]
    }

    fn build_launch_spec_oracle() -> Result<String, LaunchSpecError> {
        let mut document = String::from(
            "{\n  \"schema\": \"gpsx.launch-spec-oracle.v1\",\n  \"source_commit\": \"c8bea2642d0e95402f373af55584a3b98cd89417\",\n  \"construction\": \"json_nul_xor_cfb_zero_stream_then_base64\",\n  \"inputs\": {\n",
        );
        writeln!(
            &mut document,
            "    \"public_input_b_16\": \"{}\",",
            hex(&MORNING)
        )?;
        writeln!(
            &mut document,
            "    \"public_input_c_16\": \"{}\"",
            hex(&HANDSHAKE_KEY)
        )?;
        document.push_str("  },\n  \"cases\": [\n");

        for (case_index, case) in oracle_cases().into_iter().enumerate() {
            let rpcrypt = SessionRpcrypt::new(
                case.target,
                SessionNonce::from_session_response_bytes(case.nonce),
                morning(),
            );
            let mut plaintext = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
            let plaintext_len =
                format_plaintext(&HANDSHAKE_KEY, case.target, case.spec, &mut plaintext)?;
            let json_len = plaintext_len - 1;
            let iv = rpcrypt.counter_zero_iv()?;
            let mut keystream = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
            Encryptor::<Aes128>::new(
                GenericArray::from_slice(&rpcrypt.bright),
                GenericArray::from_slice(iv.as_ref()),
            )
            .encrypt(&mut keystream[..plaintext_len]);
            let mut ciphertext = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
            for index in 0..plaintext_len {
                ciphertext[index] = plaintext[index] ^ keystream[index];
            }
            let mut outer = Zeroizing::new([0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE]);
            let outer_len = encode_base64(&ciphertext[..plaintext_len], outer.as_mut())?;
            let mut key_b64 = Zeroizing::new([0_u8; HANDSHAKE_KEY_BASE64_SIZE]);
            let key_b64_len = encode_base64(&HANDSHAKE_KEY, key_b64.as_mut())?;
            let key_b64 = std::str::from_utf8(&key_b64[..key_b64_len])
                .map_err(|_| LaunchSpecError::FormattingFailed)?;
            let outer_b64 = std::str::from_utf8(&outer[..outer_len])
                .map_err(|_| LaunchSpecError::FormattingFailed)?;

            let mut public_rpcrypt = SessionRpcrypt::new(
                case.target,
                SessionNonce::from_session_response_bytes(case.nonce),
                morning(),
            );
            let mut public_session =
                HandshakeSession::from_unprepared_test_vectors(HANDSHAKE_KEY, [1; 32])
                    .map_err(|_| LaunchSpecError::CryptoInitialization)?;
            let mut public_output = [0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
            let public_length = public_session.write_encrypted_launch_spec(
                &mut public_rpcrypt,
                case.spec,
                &mut public_output,
            )?;
            assert_eq!(public_length.get(), outer_len);
            assert_eq!(&public_output[..public_length.get()], &outer[..outer_len]);

            document.push_str("    {\n");
            writeln!(&mut document, "      \"name\": \"{}\",", case.name)?;
            writeln!(&mut document, "      \"target\": \"{}\",", case.target_name)?;
            writeln!(&mut document, "      \"codec\": \"{}\",", case.codec_name)?;
            writeln!(&mut document, "      \"width\": {},", case.spec.width)?;
            writeln!(&mut document, "      \"height\": {},", case.spec.height)?;
            writeln!(&mut document, "      \"max_fps\": {},", case.spec.max_fps)?;
            writeln!(
                &mut document,
                "      \"bitrate_kbps\": {},",
                case.spec.bitrate_kbps
            )?;
            writeln!(&mut document, "      \"mtu\": {},", case.spec.mtu)?;
            writeln!(&mut document, "      \"rtt_ms\": {},", case.spec.rtt_ms)?;
            writeln!(
                &mut document,
                "      \"public_input_a_16\": \"{}\",",
                hex(&case.nonce)
            )?;
            writeln!(
                &mut document,
                "      \"public_input_c_base64\": \"{key_b64}\","
            )?;
            writeln!(
                &mut document,
                "      \"bright\": \"{}\",",
                hex(&rpcrypt.bright)
            )?;
            writeln!(
                &mut document,
                "      \"ambassador\": \"{}\",",
                hex(&rpcrypt.ambassador)
            )?;
            writeln!(&mut document, "      \"json_size\": {json_len},")?;
            writeln!(&mut document, "      \"plaintext_size\": {plaintext_len},")?;
            writeln!(
                &mut document,
                "      \"json_without_nul\": \"{}\",",
                hex(&plaintext[..json_len])
            )?;
            writeln!(
                &mut document,
                "      \"plaintext_with_nul\": \"{}\",",
                hex(&plaintext[..plaintext_len])
            )?;
            writeln!(&mut document, "      \"iv\": \"{}\",", hex(iv.as_ref()))?;
            writeln!(
                &mut document,
                "      \"zero_buffer_cfb_keystream\": \"{}\",",
                hex(&keystream[..plaintext_len])
            )?;
            writeln!(
                &mut document,
                "      \"xor_ciphertext\": \"{}\",",
                hex(&ciphertext[..plaintext_len])
            )?;
            writeln!(
                &mut document,
                "      \"ciphertext_base64\": \"{outer_b64}\","
            )?;
            writeln!(
                &mut document,
                "      \"ciphertext_base64_size\": {outer_len}"
            )?;
            document.push_str(if case_index + 1 == oracle_cases().len() {
                "    }\n"
            } else {
                "    },\n"
            });
        }
        document.push_str("  ]\n}\n");
        Ok(document)
    }

    fn morning() -> SessionMorning {
        let mut source = Zeroizing::new(MORNING);
        let result = SessionMorning::take_from_registration_buffer(&mut source);
        assert_eq!(*source, [0; 16]);
        result
    }

    fn spec(target: SessionTarget, codec: SessionCodec) -> (SessionRpcrypt, LaunchSpec) {
        (
            SessionRpcrypt::new(
                target,
                SessionNonce::from_session_response_bytes(NONCE),
                morning(),
            ),
            LaunchSpec::new(1920, 1080, 60, 15_000, 1454, 4, codec),
        )
    }

    #[test]
    fn rfc4648_base64_vectors_are_reproduced() {
        for (input, expected) in [
            (&b""[..], &b""[..]),
            (&b"f"[..], &b"Zg=="[..]),
            (&b"fo"[..], &b"Zm8="[..]),
            (&b"foo"[..], &b"Zm9v"[..]),
            (&b"foobar"[..], &b"Zm9vYmFy"[..]),
        ] {
            let mut output = [0_u8; 16];
            let length = encode_base64(input, &mut output).unwrap();
            assert_eq!(&output[..length], expected);
        }
    }

    #[test]
    fn closed_launch_spec_oracle_binds_every_field_and_rejects_mutations() {
        let rebuilt = build_launch_spec_oracle().unwrap();
        assert_eq!(rebuilt, LAUNCH_SPEC_ORACLE);

        for field in [
            "\"schema\"",
            "\"source_commit\"",
            "\"construction\"",
            "\"inputs\"",
            "\"public_input_a_16\"",
            "\"public_input_b_16\"",
            "\"public_input_c_16\"",
            "\"cases\"",
            "\"name\"",
            "\"target\"",
            "\"codec\"",
            "\"width\"",
            "\"height\"",
            "\"max_fps\"",
            "\"bitrate_kbps\"",
            "\"mtu\"",
            "\"rtt_ms\"",
            "\"public_input_c_base64\"",
            "\"bright\"",
            "\"ambassador\"",
            "\"json_size\"",
            "\"plaintext_size\"",
            "\"json_without_nul\"",
            "\"plaintext_with_nul\"",
            "\"iv\"",
            "\"zero_buffer_cfb_keystream\"",
            "\"xor_ciphertext\"",
            "\"ciphertext_base64\"",
            "\"ciphertext_base64_size\"",
        ] {
            let mutated = LAUNCH_SPEC_ORACLE.replacen(field, "\"mutated_field\"", 1);
            assert_ne!(
                mutated, LAUNCH_SPEC_ORACLE,
                "missing mutation target {field}"
            );
            assert_ne!(
                rebuilt, mutated,
                "mutation unexpectedly accepted for {field}"
            );
        }
        for (from, to) in [
            (
                "json_nul_xor_cfb_zero_stream_then_base64",
                "direct_plaintext_cfb",
            ),
            ("zero_buffer_cfb_keystream", "nonzero_counter_keystream"),
            ("xor_ciphertext", "xor_omitted"),
        ] {
            let mutated = LAUNCH_SPEC_ORACLE.replacen(from, to, 1);
            assert_ne!(
                mutated, LAUNCH_SPEC_ORACLE,
                "missing mutation target {from}"
            );
            assert_ne!(rebuilt, mutated, "construction mutation was accepted");
        }
        for (from, to) in [
            (hex(&NONCE), "00000000000000000000000000000000".to_owned()),
            (
                hex(&NONCE_PS4_MODERN),
                "00000000000000000000000000000000".to_owned(),
            ),
            (
                hex(&NONCE_PS5),
                "00000000000000000000000000000000".to_owned(),
            ),
            (hex(&MORNING), "00000000000000000000000000000000".to_owned()),
            (
                hex(&HANDSHAKE_KEY),
                "00000000000000000000000000000000".to_owned(),
            ),
            ("4294967294".to_owned(), "4294967293".to_owned()),
            ("4294967295".to_owned(), "4294967296".to_owned()),
        ] {
            let mutated = LAUNCH_SPEC_ORACLE.replacen(&from, &to, 1);
            assert_ne!(
                mutated, LAUNCH_SPEC_ORACLE,
                "missing mutation target {from}"
            );
            assert_ne!(rebuilt, mutated, "value mutation was accepted");
        }
        let missing_case = LAUNCH_SPEC_ORACLE.replacen("    },\n    {", "    }", 1);
        assert_ne!(missing_case, LAUNCH_SPEC_ORACLE);
        assert_ne!(rebuilt, missing_case);
        assert_ne!(rebuilt, format!("{LAUNCH_SPEC_ORACLE}appended"));
    }

    #[test]
    fn numeric_fields_accept_reviewed_u32_boundaries_independently() {
        for value in [0, 1, u32::MAX - 1, u32::MAX] {
            for field in 0..6 {
                let mut launch_spec = LaunchSpec::new(7, 7, 7, 7, 7, 7, SessionCodec::HevcHdr);
                match field {
                    0 => launch_spec.width = value,
                    1 => launch_spec.height = value,
                    2 => launch_spec.max_fps = value,
                    3 => launch_spec.bitrate_kbps = value,
                    4 => launch_spec.mtu = value,
                    5 => launch_spec.rtt_ms = value,
                    _ => unreachable!(),
                }
                let mut output = [0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE];
                let length =
                    format_plaintext(&HANDSHAKE_KEY, SessionTarget::Ps5, launch_spec, &mut output)
                        .unwrap();
                assert!(length <= 952);
                assert_eq!(output[length - 1], 0);
                let json = std::str::from_utf8(&output[..length - 1]).unwrap();
                let expected = match field {
                    0 => format!("\"resolution\":{{\"width\":{value},\"height\":7}}"),
                    1 => format!("\"resolution\":{{\"width\":7,\"height\":{value}}}"),
                    2 => format!("\"maxFps\":{value},\"score\":10"),
                    3 => format!("\"bwKbpsSent\":{value},\"bwLoss\":0.001000"),
                    4 => format!("\"mtu\":{value},\"rtt\":7"),
                    5 => format!("\"rtt\":{value},\"ports\":[53,2053]"),
                    _ => unreachable!(),
                };
                assert!(
                    json.contains(&expected),
                    "missing exact field placement: {expected}"
                );
            }
        }
    }

    #[test]
    fn exact_output_capacity_succeeds_and_one_byte_less_is_atomic() {
        let launch_spec = LaunchSpec::new(
            u32::MAX,
            u32::MAX,
            u32::MAX,
            u32::MAX,
            u32::MAX,
            u32::MAX,
            SessionCodec::HevcHdr,
        );
        let mut exact = [0xa5; 1_272];
        let mut successful = SessionRpcrypt::new(
            SessionTarget::Ps5,
            SessionNonce::from_session_response_bytes(NONCE),
            morning(),
        );
        assert_eq!(
            write_encrypted_launch_spec(&HANDSHAKE_KEY, &mut successful, launch_spec, &mut exact,)
                .unwrap()
                .get(),
            exact.len()
        );
        assert_ne!(exact, [0xa5; 1_272]);

        let mut short = [0xa5; 1_271];
        let mut rejected = SessionRpcrypt::new(
            SessionTarget::Ps5,
            SessionNonce::from_session_response_bytes(NONCE),
            morning(),
        );
        assert_eq!(
            write_encrypted_launch_spec(&HANDSHAKE_KEY, &mut rejected, launch_spec, &mut short,),
            Err(LaunchSpecError::OutputTooSmall)
        );
        assert_eq!(short, [0xa5; 1_271]);
        assert!(!rejected.launch_spec_counter_zero_consumed);
    }

    #[test]
    fn secret_owners_are_redacted_and_registration_source_is_cleared() {
        let mut source = Zeroizing::new(MORNING);
        let morning = SessionMorning::take_from_registration_buffer(&mut source);
        assert_eq!(*source, [0; 16]);
        assert_eq!(format!("{morning:?}"), "SessionMorning([REDACTED])");
        assert_eq!(format!("{morning}"), "SessionMorning([REDACTED])");
        let rpcrypt = SessionRpcrypt::new(
            SessionTarget::Ps5,
            SessionNonce::from_session_response_bytes(NONCE),
            morning,
        );
        assert_eq!(format!("{rpcrypt:?}"), "SessionRpcrypt([REDACTED])");
        assert_eq!(format!("{rpcrypt}"), "SessionRpcrypt([REDACTED])");
    }

    #[test]
    fn oracle_rejects_construction_order_nul_counter_and_plaintext_mutants() {
        let launch_spec = LaunchSpec::new(1920, 1080, 60, 15_000, 1454, 4, SessionCodec::Avc);
        let mut rpcrypt = SessionRpcrypt::new(
            SessionTarget::Ps5,
            SessionNonce::from_session_response_bytes(NONCE),
            morning(),
        );
        let mut production = [0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
        let production_len =
            write_encrypted_launch_spec(&HANDSHAKE_KEY, &mut rpcrypt, launch_spec, &mut production)
                .unwrap()
                .get();

        let reference = SessionRpcrypt::new(
            SessionTarget::Ps5,
            SessionNonce::from_session_response_bytes(NONCE),
            morning(),
        );
        let mut plaintext = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
        let plaintext_len = format_plaintext(
            &HANDSHAKE_KEY,
            SessionTarget::Ps5,
            launch_spec,
            &mut plaintext,
        )
        .unwrap();
        let iv = reference.counter_zero_iv().unwrap();
        let mut keystream = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
        Encryptor::<Aes128>::new(
            GenericArray::from_slice(&reference.bright),
            GenericArray::from_slice(iv.as_ref()),
        )
        .encrypt(&mut keystream[..plaintext_len]);

        let mut expected_ciphertext = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
        for index in 0..plaintext_len {
            expected_ciphertext[index] = plaintext[index] ^ keystream[index];
        }
        let mut expected = [0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
        let expected_len =
            encode_base64(&expected_ciphertext[..plaintext_len], &mut expected).unwrap();
        assert_eq!(production_len, expected_len);
        assert_eq!(&production[..production_len], &expected[..expected_len]);

        let mut plaintext_base64 = [0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
        let plaintext_base64_len =
            encode_base64(&plaintext[..plaintext_len], &mut plaintext_base64).unwrap();
        assert_ne!(
            &production[..production_len],
            &plaintext_base64[..plaintext_base64_len]
        );

        let mut xor_omitted = [0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
        let xor_omitted_len = encode_base64(&keystream[..plaintext_len], &mut xor_omitted).unwrap();
        assert_ne!(
            &production[..production_len],
            &xor_omitted[..xor_omitted_len]
        );

        let mut without_nul = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
        for index in 0..plaintext_len - 1 {
            without_nul[index] = plaintext[index] ^ keystream[index];
        }
        let mut without_nul_base64 = [0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
        let without_nul_len =
            encode_base64(&without_nul[..plaintext_len - 1], &mut without_nul_base64).unwrap();
        assert_ne!(
            &production[..production_len],
            &without_nul_base64[..without_nul_len]
        );

        let mut nonzero_input = Zeroizing::new([0_u8; MATERIAL_SIZE + 8]);
        nonzero_input[..MATERIAL_SIZE].copy_from_slice(&reference.ambassador);
        nonzero_input[MATERIAL_SIZE + 7] = 1;
        let mut mac = Hmac::<Sha256>::new_from_slice(&HMAC_KEY_PS5).unwrap();
        mac.update(nonzero_input.as_ref());
        let nonzero_digest = mac.finalize().into_bytes();
        let mut nonzero_ciphertext = *plaintext;
        let mut nonzero_stream = Zeroizing::new([0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE]);
        Encryptor::<Aes128>::new(
            GenericArray::from_slice(&reference.bright),
            GenericArray::from_slice(&nonzero_digest[..MATERIAL_SIZE]),
        )
        .encrypt(&mut nonzero_stream[..plaintext_len]);
        for index in 0..plaintext_len {
            nonzero_ciphertext[index] ^= nonzero_stream[index];
        }
        let mut nonzero_base64 = [0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
        let nonzero_len =
            encode_base64(&nonzero_ciphertext[..plaintext_len], &mut nonzero_base64).unwrap();
        assert_ne!(
            &production[..production_len],
            &nonzero_base64[..nonzero_len]
        );

        let mut reordered = plaintext_base64;
        Encryptor::<Aes128>::new(
            GenericArray::from_slice(&reference.bright),
            GenericArray::from_slice(iv.as_ref()),
        )
        .encrypt(&mut reordered[..plaintext_base64_len]);
        assert_ne!(
            &production[..production_len],
            &reordered[..plaintext_base64_len]
        );
    }

    #[test]
    fn pinned_ps4_pre10_counter_zero_iv_is_reproduced() {
        let rpcrypt = SessionRpcrypt::new(
            SessionTarget::Ps4Pre10,
            SessionNonce::from_session_response_bytes(NONCE),
            morning(),
        );
        assert_eq!(
            *rpcrypt.counter_zero_iv().unwrap(),
            [
                0x06, 0x29, 0xbe, 0x04, 0xe9, 0x91, 0x1c, 0x48, 0xb4, 0x5c, 0x02, 0x6d, 0xb7, 0xb7,
                0x88, 0x46,
            ]
        );
    }

    #[test]
    fn canonical_maximum_lengths_match_the_reviewed_bounds() {
        let maximum = LaunchSpec::new(
            u32::MAX,
            u32::MAX,
            u32::MAX,
            u32::MAX,
            u32::MAX,
            u32::MAX,
            SessionCodec::HevcHdr,
        );
        let mut output = [0_u8; MAX_LAUNCH_SPEC_PLAINTEXT_SIZE];
        assert_eq!(
            format_plaintext(&[0; 16], SessionTarget::Ps5, maximum, &mut output).unwrap(),
            952
        );
        let ps4 = LaunchSpec {
            codec: SessionCodec::Avc,
            ..maximum
        };
        assert_eq!(
            format_plaintext(&[0; 16], SessionTarget::Ps4Modern, ps4, &mut output).unwrap(),
            880
        );
        assert_eq!(base64_encoded_len(952).unwrap(), 1_272);
        assert_eq!(base64_encoded_len(1_024).unwrap(), 1_368);
    }

    #[test]
    fn ps4_hevc_is_rejected_without_touching_output_or_consuming_slot() {
        for target in [SessionTarget::Ps4Pre10, SessionTarget::Ps4Modern] {
            for codec in [SessionCodec::Hevc, SessionCodec::HevcHdr] {
                let (mut rpcrypt, launch_spec) = spec(target, codec);
                let mut output = [0xa5; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
                assert_eq!(
                    write_encrypted_launch_spec(&[7; 16], &mut rpcrypt, launch_spec, &mut output),
                    Err(LaunchSpecError::UnsupportedCodec)
                );
                assert_eq!(output, [0xa5; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE]);
                assert!(!rpcrypt.launch_spec_counter_zero_consumed);
            }
        }
    }

    #[test]
    fn output_capacity_failure_is_atomic() {
        let (mut rpcrypt, launch_spec) = spec(SessionTarget::Ps5, SessionCodec::HevcHdr);
        let mut output = [0xa5; 1];
        assert_eq!(
            write_encrypted_launch_spec(&[7; 16], &mut rpcrypt, launch_spec, &mut output),
            Err(LaunchSpecError::OutputTooSmall)
        );
        assert_eq!(output, [0xa5]);
        assert!(!rpcrypt.launch_spec_counter_zero_consumed);
    }

    #[test]
    fn rpcrypt_allows_one_launch_spec_across_handshake_sessions() {
        let (mut rpcrypt, launch_spec) = spec(SessionTarget::Ps5, SessionCodec::HevcHdr);
        let mut first = HandshakeSession::from_unprepared_test_vectors([7; 16], [1; 32]).unwrap();
        let mut second = HandshakeSession::from_unprepared_test_vectors([8; 16], [2; 32]).unwrap();
        let mut output = [0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];

        let length = first
            .write_encrypted_launch_spec(&mut rpcrypt, launch_spec, &mut output)
            .unwrap();
        assert!(length.get() <= 1_272);
        assert_eq!(
            first.write_encrypted_launch_spec(&mut rpcrypt, launch_spec, &mut output),
            Err(LaunchSpecError::AlreadyWritten)
        );
        assert_eq!(
            second.write_encrypted_launch_spec(&mut rpcrypt, launch_spec, &mut output),
            Err(LaunchSpecError::AlreadyWritten)
        );
    }

    #[test]
    fn handshake_cannot_finish_before_launch_spec_success() {
        let session = HandshakeSession::from_unprepared_test_vectors([7; 16], [1; 32]).unwrap();
        let peer = UnverifiedPeerKey::new(&[4; 65], &[0; 32]).unwrap();
        assert_eq!(
            session.finish(peer).unwrap_err(),
            SessionCryptoError::LaunchSpecNotWritten
        );
    }

    #[test]
    fn successful_public_write_enables_authenticated_finish() {
        let (scalar, peer) = authenticated_test_peer();
        let mut session =
            HandshakeSession::from_unprepared_test_vectors(HANDSHAKE_KEY, scalar).unwrap();
        let mut rpcrypt = SessionRpcrypt::new(
            SessionTarget::Ps5,
            SessionNonce::from_session_response_bytes(NONCE_PS5),
            morning(),
        );
        let launch_spec = LaunchSpec::new(3, 44, 555, 6666, 77777, 888888, SessionCodec::Avc);
        let mut output = [0_u8; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
        session
            .write_encrypted_launch_spec(&mut rpcrypt, launch_spec, &mut output)
            .unwrap();
        assert!(session.finish(peer).is_ok());
    }

    #[test]
    fn failed_writes_do_not_enable_finish_and_can_be_retried() {
        let invalid = LaunchSpec::new(1, 22, 333, 4444, 55555, 666666, SessionCodec::Hevc);
        let valid = LaunchSpec {
            codec: SessionCodec::Avc,
            ..invalid
        };

        let (scalar, peer) = authenticated_test_peer();
        let mut rejected_session =
            HandshakeSession::from_unprepared_test_vectors(HANDSHAKE_KEY, scalar).unwrap();
        let mut rejected_rpcrypt = SessionRpcrypt::new(
            SessionTarget::Ps4Modern,
            SessionNonce::from_session_response_bytes(NONCE_PS4_MODERN),
            morning(),
        );
        let mut output = [0xa5; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE];
        assert_eq!(
            rejected_session.write_encrypted_launch_spec(
                &mut rejected_rpcrypt,
                invalid,
                &mut output,
            ),
            Err(LaunchSpecError::UnsupportedCodec)
        );
        let mut short = [0xa5; 1];
        assert_eq!(
            rejected_session.write_encrypted_launch_spec(&mut rejected_rpcrypt, valid, &mut short,),
            Err(LaunchSpecError::OutputTooSmall)
        );
        assert_eq!(output, [0xa5; MAX_ENCRYPTED_LAUNCH_SPEC_SIZE]);
        assert_eq!(short, [0xa5]);
        assert_eq!(
            rejected_session.finish(peer).unwrap_err(),
            SessionCryptoError::LaunchSpecNotWritten
        );

        let (scalar, peer) = authenticated_test_peer();
        let mut retry_session =
            HandshakeSession::from_unprepared_test_vectors(HANDSHAKE_KEY, scalar).unwrap();
        let mut retry_rpcrypt = SessionRpcrypt::new(
            SessionTarget::Ps4Modern,
            SessionNonce::from_session_response_bytes(NONCE_PS4_MODERN),
            morning(),
        );
        assert_eq!(
            retry_session.write_encrypted_launch_spec(&mut retry_rpcrypt, invalid, &mut output,),
            Err(LaunchSpecError::UnsupportedCodec)
        );
        assert_eq!(
            retry_session.write_encrypted_launch_spec(&mut retry_rpcrypt, valid, &mut short,),
            Err(LaunchSpecError::OutputTooSmall)
        );
        retry_session
            .write_encrypted_launch_spec(&mut retry_rpcrypt, valid, &mut output)
            .unwrap();
        assert!(retry_session.finish(peer).is_ok());
    }

    #[test]
    fn pinned_zero_buffer_construction_is_not_direct_plaintext_cfb() {
        let (rpcrypt, _) = spec(SessionTarget::Ps4Pre10, SessionCodec::Avc);
        let mut upstream = *b"GPSx LaunchSpec spans blocks!";
        let original = upstream;
        encrypt_with_counter_zero_keystream(&rpcrypt, &mut upstream).unwrap();

        let iv = rpcrypt.counter_zero_iv().unwrap();
        let mut direct_cfb = original;
        Encryptor::<Aes128>::new(
            GenericArray::from_slice(&rpcrypt.bright),
            GenericArray::from_slice(iv.as_ref()),
        )
        .encrypt(&mut direct_cfb);
        assert_eq!(&upstream[..16], &direct_cfb[..16]);
        assert_ne!(&upstream[16..], &direct_cfb[16..]);
    }
}
