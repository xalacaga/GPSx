#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
"""Bounded synthetic console for the Android virtual-LAN integration test."""

from __future__ import annotations

import argparse
import socket


PS4_SEARCH = (
    b"SRCH * HTTP/1.1\n"
    b"device-discovery-protocol-version:00020020\n\0"
)
DISCOVERY_RESPONSE = (
    b"HTTP/1.1 200 OK\r\n"
    b"host-request-port:19295\r\n"
    b"host-type:PS4\r\n"
    b"\r\n"
)
CONTROL_RESPONSE = (
    b"HTTP/1.1 200 Connection Established\r\n"
    b"RP-Prohibit: 0\r\n"
    b"\r\n"
    b"SIMULATED-ANDROID-CONSOLE"
)
MAX_DISCOVERY_REQUEST = 128
MAX_CONTROL_REQUEST = 512


def receive_control_request(connection: socket.socket) -> bytes:
    value = bytearray()
    while len(value) < MAX_CONTROL_REQUEST:
        chunk = connection.recv(MAX_CONTROL_REQUEST - len(value))
        if not chunk:
            break
        value.extend(chunk)
        if b"\r\n\r\n" in value:
            break
    return bytes(value)


def drain_request(connection: socket.socket) -> None:
    while connection.recv(1):
        pass


def run(bind: str, discovery_port: int, control_port: int) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as discovery:
        discovery.settimeout(30)
        discovery.bind((bind, discovery_port))
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as control:
            control.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            control.settimeout(30)
            control.bind((bind, control_port))
            control.listen(1)
            print("GPSX_ANDROID_FAKE_CONSOLE_READY", flush=True)

            request, peer = discovery.recvfrom(MAX_DISCOVERY_REQUEST)
            if request != PS4_SEARCH:
                raise RuntimeError("unexpected bounded discovery request")
            discovery.sendto(DISCOVERY_RESPONSE, peer)

            connection, _ = control.accept()
            with connection:
                connection.settimeout(5)
                request = receive_control_request(connection)
                if not request.startswith(b"GET /sie/ps4/rp/sess/ctrl HTTP/1.1\r\n"):
                    raise RuntimeError("unexpected bounded ctrl request line")
                if b"RP-Version: 10.0\r\n" not in request:
                    raise RuntimeError("missing bounded ctrl version")
                if not request.endswith(b"\r\n\r\n"):
                    raise RuntimeError("incomplete bounded ctrl request")
                connection.sendall(CONTROL_RESPONSE)
                # Ctrl is persistent: keep the connection open until the client
                # has framed the header plus first ctrl message and closes.
                drain_request(connection)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--discovery-port", type=int, default=19_878)
    parser.add_argument("--control-port", type=int, default=19_295)
    args = parser.parse_args()
    run(args.bind, args.discovery_port, args.control_port)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
