// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
#![forbid(unsafe_code)]

//! Bounded platform transport adapters for GPSx.
//!
//! This slice performs SRCH discovery over IPv4 UDP only. It has no wake-up,
//! registration, authentication, credentials, session, media, JNI, or GP520 code.

use std::fmt;
use std::io;
use std::net::{Ipv4Addr, SocketAddr, SocketAddrV4, UdpSocket};
use std::time::{Duration, Instant};

use gpsx_protocol::{
    ConsoleGeneration, DiscoveryHost, DiscoveryState, PS4_DISCOVERY_PORT, PS5_DISCOVERY_PORT,
    format_search_request, parse_search_response,
};

pub const DEFAULT_RESPONSE_WINDOW: Duration = Duration::from_millis(750);
pub const MAX_RESPONSE_WINDOW: Duration = Duration::from_secs(5);
pub const MAX_DISCOVERED_CONSOLES: usize = 16;
const MAX_UDP_DATAGRAM_BYTES: usize = 65_535;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DiscoveryOperation {
    Bind,
    InspectLocalAddress,
    ConfigureBroadcast,
    ConfigureTimeout,
    Send,
    Receive,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DiscoveryError {
    InvalidResponseWindow,
    Io {
        operation: DiscoveryOperation,
        kind: io::ErrorKind,
    },
}

impl fmt::Display for DiscoveryError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidResponseWindow => formatter.write_str(
                "discovery response window must be greater than zero and at most five seconds",
            ),
            Self::Io { operation, kind } => {
                write!(formatter, "discovery {operation:?} failed: {kind:?}")
            }
        }
    }
}

impl std::error::Error for DiscoveryError {}

#[derive(Clone, Copy, Eq, PartialEq)]
pub struct DiscoveredConsole {
    generation: ConsoleGeneration,
    peer: SocketAddr,
    state: DiscoveryState,
    status_code: u16,
    host_request_port: Option<u16>,
}

impl DiscoveredConsole {
    #[must_use]
    pub const fn generation(&self) -> ConsoleGeneration {
        self.generation
    }

    #[must_use]
    pub const fn peer(&self) -> SocketAddr {
        self.peer
    }

    #[must_use]
    pub const fn state(&self) -> DiscoveryState {
        self.state
    }

    #[must_use]
    pub const fn status_code(&self) -> u16 {
        self.status_code
    }

    #[must_use]
    pub const fn host_request_port(&self) -> Option<u16> {
        self.host_request_port
    }
}

impl fmt::Debug for DiscoveredConsole {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("DiscoveredConsole")
            .field("generation", &self.generation)
            .field("peer", &"[redacted]")
            .field("state", &self.state)
            .field("status_code", &self.status_code)
            .field("host_request_port", &self.host_request_port)
            .finish()
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct DiscoveryReport {
    consoles: Vec<DiscoveredConsole>,
    local_port: u16,
    received_datagrams: usize,
    rejected_datagrams: usize,
    duplicate_responses: usize,
    unexpected_source_ports: usize,
    icmp_errors: usize,
    capacity_drops: usize,
}

impl DiscoveryReport {
    fn new(local_port: u16) -> Self {
        Self {
            consoles: Vec::with_capacity(MAX_DISCOVERED_CONSOLES),
            local_port,
            received_datagrams: 0,
            rejected_datagrams: 0,
            duplicate_responses: 0,
            unexpected_source_ports: 0,
            icmp_errors: 0,
            capacity_drops: 0,
        }
    }

    #[must_use]
    pub const fn local_port(&self) -> u16 {
        self.local_port
    }

    #[must_use]
    pub fn consoles(&self) -> &[DiscoveredConsole] {
        &self.consoles
    }

    #[must_use]
    pub const fn received_datagrams(&self) -> usize {
        self.received_datagrams
    }

    #[must_use]
    pub const fn rejected_datagrams(&self) -> usize {
        self.rejected_datagrams
    }

    #[must_use]
    pub const fn duplicate_responses(&self) -> usize {
        self.duplicate_responses
    }

    #[must_use]
    pub const fn unexpected_source_ports(&self) -> usize {
        self.unexpected_source_ports
    }

    #[must_use]
    pub const fn icmp_errors(&self) -> usize {
        self.icmp_errors
    }

    #[must_use]
    pub const fn capacity_drops(&self) -> usize {
        self.capacity_drops
    }

    fn record(
        &mut self,
        generation: ConsoleGeneration,
        peer: SocketAddr,
        host: &DiscoveryHost<'_>,
    ) {
        if peer.port() != discovery_port(generation) {
            self.unexpected_source_ports += 1;
        }
        if self
            .consoles
            .iter()
            .any(|known| known.generation == generation && known.peer.ip() == peer.ip())
        {
            self.duplicate_responses += 1;
            return;
        }
        if self.consoles.len() == MAX_DISCOVERED_CONSOLES {
            self.capacity_drops += 1;
            return;
        }
        self.consoles.push(DiscoveredConsole {
            generation,
            peer,
            state: host.state(),
            status_code: host.status_code(),
            host_request_port: host.host_request_port(),
        });
    }
}

/// Discovers one console generation at an explicit IPv4 UDP destination.
///
/// Exactly one bounded SRCH request is sent. Valid responses are collected until
/// `response_window` expires. The first 16 unique source IP addresses are kept;
/// later unique responses are counted in `capacity_drops`. Malformed and
/// oversized datagrams are counted without exposing their contents. ICMP reset
/// and refusal errors are counted and do not discard the partial report. Results
/// are unauthenticated hints and must not authorize registration or a session.
pub fn discover(
    generation: ConsoleGeneration,
    destination: SocketAddrV4,
    response_window: Duration,
) -> Result<DiscoveryReport, DiscoveryError> {
    if response_window.is_zero() || response_window > MAX_RESPONSE_WINDOW {
        return Err(DiscoveryError::InvalidResponseWindow);
    }

    let socket = UdpSocket::bind(SocketAddrV4::new(Ipv4Addr::UNSPECIFIED, 0))
        .map_err(|error| io_error(DiscoveryOperation::Bind, error))?;
    let local_port = socket
        .local_addr()
        .map_err(|error| io_error(DiscoveryOperation::InspectLocalAddress, error))?
        .port();
    socket
        .set_broadcast(true)
        .map_err(|error| io_error(DiscoveryOperation::ConfigureBroadcast, error))?;

    let request = format_search_request(generation);
    let sent = socket
        .send_to(request, destination)
        .map_err(|error| io_error(DiscoveryOperation::Send, error))?;
    if sent != request.len() {
        return Err(DiscoveryError::Io {
            operation: DiscoveryOperation::Send,
            kind: io::ErrorKind::WriteZero,
        });
    }

    let deadline = Instant::now() + response_window;
    let mut report = DiscoveryReport::new(local_port);
    // This buffer covers every ordinary IPv4 UDP payload and prevents Windows
    // `recvfrom` from returning WSAEMSGSIZE before the parser can classify it.
    let mut datagram = vec![0_u8; MAX_UDP_DATAGRAM_BYTES];
    loop {
        let now = Instant::now();
        if now >= deadline {
            break;
        }
        let remaining = deadline.saturating_duration_since(now);
        socket
            .set_read_timeout(Some(remaining.max(Duration::from_millis(1))))
            .map_err(|error| io_error(DiscoveryOperation::ConfigureTimeout, error))?;

        match socket.recv_from(&mut datagram) {
            Ok((length, peer)) => {
                report.received_datagrams += 1;
                match parse_search_response(&datagram[..length]) {
                    Ok(host) => report.record(generation, peer, &host),
                    Err(_) => report.rejected_datagrams += 1,
                }
            }
            Err(error) => match handle_receive_error(&mut report, error)? {
                ReceiveAction::Continue => continue,
                ReceiveAction::Stop => break,
            },
        }
    }
    Ok(report)
}

/// Discovers one console generation through the IPv4 limited-broadcast address.
pub fn discover_broadcast(
    generation: ConsoleGeneration,
    response_window: Duration,
) -> Result<DiscoveryReport, DiscoveryError> {
    let port = discovery_port(generation);
    discover(
        generation,
        SocketAddrV4::new(Ipv4Addr::BROADCAST, port),
        response_window,
    )
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum ReceiveAction {
    Continue,
    Stop,
}

fn handle_receive_error(
    report: &mut DiscoveryReport,
    error: io::Error,
) -> Result<ReceiveAction, DiscoveryError> {
    match error.kind() {
        io::ErrorKind::WouldBlock | io::ErrorKind::TimedOut => Ok(ReceiveAction::Stop),
        io::ErrorKind::ConnectionReset | io::ErrorKind::ConnectionRefused => {
            report.icmp_errors += 1;
            Ok(ReceiveAction::Continue)
        }
        _ => Err(io_error(DiscoveryOperation::Receive, error)),
    }
}

const fn discovery_port(generation: ConsoleGeneration) -> u16 {
    match generation {
        ConsoleGeneration::Ps4 => PS4_DISCOVERY_PORT,
        ConsoleGeneration::Ps5 => PS5_DISCOVERY_PORT,
    }
}

fn io_error(operation: DiscoveryOperation, error: io::Error) -> DiscoveryError {
    DiscoveryError::Io {
        operation,
        kind: error.kind(),
    }
}

#[cfg(test)]
mod tests {
    use std::net::{Ipv4Addr, SocketAddr, SocketAddrV4, UdpSocket};
    use std::thread;
    use std::time::{Duration, Instant};

    use gpsx_protocol::{ConsoleGeneration, DiscoveryState, format_search_request};

    use super::{
        DiscoveryError, DiscoveryReport, MAX_DISCOVERED_CONSOLES, MAX_RESPONSE_WINDOW,
        ReceiveAction, discover, handle_receive_error,
    };

    const READY_RESPONSE: &[u8] = b"HTTP/1.1 200 OK\r\nhost-request-port:9295\r\n\r\n";
    const OVERSIZED_RESPONSE: &[u8; gpsx_protocol::MAX_DATAGRAM_SIZE + 1] =
        &[b'x'; gpsx_protocol::MAX_DATAGRAM_SIZE + 1];

    fn responder(responses: Vec<&'static [u8]>) -> (SocketAddrV4, thread::JoinHandle<Vec<u8>>) {
        let socket = UdpSocket::bind((Ipv4Addr::LOCALHOST, 0)).expect("bind responder");
        let address = match socket.local_addr().expect("responder address") {
            SocketAddr::V4(address) => address,
            SocketAddr::V6(_) => panic!("IPv4 bind returned IPv6"),
        };
        let handle = thread::spawn(move || {
            let mut request = [0_u8; 128];
            let (length, peer) = socket.recv_from(&mut request).expect("receive request");
            for response in responses {
                socket.send_to(response, peer).expect("send response");
            }
            request[..length].to_vec()
        });
        (address, handle)
    }

    #[test]
    fn loopback_discovery_sends_exact_request_and_collects_response() {
        let (target, handle) = responder(vec![READY_RESPONSE]);
        let report = discover(ConsoleGeneration::Ps5, target, Duration::from_millis(500))
            .expect("loopback discovery");
        assert_eq!(
            handle.join().expect("responder thread"),
            format_search_request(ConsoleGeneration::Ps5)
        );
        assert_eq!(report.received_datagrams(), 1);
        assert_eq!(report.rejected_datagrams(), 0);
        assert_ne!(report.local_port(), 0);
        assert_eq!(report.unexpected_source_ports(), 1);
        assert_eq!(report.icmp_errors(), 0);
        assert_eq!(report.consoles().len(), 1);
        assert_eq!(report.consoles()[0].generation(), ConsoleGeneration::Ps5);
        assert_eq!(report.consoles()[0].state(), DiscoveryState::Ready);
        assert_eq!(report.consoles()[0].host_request_port(), Some(9_295));
    }

    #[test]
    fn malformed_response_is_counted_without_stopping_collection() {
        let (target, handle) = responder(vec![b"not-http", READY_RESPONSE]);
        let report = discover(ConsoleGeneration::Ps4, target, Duration::from_millis(500))
            .expect("loopback discovery");
        handle.join().expect("responder thread");
        assert_eq!(report.received_datagrams(), 2);
        assert_eq!(report.rejected_datagrams(), 1);
        assert_eq!(report.consoles().len(), 1);
    }

    #[test]
    fn oversized_response_is_observed_rejected_and_does_not_stop_collection() {
        let (target, handle) = responder(vec![OVERSIZED_RESPONSE, READY_RESPONSE]);
        let report = discover(ConsoleGeneration::Ps5, target, Duration::from_millis(500))
            .expect("loopback discovery");
        handle.join().expect("responder thread");
        assert_eq!(report.received_datagrams(), 2);
        assert_eq!(report.rejected_datagrams(), 1);
        assert_eq!(report.consoles().len(), 1);
    }

    #[test]
    fn duplicate_responses_are_coalesced_by_generation_and_source_ip() {
        let (target, handle) = responder(vec![READY_RESPONSE, READY_RESPONSE]);
        let report = discover(ConsoleGeneration::Ps5, target, Duration::from_millis(500))
            .expect("loopback discovery");
        handle.join().expect("responder thread");
        assert_eq!(report.consoles().len(), 1);
        assert_eq!(report.duplicate_responses(), 1);
    }

    #[test]
    fn capacity_keeps_first_sixteen_unique_addresses_and_counts_drops() {
        let host = gpsx_protocol::parse_search_response(READY_RESPONSE).expect("valid response");
        let mut report = DiscoveryReport::new(49_152);
        for last_octet in 1..=(MAX_DISCOVERED_CONSOLES as u8 + 1) {
            report.record(
                ConsoleGeneration::Ps5,
                SocketAddr::V4(SocketAddrV4::new(
                    Ipv4Addr::new(127, 0, 0, last_octet),
                    9_302,
                )),
                &host,
            );
        }
        assert_eq!(report.consoles().len(), MAX_DISCOVERED_CONSOLES);
        assert_eq!(report.capacity_drops(), 1);
    }

    #[test]
    fn unexpected_source_ports_are_counted_without_rejecting_hints() {
        let host = gpsx_protocol::parse_search_response(READY_RESPONSE).expect("valid response");
        let mut report = DiscoveryReport::new(49_152);
        report.record(
            ConsoleGeneration::Ps5,
            "127.0.0.1:9302".parse().expect("expected source port"),
            &host,
        );
        report.record(
            ConsoleGeneration::Ps5,
            "127.0.0.2:49152".parse().expect("unexpected source port"),
            &host,
        );
        assert_eq!(report.consoles().len(), 2);
        assert_eq!(report.unexpected_source_ports(), 1);
    }

    #[test]
    fn injected_icmp_error_preserves_results_and_collection_continues() {
        let host = gpsx_protocol::parse_search_response(READY_RESPONSE).expect("valid response");
        let mut report = DiscoveryReport::new(49_152);
        report.record(
            ConsoleGeneration::Ps5,
            "127.0.0.1:9302".parse().expect("first address"),
            &host,
        );
        assert_eq!(
            handle_receive_error(
                &mut report,
                std::io::Error::from(std::io::ErrorKind::ConnectionReset),
            ),
            Ok(ReceiveAction::Continue)
        );
        report.record(
            ConsoleGeneration::Ps5,
            "127.0.0.2:9302".parse().expect("second address"),
            &host,
        );
        assert_eq!(report.icmp_errors(), 1);
        assert_eq!(report.consoles().len(), 2);
    }

    #[test]
    fn connection_refused_is_non_fatal_for_portability() {
        let mut report = DiscoveryReport::new(49_152);
        assert_eq!(
            handle_receive_error(
                &mut report,
                std::io::Error::from(std::io::ErrorKind::ConnectionRefused),
            ),
            Ok(ReceiveAction::Continue)
        );
        assert_eq!(report.icmp_errors(), 1);
    }

    #[cfg(windows)]
    #[test]
    fn closed_loopback_port_returns_report_and_counts_windows_icmp_reset() {
        // Keep this address distinct from responder(), whose sockets bind to
        // 127.0.0.1, so parallel tests cannot reuse this just-released port.
        let closed_loopback = Ipv4Addr::new(127, 0, 0, 254);
        let closed_socket = UdpSocket::bind((closed_loopback, 0))
            .expect("reserve closed target port on isolated loopback address");
        let target = match closed_socket.local_addr().expect("closed target address") {
            SocketAddr::V4(address) => address,
            SocketAddr::V6(_) => panic!("IPv4 bind returned IPv6"),
        };
        drop(closed_socket);

        let report = discover(ConsoleGeneration::Ps5, target, Duration::from_millis(500))
            .expect("ICMP reset is counted instead of aborting discovery");
        assert!(report.consoles().is_empty());
        assert!(report.icmp_errors() >= 1);
    }

    #[test]
    fn response_window_is_bounded_before_network_io() {
        for invalid in [
            Duration::ZERO,
            MAX_RESPONSE_WINDOW + Duration::from_nanos(1),
        ] {
            assert_eq!(
                discover(
                    ConsoleGeneration::Ps5,
                    SocketAddrV4::new(Ipv4Addr::LOCALHOST, 9),
                    invalid,
                ),
                Err(DiscoveryError::InvalidResponseWindow)
            );
        }
    }

    #[test]
    fn silent_target_returns_an_empty_report_within_the_window() {
        let target_socket = UdpSocket::bind((Ipv4Addr::LOCALHOST, 0)).expect("bind silent target");
        let target = match target_socket.local_addr().expect("target address") {
            SocketAddr::V4(address) => address,
            SocketAddr::V6(_) => panic!("IPv4 bind returned IPv6"),
        };
        let started = Instant::now();
        let report = discover(ConsoleGeneration::Ps5, target, Duration::from_millis(40))
            .expect("timeout is not an error");
        assert!(report.consoles().is_empty());
        assert!(started.elapsed() < Duration::from_secs(1));
    }

    #[test]
    fn debug_output_redacts_peer_address() {
        let host = gpsx_protocol::parse_search_response(READY_RESPONSE).expect("valid response");
        let mut report = DiscoveryReport::new(49_152);
        report.record(
            ConsoleGeneration::Ps5,
            "192.0.2.55:9302".parse().expect("test address"),
            &host,
        );
        let rendered = format!("{:?}", report.consoles()[0]);
        assert!(rendered.contains("[redacted]"));
        assert!(!rendered.contains("192.0.2.55"));
    }
}
