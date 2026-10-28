// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

use std::collections::{BTreeMap, BTreeSet};
use std::net::Ipv4Addr;

use rand_core::{CryptoRng, RngCore};
use zeroize::Zeroizing;

use super::*;
use crate::launch_spec::{LaunchSpec, SessionMorning, SessionNonce};

const FIXTURE: &str = include_str!("../../../tests/fixtures/control_handshake_oracle_v1.json");
const SOURCE_COMMIT: &str = "c8bea2642d0e95402f373af55584a3b98cd89417";

enum Json {
    Bool(bool),
    Number(u64),
    String(String),
    Array(Vec<Self>),
    Object(BTreeMap<String, Self>),
}

fn object(entries: impl IntoIterator<Item = (&'static str, Json)>) -> Json {
    Json::Object(
        entries
            .into_iter()
            .map(|(key, value)| (key.to_owned(), value))
            .collect(),
    )
}

fn string(value: impl Into<String>) -> Json {
    Json::String(value.into())
}

fn number(value: impl Into<u64>) -> Json {
    Json::Number(value.into())
}

fn write_json_string(output: &mut String, value: &str) {
    output.push('"');
    for character in value.chars() {
        match character {
            '"' => output.push_str("\\\""),
            '\\' => output.push_str("\\\\"),
            '\u{08}' => output.push_str("\\b"),
            '\u{0c}' => output.push_str("\\f"),
            '\n' => output.push_str("\\n"),
            '\r' => output.push_str("\\r"),
            '\t' => output.push_str("\\t"),
            value if value < '\u{20}' => write!(output, "\\u{:04x}", value as u32).unwrap(),
            value => output.push(value),
        }
    }
    output.push('"');
}

fn write_json(output: &mut String, value: &Json, depth: usize) {
    match value {
        Json::Bool(value) => output.push_str(if *value { "true" } else { "false" }),
        Json::Number(value) => write!(output, "{value}").unwrap(),
        Json::String(value) => write_json_string(output, value),
        Json::Array(values) => {
            if values.is_empty() {
                output.push_str("[]");
                return;
            }
            output.push_str("[\n");
            for (index, value) in values.iter().enumerate() {
                output.push_str(&" ".repeat((depth + 1) * 2));
                write_json(output, value, depth + 1);
                if index + 1 != values.len() {
                    output.push(',');
                }
                output.push('\n');
            }
            output.push_str(&" ".repeat(depth * 2));
            output.push(']');
        }
        Json::Object(values) => {
            if values.is_empty() {
                output.push_str("{}");
                return;
            }
            output.push_str("{\n");
            for (index, (key, value)) in values.iter().enumerate() {
                output.push_str(&" ".repeat((depth + 1) * 2));
                write_json_string(output, key);
                output.push_str(": ");
                write_json(output, value, depth + 1);
                if index + 1 != values.len() {
                    output.push(',');
                }
                output.push('\n');
            }
            output.push_str(&" ".repeat(depth * 2));
            output.push('}');
        }
    }
}

fn encode_hex(bytes: &[u8]) -> String {
    bytes.iter().fold(String::new(), |mut output, byte| {
        write!(output, "{byte:02x}").unwrap();
        output
    })
}

fn encode_b64(bytes: &[u8]) -> String {
    let mut output = vec![0_u8; bytes.len().div_ceil(3) * 4];
    let length = encode_base64(bytes, &mut output).unwrap();
    output.truncate(length);
    String::from_utf8(output).unwrap()
}

fn sequence(start: u8) -> [u8; 16] {
    std::array::from_fn(|index| start.wrapping_add(index as u8))
}

fn rpcrypt(target: SessionTarget, public_a: [u8; 16], public_b: [u8; 16]) -> SessionRpcrypt {
    let mut morning = Zeroizing::new(public_b);
    SessionRpcrypt::new(
        target,
        SessionNonce::from_session_response_bytes(public_a),
        SessionMorning::take_from_registration_buffer(&mut morning),
    )
}

struct FixedRng([u8; 16]);

impl RngCore for FixedRng {
    fn next_u32(&mut self) -> u32 {
        u32::from_le_bytes(self.0[..4].try_into().unwrap())
    }

    fn next_u64(&mut self) -> u64 {
        u64::from_le_bytes(self.0[..8].try_into().unwrap())
    }

    fn fill_bytes(&mut self, destination: &mut [u8]) {
        self.try_fill_bytes(destination).unwrap();
    }

    fn try_fill_bytes(&mut self, destination: &mut [u8]) -> Result<(), rand_core::Error> {
        assert_eq!(destination.len(), self.0.len());
        destination.copy_from_slice(&self.0);
        Ok(())
    }
}

impl CryptoRng for FixedRng {}

fn field(rpcrypt: &SessionRpcrypt, name: &'static str, counter: u64, plaintext: &[u8]) -> Json {
    let iv = rpcrypt.counter_iv(counter).unwrap();
    let mut ciphertext = plaintext.to_vec();
    rpcrypt
        .encrypt_in_place(counter, ciphertext.as_mut_slice())
        .unwrap();
    object([
        ("base64", string(encode_b64(&ciphertext))),
        ("ciphertext", string(encode_hex(&ciphertext))),
        ("counter", number(counter)),
        ("iv", string(encode_hex(iv.as_ref()))),
        ("name", string(name)),
        ("plaintext", string(encode_hex(plaintext))),
    ])
}

#[derive(Clone, Copy)]
struct RequestCase {
    name: &'static str,
    profile_name: &'static str,
    profile: ControlProfile,
    target: SessionTarget,
    codec_name: &'static str,
    codec: SessionCodec,
}

fn request_cases() -> [RequestCase; 6] {
    [
        RequestCase {
            name: "ps4_8_avc",
            profile_name: "ps4_8",
            profile: ControlProfile::Ps4_8,
            target: SessionTarget::Ps4Pre10,
            codec_name: "avc",
            codec: SessionCodec::Avc,
        },
        RequestCase {
            name: "ps4_9_avc",
            profile_name: "ps4_9",
            profile: ControlProfile::Ps4_9,
            target: SessionTarget::Ps4Pre10,
            codec_name: "avc",
            codec: SessionCodec::Avc,
        },
        RequestCase {
            name: "ps4_10_avc",
            profile_name: "ps4_10",
            profile: ControlProfile::Ps4_10,
            target: SessionTarget::Ps4Modern,
            codec_name: "avc",
            codec: SessionCodec::Avc,
        },
        RequestCase {
            name: "ps5_avc",
            profile_name: "ps5",
            profile: ControlProfile::Ps5,
            target: SessionTarget::Ps5,
            codec_name: "avc",
            codec: SessionCodec::Avc,
        },
        RequestCase {
            name: "ps5_hevc",
            profile_name: "ps5",
            profile: ControlProfile::Ps5,
            target: SessionTarget::Ps5,
            codec_name: "hevc",
            codec: SessionCodec::Hevc,
        },
        RequestCase {
            name: "ps5_hevc_hdr",
            profile_name: "ps5",
            profile: ControlProfile::Ps5,
            target: SessionTarget::Ps5,
            codec_name: "hevc_hdr",
            codec: SessionCodec::HevcHdr,
        },
    ]
}

fn build_requests() -> Vec<Json> {
    request_cases()
        .into_iter()
        .enumerate()
        .map(|(index, case)| {
            let offset = (index as u8) * 0x10;
            let public_a = sequence(0x10 + offset);
            let public_b = sequence(0x80 + offset);
            let public_c = sequence(0x30 + offset);
            let did_random = sequence(0xa0 + offset);
            let field_owner = rpcrypt(case.target, public_a, public_b);
            let mut did = [0_u8; 32];
            did[..DID_PREFIX.len()].copy_from_slice(&DID_PREFIX);
            did[DID_RANDOM_RANGE].copy_from_slice(&did_random);
            let mut fields = vec![
                field(&field_owner, "auth", 0, &public_c),
                field(&field_owner, "did", 1, &did),
                field(&field_owner, "os_type", 2, OS_TYPE),
            ];
            if matches!(case.profile, ControlProfile::Ps4_10 | ControlProfile::Ps5) {
                fields.push(field(&field_owner, "bitrate", 3, &[0; 4]));
            }
            if case.profile == ControlProfile::Ps5 {
                let streaming = match case.codec {
                    SessionCodec::Avc => 1_u32,
                    SessionCodec::Hevc => 2,
                    SessionCodec::HevcHdr => 3,
                };
                fields.push(field(
                    &field_owner,
                    "streaming_type",
                    4,
                    &streaming.to_le_bytes(),
                ));
            }

            let mut request_owner = rpcrypt(case.target, public_a, public_b);
            let mut rng = FixedRng(did_random);
            let mut handshake = request_owner.begin_control(case.profile, &mut rng).unwrap();
            let mut registration = Zeroizing::new(public_c);
            let registration =
                ControlRegistrationKey::take_from_registration_buffer(&mut registration);
            let endpoint = Ipv4Addr::new(203, 0, 113, 10 + index as u8);
            let mut request = [0_u8; MAX_CONTROL_REQUEST_SIZE];
            let request_length = handshake
                .write_request(
                    ControlEndpoint::local_ipv4(endpoint),
                    &registration,
                    case.codec,
                    &mut request,
                )
                .unwrap()
                .get();
            let next_counter = handshake.rpcrypt.control_local_counter;

            object([
                ("codec", string(case.codec_name)),
                ("did_random", string(encode_hex(&did_random))),
                ("endpoint", string(endpoint.to_string())),
                ("fields", Json::Array(fields)),
                ("name", string(case.name)),
                ("next_local_counter", number(next_counter)),
                ("profile", string(case.profile_name)),
                ("public_input_a_16", string(encode_hex(&public_a))),
                ("public_input_b_16", string(encode_hex(&public_b))),
                ("public_input_c_16", string(encode_hex(&public_c))),
                ("request", string(encode_hex(&request[..request_length]))),
                ("request_length", number(request_length as u64)),
            ])
        })
        .collect()
}

fn response(
    name: String,
    bytes: Vec<u8>,
    encoded: String,
    decoded: Vec<u8>,
    upstream_http_error: u64,
    expected: &'static str,
) -> Json {
    assert_public_finish(&name, &bytes, expected);
    let (ctrl_base64_error, ctrl_success, ctrl_server_type_valid, ctrl_prohibit) =
        upstream_ctrl_evidence(&name, decoded.len(), upstream_http_error);
    let mut entries = vec![
        ("base64", string(encoded)),
        ("gpsx_expected", string(expected)),
        ("name", string(name)),
        ("response", string(encode_hex(&bytes))),
        ("upstream_ctrl_base64_error", number(ctrl_base64_error)),
        ("upstream_ctrl_rp_prohibit", Json::Bool(ctrl_prohibit)),
        (
            "upstream_ctrl_server_type_valid",
            Json::Bool(ctrl_server_type_valid),
        ),
        ("upstream_ctrl_success", Json::Bool(ctrl_success)),
        ("upstream_decoder_only_base64_error", number(0_u64)),
        (
            "upstream_decoder_only_decoded",
            string(encode_hex(&decoded)),
        ),
        (
            "upstream_decoder_only_decoded_length",
            number(decoded.len() as u64),
        ),
        ("upstream_http_error", number(upstream_http_error)),
    ];
    let name = match &entries[2].1 {
        Json::String(value) => value.clone(),
        _ => unreachable!(),
    };
    if let Some(value) = name.strip_prefix("server_type_") {
        let server_type = value.parse::<u8>().unwrap();
        let mut plaintext = [0_u8; 16];
        plaintext[0] = server_type;
        entries.push(("gpsx_decrypted", string(encode_hex(&plaintext))));
        entries.push((
            "gpsx_classified_type",
            string(["ps4", "ps4_pro", "ps5"][usize::from(server_type)]),
        ));
        entries.push(("gpsx_next_remote_counter", number(1_u64)));
    } else if matches!(expected, "accept" | "accept_unknown_server_type") {
        entries.push(("gpsx_next_remote_counter", number(0_u64)));
    }
    object(entries)
}

fn upstream_ctrl_evidence(
    name: &str,
    decoded_len: usize,
    http_error: u64,
) -> (u64, bool, bool, bool) {
    if http_error != 0 {
        return (0, false, false, false);
    }
    if let Some(value) = name.strip_prefix("server_type_") {
        return (0, true, true, value == "1");
    }
    if name == "absent_server_type"
        || matches!(
            name,
            "bare_lf" | "embedded_nul" | "nonzero_padding_bits" | "junk_after_padding"
        )
    {
        return (0, true, false, false);
    }
    if name.ends_with("_unpadded") {
        let error = if decoded_len >= 18 { 12 } else { 11 };
        return (error, false, false, false);
    }
    if decoded_len >= 17 {
        return (12, false, false, false);
    }
    if decoded_len % 3 == 0 {
        return (11, false, false, false);
    }
    (0, true, decoded_len == 16, false)
}

fn assert_public_finish(name: &str, bytes: &[u8], expected: &str) {
    let mut owner = rpcrypt(SessionTarget::Ps4Modern, sequence(0x40), sequence(0xb0));
    let mut handshake = owner
        .begin_control(ControlProfile::Ps4_10, &mut FixedRng(sequence(0xa0)))
        .unwrap();
    let mut registration_source = Zeroizing::new(sequence(0x30));
    let registration =
        ControlRegistrationKey::take_from_registration_buffer(&mut registration_source);
    let mut request = [0_u8; MAX_CONTROL_REQUEST_SIZE];
    handshake
        .write_request(
            ControlEndpoint::local_ipv4(Ipv4Addr::new(203, 0, 113, 250)),
            &registration,
            SessionCodec::Avc,
            &mut request,
        )
        .unwrap();

    let result = handshake.finish(bytes);
    match expected {
        "accept" | "accept_unknown_server_type" => {
            let (response, header_len) = result.unwrap();
            let first_terminator = bytes
                .windows(4)
                .position(|window| window == b"\r\n\r\n")
                .unwrap();
            assert_eq!(header_len.get(), first_terminator + 4, "{name}");
            assert!(header_len.get() <= bytes.len().min(MAX_CONTROL_HEADER_SIZE));
            assert!(bytes[..header_len.get()].ends_with(b"\r\n\r\n"));
            assert!(
                bytes[..first_terminator]
                    .windows(4)
                    .all(|window| window != b"\r\n\r\n")
            );
            let expected_type = name.strip_prefix("server_type_").map(|value| match value {
                "0" => ControlServerType::Ps4,
                "1" => ControlServerType::Ps4Pro,
                "2" => ControlServerType::Ps5,
                _ => unreachable!(),
            });
            assert_eq!(response.server_type, expected_type, "{name}");
            assert_eq!(response.prohibit, name == "server_type_1", "{name}");
            assert_eq!(
                owner.control_remote_counter,
                u64::from(expected_type.is_some()),
                "{name}"
            );
        }
        "reject_invalid_base64" => {
            assert_eq!(result, Err(ControlHandshakeError::InvalidBase64), "{name}");
            assert_finish_rejection_is_atomic(&mut owner, name);
        }
        "reject_invalid_server_type" => {
            assert_eq!(
                result,
                Err(ControlHandshakeError::InvalidServerType),
                "{name}"
            );
            assert_finish_rejection_is_atomic(&mut owner, name);
        }
        "reject_invalid_header" => {
            assert_eq!(result, Err(ControlHandshakeError::InvalidHeader), "{name}");
            assert_finish_rejection_is_atomic(&mut owner, name);
        }
        "reject_incomplete_header" => {
            assert_eq!(
                result,
                Err(ControlHandshakeError::IncompleteHeader),
                "{name}"
            );
            assert_finish_rejection_is_atomic(&mut owner, name);
        }
        _ => panic!("unknown GPSx verdict for {name}: {expected}"),
    }
}

fn assert_finish_rejection_is_atomic(owner: &mut SessionRpcrypt, name: &str) {
    assert!(!owner.control_finished, "{name}");
    assert_eq!(owner.control_server_type, None, "{name}");
    assert_eq!(owner.control_remote_counter, 0, "{name}");

    let mut output = [0xa5; 1_368];
    assert_eq!(
        crate::launch_spec::write_encrypted_launch_spec(
            &[0x11; 16],
            owner,
            LaunchSpec::new(1280, 720, 60, 15_000, 1454, 4, SessionCodec::Avc),
            &mut output,
        ),
        Err(LaunchSpecError::ControlHandshakeNotFinished),
        "{name}"
    );
    assert_eq!(output, [0xa5; 1_368], "{name}");
}

fn expected_length_verdict(length: usize) -> &'static str {
    if matches!(length, 1 | 2 | 4 | 5 | 7 | 8 | 10 | 11 | 13 | 14) {
        "accept_unknown_server_type"
    } else {
        "reject_invalid_server_type"
    }
}

fn build_responses() -> Vec<Json> {
    let mut responses = Vec::new();
    for length in 1_usize..=19 {
        let decoded: Vec<u8> = (0..length)
            .map(|index| (length * 7 + index) as u8)
            .collect();
        let padded = encode_b64(&decoded);
        let unpadded = padded.trim_end_matches('=').to_owned();
        for (suffix, encoded, expected) in [
            ("padded", padded.as_str(), expected_length_verdict(length)),
            (
                "unpadded",
                unpadded.as_str(),
                if unpadded.len() % 4 == 0 {
                    expected_length_verdict(length)
                } else {
                    "reject_invalid_base64"
                },
            ),
        ] {
            let bytes =
                format!("HTTP/1.1 200 OK\r\nRP-Server-Type: {encoded}\r\n\r\n").into_bytes();
            responses.push(response(
                format!("length_{length:02}_{suffix}"),
                bytes,
                encoded.to_owned(),
                decoded.clone(),
                0,
                expected,
            ));
        }
    }

    let owner = rpcrypt(SessionTarget::Ps4Modern, sequence(0x40), sequence(0xb0));
    for server_type in 0_u8..=2 {
        let mut decoded = vec![0_u8; 16];
        decoded[0] = server_type;
        owner.encrypt_in_place(0, decoded.as_mut_slice()).unwrap();
        let encoded = encode_b64(&decoded);
        let prohibit = server_type & 1;
        let bytes = format!(
            "HTTP/1.1 200 Connection Established\r\nRP-Server-Type: {encoded}\r\nRP-Prohibit: {prohibit}\r\n\r\n"
        )
        .into_bytes();
        responses.push(response(
            format!("server_type_{server_type}"),
            bytes,
            encoded,
            decoded,
            0,
            "accept",
        ));
    }

    let special = [
        (
            "absent_server_type",
            b"HTTP/1.1 200\r\nRP-Prohibit: 0\r\n\r\n".to_vec(),
            "",
            Vec::new(),
            0,
            "accept",
        ),
        (
            "bare_lf",
            b"HTTP/1.1 200 OK\nRP-Server-Type: QQ==\n\n".to_vec(),
            "QQ==",
            vec![0x41],
            0,
            "reject_incomplete_header",
        ),
        (
            "lone_cr",
            b"HTTP/1.1 200 OK\rRP-Server-Type: QQ==\r\r".to_vec(),
            "QQ==",
            vec![0x41],
            11,
            "reject_incomplete_header",
        ),
        (
            "embedded_nul",
            b"HTTP/1.1 200 OK\0\r\nRP-Server-Type: QQ==\r\n\r\n".to_vec(),
            "QQ==",
            vec![0x41],
            0,
            "reject_invalid_header",
        ),
        (
            "nonzero_padding_bits",
            b"HTTP/1.1 200 OK\r\nRP-Server-Type: AB==\r\n\r\n".to_vec(),
            "AB==",
            vec![0],
            0,
            "reject_invalid_base64",
        ),
        (
            "junk_after_padding",
            b"HTTP/1.1 200 OK\r\nRP-Server-Type: QQ==junk\r\n\r\n".to_vec(),
            "QQ==junk",
            vec![0x41],
            0,
            "reject_invalid_base64",
        ),
    ];
    responses.extend(special.into_iter().map(
        |(name, bytes, encoded, decoded, http_error, expected)| {
            response(
                name.to_owned(),
                bytes,
                encoded.to_owned(),
                decoded,
                http_error,
                expected,
            )
        },
    ));
    responses
}

fn build_control_oracle() -> String {
    let root = object([
        (
            "bounds",
            object([
                (
                    "maximum_header_bytes",
                    number(MAX_CONTROL_HEADER_SIZE as u64),
                ),
                ("maximum_header_lines", number(MAX_CONTROL_LINES as u64)),
                ("maximum_line_bytes", number(MAX_CONTROL_LINE_SIZE as u64)),
                (
                    "maximum_request_bytes",
                    number(MAX_CONTROL_REQUEST_SIZE as u64),
                ),
            ]),
        ),
        (
            "generation",
            object([
                (
                    "base64_c_decoder_only_diagnostic_executed_for_every_response",
                    Json::Bool(true),
                ),
                (
                    "ctrl_path_base64_executed_for_every_parsed_server_type",
                    Json::Bool(true),
                ),
                (
                    "http_c_compiled_and_executed_for_every_response",
                    Json::Bool(true),
                ),
                (
                    "request_formatting_transcription",
                    object([
                        ("compiled", Json::Bool(false)),
                        ("executed", Json::Bool(false)),
                    ]),
                ),
                (
                    "response_ctrl_parse_loop_transcription",
                    object([
                        ("compiled", Json::Bool(true)),
                        ("executed", Json::Bool(true)),
                    ]),
                ),
                ("source_commit", string(SOURCE_COMMIT)),
                ("synthetic_public_inputs_only", Json::Bool(true)),
                (
                    "upstream_schema",
                    string("gpsx.control-handshake-upstream-audit.v1"),
                ),
            ]),
        ),
        ("requests", Json::Array(build_requests())),
        (
            "response_crypto",
            object([
                (
                    "accepted_types",
                    Json::Array((0_u64..=2).map(number).collect()),
                ),
                (
                    "next_remote_counter_after_absent_or_short_type",
                    number(0_u64),
                ),
                ("next_remote_counter_after_classified_type", number(1_u64)),
                ("profile", string("ps4_10")),
                ("public_input_a_16", string(encode_hex(&sequence(0x40)))),
                ("public_input_b_16", string(encode_hex(&sequence(0xb0)))),
                ("server_type_counter", number(0_u64)),
            ]),
        ),
        ("responses", Json::Array(build_responses())),
        ("schema", string("gpsx.control-handshake-oracle.v1")),
    ]);
    let mut output = String::new();
    write_json(&mut output, &root, 0);
    output.push('\n');
    output
}

#[test]
fn closed_control_oracle_binds_every_field_and_rejects_mutations() {
    let expected = build_control_oracle();
    assert_eq!(expected, FIXTURE);

    let field_names: BTreeSet<&str> = FIXTURE
        .lines()
        .filter_map(|line| line.trim_start().strip_prefix('"'))
        .filter_map(|line| line.split_once("\":"))
        .map(|(name, _)| name)
        .collect();
    assert!(!field_names.is_empty());
    for field_name in field_names {
        let from = format!("\"{field_name}\":");
        let to = format!("\"{field_name}_mutated\":");
        let mutated = FIXTURE.replacen(&from, &to, 1);
        assert_ne!(mutated, FIXTURE, "field was absent: {field_name}");
        assert_ne!(
            mutated, expected,
            "field-name mutation survived: {field_name}"
        );
    }

    for (from, to) in [
        ("  \"bounds\":", "  \"bounds_mutated\":"),
        ("  \"generation\":", "  \"generation_mutated\":"),
        ("  \"requests\":", "  \"requests_mutated\":"),
        ("  \"response_crypto\":", "  \"response_crypto_mutated\":"),
        ("  \"responses\":", "  \"responses_mutated\":"),
        ("  \"schema\":", "  \"schema_mutated\":"),
        ("\"counter\": 0", "\"counter\": 9"),
        ("\"codec\": \"avc\"", "\"codec\": \"mutated\""),
        ("\"profile\": \"ps4_8\"", "\"profile\": \"ps5\""),
        (
            "\"iv\": \"5ba7063e734c9fa92c6f05652f79674a\"",
            "\"iv\": \"5ba7063e734c9fa92c6f05652f79674b\"",
        ),
        ("\"request_length\": 328", "\"request_length\": 329"),
        (
            "\"gpsx_expected\": \"accept\"",
            "\"gpsx_expected\": \"reject\"",
        ),
        ("\"upstream_http_error\": 11", "\"upstream_http_error\": 0"),
        (
            "\"gpsx_next_remote_counter\": 1",
            "\"gpsx_next_remote_counter\": 2",
        ),
        ("\"base64\": \"Bw==\"", "\"base64\": \"Bw=\""),
        ("4f4b000d0a", "4f4b0d0a"),
        ("0d0a0d0a\"", "0a0a\""),
        (
            "486f73743a203230332e302e3131332e31303a393239350d0a557365722d4167656e743a2072656d6f7465706c61792057696e646f7773",
            "557365722d4167656e743a2072656d6f7465706c61792057696e646f77730d0a486f73743a203230332e302e3131332e31303a39323935",
        ),
        (
            "52502d50726f68696269743a20300d0a0d0a",
            "52502d50726f68696269743a20310d0a0d0a",
        ),
        ("\"request\": \"47455420", "\"request\": \"47455421"),
        ("  \"requests\": [", "  \"requests\": [\n    {},"),
    ] {
        let mutated = FIXTURE.replacen(from, to, 1);
        assert_ne!(mutated, FIXTURE, "mutation source was absent: {from}");
        assert_ne!(mutated, expected, "mutation survived: {from}");
    }

    let appended = format!("{FIXTURE}x");
    assert_ne!(appended, expected);
}
