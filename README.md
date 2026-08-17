# IOBus

A wireless Android-to-macOS remote control system with automatic discovery.

## Overview

IOBus turns an Android phone into a keyboard, trackpad, and system controller for macOS. It communicates over local Wi-Fi using a custom binary protocol. No internet connection is required.

The Android client captures touch and key input, encodes it into binary messages using a 4-byte header format, and sends them to a Python server running on macOS. The server injects input events into macOS via CGEvent through the Accessibility API. TCP carries the control plane (handshake, keepalive); UDP carries the data plane (input events).

## Features

- **Auto-Discovery**: mDNS/Bonjour service advertisement, server hostname resolution survives IP changes
- **Connection Reliability**: Extended keepalive timeout (120s grace period), automatic reconnection with exponential backoff on unexpected drops, foreground service + wake lock keep the socket alive while the app is backgrounded
- **Live Mac status HUD**: battery percentage and charging state, brightness, volume/mute, screen-lock status, and idle/activity — synced on connect and pushed automatically whenever a value changes on the Mac (no polling — event-driven with a 500 ms watcher)
- Full on-screen keyboard with modifier tracking (Shift, Ctrl, Alt, Cmd)
- Function key row with media key mappings for F1, F2, F3 (Mission Control), F4 (Spotlight), F7--F12
- Trackpad with single-finger move, tap-to-click, two-finger scroll, two-finger tap for right-click, and long-press drag
- Portrait orientation on launch for one-handed trackpad use; switches to landscape only for modes that need it (keyboard, split-screen)
- Combined split-screen mode (trackpad + keyboard side-by-side in landscape)
- Control Center for brightness (with max/min quick-set), volume (with max/min quick-set), and media playback — play/pause/next/prev plus 10-second seek back/forward (arrow-key scrubbing) — adapts to portrait (vertical sliders) and landscape (horizontal sliders)
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
| ConnectionManager        -- TCP ----------+--->| TCPControlServer   (handshake, keepalive) |
| ControlsPanel            -- UDP ----------+--->| SystemActions      (AppleScript, pmset)   |
| MdnsDiscovery (NSD)      -- mDNS ---------+--->| MdnsAdvertiser     (_iobus._tcp.local.)   |
+-------------------------------------------+    +-------------------------------------------+
```

**TCP (port 9800)** -- Handshake, keepalive (ping/pong), disconnect, system state push (brightness, volume, mute, battery percentage/charging state, screen-lock status, idle time), app launch commands. The server pushes a `SYSTEM_STATE_RESPONSE` immediately on handshake and again whenever any of these values changes, so the app's HUD and slider positions are always in sync.

**UDP (port 9801)** -- Mouse move/click/scroll/drag, key events, system actions, app launch commands.

**Wire format** -- 4-byte header `[version:u8][type:u8][payload_len:u16be]` followed by a variable-length payload. All multi-byte values are big-endian. Encoding and decoding use Python `struct` and Kotlin `ByteBuffer` with no JSON involved.

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
│   ├── main.py                 Entry point, CLI argument parsing
│   ├── __main__.py             python -m server hook
│   ├── config.py               ServerConfig (CLI / env / defaults)
│   ├── permissions.py          Accessibility permission gate
│   ├── discovery.py            LAN IP detection, mDNS advertiser
│   ├── auth.py                 PIN authentication, rate limiting (experimental, see Coming Soon)
│   ├── transport/
│   │   ├── tcp_server.py       TCP control plane
│   │   └── udp_server.py       UDP data plane
│   └── input/
│       ├── keyboard.py         CGEvent keyboard injection
│       ├── mouse.py            CGEvent mouse injection
│       └── actions.py          System actions (lock, sleep, shutdown, restart, spotlight, siri)
├── android/                    Android client (Kotlin, Jetpack Compose)
│   └── app/src/main/java/com/iobus/client/
│       ├── protocol/           Constants, KeyCodes, Messages
│       ├── network/            TCP/UDP clients, ConnectionManager, SavedServersStore
│       ├── input/              TouchProcessor, KeyProcessor
│       ├── security/           PasscodeStore (SHA-256), PinStore (encrypted, experimental)
│       ├── discovery/          MdnsDiscovery (NSD)
│       ├── service/            ConnectionService — foreground service + wake lock for background persistence
│       ├── haptics/            HapticManager — vibration feedback on key/trackpad taps
│       └── ui/                 Compose UI (connection, control, theme)
├── notes/                      Internal architecture and design notes (not part of the public repo)
├── iobus.command               macOS double-click launcher script (starts the server)
├── iobus-stop.command          macOS double-click stop script (SIGTERM via saved PID file)
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
╔══════════════════════════════════════════════╗
║            SERVER READY                      ║
╠══════════════════════════════════════════════╣
║  IP Address : 192.168.x.x                   ║
║  TCP Port   : 9800                           ║
║  UDP Port   : 9801                           ║
╚══════════════════════════════════════════════╝
```

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
- `IOBUS_MDNS_ENABLED` (default: true), `IOBUS_MDNS_HOSTNAME` (default: system hostname)
- `IOBUS_PIN_ENABLED` (default: false) — experimental PIN authentication, still under active development; see [Coming Soon](#coming-soon)

Alternatively, double-click `iobus.command` to start the server in the background. Logs are written to `/tmp/iobus-server.log`, and the process ID to `/tmp/iobus-server.pid`. Double-click `iobus-stop.command` to stop it — it reads the PID file, sends `SIGTERM`, waits up to 5s for graceful shutdown, and cleans up the PID file.

### Android Client

1. Open the `android/` directory in Android Studio.
2. Let Gradle sync complete.
3. Connect a physical Android device with USB debugging enabled.
4. Select the **debug** build variant.
5. Click **Run** to build and install.
6. The app will auto-discover servers via mDNS. Tap a discovered server or manually enter the IP address.
7. Tap **Connect**.
8. The app auto-reconnects to the last server with exponential backoff if the connection drops unexpectedly.

Required Android permissions (most granted automatically at install time; a couple are requested at runtime):

- `INTERNET` - Network communication
- `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE` - mDNS discovery
- `VIBRATE` - Haptic feedback on key/trackpad taps
- `WAKE_LOCK`, `FOREGROUND_SERVICE` - Keep the connection alive while the app is backgrounded
- `POST_NOTIFICATIONS` - Persistent notification for the background connection service (runtime prompt on Android 13+)
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - Ask the user to exempt the app from battery optimization so background persistence isn't killed

## First Run Checklist

1. Start the macOS server and note the printed IP/ports.
2. Ensure both the Mac and the Android device are on the same Wi-Fi network or hotspot.
3. Launch the IOBus app on Android.
4. If mDNS discovery finds the server, tap it. Otherwise, manually enter the server IP.
5. Tap Connect.
6. Verify the status indicator shows connected (green dot).
7. Test keyboard input and trackpad movement.

## Troubleshooting

| Symptom                        | Likely Cause                          | Fix                                                                                                      |
| ------------------------------ | ------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Connection refused             | macOS firewall blocking the port      | Allow incoming connections for Python in System Settings > Firewall, or temporarily disable the firewall |
| Cannot inject input            | Accessibility permission not granted  | Open System Settings > Privacy & Security > Accessibility and ensure your terminal app is checked        |
| No servers discovered          | mDNS not working on network           | Manually enter the server IP address; some networks block mDNS/Bonjour                                   |
| No response after connecting   | Wrong IP address entered              | Confirm the IP printed by the server matches what you entered on Android                                 |
| UDP input not working          | Devices on different subnets or VLANs | Ensure both devices are on the same Wi-Fi network; avoid guest networks with client isolation            |
| Server exits immediately       | Python version too old                | Run `python3 --version` and confirm 3.12 or later                                                        |
| Connection drops frequently    | Weak Wi-Fi signal or network issues   | Move devices closer to router; check for interference; 120s keepalive timeout, then auto-reconnect kicks in |

## Limitations

- No encryption of data plane traffic. UDP input events are plaintext over the local network.
- Caps Lock key is displayed but non-functional. Synthetic Caps Lock injection is unreliable on macOS; deferred to v2.
- F5--F6 system actions (Dictation, Do Not Disturb) currently emit standard F5--F6 key events rather than triggering the intended system feature; see [Coming Soon](#coming-soon).
- Passcode protection for power actions is enforced on the Android client only. The macOS server does not independently validate power commands.
- Single-client only. The server accepts one connection at a time.
- No cross-platform server or client support.
- No automated tests (server or Android).

## Security

The system is designed for trusted local networks only — a home network or a personal phone hotspot.

- No TLS/SSL on either channel. Do not expose the server port to untrusted networks or the internet.
- UDP input events are unauthenticated and unencrypted; the server trusts any datagram whose source IP matches the currently connected TCP client.
- PIN authentication exists in the codebase but is experimental and off by default — see [Coming Soon](#coming-soon).
- mDNS/Bonjour advertisement exposes the server's presence on the local network. This is intentional for automatic discovery. Disable with `IOBUS_MDNS_ENABLED=false` if you prefer manual IP entry only.

## Coming Soon

Built but not yet ready for general use, or not yet started:

- **PIN authentication** — SHA-256 challenge-response handshake, per-IP rate limiting, and encrypted on-device PIN storage are implemented, but the feature is still being hardened and isn't recommended for use yet. Stays off by default (`IOBUS_PIN_ENABLED=false`) until it's ready.
- **Extended macOS system integration** — Dictation and Do Not Disturb toggles for the F5/F6 keys.
- **Caps Lock state synchronization**.
- **Instant reconnect on Wi-Fi return**, rather than waiting out an in-flight backoff delay.
- **Automated test coverage** for the server and Android client.
- **Enhanced trackpad gestures**.
- **Cross-platform client support** (the shared protocol is designed to allow this without protocol changes).

## Development Status

**Since v1.6.0:**

- Mac battery percentage and charging state, surfaced in the app's live status HUD
- Media tab seek controls (10s back/forward via arrow-key scrubbing), alongside play/pause/next/prev
- Portrait orientation on launch for one-handed trackpad use; landscape only when a mode needs it
- `iobus-stop.command` companion script for graceful server shutdown
- Faster charging-state detection
- Background persistence via a foreground service + wake lock, and the live Mac status HUD itself
- mDNS discovery and background persistence — previously listed as future work — are now implemented

**v1.6.0** -- Security and reliability enhancements.

- **PIN Authentication** (experimental): 6-digit PIN with SHA-256 hashing, salt + challenge, rate limiting (5 attempts, 5-min lockout)
- **mDNS Auto-Discovery**: Server advertises as `_iobus._tcp.local.` with hostname, port, and auth requirements
- **Connection Reliability**: Keepalive timeout extended to 120s (was 15s), hostname resolution survives IP changes
- **Encrypted PIN Storage**: Android stores PINs encrypted using Android Keystore
- **Server Status Display**: Connection details shown in a bordered box on startup for easy pairing

**v1.5** -- Stable foundation with enhanced system integration.

- Added F4 Spotlight activation via Cmd+Space
- Redesigned radial menu to vertical scrollable layout
- Implemented fullscreen layouts for all control modes
- Added landscape layout for Control Center with horizontal sliders
- Fixed orientation lock re-application on device rotation
- Codebase refactoring: removed ~310 lines of dead code, unified protocol constants between client and server, replaced magic numbers with named constants, extracted shared helpers, renamed internal components for clarity
