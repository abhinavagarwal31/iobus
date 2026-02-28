package com.iobus.client.protocol

/**
 * Protocol constants — mirrors protocol/constants.py.
 *
 * These values MUST stay in sync with the server definitions.
 */
object Constants {
    const val PROTOCOL_VERSION: Int = 1

    const val DEFAULT_TCP_PORT: Int = 9800
    const val DEFAULT_UDP_PORT: Int = 9801

    const val KEEPALIVE_INTERVAL_SECONDS: Int = 5
    const val KEEPALIVE_TIMEOUT_MULTIPLIER: Int = 3

    const val MAX_PAYLOAD_SIZE: Int = 512
    const val CLIENT_NAME_MAX_LENGTH: Int = 32
    const val HEADER_SIZE: Int = 4

    // Handshake rejection reason codes
    const val REJECT_VERSION_MISMATCH: Int = 1
    const val REJECT_BUSY: Int = 2

    // String payload max lengths
    const val APP_NAME_MAX_LENGTH: Int = 128
    const val ERROR_MESSAGE_MAX_LENGTH: Int = 256
}
