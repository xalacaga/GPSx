// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#![forbid(unsafe_code)]

use std::io::{Read, Write};
use std::net::{Ipv4Addr, Shutdown, TcpListener, TcpStream};
use std::thread;
use std::time::Duration;

use gpsx_crypto::{
    ControlEndpoint, ControlProfile, ControlRegistrationKey, SessionCodec, SessionMorning,
    SessionNonce, SessionRpcrypt, SessionTarget,
};
use rand_core::{CryptoRng, RngCore};
use zeroize::Zeroizing;

const IO_TIMEOUT: Duration = Duration::from_secs(2);
const NONCE: [u8; 16] = [
    0x43, 0x09, 0x67, 0xae, 0x36, 0x4b, 0x1c, 0x45, 0x26, 0x62, 0x37, 0x7a, 0xbf, 0x3f, 0xe9, 0x1d,
];
const MORNING: [u8; 16] = [
    0x30, 0x71, 0x19, 0x22, 0xe8, 0x41, 0x9d, 0x70, 0x4f, 0x29, 0x64, 0x35, 0x56, 0x18, 0xc3, 0x8a,
];
const RESPONSE: &[u8] =
    b"HTTP/1.1 200 Connection Established\r\nRP-Prohibit: 0\r\n\r\nSIMULATED-CTRL-PAYLOAD";

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

fn receive_bounded_header(stream: &mut TcpStream) -> std::io::Result<([u8; 512], usize)> {
    let mut request = [0_u8; 512];
    let mut cursor = 0;
    loop {
        let read = stream.read(&mut request[cursor..])?;
        if read == 0 {
            return Ok((request, cursor));
        }
        cursor += read;
        if request[..cursor]
            .windows(4)
            .any(|window| window == b"\r\n\r\n")
            || cursor == request.len()
        {
            return Ok((request, cursor));
        }
    }
}

fn read_bounded_response(stream: &mut TcpStream, output: &mut [u8]) -> std::io::Result<usize> {
    let mut cursor = 0;
    loop {
        let read = stream.read(&mut output[cursor..])?;
        if read == 0 {
            return Ok(cursor);
        }
        cursor += read;
        if cursor == output.len() {
            return Ok(cursor);
        }
    }
}

#[test]
fn simulated_tcp_peer_completes_the_control_handshake_on_loopback() {
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).expect("bind loopback simulator");
    let address = listener.local_addr().expect("read simulator address");
    let peer = thread::spawn(move || {
        let (mut stream, source) = listener.accept().expect("accept simulated client");
        assert!(source.ip().is_loopback());
        stream
            .set_read_timeout(Some(IO_TIMEOUT))
            .expect("bound peer read");
        stream
            .set_write_timeout(Some(IO_TIMEOUT))
            .expect("bound peer write");
        let (request, request_len) =
            receive_bounded_header(&mut stream).expect("receive bounded ctrl request");
        let request = &request[..request_len];
        assert!(request.starts_with(b"GET /sie/ps4/rp/sess/ctrl HTTP/1.1\r\n"));
        assert!(
            request
                .windows(18)
                .any(|line| line == b"RP-Version: 10.0\r\n")
        );
        assert!(request.ends_with(b"\r\n\r\n"));
        stream.write_all(RESPONSE).expect("send simulated response");
    });

    let mut morning_source = Zeroizing::new(MORNING);
    let mut rpcrypt = SessionRpcrypt::new(
        SessionTarget::Ps4Modern,
        SessionNonce::from_session_response_bytes(NONCE),
        SessionMorning::take_from_registration_buffer(&mut morning_source),
    );
    let mut rng = PatternRng;
    let mut handshake = rpcrypt
        .begin_control(ControlProfile::Ps4_10, &mut rng)
        .expect("start simulated ctrl handshake");
    let mut registration_source = Zeroizing::new([0x5a; 16]);
    let registration_key =
        ControlRegistrationKey::take_from_registration_buffer(&mut registration_source);
    let mut request = [0_u8; 512];
    let request_len = handshake
        .write_request(
            ControlEndpoint::local_ipv4(Ipv4Addr::LOCALHOST),
            &registration_key,
            SessionCodec::Avc,
            &mut request,
        )
        .expect("prepare ctrl request")
        .get();
    assert_eq!(request_len, 354);
    assert_eq!(*registration_source, [0; 16]);

    let mut stream = TcpStream::connect(address).expect("connect to loopback simulator");
    stream
        .set_read_timeout(Some(IO_TIMEOUT))
        .expect("bound client read");
    stream
        .set_write_timeout(Some(IO_TIMEOUT))
        .expect("bound client write");
    stream
        .write_all(&request[..request_len])
        .expect("send ctrl request");
    stream
        .shutdown(Shutdown::Write)
        .expect("finish simulated request");
    let mut response = [0_u8; 512];
    let response_len =
        read_bounded_response(&mut stream, &mut response).expect("receive ctrl response");
    let (parsed, header_len) = handshake
        .finish(&response[..response_len])
        .expect("finish simulated ctrl response");

    assert_eq!(parsed.server_type, None);
    assert!(!parsed.prohibit);
    assert_eq!(
        &response[header_len.get()..response_len],
        b"SIMULATED-CTRL-PAYLOAD"
    );
    peer.join().expect("simulated peer thread");
}
