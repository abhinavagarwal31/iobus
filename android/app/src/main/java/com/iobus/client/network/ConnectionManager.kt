package com.iobus.client.network

import android.content.Context
import com.iobus.client.protocol.Constants
import com.iobus.client.protocol.Messages
import com.iobus.client.protocol.SystemStateData
import com.iobus.client.security.PinStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level connection manager tying TCP + UDP together.
 *- Orchestrates connect / disconnect lifecycle
 * - Manages PIN authentication (v1.6.0)
 * - Exposes observable connection state
 * - Provides a single send() entry point for input events
 */
class ConnectionManager(
    private val context: Context,
    private val savedServersStore: SavedServersStore = SavedServersStore(context),
) {
    companion object {
        private const val RECONNECT_BASE_DELAY_MS = 2000L
        private const val RECONNECT_MAX_DELAY_MS = 30000L
    }

    private var tcpClient: TcpClient? = null
    private var udpClient: UdpClient? = null
    private var scope: CoroutineScope? = null
    private val pinStore = PinStore(context)

    // Auto-reconnect state
    private var lastConnectHost: String? = null
    private var lastConnectPort: Int? = null
    private var lastConnectPin: String? = null
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var isUserDisconnect = false

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _authRequired = MutableStateFlow(false)
    val authRequired: StateFlow<Boolean> = _authRequired

    /**
     * Latest system state pushed by the server (brightness + volume).
     * Null until the first push is received after connection.
     * Cleared on disconnect.
     */
    private val _systemState = MutableStateFlow<SystemStateData?>(null)
    val systemState: StateFlow<SystemStateData?> = _systemState

    /** Server host currently connected to (or last attempted). */
    var host: String = ""
        private set

    // ------------------------------------------------
    // Public API
    // ------------------------------------------------

    /**
     * Connect to a server at [host]:[tcpPort].
     * TCP handshake is performed (with PIN auth if required); on success UDP socket is opened.
     *
     * @param pin Optional PIN for authentication. If null, will check PinStore for saved PIN.
     *            If auth is required but no PIN available, authRequired will be set to true.
     * @param enableAutoReconnect If true, will attempt to reconnect automatically on connection loss
     */
    suspend fun connect(
        host: String,
        tcpPort: Int = Constants.DEFAULT_TCP_PORT,
        pin: String? = null,
        enableAutoReconnect: Boolean = true,
    ) {
        // Tear down any existing connection
        isUserDisconnect = false
        reconnectJob?.cancel()
        disconnectInternal()

        // Store connection params for auto-reconnect
        lastConnectHost = host
        lastConnectPort = tcpPort
        lastConnectPin = pin
        reconnectAttempts = 0

        this.host = host
        _errorMessage.value = null
        _authRequired.value = false
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val serverAddress = "$host:$tcpPort"

        // PIN provider: use provided PIN, or check store, or return null (will trigger authRequired)
        val pinProvider: suspend (String) -> String? = { addr ->
            pin ?: pinStore.getPin(addr)
        }

        val tcp = TcpClient(
            host = host,
            port = tcpPort,
            deviceName = android.os.Build.MODEL,
            pinProvider = pinProvider,
            onStateChange = { newState -> _state.value = newState },
            onError = { msg ->
                // Check if error is due to missing PIN
                if (msg.contains("Authentication required but no PIN available")) {
                    _authRequired.value = true
                    _errorMessage.value = "PIN required for authentication"
                } else {
                    _errorMessage.value = msg
                    // Attempt auto-reconnect if not user-initiated disconnect
                    if (enableAutoReconnect && !isUserDisconnect) {
                        scheduleReconnect()
                    }
                }
                scope?.launch { disconnectInternal() }
            },
            onSystemState = { data -> _systemState.value = data },
        )
        tcpClient = tcp

        // TCP connect + handshake
        tcp.connect()

        if (tcp.state.value != ConnectionState.CONNECTED) {
            // Error already reported via callback
            return
        }

        // Store PIN on successful connection
        if (pin != null) {
            pinStore.storePin(serverAddress, pin)
        }

        // Save last connection for auto-reconnect on app restart
        savedServersStore.saveLastConnection(host, tcpPort)

        // Open UDP, using server-specified port from handshake ack
        val udp = UdpClient(host, tcp.serverUdpPort)
        udp.open()
        udpClient = udp
    }

    /*
     * Graceful disconnect.
     */
    suspend fun disconnect() {
        isUserDisconnect = true
        reconnectJob?.cancel()
        lastConnectHost = null
        lastConnectPort = null
        lastConnectPin = null
        savedServersStore.clearLastConnection()
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

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.Main).launch {
            // Exponential backoff: 2s, 4s, 8s, 16s, 30s (max)
            reconnectAttempts++
            val delayMs = minOf(RECONNECT_BASE_DELAY_MS * (1 shl (reconnectAttempts - 1)), RECONNECT_MAX_DELAY_MS)

            _errorMessage.value = "Connection lost. Reconnecting in ${delayMs / 1000}s..."
            delay(delayMs)

            val host = lastConnectHost ?: return@launch
            val port = lastConnectPort ?: return@launch
            val pin = lastConnectPin

            try {
                connect(host, port, pin, enableAutoReconnect = true)
            } catch (e: Exception) {
                // Reconnect failed, will retry via onError callback
            }
        }
    }

    private suspend fun disconnectInternal() {
        try { tcpClient?.disconnect() } catch (_: Exception) { }
        udpClient?.close()
        scope?.cancel()
        tcpClient = null
        udpClient = null
        scope = null
        _state.value = ConnectionState.DISCONNECTED
        _systemState.value = null
    }
}
