# Architecture Overview

> IOBus — Low-Latency Wireless Remote Control (Android → macOS)

## System Summary

IOBus is a real-time, event-driven client–server system that turns an Android phone
into a full MacBook-style keyboard and trackpad for a macOS host. Communication
happens over a local network (Wi-Fi or phone hotspot) with zero internet dependency.

## High-Level Architecture

```
┌───────────────┐        UDP / TCP (LAN)        ┌───────────────┐
│  Android App  │  ───────────────────────────►  │  macOS Server │
│  (Client)     │  ◄───────────────────────────  │  (Host)       │
└───────────────┘                                └───────────────┘
      Phone                                         MacBook
```

### Why Client–Server?

- **Simplicity**: One phone (client) controls one Mac (server). No peer discovery
  complexity, no mesh networking.
- **Directionality**: Input flows one way (phone → Mac). Only lightweight
  acknowledgements or status travel back.
- **Low latency**: Direct socket communication on a local network. No relays,
  no cloud, no NAT traversal.

## Component Breakdown

### 1. Android Client (`android/`)

- Native Android app (Kotlin + Jetpack Compose planned)
- Provides a landscape MacBook-style keyboard and a trackpad surface
- Captures touch events, key presses, and gestures
- Encodes them into protocol messages and sends to the server
- Handles server discovery (mDNS / manual IP entry)
- Dark, futuristic HUD-style UI

### 2. macOS Server (`server/`)

- Python application running on the Mac
- Listens for incoming connections from the Android client
- Decodes protocol messages
- Translates them into native macOS input events using:
  - **Quartz Event Services** (CGEvent API) for mouse/keyboard injection
  - **pyobjc** for Objective-C bridge where needed
- Manages Accessibility permission checks (required by macOS for input injection)
- Provides connection status feedback

### 3. Shared Protocol (`protocol/`)

- Platform-neutral message definitions
- Versioned (starting at v1)
- No hard-coded branding in the wire format
- Designed so an iOS client can be added later without protocol changes

## Data Flow (Typical Input Event)

```
  1. User touches trackpad area on phone
  2. Android captures MotionEvent
  3. Client encodes: { type: "mouse_move", dx: 12, dy: -3 }
  4. Client sends UDP datagram to server
  5. Server receives and decodes
  6. Server calls CGEventCreateMouseEvent() to move cursor
  7. macOS processes the injected event
```

Total expected latency budget: < 20ms over local Wi-Fi.

## Transport Strategy

| Channel       | Transport | Purpose                                      |
| ------------- | --------- | -------------------------------------------- |
| Input events  | UDP       | Mouse moves, key events — low latency        |
| Control plane | TCP       | Handshake, capability negotiation, keepalive |

UDP is chosen for input events because:

- Mouse moves and key events are high-frequency
- Dropped packets are acceptable (next event corrects state)
- No head-of-line blocking
- Lower overhead than TCP

TCP is reserved for:

- Initial connection handshake
- Protocol version negotiation
- Keepalive / heartbeat
- Any operation requiring guaranteed delivery

## Security Considerations (v1)

- v1 operates **only on trusted local networks**
- No encryption in v1 (planned for v2)
- Server binds to a configurable port
- Optional: simple pairing code / PIN for initial connection
- No sensitive data (passwords, tokens) is transmitted in the protocol itself
  (power actions like "lock screen" are local keyboard shortcuts, not credentials)

## Scalability Notes

- 1:1 only (one phone controls one Mac) in v1
- Architecture doesn't preclude multi-client in the future
- iOS client addition requires only a new client implementation against the same protocol

## macOS Permissions

The server MUST have **Accessibility** access to inject input events.

- On first run, the server should check for this permission
- If not granted, it should guide the user to System Settings → Privacy & Security → Accessibility
- The server must NOT assume permissions are already granted
- It should fail gracefully with a clear error message if access is denied

## Non-Goals (v1)

- Touch ID / fingerprint authentication
- Screen mirroring or screen streaming
- File transfer
- Internet connectivity
- Multi-client support
