package com.iobus.client.network

import com.iobus.client.protocol.Constants
import com.iobus.client.protocol.Messages
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level connection manager tying TCP + UDP together.
 *- Orchestrates connect / disconnect lifecycle
 * - Exposes observable connection state
 * - Provides a single send() entry point for input events
 */
class ConnectionManager {

    private var tcpClient: TcpClient? = null
    private var udpClient: UdpClient? = null
    private var scope: CoroutineScope? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    /** Server host currently connected to (or last attempted). */
    var host: String = ""
        private set

    // ------------------------------------------------
    // Public API
    // ------------------------------------------------

    /**
     * Connect to a server at [host]:[tcpPort].
     * TCP handshake is performed; on success UDP socket is opened.
     */
    suspend fun connect(
        host: String,
        tcpPort: Int = Constants.TCP_PORT,
    ) {
        // Tear down any existing connection
        disconnectInternal()

        this.host = host
        _errorMessage.value = null
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val tcp = TcpClient(
            host = host,
            port = tcpPort,
            deviceName = android.os.Build.MODEL,
            onStateChange = { newState -> _state.value = newState },
            onError = { msg ->
                _errorMessage.value = msg
                scope?.launch { disconnectInternal() }
            },
        )
        tcpClient = tcp

        // TCP connect + handshake
        tcp.connect()

        if (tcp.state.value != ConnectionState.CONNECTED) {
            // Error already reported via callback
            return
        }

        // Open UDP, using server-specified port from handshake ack
        val udp = UdpClient(host, tcp.serverUdpPort)
        udp.open()
        udpClient = udp
    }

    /**
     * Graceful disconnect.
     */
    suspend fun disconnect() {
        disconnectInternal()
    }

    /**
     * Send raw bytes over UDP (input events). Fire-and-forget.
     */
    fun sendInput(data: ByteArray) {
        try {
            udpClient?.send(data)
        } catch (_: Exception) {
            // Silently drop to prevent crashes
        }
    }

    // Convenience senders --------------------------

    fun sendMouseMove(dx: Float, dy: Float) {
        val timestamp = System.currentTimeMillis()
        sendInput(Messages.encodeMouseMove(timestamp, dx.toInt(), dy.toInt()))
    }

    fun sendMouseClick(button: Int, action: Int) {
        val timestamp = System.currentTimeMillis()
        sendInput(Messages.encodeMouseClick(timestamp, button.toByte(), action.toByte()))
    }

    fun sendMouseScroll(dx: Float, dy: Float) {
        val timestamp = System.currentTimeMillis()
        sendInput(Messages.encodeMouseScroll(timestamp, dx.toInt(), dy.toInt()))
    }

    fun sendMouseDrag(button: Int, dx: Float, dy: Float) {
        val timestamp = System.currentTimeMillis()
        sendInput(Messages.encodeMouseDrag(timestamp, button.toByte(), dx.toInt(), dy.toInt()))
    }

    fun sendKeyEvent(keyCode: Int, action: Int, modifiers: Int = 0) {
        val timestamp = System.currentTimeMillis()
        sendInput(Messages.encodeKeyEvent(timestamp, action.toByte(), keyCode, modifiers))
    }

    fun sendSystemAction(actionId: Byte) {
        val timestamp = System.currentTimeMillis()
        sendInput(Messages.encodeSystemAction(timestamp, actionId))
    }

    fun sendLaunchApp(appName: String) {
        val timestamp = System.currentTimeMillis()
        sendInput(Messages.encodeLaunchApp(timestamp, appName))
    }

    // ------------------------------------------------
    // Internal
    // ------------------------------------------------

    private suspend fun disconnectInternal() {
        try { tcpClient?.disconnect() } catch (_: Exception) { }
        udpClient?.close()
        scope?.cancel()
        tcpClient = null
        udpClient = null
        scope = null
        _state.value = ConnectionState.DISCONNECTED
    }
}
