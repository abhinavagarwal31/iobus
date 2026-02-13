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
import java.nio.ByteBuffer

/**
 * TCP control-plane client.
 *
 * Responsibilities:
 *  - Connect to server, send handshake, validate ack
 *  - Run keepalive loop (send PING, expect PONG)
 *  - Send DISCONNECT on graceful close
 *  - Dispatch incoming TCP responses (ACK, ERROR, SYSTEM_STATE_RESPONSE)
 *  - Notify owner of connection events via callbacks
 */
class TcpClient(
    private val host: String,
    private val port: Int = Constants.TCP_PORT,
    private val deviceName: String = "Android",
    private val onStateChange: (ConnectionState) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onSystemState: (SystemStateData) -> Unit = {},
    private val onLaunchAck: (Int) -> Unit = {},
    private val onLaunchError: (Int) -> Unit = {},
) {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private var readJob: Job? = null
    private var keepaliveJob: Job? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    /** Server-assigned UDP port (from handshake ack). */
    var serverUdpPort: Int = Constants.UDP_PORT
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
            sock.soTimeout = 0  // blocking reads handled in coroutine
            sock.connect(InetSocketAddress(host, port), 5_000)

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

    private fun performHandshake() {
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
            // HANDSHAKE_ACK = 0x02
            0x02 -> {
                val ack = Messages.decodeHandshakeAck(payload)
                serverUdpPort = ack.udpPort
            }
            // HANDSHAKE_REJECT = 0x03
            0x03 -> {
                val reason = if (payload.isNotEmpty()) String(payload, Charsets.UTF_8) else "rejected"
                throw HandshakeException(reason)
            }
            else -> throw HandshakeException("Unexpected response type: 0x${type.toString(16)}")
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
                        } catch (_: IOException) { }
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
                        if (payload.size >= 8) {
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
        } catch (e: IOException) {
            if (_state.value == ConnectionState.CONNECTED) {
                handleError("Connection lost: ${e.message}")
            }
        }
    }

    // ------------------------------------------------
    // Keepalive
    // ------------------------------------------------

    private suspend fun keepaliveLoop() {
        while (isActive()) {
            delay(Constants.KEEPALIVE_INTERVAL_SECONDS * 1000L)
            try {
                sendRaw(Messages.encodePing())
            } catch (e: IOException) {
                handleError("Keepalive failed: ${e.message}")
                return
            }
        }
    }

    // ------------------------------------------------
    // Helpers
    // ------------------------------------------------

    @Synchronized
    private fun sendRaw(data: ByteArray) {
        outputStream?.let {
            it.write(data)
            it.flush()
        }
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
