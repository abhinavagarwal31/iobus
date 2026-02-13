# IOBus

A wireless Android-to-macOS remote control system.

## Overview

IOBus turns an Android phone into a keyboard, trackpad, and system controller for macOS. It communicates over local Wi-Fi using a custom binary protocol. No internet connection is required.

The Android client captures touch and key input, encodes it into binary messages using a 4-byte header format, and sends them to a Python server running on macOS. The server injects input events into macOS via CGEvent through the Accessibility API. TCP carries the control plane (handshake, keepalive); UDP carries the data plane (input events).

## Features

- Full on-screen keyboard with modifier tracking (Shift, Ctrl, Alt, Cmd)
- Function key row with media key mappings for F1, F2, F7--F12. F3--F6 system-level media actions are deferred to v2; these keys currently emit standard F3--F6 key events
- Trackpad with single-finger move, tap-to-click, two-finger scroll, two-finger tap for right-click, and long-press drag
- Combined split-screen mode (trackpad + keyboard side-by-side in landscape)
- Control Center for brightness, volume, media playback, screen lock, and power actions
- Passcode-gated shutdown and restart (SHA-256 hashed, stored locally on device)
- Persistent TCP connection with server-driven keepalive (5-second interval, 15-second timeout)
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
+-------------------------------------------+    +-------------------------------------------+
```

**TCP (port 9800)** -- Handshake, keepalive (ping/pong), disconnect, system state queries, app launch commands.

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
│   ├── constants.py            Version, ports, timeouts
│   ├── keycodes.py             Platform-neutral key code enum
│   └── messages.py             Message types, binary encode/decode
├── server/                     macOS server (Python 3.12, asyncio)
│   ├── main.py                 Entry point, CLI argument parsing
│   ├── __main__.py             python -m server hook
│   ├── config.py               ServerConfig (CLI / env / defaults)
│   ├── permissions.py          Accessibility permission gate
│   ├── discovery.py            LAN IP detection
│   ├── transport/
│   │   ├── tcp_server.py       TCP control plane
│   │   └── udp_server.py       UDP data plane
│   └── input/
│       ├── keyboard.py         CGEvent keyboard injection
│       ├── mouse.py            CGEvent mouse injection
│       └── actions.py          System actions (lock, sleep, shutdown, restart)
├── android/                    Android client (Kotlin, Jetpack Compose)
│   └── app/src/main/java/com/iobus/client/
│       ├── protocol/           Constants, KeyCodes, Messages
│       ├── network/            TCP/UDP clients, ConnectionManager, SavedServersStore
│       ├── input/              TouchProcessor, KeyProcessor
│       ├── security/           PasscodeStore (SHA-256)
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

Environment variable overrides (`IOBUS_TCP_PORT`, `IOBUS_UDP_PORT`, `IOBUS_BIND_ADDRESS`, `IOBUS_LOG_LEVEL`, `IOBUS_KEEPALIVE_INTERVAL`, `IOBUS_KEEPALIVE_TIMEOUT_MULT`) are also supported.

Alternatively, double-click `iobus.command` to start the server in the background. Logs are written to `/tmp/iobus-server.log`.

### Android Client

1. Open the `android/` directory in Android Studio.
2. Let Gradle sync complete.
3. Connect a physical Android device with USB debugging enabled.
4. Select the **debug** build variant.
5. Click **Run** to build and install.
6. Enter the server IP address displayed in the terminal.
7. Tap **Connect**.

No runtime permissions need to be granted on Android. The app uses `INTERNET`, `ACCESS_WIFI_STATE`, and `ACCESS_NETWORK_STATE`, all of which are granted at install time.

## First Run Checklist

1. Start the macOS server and confirm the IP address is printed.
2. Ensure both the Mac and the Android device are on the same Wi-Fi network or hotspot.
3. Launch the IOBus app on Android.
4. Enter the server IP and tap Connect.
5. Verify the status indicator shows connected (green dot).
6. Test keyboard input and trackpad movement.

## Troubleshooting

| Symptom                      | Likely Cause                          | Fix                                                                                                      |
| ---------------------------- | ------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Connection refused           | macOS firewall blocking the port      | Allow incoming connections for Python in System Settings > Firewall, or temporarily disable the firewall |
| Cannot inject input          | Accessibility permission not granted  | Open System Settings > Privacy & Security > Accessibility and ensure your terminal app is checked        |
| No response after connecting | Wrong IP address entered              | Confirm the IP printed by the server matches what you entered on Android                                 |
| UDP input not working        | Devices on different subnets or VLANs | Ensure both devices are on the same Wi-Fi network; avoid guest networks with client isolation            |
| Server exits immediately     | Python version too old                | Run `python3 --version` and confirm 3.12 or later                                                        |

## Limitations (v1)

- No encryption. All traffic is plaintext over the local network.
- No automatic reconnection. If the connection drops, the user must reconnect manually (saved presets make this quick).
- Caps Lock key is displayed but non-functional. Synthetic Caps Lock injection is unreliable on macOS; deferred to v2.
- F3--F6 media actions (Mission Control, Spotlight, Dictation, Do Not Disturb) are deferred to v2. These keys work as standard F3--F6 when Fn is held.
- Passcode protection for power actions is enforced on the Android client only. The macOS server does not independently validate power commands.
- Single-client only. The server accepts one connection at a time.
- No cross-platform server or client support.

## Security

IOBus communicates over the local network only. The protocol includes no encryption or transport-layer security in v1. There is no authentication beyond the initial protocol handshake. The passcode gate for power actions is enforced on the Android client and is not a server-side security boundary. The system is designed for trusted environments such as a home network or personal hotspot. Do not expose the server port to untrusted networks.

## Roadmap

- Extended macOS system integration (Mission Control, Spotlight, Dictation, Do Not Disturb)
- Caps Lock state synchronization
- Automatic reconnection with backoff
- Enhanced trackpad gestures
- Cross-platform client support

## Development Status (v1)

**v1** -- Stable foundation.
