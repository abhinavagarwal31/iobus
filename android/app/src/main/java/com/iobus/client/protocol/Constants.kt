package com.iobus.client.protocol

/**
 * Protocol constants — mirrors protocol/constants.py.
 *
 * These values MUST stay in sync with the server definitions.
 */
object Constants {
    const val PROTOCOL_VERSION: Int = 5  // v1.9.0: Added AC-power/no-battery detection

    const val DEFAULT_TCP_PORT: Int = 9800
    const val DEFAULT_UDP_PORT: Int = 9801

    const val KEEPALIVE_INTERVAL_SECONDS: Int = 10  // v1.6.0: Increased from 5s
    const val KEEPALIVE_TIMEOUT_MULTIPLIER: Int = 12  // v1.6.0: 120s grace period (was 15s)

    const val MAX_PAYLOAD_SIZE: Int = 512
    const val CLIENT_NAME_MAX_LENGTH: Int = 32
    const val HEADER_SIZE: Int = 4

    // Handshake rejection reason codes
    const val REJECT_VERSION_MISMATCH: Int = 1
    const val REJECT_BUSY: Int = 2
    const val REJECT_AUTH_REQUIRED: Int = 3  // v1.6.0: Auth required but not provided

    // Authentication (v1.6.0)
    const val PIN_LENGTH: Int = 6
    const val PIN_SALT_SIZE: Int = 16
    const val PIN_CHALLENGE_SIZE: Int = 4
    const val PIN_HASH_SIZE: Int = 32  // SHA-256
    const val SESSION_TOKEN_SIZE: Int = 16
    const val MAX_AUTH_ATTEMPTS_PER_IP: Int = 5
    const val AUTH_LOCKOUT_DURATION_SECONDS: Int = 300  // 5 minutes

    // String payload max lengths
    const val APP_NAME_MAX_LENGTH: Int = 128
    const val ERROR_MESSAGE_MAX_LENGTH: Int = 256
}
