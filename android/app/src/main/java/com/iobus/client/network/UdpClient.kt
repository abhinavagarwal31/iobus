package com.iobus.client.network

import com.iobus.client.protocol.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * UDP data-plane client.
 *
 * Fire-and-forget sender for low-latency input events.
 * No response reading — UDP is one-way in our protocol.
 */
class UdpClient(
    private val host: String,
    private val port: Int = Constants.UDP_PORT,
) {
    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Open the UDP socket. Must be called before [send].
     */
    suspend fun open() = withContext(Dispatchers.IO) {
        address = InetAddress.getByName(host)
        socket = DatagramSocket().apply {
            // No connect() — we send datagrams individually
            soTimeout = 0
        }
    }

    /**
     * Send a pre-encoded message via UDP. Fire-and-forget.
     */
    fun send(data: ByteArray) {
        val sock = socket ?: return
        val addr = address ?: return
        executor.execute {
            try {
                val packet = DatagramPacket(data, data.size, addr, port)
                sock.send(packet)
            } catch (_: IOException) {
                // UDP — best effort, drop silently
            } catch (_: Exception) {
                // Catch-all to prevent crashes
            }
        }
    }

    /**
     * Close the UDP socket.
     */
    fun close() {
        try { socket?.close() } catch (_: Exception) { }
        try { executor.shutdownNow() } catch (_: Exception) { }
        socket = null
        address = null
    }
}
