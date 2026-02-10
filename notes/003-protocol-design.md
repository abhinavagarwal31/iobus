# Protocol Design — v1

> Platform-neutral wire protocol for IOBus remote control system.

## Design Principles

1. **Platform-neutral** — No Android or macOS specifics in the wire format
2. **Versioned** — Every connection starts with version negotiation
3. **No branding** — The protocol uses generic names, not "IOBus" in messages
4. **Extensible** — New event types can be added without breaking v1 clients
5. **Compact** — Minimize bytes on the wire for low latency
6. **Stateless events** — Each input event is self-contained (no session state needed for UDP)

## Transport Layout

```
┌─────────────────────────────────────────────┐
│              TCP (Control Plane)            │
├─────────────────────────────────────────────┤
│  • Handshake & version negotiation          │
│  • Client capability announcement           │
│  • Server capability response               │
│  • Keepalive ping/pong                      │
│  • Graceful disconnect                      │
│  • Error messages                           │
├─────────────────────────────────────────────┤
│              UDP (Data Plane)               │
├─────────────────────────────────────────────┤
│  • Mouse move                               │
│  • Mouse click (left, right, middle)        │
│  • Mouse drag                               │
│  • Scroll                                   │
│  • Key down / key up                        │
│  • Modifier state                           │
└─────────────────────────────────────────────┘
```

## Message Format

All messages use a compact binary format with a fixed header.

### Header (4 bytes)

```
Byte 0:    Protocol version (uint8)   — 0x01 for v1
Byte 1:    Message type (uint8)       — see table below
Bytes 2-3: Payload length (uint16, big-endian)
```

### Message Types

| Code | Name             | Transport | Direction       | Description                       |
| ---- | ---------------- | --------- | --------------- | --------------------------------- |
| 0x01 | HANDSHAKE_REQ    | TCP       | Client → Server | Initiate connection               |
| 0x02 | HANDSHAKE_ACK    | TCP       | Server → Client | Accept connection                 |
| 0x03 | HANDSHAKE_REJECT | TCP       | Server → Client | Reject connection (version/error) |
| 0x10 | PING             | TCP       | Bidirectional   | Keepalive request                 |
| 0x11 | PONG             | TCP       | Bidirectional   | Keepalive response                |
| 0x1F | DISCONNECT       | TCP       | Bidirectional   | Graceful disconnect               |
| 0x20 | MOUSE_MOVE       | UDP       | Client → Server | Relative mouse movement           |
| 0x21 | MOUSE_CLICK      | UDP       | Client → Server | Mouse button press/release        |
| 0x22 | MOUSE_SCROLL     | UDP       | Client → Server | Scroll wheel event                |
| 0x23 | MOUSE_DRAG       | UDP       | Client → Server | Click-and-drag movement           |
| 0x30 | KEY_EVENT        | UDP       | Client → Server | Key down or key up                |
| 0xFF | ERROR            | TCP       | Server → Client | Error notification                |

### Payload Definitions

#### HANDSHAKE_REQ (0x01)

```
Bytes 0-1:  Client protocol version (uint16, big-endian)
Bytes 2-3:  Client flags (uint16, reserved, set to 0x0000)
Bytes 4-35: Client name (UTF-8, null-padded to 32 bytes)
```

#### HANDSHAKE_ACK (0x02)

```
Bytes 0-1:  Server protocol version (uint16, big-endian)
Bytes 2-3:  Server flags (uint16, reserved)
Bytes 4-5:  UDP port for data plane (uint16, big-endian)
Bytes 6-7:  Keepalive interval in seconds (uint16, big-endian)
```

#### MOUSE_MOVE (0x20)

```
Bytes 0-3:  Timestamp (uint32, milliseconds since connection, big-endian)
Bytes 4-5:  Delta X (int16, big-endian) — pixels
Bytes 6-7:  Delta Y (int16, big-endian) — pixels
```

Total: 8 bytes payload → 12 bytes on wire (with header)

#### MOUSE_CLICK (0x21)

```
Bytes 0-3:  Timestamp (uint32)
Byte 4:     Button (uint8: 0=left, 1=right, 2=middle)
Byte 5:     Action (uint8: 0=press, 1=release)
```

#### MOUSE_SCROLL (0x22)

```
Bytes 0-3:  Timestamp (uint32)
Bytes 4-5:  Delta X (int16, big-endian) — horizontal scroll
Bytes 6-7:  Delta Y (int16, big-endian) — vertical scroll
```

#### MOUSE_DRAG (0x23)

```
Bytes 0-3:  Timestamp (uint32)
Byte 4:     Button (uint8: which button is held)
Bytes 5-6:  Delta X (int16, big-endian)
Bytes 7-8:  Delta Y (int16, big-endian)
```

#### KEY_EVENT (0x30)

```
Bytes 0-3:  Timestamp (uint32)
Byte 4:     Action (uint8: 0=key_down, 1=key_up)
Bytes 5-6:  Key code (uint16, big-endian) — platform-neutral key code
Byte 7:     Modifier flags (uint8, bitmask):
              Bit 0: Shift
              Bit 1: Control
              Bit 2: Alt/Option
              Bit 3: Meta/Cmd
              Bit 4: Fn
              Bits 5-7: Reserved
```

## Platform-Neutral Key Codes

Key codes are NOT Android keycodes or macOS virtual keycodes.
The protocol defines its own key code table to remain platform-neutral.
Mapping tables exist on both client and server:

- Android client: maps Android KeyEvent → protocol key code
- macOS server: maps protocol key code → macOS virtual key code (CGKeyCode)

The key code table will be defined in `protocol/keycodes.py` (for server)
and mirrored in the Android client.

## Connection Lifecycle

```
Client                          Server
  │                                │
  │──── TCP CONNECT ──────────────►│
  │──── HANDSHAKE_REQ ────────────►│
  │◄─── HANDSHAKE_ACK ────────────│  (includes UDP port)
  │                                │
  │──── UDP input events ─────────►│  (continuous)
  │                                │
  │◄─── PING ─────────────────────│  (periodic)
  │──── PONG ─────────────────────►│
  │                                │
  │──── DISCONNECT ───────────────►│  (graceful)
  │──── TCP CLOSE ────────────────►│
```

## Error Handling

- If the server receives an unknown message type, it sends an ERROR message
  and does NOT disconnect (forward compatibility)
- If the server receives a HANDSHAKE_REQ with an unsupported version,
  it sends HANDSHAKE_REJECT with its supported version
- UDP packet loss is expected and tolerated — no retransmission
- TCP keepalive timeout: if no PONG received within 3× keepalive interval,
  the connection is considered dead

## Future Considerations (v2+)

- DTLS encryption for the UDP channel
- TLS for TCP channel
- Authentication tokens in handshake
- Multi-touch gesture events
- Haptic feedback commands (server → client)
