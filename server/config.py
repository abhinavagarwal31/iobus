"""
Server configuration.

Responsibilities:
- Define default values for ports, timeouts, and tuning parameters
- Load overrides from environment variables or config file
- Provide a single Config object used throughout the server
"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass, field

from protocol.constants import (
    DEFAULT_TCP_PORT,
    DEFAULT_UDP_PORT,
    KEEPALIVE_INTERVAL_SECONDS,
    KEEPALIVE_TIMEOUT_MULTIPLIER,
)

logger = logging.getLogger(__name__)


def _env_int(name: str, default: int) -> int:
    """Read an integer from an environment variable, falling back to *default*."""
    raw = os.environ.get(name)
    if raw is None:
        return default
    try:
        return int(raw)
    except ValueError:
        logger.warning("Invalid integer for %s=%r, using default %d", name, raw, default)
        return default


@dataclass(frozen=True, slots=True)
class ServerConfig:
    """Immutable server configuration.

    Values are resolved once at startup.  Precedence:
      1. Explicit constructor arguments
      2. Environment variables (IOBUS_*)
      3. Protocol-level defaults
    """

    # Network
    tcp_port: int = field(default_factory=lambda: _env_int("IOBUS_TCP_PORT", DEFAULT_TCP_PORT))
    udp_port: int = field(default_factory=lambda: _env_int("IOBUS_UDP_PORT", DEFAULT_UDP_PORT))
    bind_address: str = field(
        default_factory=lambda: os.environ.get("IOBUS_BIND_ADDRESS", "0.0.0.0")
    )

    # Keepalive
    keepalive_interval: int = field(
        default_factory=lambda: _env_int(
            "IOBUS_KEEPALIVE_INTERVAL", KEEPALIVE_INTERVAL_SECONDS
        )
    )
    keepalive_timeout_multiplier: int = field(
        default_factory=lambda: _env_int(
            "IOBUS_KEEPALIVE_TIMEOUT_MULT", KEEPALIVE_TIMEOUT_MULTIPLIER
        )
    )

    # Logging
    log_level: str = field(
        default_factory=lambda: os.environ.get("IOBUS_LOG_LEVEL", "INFO").upper()
    )

    # Derived -------------------------------------------------------------------

    @property
    def keepalive_timeout(self) -> int:
        """Seconds of missed pongs before a client is considered dead."""
        return self.keepalive_interval * self.keepalive_timeout_multiplier

    def summary(self) -> str:
        """Human-readable summary for startup logging."""
        return (
            f"TCP={self.bind_address}:{self.tcp_port}  "
            f"UDP={self.bind_address}:{self.udp_port}  "
            f"keepalive={self.keepalive_interval}s  "
            f"timeout={self.keepalive_timeout}s  "
            f"log={self.log_level}"
        )
