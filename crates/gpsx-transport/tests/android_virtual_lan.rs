// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#![forbid(unsafe_code)]

use std::io::{Read, Write};
use std::net::{Ipv4Addr, SocketAddrV4, TcpStream};
use std::str::FromStr;
use std::time::Duration;

use gpsx_crypto::{
    ControlEndpoint, ControlProfile, ControlRegistrationKey, SessionCodec, SessionMorning,
    SessionNonce, SessionRpcrypt, SessionTarget,
};
use gpsx_protocol::{ConsoleGeneration, DiscoveryState};
use gpsx_transport::discover;
use rand_core::{CryptoRng, RngCore};
use zeroize::Zeroizing;

const DISCOVERY_PORT: u16 = 19_878;
const CONTROL_PORT: u16 = 19_295;
const IO_TIMEOUT: Duration = Duration::from_secs(3);
const TRAILING_MARKER: &[u8] = b"SIMULATED-ANDROID-CONSOLE";
const NONCE: [u8; 16] = [
    0x43, 0x09, 0x67, 0xae, 0x36, 0x4b, 0x1c, 0x45, 0x26, 0x62, 0x37, 0x7a, 0xbf, 0x3f, 0xe9, 0x1d,
];
const MORNING: [u8; 16] = [
    0x30, 0x71, 0x19, 0x22, 0xe8, 0x41, 0x9d, 0x70, 0x4f, 0x29, 0x64, 0x35, 0x56, 0x18, 0xc3, 0x8a,
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

fn simulated_host() -> Ipv4Addr {
    let value = std::env::var("GPSX_ANDROID_SIM_HOST")
        .expect("GPSX_ANDROID_SIM_HOST must name the emulator host gateway");
    Ipv4Addr::from_str(&value).expect("GPSX_ANDROID_SIM_HOST must be an IPv4 address")
}

fn read_bounded_response(stream: &mut TcpStream, response: &mut [u8]) -> usize {
    let mut response_len = 0;
    loop {
        assert!(
            response_len < response.len(),
            "simulated ctrl response is bounded"
        );
        let read = stream
            .read(&mut response[response_len..])
            .expect("receive Android simulated ctrl response");
        if read == 0 {
            return response_len;
        }
        response_len += read;
        if let Some(terminator) = response[..response_len]
            .windows(4)
            .position(|window| window == b"\r\n\r\n")
        {
            let expected_len = terminator + 4 + TRAILING_MARKER.len();
            if response_len >= expected_len {
                return response_len;
            }
        }
    }
}

#[test]
#[ignore = "requires an Android emulator and the bounded host fake-console process"]
fn android_virtual_lan_discovers_and_opens_ctrl_to_fake_console() {
    let host = simulated_host();
    let report = discover(
        ConsoleGeneration::Ps4,
        SocketAddrV4::new(host, DISCOVERY_PORT),
        Duration::from_secs(1),
    )
    .expect("discover the synthetic console across the emulator virtual LAN");
    assert_eq!(report.received_datagrams(), 1);
    assert_eq!(report.rejected_datagrams(), 0);
    assert_eq!(report.consoles().len(), 1);
    assert_eq!(report.consoles()[0].state(), DiscoveryState::Ready);
    assert_eq!(report.consoles()[0].host_request_port(), Some(CONTROL_PORT));

    let mut morning_source = Zeroizing::new(MORNING);
    let mut rpcrypt = SessionRpcrypt::new(
        SessionTarget::Ps4Modern,
        SessionNonce::from_session_response_bytes(NONCE),
        SessionMorning::take_from_registration_buffer(&mut morning_source),
    );
    let mut handshake = rpcrypt
        .begin_control(ControlProfile::Ps4_10, &mut PatternRng)
        .expect("start Android simulated ctrl handshake");
    let mut registration_source = Zeroizing::new([0x5a; 16]);
    let registration_key =
        ControlRegistrationKey::take_from_registration_buffer(&mut registration_source);
    let mut request = [0_u8; 512];
    let request_len = handshake
        .write_request(
            ControlEndpoint::local_ipv4(host),
            &registration_key,
            SessionCodec::Avc,
            &mut request,
        )
        .expect("prepare Android simulated ctrl request")
        .get();
    assert_eq!(*registration_source, [0; 16]);

    let mut stream = TcpStream::connect(SocketAddrV4::new(host, CONTROL_PORT))
        .expect("connect to the synthetic console across the emulator virtual LAN");
    stream
        .set_read_timeout(Some(IO_TIMEOUT))
        .expect("bound Android simulated read");
    stream
        .set_write_timeout(Some(IO_TIMEOUT))
        .expect("bound Android simulated write");
    stream
        .write_all(&request[..request_len])
        .expect("send Android simulated ctrl request");
    let mut response = [0_u8; 512];
    let response_len = read_bounded_response(&mut stream, &mut response);
    let (parsed, header_len) = handshake
        .finish(&response[..response_len])
        .expect("finish Android simulated ctrl response");
    assert_eq!(parsed.server_type, None);
    assert!(!parsed.prohibit);
    let trailing = &response[header_len.get()..response_len];
    assert_eq!(trailing, TRAILING_MARKER);
}
