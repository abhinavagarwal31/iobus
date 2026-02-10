# Component Responsibilities

> Clear ownership boundaries for every module in the system.

---

## Server (`server/`)

The macOS host application. Receives input events and injects them into macOS.

### `server/main.py`

- Entry point for the macOS server
- Parses CLI arguments (port, log level, etc.)
- Checks macOS Accessibility permissions before starting
- Boots the asyncio event loop
- Orchestrates startup of TCP and UDP listeners

### `server/config.py`

- Server configuration (ports, timeouts, defaults)
- Reads from environment variables or a config file
- No hard-coded magic numbers in other modules

### `server/transport/tcp_server.py`

- TCP listener for control plane
- Handles HANDSHAKE_REQ → HANDSHAKE_ACK / HANDSHAKE_REJECT
- Manages PING/PONG keepalive
- Handles DISCONNECT
- Tracks connected client state

### `server/transport/udp_server.py`

- UDP listener for data plane (input events)
- Receives datagrams, decodes headers, dispatches to handlers
- Stateless — each packet is processed independently
- Validates sender against the TCP-authenticated client

### `server/input/mouse.py`

- Translates protocol mouse events into CGEvent calls
- Handles: move, left click, right click, middle click, drag, scroll
- Manages mouse state (current position, button state for drag)

### `server/input/keyboard.py`

- Translates protocol key events into CGEvent keyboard calls
- Maps protocol key codes → macOS virtual key codes (CGKeyCode)
- Handles modifier keys (Cmd, Option, Control, Shift, Fn)
- Handles key down / key up independently (not key press)

### `server/input/actions.py`

- Implements high-level power/system actions
- Lock screen: injects Ctrl+Cmd+Q
- Show power dialog: injects Ctrl+Power (or Ctrl+Eject)
- Sleep: calls pmset or IOKit sleep (if feasible from user space)
- These are composed from keyboard.py primitives where possible

### `server/permissions.py`

- Checks if the app has macOS Accessibility access
- Uses `AXIsProcessTrustedWithOptions` from ApplicationServices
- On first run: prompts user to grant access
- Returns clear status: granted / denied / unknown

### `server/discovery.py`

- (Future) Registers the server via mDNS/Bonjour for auto-discovery
- For v1: server prints its IP and port; user enters manually on client

---

## Protocol (`protocol/`)

Shared definitions used by both server and client. The Python files here
are the canonical source; the Android client mirrors these definitions.

### `protocol/messages.py`

- Message type constants (0x01, 0x20, etc.)
- Header struct format
- Encoder/decoder functions for each message type
- Uses Python's `struct` module — no external dependencies

### `protocol/keycodes.py`

- Platform-neutral key code definitions
- Enum or constant class with all supported keys
- This is the shared key code table that both platforms implement

### `protocol/constants.py`

- Protocol version (v1 = 1)
- Default ports
- Keepalive intervals
- Max payload sizes
- Timeout values

---

## Android Client (`android/`)

The mobile app that captures input and sends it to the server.

### Overview

- Native Android app (Kotlin)
- Jetpack Compose for UI
- Landscape-primary orientation
- Two main surfaces: Keyboard and Trackpad
- Dark futuristic HUD theme

### Planned Modules (Kotlin packages)

```
com.iobus.client/
├── ui/
│   ├── theme/        — Colors, typography, shapes (HUD theme)
│   ├── keyboard/     — Full keyboard layout component
│   ├── trackpad/     — Trackpad touch surface component
│   └── connection/   — Server connection UI (IP entry, status)
├── input/
│   ├── TouchProcessor.kt   — Converts MotionEvents to protocol events
│   └── KeyProcessor.kt     — Converts Android key codes to protocol codes
├── network/
│   ├── TcpClient.kt        — Control plane (handshake, keepalive)
│   ├── UdpClient.kt        — Data plane (input event sending)
│   └── ConnectionManager.kt — Lifecycle, reconnection, state
├── protocol/
│   ├── Messages.kt          — Message encoding/decoding (mirrors protocol/)
│   └── KeyCodes.kt          — Platform-neutral key codes (mirrors protocol/)
└── app/
    └── MainActivity.kt      — Entry point, navigation
```

---

## Notes (`notes/`)

Living documentation. Not deployed, not in builds.

| File                   | Purpose                              |
| ---------------------- | ------------------------------------ |
| 001-architecture.md    | System overview and architecture     |
| 002-decisions.md       | Decision log with rationale          |
| 003-protocol-design.md | Wire protocol specification          |
| 004-components.md      | This file — module responsibilities  |
| 005-python-setup.md    | Python version choice and venv setup |

---

## Dependency Direction

```
Android Client ──► protocol/ ◄── macOS Server
                   (shared)
```

- Server imports from `protocol/`
- Android mirrors `protocol/` definitions in Kotlin
- Neither client nor server imports from the other
- `protocol/` has ZERO dependencies on either platform
