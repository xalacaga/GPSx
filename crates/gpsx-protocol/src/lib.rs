// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#![forbid(unsafe_code)]

//! Pure, bounded protocol formatting and parsing for GPSx.
//!
//! The implemented slices cover discovery SRCH and bounded local-registration
//! wire formatting/parsing. The crate performs no I/O and contains no wake-up,
//! authentication, cryptography, session, or media implementation.

mod discovery;
mod http;
mod registration;

pub use discovery::{
    ConsoleGeneration, DiscoveryHost, DiscoveryParseError, DiscoveryState, PS4_DISCOVERY_PORT,
    PS4_PROTOCOL_VERSION, PS5_DISCOVERY_PORT, PS5_PROTOCOL_VERSION, format_search_request,
    parse_search_response,
};
pub use http::{HttpParseError, MAX_DATAGRAM_SIZE, MAX_HEADER_COUNT, MAX_LINE_SIZE};
pub use registration::{
    AccountId, MAX_REGISTRATION_BODY_SIZE, MAX_REGISTRATION_FIELD_COUNT,
    MAX_REGISTRATION_FIELD_LINE_SIZE, MAX_REGISTRATION_FIELD_NAME_SIZE,
    MAX_REGISTRATION_REQUEST_HEADER_SIZE, MAX_REGISTRATION_RESPONSE_HEADER_SIZE,
    MAX_REGISTRATION_RESPONSE_SIZE, OnlineId, PreparedRegistrationPrefix,
    REGISTRATION_REQUEST_PREFIX_SIZE, RegistrationCodecError, RegistrationCryptoTarget,
    RegistrationHttpResponse, RegistrationIdentity, RegistrationOffsets, RegistrationPayload,
    RegistrationProfile, RegistrationResponseView, finish_registration_request_payload,
    format_registration_http_request_header, format_registration_inner_request,
    parse_registration_http_response, parse_registration_response_fields,
    prepare_registration_request_prefix, validate_registration_crypto_target,
};
