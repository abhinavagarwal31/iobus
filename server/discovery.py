"""
Server discovery (mDNS / Bonjour).

Responsibilities:
- Print server IP and port for manual entry
- (v1.6.0) Register via mDNS for automatic client discovery
"""

from __future__ import annotations

import logging
import socket
from typing import Optional

try:
    from zeroconf import ServiceInfo, Zeroconf
    MDNS_AVAILABLE = True
except ImportError:
    MDNS_AVAILABLE = False

from protocol.constants import PROTOCOL_VERSION

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


class MdnsAdvertiser:
    """
    Advertises IOBus server via mDNS for automatic discovery (v1.6.0).

    Service Type: _iobus._tcp.local.
    Service Name: {hostname}._iobus._tcp.local.

    Properties:
        - version: Protocol version (e.g. "2")
        - auth: Authentication requirement ("pin" or "none")
        - hostname: Mac hostname
    """

    def __init__(
        self,
        port: int,
        hostname: str | None = None,
        auth_enabled: bool = True,
    ):
        """
        Initialize mDNS advertiser.

        Args:
            port: TCP port number (e.g. 9800)
            hostname: Server hostname. If None, uses system hostname.
            auth_enabled: Whether PIN authentication is required
        """
        if not MDNS_AVAILABLE:
            logger.warning(
                "Zeroconf library not installed. mDNS discovery unavailable. "
                "Install with: pip install zeroconf"
            )
            self.zeroconf: Optional[Zeroconf] = None
            self.service_info: Optional[ServiceInfo] = None
            return

        self.port = port
        self.hostname = hostname or socket.gethostname()
        self.auth_enabled = auth_enabled

        # Clean hostname for mDNS (remove .local if present)
        if self.hostname.endswith(".local"):
            self.hostname = self.hostname[:-6]

        self.zeroconf: Optional[Zeroconf] = None
        self.service_info: Optional[ServiceInfo] = None

    def start(self) -> bool:
        """
        Start advertising the service.

        Returns:
            True if started successfully, False otherwise
        """
        if not MDNS_AVAILABLE:
            return False

        try:
            local_ip = get_local_ip()

            # Service type: _iobus._tcp.local.
            service_type = "_iobus._tcp.local."

            # Service name: {hostname}._iobus._tcp.local.
            service_name = f"{self.hostname}.{service_type}"

            # Service properties
            properties = {
                "version": str(PROTOCOL_VERSION),
                "auth": "pin" if self.auth_enabled else "none",
                "hostname": self.hostname,
            }

            # Create service info
            self.service_info = ServiceInfo(
                type_=service_type,
                name=service_name,
                addresses=[socket.inet_aton(local_ip)],
                port=self.port,
                properties=properties,
                server=f"{self.hostname}.local.",
            )

            # Register service
            self.zeroconf = Zeroconf()
            self.zeroconf.register_service(self.service_info)

            logger.info(
                f"mDNS service advertised: {service_name} at {local_ip}:{self.port} "
                f"(auth={properties['auth']})"
            )
            return True

        except Exception as e:
            logger.error(f"Failed to start mDNS advertisement: {e}")
            return False

    def stop(self) -> None:
        """Stop advertising the service"""
        if not MDNS_AVAILABLE or not self.zeroconf:
            return

        try:
            if self.service_info:
                self.zeroconf.unregister_service(self.service_info)
                logger.info("mDNS service unregistered")
            self.zeroconf.close()
        except Exception as e:
            logger.error(f"Error stopping mDNS advertisement: {e}")
        finally:
            self.zeroconf = None
            self.service_info = None

    def __enter__(self):
        self.start()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.stop()

