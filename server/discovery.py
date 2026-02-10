"""
Server discovery (mDNS / Bonjour).

Responsibilities:
- (v1) Print server IP and port for manual entry
- (Future) Register via mDNS for automatic client discovery
"""

from __future__ import annotations

import logging
import socket

logger = logging.getLogger(__name__)


def get_local_ip() -> str:
    """Best-effort detection of the machine's LAN IP address.

    Opens a UDP socket to a non-routable address to determine which
    network interface the OS would choose for LAN traffic.  No data
    is actually sent.
    """
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("10.255.255.255", 1))
            return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"


def print_connection_info(tcp_port: int, udp_port: int) -> None:
    """Print connection details for the user to enter on the Android client."""
    ip = get_local_ip()
    logger.info(
        "\n"
        "╔══════════════════════════════════════════════╗\n"
        "║            SERVER READY                      ║\n"
        "╠══════════════════════════════════════════════╣\n"
        "║  IP Address : %-28s  ║\n"
        "║  TCP Port   : %-28d  ║\n"
        "║  UDP Port   : %-28d  ║\n"
        "╠══════════════════════════════════════════════╣\n"
        "║  Enter the IP and TCP port in the app.      ║\n"
        "║  Make sure both devices are on the same      ║\n"
        "║  Wi-Fi network or hotspot.                   ║\n"
        "╚══════════════════════════════════════════════╝",
        ip, tcp_port, udp_port,
    )
