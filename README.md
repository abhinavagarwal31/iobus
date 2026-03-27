# IOBus

A wireless Android-to-macOS remote control system with secure PIN authentication and automatic discovery.

## Overview

IOBus turns an Android phone into a keyboard, trackpad, and system controller for macOS. It communicates over local Wi-Fi using a custom binary protocol with PIN authentication. No internet connection is required.

The Android client captures touch and key input, encodes it into binary messages using a 4-byte header format, and sends them to a Python server running on macOS. The server injects input events into macOS via CGEvent through the Accessibility API. TCP carries the control plane (handshake, authentication, keepalive); UDP carries the data plane (input events).

## Features

- **Security (v1.6.0)**: PIN authentication with SHA-256 hashing, rate limiting (5 attempts per IP, 5-minute lockout), encrypted PIN storage on Android
- **Auto-Discovery (v1.6.0)**: mDNS/Bonjour service advertisement, server hostname resolution survives IP changes
- **Connection Reliability (v1.6.0)**: Extended keepalive timeout (120s grace period, up from 15s)
- Full on-screen keyboard with modifier tracking (Shift, Ctrl, Alt, Cmd)
- Function key row with media key mappings for F1, F2, F3 (Mission Control), F4 (Spotlight), F7--F12. F5--F6 system-level actions (Dictation, Do Not Disturb) are deferred to v2
- Trackpad with single-finger move, tap-to-click, two-finger scroll, two-finger tap for right-click, and long-press drag
- Combined split-screen mode (trackpad + keyboard side-by-side in landscape)
- Control Center for brightness (with max/min quick-set) and volume (with max/min quick-set) and media playback controls — adapts to portrait (vertical sliders) and landscape (horizontal sliders)
- Brightness, volume, and mute state synced from Mac to app on connect and pushed automatically whenever the values change on the Mac (no polling — event-driven with a 500 ms watcher)
- Screen lock and power actions on the Home screen (power is passcode-gated)
- Corner floating menu with vertical scrollable mode selection and system actions
- Passcode-gated shutdown and restart (SHA-256 hashed, stored locally on device)
- Persistent TCP connection with server-driven keepalive (10-second interval, 120-second timeout)
- Saved server presets for quick manual reconnection
- Single-activity Compose navigation

## Architecture

```
Android Client (Kotlin / Jetpack Compose)       macOS Server (Python 3.12 / asyncio)
+-------------------------------------------+    +-------------------------------------------+
| TouchProcessor           -- UDP ----------+--->| MouseController    (CGEvent injection)    |
| KeyProcessor             -- UDP ----------+--->| KeyboardController (CGEvent injection)    |
| ConnectionManager        -- TCP ----------+--->| TCPControlServer   (handshake, auth)      |
| ControlsPanel            -- UDP ----------+--->| SystemActions      (AppleScript, pmset)   |
| PinStore (encrypted)     -- local --------+--->| PinAuthenticator   (SHA-256, rate limit)  |
| MdnsDiscovery (NSD)      -- mDNS ---------+--->| MdnsAdvertiser     (_iobus._tcp.local.)   |
+-------------------------------------------+    +-------------------------------------------+
```

**TCP (port 9800)** -- Handshake with PIN authentication (v1.6.0), keepalive (ping/pong), disconnect, system state push (brightness, volume, mute), app launch commands. The server pushes a `SYSTEM_STATE_RESPONSE` immediately on handshake and again whenever brightness, volume, or mute changes, so slider positions are always in sync.

**UDP (port 9801)** -- Mouse move/click/scroll/drag, key events, system actions, app launch commands.

**Wire format** -- 4-byte header `[version:u8][type:u8][payload_len:u16be]` followed by a variable-length payload. All multi-byte values are big-endian. Encoding and decoding use Python `struct` and Kotlin `ByteBuffer` with no JSON involved.

**Protocol v2 (v1.6.0)** -- Added 4 new message types for PIN authentication:

- `HANDSHAKE_AUTH_REQUIRED (0x04)` - Server sends salt + challenge
- `HANDSHAKE_AUTH_RESPONSE (0x05)` - Client sends SHA-256(PIN + salt + challenge)
- `HANDSHAKE_AUTH_SUCCESS (0x06)` - Auth succeeded, returns session token
- `HANDSHAKE_AUTH_FAILED (0x07)` - Auth failed, returns retry_after seconds

## Design Principles

IOBus v1 prioritizes:

- Deterministic behavior over feature breadth
- Low-latency local networking
- Clear separation between control and data planes
- Minimal, system-oriented UI design
- Explicit limitations instead of unreliable system-level workarounds

## Project Structure

```
IOBus/
├── protocol/                   Shared protocol definitions (mirrored in Kotlin)
│   ├── constants.py            Version, ports, timeouts, auth constants
│   ├── keycodes.py             Platform-neutral key code enum
│   └── messages.py             Message types, binary encode/decode
├── server/                     macOS server (Python 3.12, asyncio)
│   ├── main.py                 Entry point, CLI argument parsing, PIN display
│   ├── __main__.py             python -m server hook
│   ├── config.py               ServerConfig (CLI / env / defaults)
│   ├── permissions.py          Accessibility permission gate
│   ├── discovery.py            LAN IP detection, mDNS advertiser (v1.6.0)
│   ├── auth.py                 PIN authentication, rate limiting (v1.6.0)
│   ├── transport/
│   │   ├── tcp_server.py       TCP control plane with auth flow (v1.6.0)
│   │   └── udp_server.py       UDP data plane
│   └── input/
│       ├── keyboard.py         CGEvent keyboard injection
│       ├── mouse.py            CGEvent mouse injection
│       └── actions.py          System actions (lock, sleep, shutdown, restart, spotlight, siri)
├── android/                    Android client (Kotlin, Jetpack Compose)
│   └── app/src/main/java/com/iobus/client/
│       ├── protocol/           Constants, KeyCodes, Messages (v2 with auth types)
│       ├── network/            TCP/UDP clients, ConnectionManager, SavedServersStore
│       ├── input/              TouchProcessor, KeyProcessor
│       ├── security/           PasscodeStore (SHA-256), PinStore (encrypted, v1.6.0)
│       ├── discovery/          MdnsDiscovery (NSD, v1.6.0)
│       └── ui/                 Compose UI (connection, control, theme)
├── notes/                      Architecture and design notes
├── iobus.command               macOS double-click launcher script
└── pyproject.toml              Python project metadata and tool config
```

## Prerequisites

### macOS Server

| Requirement              | Detail                                                   |
| ------------------------ | -------------------------------------------------------- |
| macOS                    | 10.9 or later (uses CGEvent and AXIsProcessTrusted APIs) |
| Python                   | 3.12 or later                                            |
| pip                      | Included with Python 3.12                                |
| Accessibility permission | Required for input injection; granted at first run       |

### Android Client

| Requirement    | Detail                                                              |
| -------------- | ------------------------------------------------------------------- |
| Android Studio | Recent stable release (tested with Ladybug and later)               |
| JDK            | 17                                                                  |
| Compile SDK    | 36                                                                  |
| Minimum SDK    | 29 (Android 10)                                                     |
| Device         | Physical device recommended; emulator will work for UI testing only |

## Setup

### macOS Server

```bash
# 1. Clone and enter the repository
git clone https://github.com/abhinavagarwal31/iobus.git
cd IOBus

# 2. Create and activate a virtual environment
python3 -m venv .venv
source .venv/bin/activate

# 3. Install dependencies
pip install -r server/requirements.txt

# 4. Run the server
python -m server
```

On first launch, macOS will prompt for Accessibility permission. Grant it at **System Settings > Privacy & Security > Accessibility**. If the terminal application is not listed, add it manually and restart the server.

The server prints connection details on startup:

```
═══════════════════════════════════════════════
  PIN Authentication Enabled
  Pairing PIN: 482 915
═══════════════════════════════════════════════
╔══════════════════════════════════════════════╗
║            SERVER READY                      ║
╠══════════════════════════════════════════════╣
║  IP Address : 192.168.x.x                   ║
║  TCP Port   : 9800                           ║
║  UDP Port   : 9801                           ║
╚══════════════════════════════════════════════╝
```

**v1.6.0 Security Note**: The server generates a random 6-digit PIN displayed on startup. Enter this PIN on the Android app when prompted. The PIN is stored encrypted on the device after successful authentication, so you won't need to enter it again unless the server PIN changes.

Server CLI options:

| Flag                      | Default | Description                           |
| ------------------------- | ------- | ------------------------------------- |
| `--tcp-port`              | 9800    | TCP control plane port                |
| `--udp-port`              | 9801    | UDP data plane port                   |
| `--bind`                  | 0.0.0.0 | Bind address                          |
| `--log-level`             | INFO    | DEBUG, INFO, WARNING, or ERROR        |
| `--skip-permission-check` | off     | Skip Accessibility check (debug only) |

Environment variable overrides:

- `IOBUS_TCP_PORT`, `IOBUS_UDP_PORT`, `IOBUS_BIND_ADDRESS`, `IOBUS_LOG_LEVEL`
- `IOBUS_KEEPALIVE_INTERVAL` (default: 10s), `IOBUS_KEEPALIVE_TIMEOUT_MULT` (default: 12, gives 120s grace)
- `IOBUS_PIN_ENABLED` (default: true), `IOBUS_MDNS_ENABLED` (default: true)
- `IOBUS_MDNS_HOSTNAME` (default: system hostname)

Alternatively, double-click `iobus.command` to start the server in the background. Logs are written to `/tmp/iobus-server.log`.

### Android Client

1. Open the `android/` directory in Android Studio.
2. Let Gradle sync complete.
3. Connect a physical Android device with USB debugging enabled.
4. Select the **debug** build variant.
5. Click **Run** to build and install.
6. (v1.6.0) The app will auto-discover servers via mDNS. Tap a discovered server or manually enter the IP address.
7. Tap **Connect** and enter the 6-digit PIN shown on the server when prompted.
8. The PIN is saved encrypted on the device, so subsequent connections are automatic.

Required Android permissions (granted at install time):

- `INTERNET` - Network communication
- `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE` - mDNS discovery (v1.6.0)

## First Run Checklist

1. Start the macOS server and note the displayed PIN (v1.6.0).
2. Ensure both the Mac and the Android device are on the same Wi-Fi network or hotspot.
3. Launch the IOBus app on Android.
4. If mDNS discovery finds the server, tap it. Otherwise, manually enter the server IP.
5. Tap Connect and enter the 6-digit PIN when prompted.
6. Verify the status indicator shows connected (green dot).
7. Test keyboard input and trackpad movement.

## Troubleshooting

| Symptom                        | Likely Cause                          | Fix                                                                                                      |
| ------------------------------ | ------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Connection refused             | macOS firewall blocking the port      | Allow incoming connections for Python in System Settings > Firewall, or temporarily disable the firewall |
| Cannot inject input            | Accessibility permission not granted  | Open System Settings > Privacy & Security > Accessibility and ensure your terminal app is checked        |
| Authentication failed (v1.6.0) | Wrong PIN entered                     | Check the PIN displayed on the server terminal; case-sensitive, 6 digits                                 |
| No servers discovered (v1.6.0) | mDNS not working on network           | Manually enter the server IP address; some networks block mDNS/Bonjour                                   |
| Rate limited (v1.6.0)          | Too many failed auth attempts         | Wait 5 minutes, then retry with the correct PIN                                                          |
| No response after connecting   | Wrong IP address entered              | Confirm the IP printed by the server matches what you entered on Android                                 |
| UDP input not working          | Devices on different subnets or VLANs | Ensure both devices are on the same Wi-Fi network; avoid guest networks with client isolation            |
| Server exits immediately       | Python version too old                | Run `python3 --version` and confirm 3.12 or later                                                        |
| Connection drops frequently    | Weak Wi-Fi signal or network issues   | Move devices closer to router; check for interference; v1.6.0 has 120s keepalive timeout                 |

## Limitations (v1.6.0)

- No encryption of data plane traffic. Control plane uses hashed PIN auth, but UDP input events are plaintext over the local network.
- No automatic reconnection. If the connection drops, the user must reconnect manually (saved PINs and hostnames make this quick).
- Caps Lock key is displayed but non-functional. Synthetic Caps Lock injection is unreliable on macOS; deferred to v2.
- F5--F6 system actions (Dictation, Do Not Disturb) are deferred to v2. These keys currently emit standard F5--F6 key events.
- Passcode protection for power actions is enforced on the Android client only. The macOS server does not independently validate power commands.
- Single-client only. The server accepts one connection at a time.
- No cross-platform server or client support.

## Security

**v1.6.0 Security Model:**

IOBus now requires PIN authentication by default. The 6-digit PIN is displayed on the server terminal at startup and must be entered on first connection. Authentication uses SHA-256 hashing with salt + challenge to prevent replay attacks. Rate limiting (5 attempts per IP, 5-minute lockout) prevents brute force. PINs are stored encrypted on Android using Android Keystore.

**Remaining Limitations:**

- Data plane (UDP input events) is not encrypted. This is acceptable for trusted local networks.
- No TLS/SSL for TCP control plane. PIN auth provides authentication but not transport encryption.
- The system is designed for trusted environments such as a home network or personal hotspot.
- Do not expose the server port to untrusted networks or the internet.

mDNS/Bonjour advertisement exposes the server presence on the local network. This is intentional for automatic discovery. Disable with `IOBUS_MDNS_ENABLED=false` if you prefer manual IP entry only.

## Roadmap

- Extended macOS system integration (Dictation, Do Not Disturb)
- Caps Lock state synchronization
- Automatic reconnection with backoff
- Enhanced trackpad gestures
- Cross-platform client support

## Development Status (v1.6.0)

**v1.6.0** -- Security and reliability enhancements.

- **PIN Authentication**: 6-digit PIN with SHA-256 hashing, salt + challenge, rate limiting (5 attempts, 5-min lockout)
- **mDNS Auto-Discovery**: Server advertises as `_iobus._tcp.local.` with hostname, port, and auth requirements
- **Connection Reliability**: Keepalive timeout extended to 120s (was 15s), hostname resolution survives IP changes
- **Encrypted PIN Storage**: Android stores PINs encrypted using Android Keystore
- **Protocol v2**: Added 4 new message types for authentication handshake
- **Server Status Display**: PIN shown in bordered box on startup for easy pairing

**v1.5** -- Stable foundation with enhanced system integration.

- Added F4 Spotlight activation via Cmd+Space
- Redesigned radial menu to vertical scrollable layout
- Implemented fullscreen layouts for all control modes
- Added landscape layout for Control Center with horizontal sliders
- Fixed orientation lock re-application on device rotation
- Codebase refactoring: removed ~310 lines of dead code, unified protocol constants between client and server, replaced magic numbers with named constants, extracted shared helpers, renamed internal components for clarity
