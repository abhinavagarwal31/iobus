"""
Protocol message types and encoding/decoding.

Defines the binary wire format for all message types.
Uses Python's struct module — no external dependencies.

Wire format:
  Header (4 bytes): [version:u8] [type:u8] [payload_len:u16be]
  Payload: variable, defined per message type
"""

from __future__ import annotations

import struct
from dataclasses import dataclass
from enum import IntEnum
from typing import Self

from protocol.constants import CLIENT_NAME_MAX_LENGTH, PROTOCOL_VERSION


# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------

class MessageType(IntEnum):
    """Protocol message types. Values are wire-format codes."""

    # Control plane (TCP)
    HANDSHAKE_REQ = 0x01
    HANDSHAKE_ACK = 0x02
    HANDSHAKE_REJECT = 0x03
    PING = 0x10
    PONG = 0x11
    DISCONNECT = 0x1F

    # Data plane (UDP) — Mouse
    MOUSE_MOVE = 0x20
    MOUSE_CLICK = 0x21
    MOUSE_SCROLL = 0x22
    MOUSE_DRAG = 0x23

    # Data plane (UDP) — Keyboard
    KEY_EVENT = 0x30

    # Data plane (UDP) — System actions
    SYSTEM_ACTION = 0x40

    # Data plane (UDP) — App launcher
    LAUNCH_APP = 0x50

    # System state (TCP)
    GET_SYSTEM_STATE = 0x5F
    SYSTEM_STATE_RESPONSE = 0x60
    ACK = 0x61
    COMMAND_ERROR = 0x62

    # Error
    ERROR = 0xFF


class MouseButton(IntEnum):
    """Mouse button identifiers."""
    LEFT = 0
    RIGHT = 1
    MIDDLE = 2


class ClickAction(IntEnum):
    """Mouse click actions."""
    PRESS = 0
    RELEASE = 1


class KeyAction(IntEnum):
    """Key press actions."""
    KEY_DOWN = 0
    KEY_UP = 1


class ModifierFlag:
    """Modifier key bitmask flags (byte 7 of KEY_EVENT payload)."""
    SHIFT = 0x01    # Bit 0
    CONTROL = 0x02  # Bit 1
    ALT = 0x04      # Bit 2 (Option on macOS)
    META = 0x08     # Bit 3 (Cmd on macOS)
    FN = 0x10       # Bit 4


class SystemActionId(IntEnum):
    """System action identifiers for SYSTEM_ACTION messages."""
    LOCK_SCREEN = 1
    POWER_DIALOG = 2
    SLEEP = 3
    SHUTDOWN = 4
    RESTART = 5


# ---------------------------------------------------------------------------
# Struct formats (big-endian)
# ---------------------------------------------------------------------------

HEADER_FMT = ">BBH"  # version(u8), type(u8), payload_len(u16)
HEADER_SIZE = struct.calcsize(HEADER_FMT)

# Payload formats
MOUSE_MOVE_FMT = ">Ihh"       # timestamp(u32), dx(i16), dy(i16)
MOUSE_CLICK_FMT = ">IBB"      # timestamp(u32), button(u8), action(u8)
MOUSE_SCROLL_FMT = ">Ihh"     # timestamp(u32), dx(i16), dy(i16)
MOUSE_DRAG_FMT = ">IBhh"      # timestamp(u32), button(u8), dx(i16), dy(i16)
KEY_EVENT_FMT = ">IBHB"       # timestamp(u32), action(u8), keycode(u16), modifiers(u8)
SYSTEM_ACTION_FMT = ">IB"      # timestamp(u32), action_id(u8)
SYSTEM_STATE_RESPONSE_FMT = ">HHH"  # brightness(u16), volume(u16), flags(u16)
ACK_FMT = ">B"                # app_id(u8)
COMMAND_ERROR_FMT = ">B"      # app_id(u8)

HANDSHAKE_REQ_FMT = ">HH"     # client_version(u16), flags(u16)  + 32-byte name
HANDSHAKE_ACK_FMT = ">HHHH"   # server_version(u16), flags(u16), udp_port(u16), keepalive(u16)
HANDSHAKE_REJECT_FMT = ">HH"  # server_version(u16), reason_code(u16)

HANDSHAKE_REQ_NAME_LEN = CLIENT_NAME_MAX_LENGTH  # 32 bytes, null-padded

# Payload sizes (pre-calculated)
MOUSE_MOVE_SIZE = struct.calcsize(MOUSE_MOVE_FMT)
MOUSE_CLICK_SIZE = struct.calcsize(MOUSE_CLICK_FMT)
MOUSE_SCROLL_SIZE = struct.calcsize(MOUSE_SCROLL_FMT)
MOUSE_DRAG_SIZE = struct.calcsize(MOUSE_DRAG_FMT)
KEY_EVENT_SIZE = struct.calcsize(KEY_EVENT_FMT)
SYSTEM_ACTION_SIZE = struct.calcsize(SYSTEM_ACTION_FMT)
SYSTEM_STATE_RESPONSE_SIZE = struct.calcsize(SYSTEM_STATE_RESPONSE_FMT)
ACK_SIZE = struct.calcsize(ACK_FMT)
COMMAND_ERROR_SIZE = struct.calcsize(COMMAND_ERROR_FMT)
HANDSHAKE_REQ_SIZE = struct.calcsize(HANDSHAKE_REQ_FMT) + HANDSHAKE_REQ_NAME_LEN
HANDSHAKE_ACK_SIZE = struct.calcsize(HANDSHAKE_ACK_FMT)


# ---------------------------------------------------------------------------
# Data classes — decoded messages
# ---------------------------------------------------------------------------

@dataclass(frozen=True, slots=True)
class Header:
    version: int
    msg_type: MessageType
    payload_length: int

    def encode(self) -> bytes:
        return struct.pack(HEADER_FMT, self.version, self.msg_type, self.payload_length)

    @classmethod
    def decode(cls, data: bytes) -> Self:
        if len(data) < HEADER_SIZE:
            raise ValueError(f"Header requires {HEADER_SIZE} bytes, got {len(data)}")
        ver, mtype, plen = struct.unpack(HEADER_FMT, data[:HEADER_SIZE])
        return cls(version=ver, msg_type=MessageType(mtype), payload_length=plen)


@dataclass(frozen=True, slots=True)
class HandshakeReq:
    client_version: int
    flags: int
    client_name: str

    def encode(self) -> bytes:
        name_bytes = self.client_name.encode("utf-8")[:HANDSHAKE_REQ_NAME_LEN]
        name_padded = name_bytes.ljust(HANDSHAKE_REQ_NAME_LEN, b"\x00")
        payload = struct.pack(HANDSHAKE_REQ_FMT, self.client_version, self.flags) + name_padded
        header = Header(PROTOCOL_VERSION, MessageType.HANDSHAKE_REQ, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        fixed_size = struct.calcsize(HANDSHAKE_REQ_FMT)
        ver, flags = struct.unpack(HANDSHAKE_REQ_FMT, payload[:fixed_size])
        name_raw = payload[fixed_size:fixed_size + HANDSHAKE_REQ_NAME_LEN]
        name = name_raw.rstrip(b"\x00").decode("utf-8", errors="replace")
        return cls(client_version=ver, flags=flags, client_name=name)


@dataclass(frozen=True, slots=True)
class HandshakeAck:
    server_version: int
    flags: int
    udp_port: int
    keepalive_interval: int

    def encode(self) -> bytes:
        payload = struct.pack(
            HANDSHAKE_ACK_FMT,
            self.server_version, self.flags, self.udp_port, self.keepalive_interval,
        )
        header = Header(PROTOCOL_VERSION, MessageType.HANDSHAKE_ACK, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ver, flags, udp_port, keepalive = struct.unpack(HANDSHAKE_ACK_FMT, payload)
        return cls(
            server_version=ver, flags=flags,
            udp_port=udp_port, keepalive_interval=keepalive,
        )


@dataclass(frozen=True, slots=True)
class HandshakeReject:
    server_version: int
    reason_code: int  # 1=version_mismatch, 2=busy

    def encode(self) -> bytes:
        payload = struct.pack(HANDSHAKE_REJECT_FMT, self.server_version, self.reason_code)
        header = Header(PROTOCOL_VERSION, MessageType.HANDSHAKE_REJECT, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ver, reason = struct.unpack(HANDSHAKE_REJECT_FMT, payload)
        return cls(server_version=ver, reason_code=reason)


@dataclass(frozen=True, slots=True)
class MouseMove:
    timestamp: int
    dx: int
    dy: int

    def encode(self) -> bytes:
        payload = struct.pack(MOUSE_MOVE_FMT, self.timestamp, self.dx, self.dy)
        header = Header(PROTOCOL_VERSION, MessageType.MOUSE_MOVE, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ts, dx, dy = struct.unpack(MOUSE_MOVE_FMT, payload[:MOUSE_MOVE_SIZE])
        return cls(timestamp=ts, dx=dx, dy=dy)


@dataclass(frozen=True, slots=True)
class MouseClick:
    timestamp: int
    button: MouseButton
    action: ClickAction

    def encode(self) -> bytes:
        payload = struct.pack(MOUSE_CLICK_FMT, self.timestamp, self.button, self.action)
        header = Header(PROTOCOL_VERSION, MessageType.MOUSE_CLICK, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ts, btn, act = struct.unpack(MOUSE_CLICK_FMT, payload[:MOUSE_CLICK_SIZE])
        return cls(timestamp=ts, button=MouseButton(btn), action=ClickAction(act))


@dataclass(frozen=True, slots=True)
class MouseScroll:
    timestamp: int
    dx: int
    dy: int

    def encode(self) -> bytes:
        payload = struct.pack(MOUSE_SCROLL_FMT, self.timestamp, self.dx, self.dy)
        header = Header(PROTOCOL_VERSION, MessageType.MOUSE_SCROLL, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ts, dx, dy = struct.unpack(MOUSE_SCROLL_FMT, payload[:MOUSE_SCROLL_SIZE])
        return cls(timestamp=ts, dx=dx, dy=dy)


@dataclass(frozen=True, slots=True)
class MouseDrag:
    timestamp: int
    button: MouseButton
    dx: int
    dy: int

    def encode(self) -> bytes:
        payload = struct.pack(MOUSE_DRAG_FMT, self.timestamp, self.button, self.dx, self.dy)
        header = Header(PROTOCOL_VERSION, MessageType.MOUSE_DRAG, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ts, btn, dx, dy = struct.unpack(MOUSE_DRAG_FMT, payload[:MOUSE_DRAG_SIZE])
        return cls(timestamp=ts, button=MouseButton(btn), dx=dx, dy=dy)


@dataclass(frozen=True, slots=True)
class KeyEvent:
    timestamp: int
    action: KeyAction
    keycode: int
    modifiers: int  # bitmask of ModifierFlag

    def encode(self) -> bytes:
        payload = struct.pack(
            KEY_EVENT_FMT, self.timestamp, self.action, self.keycode, self.modifiers,
        )
        header = Header(PROTOCOL_VERSION, MessageType.KEY_EVENT, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ts, act, kc, mods = struct.unpack(KEY_EVENT_FMT, payload[:KEY_EVENT_SIZE])
        return cls(timestamp=ts, action=KeyAction(act), keycode=kc, modifiers=mods)


@dataclass(frozen=True, slots=True)
class SystemAction:
    timestamp: int
    action_id: SystemActionId

    def encode(self) -> bytes:
        payload = struct.pack(SYSTEM_ACTION_FMT, self.timestamp, self.action_id)
        header = Header(PROTOCOL_VERSION, MessageType.SYSTEM_ACTION, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ts, aid = struct.unpack(SYSTEM_ACTION_FMT, payload[:SYSTEM_ACTION_SIZE])
        return cls(timestamp=ts, action_id=SystemActionId(aid))


@dataclass(frozen=True, slots=True)
class LaunchApp:
    """Launch application message — variable-length app name."""
    timestamp: int
    app_name: str

    def encode(self) -> bytes:
        name_bytes = self.app_name.encode("utf-8")[:128]
        payload = struct.pack(">IB", self.timestamp, len(name_bytes)) + name_bytes
        header = Header(PROTOCOL_VERSION, MessageType.LAUNCH_APP, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ts = struct.unpack(">I", payload[:4])[0]
        name_len = payload[4]
        app_name = payload[5:5 + name_len].decode("utf-8", errors="replace")
        return cls(timestamp=ts, app_name=app_name)


# ---------------------------------------------------------------------------
# Response messages
# ---------------------------------------------------------------------------

@dataclass(frozen=True, slots=True)
class SystemStateResponse:
    """System state response — brightness, volume, flags."""
    brightness: float
    volume: float
    is_muted: bool = False
    is_locked: bool = False

    def encode(self) -> bytes:
        b = int(self.brightness * 100) & 0xFFFF
        v = int(self.volume * 100) & 0xFFFF
        flags = (0x01 if self.is_muted else 0) | (0x02 if self.is_locked else 0)
        payload = struct.pack(SYSTEM_STATE_RESPONSE_FMT, b, v, flags)
        header = Header(PROTOCOL_VERSION, MessageType.SYSTEM_STATE_RESPONSE, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        b, v, flags = struct.unpack(SYSTEM_STATE_RESPONSE_FMT, payload[:SYSTEM_STATE_RESPONSE_SIZE])
        return cls(
            brightness=b / 100.0,
            volume=v / 100.0,
            is_muted=bool(flags & 0x01),
            is_locked=bool(flags & 0x02),
        )


@dataclass(frozen=True, slots=True)
class Ack:
    """Generic acknowledgement with an app_id reference."""
    app_id: int = 0

    def encode(self) -> bytes:
        payload = struct.pack(ACK_FMT, self.app_id)
        header = Header(PROTOCOL_VERSION, MessageType.ACK, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        (app_id,) = struct.unpack(ACK_FMT, payload[:ACK_SIZE])
        return cls(app_id=app_id)


@dataclass(frozen=True, slots=True)
class CommandError:
    """Error response with an app_id reference."""
    app_id: int = 0

    def encode(self) -> bytes:
        payload = struct.pack(COMMAND_ERROR_FMT, self.app_id)
        header = Header(PROTOCOL_VERSION, MessageType.COMMAND_ERROR, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        (app_id,) = struct.unpack(COMMAND_ERROR_FMT, payload[:COMMAND_ERROR_SIZE])
        return cls(app_id=app_id)


def encode_ping() -> bytes:
    """Encode a PING message (header only, no payload)."""
    return Header(PROTOCOL_VERSION, MessageType.PING, 0).encode()


def encode_pong() -> bytes:
    """Encode a PONG message (header only, no payload)."""
    return Header(PROTOCOL_VERSION, MessageType.PONG, 0).encode()


def encode_disconnect() -> bytes:
    """Encode a DISCONNECT message (header only, no payload)."""
    return Header(PROTOCOL_VERSION, MessageType.DISCONNECT, 0).encode()


def encode_error(message: str) -> bytes:
    """Encode an ERROR message with a UTF-8 error string."""
    payload = message.encode("utf-8")[:256]  # Cap error messages at 256 bytes
    header = Header(PROTOCOL_VERSION, MessageType.ERROR, len(payload))
    return header.encode() + payload


def decode_error(payload: bytes) -> str:
    """Decode an ERROR message payload into a string."""
    return payload.decode("utf-8", errors="replace")


# ---------------------------------------------------------------------------
# Dispatch decoder
# ---------------------------------------------------------------------------

# Type alias for all decoded message types
DecodedMessage = (
    HandshakeReq | HandshakeAck | HandshakeReject
    | MouseMove | MouseClick | MouseScroll | MouseDrag
    | KeyEvent | SystemAction | LaunchApp | str  # str for ERROR payload
)

# Map message types to their decoder
_DECODERS: dict[MessageType, type | None] = {
    MessageType.HANDSHAKE_REQ: HandshakeReq,
    MessageType.HANDSHAKE_ACK: HandshakeAck,
    MessageType.HANDSHAKE_REJECT: HandshakeReject,
    MessageType.PING: None,
    MessageType.PONG: None,
    MessageType.DISCONNECT: None,
    MessageType.MOUSE_MOVE: MouseMove,
    MessageType.MOUSE_CLICK: MouseClick,
    MessageType.MOUSE_SCROLL: MouseScroll,
    MessageType.MOUSE_DRAG: MouseDrag,
    MessageType.KEY_EVENT: KeyEvent,
    MessageType.SYSTEM_ACTION: SystemAction,
    MessageType.LAUNCH_APP: LaunchApp,
    MessageType.GET_SYSTEM_STATE: None,
    MessageType.SYSTEM_STATE_RESPONSE: SystemStateResponse,
    MessageType.ACK: Ack,
    MessageType.COMMAND_ERROR: CommandError,
    MessageType.ERROR: None,  # handled separately
}


def decode_message(data: bytes) -> tuple[Header, DecodedMessage | None]:
    """Decode a complete message (header + payload) from raw bytes.

    Returns:
        (header, decoded_payload) where decoded_payload is ``None`` for
        payload-less messages like PING/PONG/DISCONNECT.

    Raises:
        ValueError: If the data is too short or the message type is unknown.
    """
    header = Header.decode(data)
    payload = data[HEADER_SIZE:HEADER_SIZE + header.payload_length]

    if header.msg_type == MessageType.ERROR:
        return header, decode_error(payload)

    decoder_cls = _DECODERS.get(header.msg_type)
    if decoder_cls is None:
        # Payload-less message (PING, PONG, DISCONNECT) or unknown
        return header, None

    return header, decoder_cls.decode(payload)
