package com.iobus.client.network

/**
 * Connection state machine.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    CONNECTED,
    RECONNECTING,
    ERROR,
}
