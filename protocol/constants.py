"""
Protocol constants.

Shared values used by both client and server.
These are the canonical definitions — the Android client mirrors them.
"""

# Protocol version
PROTOCOL_VERSION: int = 1

# Default network ports
DEFAULT_TCP_PORT: int = 9800
DEFAULT_UDP_PORT: int = 9801

# Keepalive configuration
KEEPALIVE_INTERVAL_SECONDS: int = 5
KEEPALIVE_TIMEOUT_MULTIPLIER: int = 3  # Disconnect after 3× missed pongs

# Limits
CLIENT_NAME_MAX_LENGTH: int = 32  # bytes, UTF-8, null-padded
