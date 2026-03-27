"""
Protocol constants.

Shared values used by both client and server.
These are the canonical definitions — the Android client mirrors them.
"""

# Protocol version
PROTOCOL_VERSION: int = 3  # v1.7.0: Added lock/activity status tracking

# Default network ports
DEFAULT_TCP_PORT: int = 9800
DEFAULT_UDP_PORT: int = 9801

# Keepalive configuration
KEEPALIVE_INTERVAL_SECONDS: int = 10  # Ping every 10s (increased from 5s)
KEEPALIVE_TIMEOUT_MULTIPLIER: int = 12  # Disconnect after 120s (12× missed pongs)

# Limits
CLIENT_NAME_MAX_LENGTH: int = 32  # bytes, UTF-8, null-padded
HEADER_SIZE: int = 4
MAX_PAYLOAD_SIZE: int = 512

# Handshake rejection reason codes
REJECT_VERSION_MISMATCH: int = 1
REJECT_BUSY: int = 2
REJECT_AUTH_REQUIRED: int = 3  # Server requires PIN authentication

# String payload max lengths
APP_NAME_MAX_LENGTH: int = 128
ERROR_MESSAGE_MAX_LENGTH: int = 256

# PIN Authentication (v1.6.0)
PIN_LENGTH: int = 6  # Digits only: 000000-999999
PIN_SALT_SIZE: int = 16  # bytes
PIN_CHALLENGE_SIZE: int = 4  # bytes
PIN_HASH_SIZE: int = 32  # bytes (SHA-256 output)
SESSION_TOKEN_SIZE: int = 16  # bytes (reserved for future use)

# Rate Limiting
MAX_AUTH_ATTEMPTS_PER_IP: int = 5
AUTH_LOCKOUT_DURATION_SECONDS: int = 300  # 5 minutes
