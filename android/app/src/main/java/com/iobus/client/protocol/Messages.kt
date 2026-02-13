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
    const val KEY_DOWN: Byte = 0
    const val KEY_UP: Byte = 1
}

object ModifierFlag {
    const val SHIFT: Byte = 0x01
    const val CONTROL: Byte = 0x02
    const val ALT: Byte = 0x04
    const val META: Byte = 0x08
    const val FN: Byte = 0x10
}

object SystemActionId {
    const val LOCK_SCREEN: Byte = 1
    const val POWER_DIALOG: Byte = 2
    const val SLEEP: Byte = 3
    const val SHUTDOWN: Byte = 4
    const val RESTART: Byte = 5
}

/**
 * Decoded system state from SYSTEM_STATE_RESPONSE.
 */
data class SystemStateData(
    val brightness: Int,
    val volume: Int,
    val isMuted: Boolean,
    val isLocked: Boolean,
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

    fun decodeSystemState(payload: ByteArray): SystemStateData {
        val buf = ByteBuffer.wrap(payload)
        buf.order(ByteOrder.BIG_ENDIAN)
        val brightness = buf.getShort().toInt() and 0xFFFF
        val volume = buf.getShort().toInt() and 0xFFFF
        val flags = buf.getShort().toInt() and 0xFFFF
        return SystemStateData(
            brightness = brightness,
            volume = volume,
            isMuted = (flags and 0x01) != 0,
            isLocked = (flags and 0x02) != 0,
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
