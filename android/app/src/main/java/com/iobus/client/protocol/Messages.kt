package com.iobus.client.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire protocol message encoding — mirrors protocol/messages.py.
 *
 * Binary format:
 *   Header (4 bytes): [version:u8] [type:u8] [payload_len:u16be]
 *   Payload: variable per message type
 */
object MessageType {
    // Control plane (TCP)
    const val HANDSHAKE_REQ: Byte = 0x01
    const val HANDSHAKE_ACK: Byte = 0x02
    const val HANDSHAKE_REJECT: Byte = 0x03
    const val HANDSHAKE_AUTH_REQUIRED: Byte = 0x04  // v1.6.0: Server requests PIN
    const val HANDSHAKE_AUTH_RESPONSE: Byte = 0x05  // v1.6.0: Client sends PIN hash
    const val HANDSHAKE_AUTH_SUCCESS: Byte = 0x06  // v1.6.0: Auth succeeded
    const val HANDSHAKE_AUTH_FAILED: Byte = 0x07   // v1.6.0: Auth failed
    const val PING: Byte = 0x10
    const val PONG: Byte = 0x11
    const val DISCONNECT: Byte = 0x1F

    // Data plane (UDP) — Mouse
    const val MOUSE_MOVE: Byte = 0x20
    const val MOUSE_CLICK: Byte = 0x21
    const val MOUSE_SCROLL: Byte = 0x22
    const val MOUSE_DRAG: Byte = 0x23

    // Data plane (UDP) — Keyboard
    const val KEY_EVENT: Byte = 0x30

    // Data plane (UDP) — System actions
    const val SYSTEM_ACTION: Byte = 0x40

    // Data plane (UDP) — App launcher
    const val LAUNCH_APP: Byte = 0x50

    // Response / ack (TCP)
    const val GET_SYSTEM_STATE: Byte = 0x5F
    const val SYSTEM_STATE_RESPONSE: Byte = 0x60
    const val ACK: Byte = 0x61
    const val COMMAND_ERROR: Byte = 0x62

    // Error
    const val ERROR: Byte = 0xFF.toByte()
}

object MouseButton {
    const val LEFT: Byte = 0
    const val RIGHT: Byte = 1
    const val MIDDLE: Byte = 2
}

object ClickAction {
    const val PRESS: Byte = 0
    const val RELEASE: Byte = 1
}

object KeyAction {
    const val KEY_DOWN: Int = 0
    const val KEY_UP: Int = 1
}

object ModifierFlag {
    const val SHIFT: Int = 0x01
    const val CONTROL: Int = 0x02
    const val ALT: Int = 0x04
    const val META: Int = 0x08
    const val FN: Int = 0x10
}

object SystemActionId {
    const val LOCK_SCREEN: Byte = 1
    const val POWER_DIALOG: Byte = 2
    const val SLEEP: Byte = 3
    const val SHUTDOWN: Byte = 4
    const val RESTART: Byte = 5
    const val SIRI_VOICE: Byte = 6
    const val SPOTLIGHT: Byte = 7
}

object ActivityStatus {
    const val ACTIVE: Byte = 0  // Currently using keyboard/mouse (< 2s idle)
    const val IDLE: Byte = 1    // Stepped away from keyboard (2s-5min)
    const val AWAY: Byte = 2    // Left the Mac (> 5min)
}

/**
 * Decoded system state from SYSTEM_STATE_RESPONSE.
 */
data class SystemStateData(
    val brightness: Int,
    val volume: Int,
    val isMuted: Boolean,
    val isLocked: Boolean,
    val activityStatus: String,  // "active", "idle", or "away"
    val idleTime: Int,  // seconds
    val batteryPercent: Int = 100,  // 0-100
    val isCharging: Boolean = false,
)

/**
 * Decoded handshake acknowledgement from the server.
 */
data class HandshakeAckData(
    val serverVersion: Int,
    val flags: Int,
    val udpPort: Int,
    val keepaliveInterval: Int,
)

/**
 * Decoded auth challenge from server (v1.6.0).
 */
data class HandshakeAuthRequiredData(
    val pinSalt: ByteArray,
    val challenge: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HandshakeAuthRequiredData
        if (!pinSalt.contentEquals(other.pinSalt)) return false
        if (!challenge.contentEquals(other.challenge)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pinSalt.contentHashCode()
        result = 31 * result + challenge.contentHashCode()
        return result
    }
}

/**
 * Decoded auth success from server (v1.6.0).
 */
data class HandshakeAuthSuccessData(
    val serverVersion: Int,
    val flags: Int,
    val sessionToken: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HandshakeAuthSuccessData
        if (serverVersion != other.serverVersion) return false
        if (flags != other.flags) return false
        if (!sessionToken.contentEquals(other.sessionToken)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = serverVersion
        result = 31 * result + flags
        result = 31 * result + sessionToken.contentHashCode()
        return result
    }
}

/**
 * Decoded auth failure from server (v1.6.0).
 */
data class HandshakeAuthFailedData(
    val retryAfter: Int,  // seconds
)

/**
 * Decoded header from a received message.
 */
data class MessageHeader(
    val version: Int,
    val type: Byte,
    val payloadLength: Int,
)

/**
 * Protocol message encoder/decoder.
 *
 * All encoding methods return a complete message (header + payload) as ByteArray.
 * All multi-byte integers are big-endian on the wire.
 */
object Messages {

    // ---- Header ----

    private fun encodeHeader(type: Byte, payloadLength: Int): ByteArray {
        val buf = ByteBuffer.allocate(Constants.HEADER_SIZE)
        buf.order(ByteOrder.BIG_ENDIAN)
        buf.put(Constants.PROTOCOL_VERSION.toByte())
        buf.put(type)
        buf.putShort(payloadLength.toShort())
        return buf.array()
    }

    fun decodeHeader(data: ByteArray, offset: Int = 0): MessageHeader {
        val buf = ByteBuffer.wrap(data, offset, Constants.HEADER_SIZE)
        buf.order(ByteOrder.BIG_ENDIAN)
        val version = buf.get().toInt() and 0xFF
        val type = buf.get()
        val payloadLen = buf.getShort().toInt() and 0xFFFF
        return MessageHeader(version, type, payloadLen)
    }

    // ---- Handshake ----

    fun encodeHandshakeReq(clientName: String): ByteArray {
        val nameBytes = clientName.toByteArray(Charsets.UTF_8)
        val namePadded = ByteArray(Constants.CLIENT_NAME_MAX_LENGTH)
        nameBytes.copyInto(namePadded, endIndex = minOf(nameBytes.size, Constants.CLIENT_NAME_MAX_LENGTH))

        val payloadSize = 4 + Constants.CLIENT_NAME_MAX_LENGTH  // version(2) + flags(2) + name(32)
        val payload = ByteBuffer.allocate(payloadSize)
        payload.order(ByteOrder.BIG_ENDIAN)
        payload.putShort(Constants.PROTOCOL_VERSION.toShort())
        payload.putShort(0) // flags reserved
        payload.put(namePadded)

        val header = encodeHeader(MessageType.HANDSHAKE_REQ, payloadSize)
        return header + payload.array()
    }

    fun decodeHandshakeAck(payload: ByteArray): HandshakeAckData {
        val buf = ByteBuffer.wrap(payload)
        buf.order(ByteOrder.BIG_ENDIAN)
        val serverVersion = buf.getShort().toInt() and 0xFFFF
        val flags = buf.getShort().toInt() and 0xFFFF
        val udpPort = buf.getShort().toInt() and 0xFFFF
        val keepalive = buf.getShort().toInt() and 0xFFFF
        return HandshakeAckData(serverVersion, flags, udpPort, keepalive)
    }

    // ---- Authentication (v1.6.0) ----

    fun decodeHandshakeAuthRequired(payload: ByteArray): HandshakeAuthRequiredData {
        require(payload.size >= Constants.PIN_SALT_SIZE + Constants.PIN_CHALLENGE_SIZE) {
            "Invalid HANDSHAKE_AUTH_REQUIRED payload size"
        }
        val pinSalt = payload.copyOfRange(0, Constants.PIN_SALT_SIZE)
        val challenge = payload.copyOfRange(Constants.PIN_SALT_SIZE, Constants.PIN_SALT_SIZE + Constants.PIN_CHALLENGE_SIZE)
        return HandshakeAuthRequiredData(pinSalt, challenge)
    }

    fun encodeHandshakeAuthResponse(pinHash: ByteArray): ByteArray {
        require(pinHash.size == Constants.PIN_HASH_SIZE) {
            "PIN hash must be ${Constants.PIN_HASH_SIZE} bytes (SHA-256)"
        }
        val header = encodeHeader(MessageType.HANDSHAKE_AUTH_RESPONSE, Constants.PIN_HASH_SIZE)
        return header + pinHash
    }

    fun decodeHandshakeAuthSuccess(payload: ByteArray): HandshakeAuthSuccessData {
        require(payload.size >= 4 + Constants.SESSION_TOKEN_SIZE) {
            "Invalid HANDSHAKE_AUTH_SUCCESS payload size"
        }
        val buf = ByteBuffer.wrap(payload)
        buf.order(ByteOrder.BIG_ENDIAN)
        val serverVersion = buf.getShort().toInt() and 0xFFFF
        val flags = buf.getShort().toInt() and 0xFFFF
        val sessionToken = ByteArray(Constants.SESSION_TOKEN_SIZE)
        buf.get(sessionToken)
        return HandshakeAuthSuccessData(serverVersion, flags, sessionToken)
    }

    fun decodeHandshakeAuthFailed(payload: ByteArray): HandshakeAuthFailedData {
        require(payload.size >= 2) {
            "Invalid HANDSHAKE_AUTH_FAILED payload size"
        }
        val buf = ByteBuffer.wrap(payload)
        buf.order(ByteOrder.BIG_ENDIAN)
        val retryAfter = buf.getShort().toInt() and 0xFFFF
        return HandshakeAuthFailedData(retryAfter)
    }

    // ---- Simple messages (no payload) ----

    fun encodePing(): ByteArray = encodeHeader(MessageType.PING, 0)
    fun encodePong(): ByteArray = encodeHeader(MessageType.PONG, 0)
    fun encodeDisconnect(): ByteArray = encodeHeader(MessageType.DISCONNECT, 0)

    // ---- Mouse events (UDP) ----

    fun encodeMouseMove(timestamp: Long, dx: Int, dy: Int): ByteArray {
        val payloadSize = 8  // timestamp(4) + dx(2) + dy(2)
        val payload = ByteBuffer.allocate(payloadSize)
        payload.order(ByteOrder.BIG_ENDIAN)
        payload.putInt((timestamp and 0xFFFFFFFFL).toInt())
        payload.putShort(dx.coerceIn(-32768, 32767).toShort())
        payload.putShort(dy.coerceIn(-32768, 32767).toShort())

        return encodeHeader(MessageType.MOUSE_MOVE, payloadSize) + payload.array()
    }

    fun encodeMouseClick(timestamp: Long, button: Byte, action: Byte): ByteArray {
        val payloadSize = 6  // timestamp(4) + button(1) + action(1)
        val payload = ByteBuffer.allocate(payloadSize)
        payload.order(ByteOrder.BIG_ENDIAN)
        payload.putInt((timestamp and 0xFFFFFFFFL).toInt())
        payload.put(button)
        payload.put(action)

        return encodeHeader(MessageType.MOUSE_CLICK, payloadSize) + payload.array()
    }

    fun encodeMouseScroll(timestamp: Long, dx: Int, dy: Int): ByteArray {
        val payloadSize = 8
        val payload = ByteBuffer.allocate(payloadSize)
        payload.order(ByteOrder.BIG_ENDIAN)
        payload.putInt((timestamp and 0xFFFFFFFFL).toInt())
        payload.putShort(dx.coerceIn(-32768, 32767).toShort())
        payload.putShort(dy.coerceIn(-32768, 32767).toShort())

        return encodeHeader(MessageType.MOUSE_SCROLL, payloadSize) + payload.array()
    }

    fun encodeMouseDrag(timestamp: Long, button: Byte, dx: Int, dy: Int): ByteArray {
        val payloadSize = 9  // timestamp(4) + button(1) + dx(2) + dy(2)
        val payload = ByteBuffer.allocate(payloadSize)
        payload.order(ByteOrder.BIG_ENDIAN)
        payload.putInt((timestamp and 0xFFFFFFFFL).toInt())
        payload.put(button)
        payload.putShort(dx.coerceIn(-32768, 32767).toShort())
        payload.putShort(dy.coerceIn(-32768, 32767).toShort())

        return encodeHeader(MessageType.MOUSE_DRAG, payloadSize) + payload.array()
    }

    // ---- Keyboard events (UDP) ----

    fun encodeKeyEvent(timestamp: Long, action: Byte, keycode: Int, modifiers: Int): ByteArray {
        val payloadSize = 8  // timestamp(4) + action(1) + keycode(2) + modifiers(1)
        val payload = ByteBuffer.allocate(payloadSize)
        payload.order(ByteOrder.BIG_ENDIAN)
        payload.putInt((timestamp and 0xFFFFFFFFL).toInt())
        payload.put(action)
        payload.putShort(keycode.toShort())
        payload.put(modifiers.toByte())

        return encodeHeader(MessageType.KEY_EVENT, payloadSize) + payload.array()
    }

    // ---- System actions (UDP) ----

    fun encodeSystemAction(timestamp: Long, actionId: Byte): ByteArray {
        val payloadSize = 5  // timestamp(4) + action_id(1)
        val payload = ByteBuffer.allocate(payloadSize)
        payload.order(ByteOrder.BIG_ENDIAN)
        payload.putInt((timestamp and 0xFFFFFFFFL).toInt())
        payload.put(actionId)

        return encodeHeader(MessageType.SYSTEM_ACTION, payloadSize) + payload.array()
    }

    // ---- System state (TCP) ----

    /** Request current system state from the server (header-only, no payload). */
    fun encodeGetSystemState(): ByteArray = encodeHeader(MessageType.GET_SYSTEM_STATE, 0)

    fun decodeSystemState(payload: ByteArray): SystemStateData {
        val buf = ByteBuffer.wrap(payload)
        buf.order(ByteOrder.BIG_ENDIAN)
        val brightness = buf.getShort().toInt() and 0xFFFF
        val volume = buf.getShort().toInt() and 0xFFFF
        val flags = buf.getShort().toInt() and 0xFFFF

        // Backward compatibility: check if new fields exist (protocol v3+)
        val activityStatus: String
        val idleTime: Int

        if (payload.size >= 9) {  // New format: 6 bytes (old) + 1 + 2 = 9 bytes
            val activityStatusByte = buf.get()
            idleTime = buf.getShort().toInt() and 0xFFFF

            // Map activity status byte to string
            activityStatus = when (activityStatusByte) {
                ActivityStatus.IDLE -> "idle"
                ActivityStatus.AWAY -> "away"
                else -> "active"
            }
        } else {
            // Old format (protocol v2): default values
            activityStatus = "active"
            idleTime = 0
        }

        // Backward compatibility: battery byte only present on protocol v4+ (10 bytes total)
        val batteryPercent: Int
        if (payload.size >= 10) {
            batteryPercent = buf.get().toInt() and 0xFF
        } else {
            batteryPercent = 100
        }

        return SystemStateData(
            brightness = brightness,
            volume = volume,
            isMuted = (flags and 0x01) != 0,
            isLocked = (flags and 0x02) != 0,
            activityStatus = activityStatus,
            idleTime = idleTime,
            batteryPercent = batteryPercent,
            isCharging = (flags and 0x04) != 0,
        )
    }

    // ---- App launcher (UDP) ----

    fun encodeLaunchApp(timestamp: Long, appName: String): ByteArray {
        val nameBytes = appName.toByteArray(Charsets.UTF_8)
        val truncated = if (nameBytes.size > 128) nameBytes.copyOf(128) else nameBytes
        val payloadSize = 4 + 1 + truncated.size  // timestamp(4) + name_len(1) + name(var)
        val payload = ByteBuffer.allocate(payloadSize)
        payload.order(ByteOrder.BIG_ENDIAN)
        payload.putInt((timestamp and 0xFFFFFFFFL).toInt())
        payload.put(truncated.size.toByte())
        payload.put(truncated)

        return encodeHeader(MessageType.LAUNCH_APP, payloadSize) + payload.array()
    }
}
