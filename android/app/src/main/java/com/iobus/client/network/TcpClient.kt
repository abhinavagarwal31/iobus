package com.iobus.client.network

import com.iobus.client.protocol.Constants
import com.iobus.client.protocol.MessageType
import com.iobus.client.protocol.Messages
import com.iobus.client.protocol.SystemStateData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer

/**
 * TCP control-plane client.
 *
 * Responsibilities:
 *  - Connect to server, send handshake, handle auth (v1.6.0), validate ack
 *  - Run keepalive loop (send PING, expect PONG)
 *  - Send DISCONNECT on graceful close
 *  - Dispatch incoming TCP responses (ACK, ERROR, SYSTEM_STATE_RESPONSE)
 *  - Notify owner of connection events via callbacks
 */
class TcpClient(
    private val host: String,
    private val port: Int = Constants.DEFAULT_TCP_PORT,
    private val deviceName: String = "Android",
    private val pinProvider: suspend (String) -> String?,  // v1.6.0: Get PIN for server
    private val onStateChange: (ConnectionState) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onSystemState: (SystemStateData) -> Unit = {},
    private val onLaunchAck: (Int) -> Unit = {},
    private val onLaunchError: (Int) -> Unit = {},
) {
    companion object {
        private const val SOCKET_BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT_MS = 5_000
    }

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private var readJob: Job? = null
    private var keepaliveJob: Job? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    /** Server-assigned UDP port (from handshake ack). */
    var serverUdpPort: Int = Constants.DEFAULT_UDP_PORT
        private set

    // ------------------------------------------------
    // Public API
    // ------------------------------------------------

    /**
     * Connect to the server over TCP and perform the handshake.
     * Must be called from a coroutine (suspending).
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        if (_state.value == ConnectionState.CONNECTED) return@withContext

        setState(ConnectionState.CONNECTING)
        try {
            val sock = Socket()
            sock.tcpNoDelay = true
            sock.keepAlive = true  // Enable TCP keepalive to prevent OS from closing socket
            sock.soTimeout = 0  // blocking reads handled in coroutine (0 = infinite timeout)
            sock.sendBufferSize = SOCKET_BUFFER_SIZE
            sock.receiveBufferSize = SOCKET_BUFFER_SIZE
            sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

            socket = sock
            outputStream = sock.getOutputStream()
            inputStream = sock.getInputStream()

            setState(ConnectionState.HANDSHAKING)
            performHandshake()
            setState(ConnectionState.CONNECTED)

            // Start read + keepalive loops
            readJob = CoroutineScope(Dispatchers.IO).launch { readLoop() }
            keepaliveJob = CoroutineScope(Dispatchers.IO).launch { keepaliveLoop() }
        } catch (e: IOException) {
            handleError("Connection failed: ${e.message}")
        } catch (e: HandshakeException) {
            handleError("Handshake failed: ${e.message}")
        }
    }

    /**
     * Graceful disconnect — send DISCONNECT message and close.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            sendRaw(Messages.encodeDisconnect())
        } catch (_: IOException) { /* best-effort */ }
        close()
    }

    /**
     * Send a raw TCP message (for LAUNCH_APP, GET_SYSTEM_STATE, etc.).
     */
    fun sendTcp(data: ByteArray) {
        sendRaw(data)
    }

    // ------------------------------------------------
    // Handshake
    // ------------------------------------------------

    private suspend fun performHandshake() {
        val reqBytes = Messages.encodeHandshakeReq(deviceName)
        outputStream!!.write(reqBytes)
        outputStream!!.flush()

        // Read response header (4 bytes) then payload
        val header = readExact(4)
        val version = header[0].toInt() and 0xFF
        val type = header[1].toInt() and 0xFF
        val payloadLen = ByteBuffer.wrap(header, 2, 2).short.toInt() and 0xFFFF

        if (version != Constants.PROTOCOL_VERSION) {
            throw HandshakeException("Protocol version mismatch: got $version, expected ${Constants.PROTOCOL_VERSION}")
        }

        val payload = if (payloadLen > 0) readExact(payloadLen) else ByteArray(0)

        when (type) {
            // HANDSHAKE_ACK = 0x02 (no auth required)
            0x02 -> {
                val ack = Messages.decodeHandshakeAck(payload)
                serverUdpPort = ack.udpPort
            }
            // HANDSHAKE_REJECT = 0x03
            0x03 -> {
                val reason = if (payload.isNotEmpty()) String(payload, Charsets.UTF_8) else "rejected"
                throw HandshakeException(reason)
            }
            // HANDSHAKE_AUTH_REQUIRED = 0x04 (v1.6.0)
            0x04 -> {
                handleAuthChallenge(payload)
            }
            else -> throw HandshakeException("Unexpected response type: 0x${type.toString(16)}")
        }
    }

    private suspend fun handleAuthChallenge(payload: ByteArray) {
        // Decode auth challenge
        val authReq = Messages.decodeHandshakeAuthRequired(payload)

        // Get PIN from provider (e.g., from storage or UI prompt)
        val serverAddress = "$host:$port"
        val pin = pinProvider(serverAddress)
            ?: throw HandshakeException("Authentication required but no PIN available")

        // Compute PIN hash (SHA-256)
        val pinHash = try {
            java.security.MessageDigest.getInstance("SHA-256").apply {
                update(pin.toByteArray(Charsets.UTF_8))
                update(authReq.pinSalt)
                update(authReq.challenge)
            }.digest()
        } catch (e: Exception) {
            throw HandshakeException("Failed to compute PIN hash: ${e.message}")
        }

        // Send auth response
        val authResp = Messages.encodeHandshakeAuthResponse(pinHash)
        outputStream!!.write(authResp)
        outputStream!!.flush()

        // Wait for auth result
        val authHeader = readExact(4)
        val authVersion = authHeader[0].toInt() and 0xFF
        val authType = authHeader[1].toInt() and 0xFF
        val authPayloadLen = ByteBuffer.wrap(authHeader, 2, 2).short.toInt() and 0xFFFF
        val authPayload = if (authPayloadLen > 0) readExact(authPayloadLen) else ByteArray(0)

        when (authType) {
            // HANDSHAKE_AUTH_SUCCESS = 0x06
            0x06 -> {
                val authSuccess = Messages.decodeHandshakeAuthSuccess(authPayload)
                // Store session token (future use for reconnect)
                // Continue to read HANDSHAKE_ACK
                val ackHeader = readExact(4)
                val ackType = ackHeader[1].toInt() and 0xFF
                val ackPayloadLen = ByteBuffer.wrap(ackHeader, 2, 2).short.toInt() and 0xFFFF
                val ackPayload = if (ackPayloadLen > 0) readExact(ackPayloadLen) else ByteArray(0)

                if (ackType == 0x02) {  // HANDSHAKE_ACK
                    val ack = Messages.decodeHandshakeAck(ackPayload)
                    serverUdpPort = ack.udpPort
                } else {
                    throw HandshakeException("Expected HANDSHAKE_ACK after auth success, got 0x${ackType.toString(16)}")
                }
            }
            // HANDSHAKE_AUTH_FAILED = 0x07
            0x07 -> {
                val authFailed = Messages.decodeHandshakeAuthFailed(authPayload)
                throw HandshakeException("Authentication failed (retry after ${authFailed.retryAfter}s)")
            }
            else -> throw HandshakeException("Unexpected auth response type: 0x${authType.toString(16)}")
        }
    }

    // ------------------------------------------------
    // Read loop
    // ------------------------------------------------

    private suspend fun readLoop() {
        try {
            while (isActive()) {
                val header = readExact(4)
                val type = header[1].toInt() and 0xFF
                val payloadLen = ByteBuffer.wrap(header, 2, 2).short.toInt() and 0xFFFF

                val payload = if (payloadLen > 0) readExact(payloadLen) else ByteArray(0)

                when (type) {
                    // PING → respond with PONG
                    MessageType.PING.toInt() and 0xFF -> {
                        try {
                            sendRaw(Messages.encodePong())
                        } catch (e: IOException) {
                            // Can't respond to ping, connection is broken
                            throw e
                        }
                    }
                    // PONG → keepalive ack
                    MessageType.PONG.toInt() and 0xFF -> { }
                    // DISCONNECT → server closing
                    MessageType.DISCONNECT.toInt() and 0xFF -> {
                        close()
                        return
                    }
                    // SYSTEM_STATE_RESPONSE
                    MessageType.SYSTEM_STATE_RESPONSE.toInt() and 0xFF -> {
                        if (payload.size >= 6) {  // brightness(u16) + volume(u16) + flags(u16)
                            val state = Messages.decodeSystemState(payload)
                            onSystemState(state)
                        }
                    }
                    // ACK (launch success)
                    MessageType.ACK.toInt() and 0xFF -> {
                        if (payload.isNotEmpty()) {
                            onLaunchAck(payload[0].toInt() and 0xFF)
                        }
                    }
                    // COMMAND_ERROR (launch failure)
                    MessageType.COMMAND_ERROR.toInt() and 0xFF -> {
                        if (payload.isNotEmpty()) {
                            onLaunchError(payload[0].toInt() and 0xFF)
                        }
                    }
                }
            }
        } catch (e: java.net.SocketException) {
            if (_state.value == ConnectionState.CONNECTED) {
                handleError("Connection lost: ${e.message ?: "Network error"}")
            }
        } catch (e: IOException) {
            if (_state.value == ConnectionState.CONNECTED) {
                handleError("Connection error: ${e.message ?: "IO error"}")
            }
        }
    }

    // ------------------------------------------------
    // Keepalive
    // ------------------------------------------------

    private suspend fun keepaliveLoop() {
        while (isActive()) {
            delay(Constants.KEEPALIVE_INTERVAL_SECONDS * 1000L)

            // Double-check socket is still valid before sending
            if (!isActive()) {
                return
            }

            try {
                sendRaw(Messages.encodePing())
            } catch (e: java.net.SocketException) {
                // Socket-specific error (connection reset, broken pipe, etc.)
                handleError("Connection lost: ${e.message ?: "Socket error"}")
                return
            } catch (e: IOException) {
                // Generic IO error
                handleError("Connection error: ${e.message ?: "IO error"}")
                return
            }
        }
    }

    // ------------------------------------------------
    // Helpers
    // ------------------------------------------------

    @Synchronized
    private fun sendRaw(data: ByteArray) {
        val sock = socket ?: throw IOException("Socket not connected")
        if (sock.isClosed || !sock.isConnected) {
            throw IOException("Connection is closed")
        }
        val out = outputStream ?: throw IOException("Output stream not available")
        out.write(data)
        out.flush()
    }

    private fun readExact(n: Int): ByteArray {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = inputStream!!.read(buf, offset, n - offset)
            if (read == -1) throw IOException("Stream closed")
            offset += read
        }
        return buf
    }

    private fun isActive(): Boolean {
        val sock = socket ?: return false
        return !sock.isClosed && sock.isConnected
    }

    private fun setState(newState: ConnectionState) {
        _state.value = newState
        onStateChange(newState)
    }

    private fun handleError(msg: String) {
        setState(ConnectionState.ERROR)
        onError(msg)
        close()
    }

    private fun close() {
        readJob?.cancel()
        keepaliveJob?.cancel()
        try { socket?.close() } catch (_: IOException) { }
        socket = null
        outputStream = null
        inputStream = null
        if (_state.value != ConnectionState.ERROR) {
            setState(ConnectionState.DISCONNECTED)
        }
    }

    private class HandshakeException(msg: String) : Exception(msg)
}
