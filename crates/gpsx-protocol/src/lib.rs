// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#![forbid(unsafe_code)]

//! Pure, bounded protocol formatting and parsing for GPSx.
//!
//! This first slice contains discovery SRCH only. It performs no I/O and has no
//! wake-up, registration, authentication, cryptography, session, or media code.

mod discovery;
mod http;

pub use discovery::{
    ConsoleGeneration, DiscoveryHost, DiscoveryParseError, DiscoveryState, PS4_DISCOVERY_PORT,
    PS4_PROTOCOL_VERSION, PS5_DISCOVERY_PORT, PS5_PROTOCOL_VERSION, format_search_request,
    parse_search_response,
};
pub use http::{HttpParseError, MAX_DATAGRAM_SIZE, MAX_HEADER_COUNT, MAX_LINE_SIZE};
