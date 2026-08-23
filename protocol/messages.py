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

from protocol.constants import (
    APP_NAME_MAX_LENGTH,
    CLIENT_NAME_MAX_LENGTH,
    ERROR_MESSAGE_MAX_LENGTH,
    PROTOCOL_VERSION,
)


# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------

class MessageType(IntEnum):
    """Protocol message types. Values are wire-format codes."""

    # Control plane (TCP)
    HANDSHAKE_REQ = 0x01
    HANDSHAKE_ACK = 0x02
    HANDSHAKE_REJECT = 0x03
    HANDSHAKE_AUTH_REQUIRED = 0x04  # v1.6.0: Server requests PIN
    HANDSHAKE_AUTH_RESPONSE = 0x05  # v1.6.0: Client sends PIN hash
    HANDSHAKE_AUTH_SUCCESS = 0x06   # v1.6.0: Auth succeeded
    HANDSHAKE_AUTH_FAILED = 0x07    # v1.6.0: Auth failed (invalid PIN)
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
    SIRI_VOICE = 6
    SPOTLIGHT = 7


class ActivityStatus(IntEnum):
    """Activity status identifiers for SYSTEM_STATE_RESPONSE."""
    ACTIVE = 0  # Currently using keyboard/mouse (< 2s idle)
    IDLE = 1    # Stepped away from keyboard (2s-5min)
    AWAY = 2    # Left the Mac (> 5min)


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
SYSTEM_STATE_RESPONSE_FMT = ">HHHBHBB"  # brightness(u16), volume(u16), flags(u16), activity_status(u8), idle_time(u16), battery_percent(u8), power_flags(u8)
ACK_FMT = ">B"                # app_id(u8)
COMMAND_ERROR_FMT = ">B"      # app_id(u8)

HANDSHAKE_REQ_FMT = ">HH"     # client_version(u16), flags(u16)  + 32-byte name
HANDSHAKE_ACK_FMT = ">HHHH"   # server_version(u16), flags(u16), udp_port(u16), keepalive(u16)
HANDSHAKE_REJECT_FMT = ">HH"  # server_version(u16), reason_code(u16)

# PIN Authentication (v1.6.0)
HANDSHAKE_AUTH_REQUIRED_FMT = ">16s4s"  # salt(16 bytes), challenge(4 bytes)
HANDSHAKE_AUTH_RESPONSE_FMT = ">32s"    # pin_hash(32 bytes SHA-256)
HANDSHAKE_AUTH_SUCCESS_FMT = ">HH16s"   # server_version(u16), flags(u16), session_token(16 bytes)
HANDSHAKE_AUTH_FAILED_FMT = ">H"        # retry_after(u16 seconds)

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
HANDSHAKE_REJECT_SIZE = struct.calcsize(HANDSHAKE_REJECT_FMT)
HANDSHAKE_AUTH_REQUIRED_SIZE = struct.calcsize(HANDSHAKE_AUTH_REQUIRED_FMT)
HANDSHAKE_AUTH_RESPONSE_SIZE = struct.calcsize(HANDSHAKE_AUTH_RESPONSE_FMT)
HANDSHAKE_AUTH_SUCCESS_SIZE = struct.calcsize(HANDSHAKE_AUTH_SUCCESS_FMT)
HANDSHAKE_AUTH_FAILED_SIZE = struct.calcsize(HANDSHAKE_AUTH_FAILED_FMT)


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
    reason_code: int  # 1=version_mismatch, 2=busy, 3=auth_required

    def encode(self) -> bytes:
        payload = struct.pack(HANDSHAKE_REJECT_FMT, self.server_version, self.reason_code)
        header = Header(PROTOCOL_VERSION, MessageType.HANDSHAKE_REJECT, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ver, reason = struct.unpack(HANDSHAKE_REJECT_FMT, payload)
        return cls(server_version=ver, reason_code=reason)


@dataclass(frozen=True, slots=True)
class HandshakeAuthRequired:
    """Server requests PIN authentication (v1.6.0)"""
    salt: bytes  # 16 bytes
    challenge: bytes  # 4 bytes

    def encode(self) -> bytes:
        if len(self.salt) != 16:
            raise ValueError(f"Salt must be 16 bytes, got {len(self.salt)}")
        if len(self.challenge) != 4:
            raise ValueError(f"Challenge must be 4 bytes, got {len(self.challenge)}")
        payload = struct.pack(HANDSHAKE_AUTH_REQUIRED_FMT, self.salt, self.challenge)
        header = Header(PROTOCOL_VERSION, MessageType.HANDSHAKE_AUTH_REQUIRED, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        salt, challenge = struct.unpack(HANDSHAKE_AUTH_REQUIRED_FMT, payload[:HANDSHAKE_AUTH_REQUIRED_SIZE])
        return cls(salt=salt, challenge=challenge)


@dataclass(frozen=True, slots=True)
class HandshakeAuthResponse:
    """Client sends PIN hash (v1.6.0)"""
    pin_hash: bytes  # 32 bytes (SHA-256)

    def encode(self) -> bytes:
        if len(self.pin_hash) != 32:
            raise ValueError(f"PIN hash must be 32 bytes, got {len(self.pin_hash)}")
        payload = struct.pack(HANDSHAKE_AUTH_RESPONSE_FMT, self.pin_hash)
        header = Header(PROTOCOL_VERSION, MessageType.HANDSHAKE_AUTH_RESPONSE, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        pin_hash, = struct.unpack(HANDSHAKE_AUTH_RESPONSE_FMT, payload[:HANDSHAKE_AUTH_RESPONSE_SIZE])
        return cls(pin_hash=pin_hash)


@dataclass(frozen=True, slots=True)
class HandshakeAuthSuccess:
    """Server confirms successful authentication (v1.6.0)"""
    server_version: int
    flags: int
    session_token: bytes  # 16 bytes (reserved for future use)

    def encode(self) -> bytes:
        if len(self.session_token) != 16:
            raise ValueError(f"Session token must be 16 bytes, got {len(self.session_token)}")
        payload = struct.pack(HANDSHAKE_AUTH_SUCCESS_FMT, self.server_version, self.flags, self.session_token)
        header = Header(PROTOCOL_VERSION, MessageType.HANDSHAKE_AUTH_SUCCESS, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        ver, flags, token = struct.unpack(HANDSHAKE_AUTH_SUCCESS_FMT, payload[:HANDSHAKE_AUTH_SUCCESS_SIZE])
        return cls(server_version=ver, flags=flags, session_token=token)


@dataclass(frozen=True, slots=True)
class HandshakeAuthFailed:
    """Server rejects invalid PIN with rate limiting (v1.6.0)"""
    retry_after: int  # Seconds to wait before retry (0 = immediate)

    def encode(self) -> bytes:
        payload = struct.pack(HANDSHAKE_AUTH_FAILED_FMT, self.retry_after)
        header = Header(PROTOCOL_VERSION, MessageType.HANDSHAKE_AUTH_FAILED, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        retry_after, = struct.unpack(HANDSHAKE_AUTH_FAILED_FMT, payload[:HANDSHAKE_AUTH_FAILED_SIZE])
        return cls(retry_after=retry_after)


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
        name_bytes = self.app_name.encode("utf-8")[:APP_NAME_MAX_LENGTH]
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
    """System state response — brightness, volume, lock/activity status."""
    brightness: float
    volume: float
    is_muted: bool = False
    is_locked: bool = False
    activity_status: str = "active"  # 'active', 'idle', 'away'
    idle_time: float = 0.0  # seconds
    battery_percent: int = 100  # 0-100
    is_charging: bool = False  # actively drawing charge current
    external_connected: bool = True  # plugged into AC (may or may not be charging)
    has_battery: bool = True  # False for desktop Macs with no battery hardware

    def encode(self) -> bytes:
        b = int(self.brightness * 100) & 0xFFFF
        v = int(self.volume * 100) & 0xFFFF
        flags = (
            (0x01 if self.is_muted else 0)
            | (0x02 if self.is_locked else 0)
            | (0x04 if self.is_charging else 0)
        )

        # Map activity status to enum value
        activity_map = {"active": ActivityStatus.ACTIVE, "idle": ActivityStatus.IDLE, "away": ActivityStatus.AWAY}
        activity = activity_map.get(self.activity_status, ActivityStatus.ACTIVE)

        # Clamp idle time to u16 range (0-65535 seconds, ~18 hours)
        idle = int(min(self.idle_time, 65535)) & 0xFFFF
        battery = max(0, min(100, self.battery_percent)) & 0xFF
        power_flags = (
            (0x01 if self.external_connected else 0)
            | (0x02 if self.has_battery else 0)
        )

        payload = struct.pack(SYSTEM_STATE_RESPONSE_FMT, b, v, flags, activity, idle, battery, power_flags)
        header = Header(PROTOCOL_VERSION, MessageType.SYSTEM_STATE_RESPONSE, len(payload))
        return header.encode() + payload

    @classmethod
    def decode(cls, payload: bytes) -> Self:
        b, v, flags, activity, idle, battery, power_flags = struct.unpack(
            SYSTEM_STATE_RESPONSE_FMT, payload[:SYSTEM_STATE_RESPONSE_SIZE]
        )

        # Map enum value back to string
        activity_map = {ActivityStatus.ACTIVE: "active", ActivityStatus.IDLE: "idle", ActivityStatus.AWAY: "away"}
        activity_str = activity_map.get(activity, "active")

        return cls(
            brightness=b / 100.0,
            volume=v / 100.0,
            is_muted=bool(flags & 0x01),
            is_locked=bool(flags & 0x02),
            activity_status=activity_str,
            idle_time=float(idle),
            battery_percent=battery,
            is_charging=bool(flags & 0x04),
            external_connected=bool(power_flags & 0x01),
            has_battery=bool(power_flags & 0x02),
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


def encode_error(message: str) -> bytes:
    """Encode an ERROR message with a UTF-8 error string."""
    payload = message.encode("utf-8")[:ERROR_MESSAGE_MAX_LENGTH]
    header = Header(PROTOCOL_VERSION, MessageType.ERROR, len(payload))
    return header.encode() + payload


def decode_error(payload: bytes) -> str:
    """Decode an ERROR message payload into a string."""
    return payload.decode("utf-8", errors="replace")
