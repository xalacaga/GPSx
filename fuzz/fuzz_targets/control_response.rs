// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#![no_main]

use std::net::Ipv4Addr;

use gpsx_crypto::{
    ControlEndpoint, ControlProfile, ControlRegistrationKey, MAX_CONTROL_HEADER_SIZE,
    MAX_CONTROL_REQUEST_SIZE, SessionCodec, SessionMorning, SessionNonce, SessionRpcrypt,
    SessionTarget,
};
use libfuzzer_sys::fuzz_target;
use rand_core::{CryptoRng, RngCore};
use zeroize::Zeroizing;

struct FixedRng;

impl RngCore for FixedRng {
    fn next_u32(&mut self) -> u32 {
        0xa3a2_a1a0
    }

    fn next_u64(&mut self) -> u64 {
        0xa7a6_a5a4_a3a2_a1a0
    }

    fn fill_bytes(&mut self, destination: &mut [u8]) {
        self.try_fill_bytes(destination).unwrap();
    }

    fn try_fill_bytes(&mut self, destination: &mut [u8]) -> Result<(), rand_core::Error> {
        for (index, byte) in destination.iter_mut().enumerate() {
            *byte = 0xa0_u8.wrapping_add(index as u8);
        }
        Ok(())
    }
}

impl CryptoRng for FixedRng {}

fn exercise(input: &[u8]) {
    let mut morning = Zeroizing::new([0xb0; 16]);
    let mut owner = SessionRpcrypt::new(
        SessionTarget::Ps4Modern,
        SessionNonce::from_session_response_bytes([0x40; 16]),
        SessionMorning::take_from_registration_buffer(&mut morning),
    );
    let mut handshake = owner
        .begin_control(ControlProfile::Ps4_10, &mut FixedRng)
        .unwrap();
    let mut registration = Zeroizing::new([0x5a; 16]);
    let registration = ControlRegistrationKey::take_from_registration_buffer(&mut registration);
    let mut request = [0_u8; MAX_CONTROL_REQUEST_SIZE];
    handshake
        .write_request(
            ControlEndpoint::local_ipv4(Ipv4Addr::LOCALHOST),
            &registration,
            SessionCodec::Avc,
            &mut request,
        )
        .unwrap();
    if let Ok((_, header_len)) = handshake.finish(input) {
        let header_len = header_len.get();
        assert!(header_len <= input.len().min(MAX_CONTROL_HEADER_SIZE));
        assert!(input[..header_len].ends_with(b"\r\n\r\n"));
        let first_terminator = input
            .windows(4)
            .position(|window| window == b"\r\n\r\n")
            .unwrap();
        assert_eq!(header_len, first_terminator + 4);
        assert!(input[..first_terminator]
            .windows(4)
            .all(|window| window != b"\r\n\r\n"));
    }
}

fuzz_target!(|input: &[u8]| {
    exercise(input);
});
