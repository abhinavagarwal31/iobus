# IOBus (Temporary Codename)

Low-latency wireless remote control system that turns an Android phone into a full keyboard and trackpad for macOS. Communicates over local Wi-Fi using a custom binary protocol with no internet dependency.

## Features (v1)

- Trackpad with move, tap, two-finger scroll, right-click, and drag
- Full on-screen keyboard with modifier tracking (Shift, Ctrl, Alt, Cmd) and function keys
- System controls: brightness, volume, media playback, screen lock, power actions
- Combined split-screen mode (trackpad + keyboard side-by-side in landscape)
- Saved server presets for quick reconnection
- Passcode-protected shutdown/restart actions
- Single-client TCP handshake with UDP data plane for minimal latency

## Architecture

```
Android Phone (Kotlin/Compose)          macOS Server (Python 3.12)
+--------------------------+            +--------------------------+
| TouchProcessor           | -- UDP --> | MouseController (CGEvent)|
| KeyProcessor             | -- UDP --> | KeyboardController       |
| ConnectionManager        | -- TCP --> | TCPControlServer         |
| ControlsPanel            | -- UDP --> | SystemActions            |
+--------------------------+            +--------------------------+
```

- **TCP (port 9800)**: Handshake, keepalive, disconnect, system state queries, app launch responses
- **UDP (port 9801)**: Mouse events, key events, system actions, app launch commands
- **Wire format**: 4-byte header `[version:u8][type:u8][payload_len:u16be]` + variable payload

## Project Structure

```
IOBus/
├── protocol/                # Shared protocol definitions (Python, mirrored in Kotlin)
│   ├── messages.py          # Message types, binary encode/decode
│   ├── keycodes.py          # Platform-neutral key codes
│   └── constants.py         # Version, ports, timeouts
├── server/                  # macOS server (Python 3.12, asyncio)
│   ├── main.py              # Entry point
│   ├── config.py            # ServerConfig with CLI/env overrides
│   ├── permissions.py       # Accessibility permission gate
│   ├── discovery.py         # LAN IP detection
│   ├── transport/           # TCP + UDP server implementations
│   └── input/               # CGEvent injection (mouse, keyboard, system actions)
├── android/                 # Android client (Kotlin, Jetpack Compose)
│   └── app/src/main/java/com/iobus/client/
│       ├── protocol/        # Constants, KeyCodes, Messages (mirrors Python)
│       ├── network/         # TCP/UDP clients, ConnectionManager
│       ├── input/           # Touch and key event processors
│       ├── security/        # Passcode store (SHA-256 hashed)
│       └── ui/              # Compose UI (connection, controls, keyboard, trackpad)
└── notes/                   # Architecture and design documentation
```

## Running the macOS Server

Requires Python 3.12+ and macOS Accessibility permission.

```bash
# Create and activate virtual environment
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -r server/requirements.txt

# Run server
python -m server

# With options
python -m server --tcp-port 9800 --udp-port 9801 --log-level INFO
```

Grant Accessibility permission when prompted: System Settings > Privacy & Security > Accessibility.

The server prints the local IP and ports on startup. Both the phone and Mac must be on the same Wi-Fi network.

## Building the Android App

Requires Android Studio with SDK 36 and JDK 17.

1. Open the `android/` directory in Android Studio
2. Sync Gradle
3. Build and run on a device running Android 10+ (API 29)
4. Enter the server IP shown in the terminal and tap Connect

## Permissions

| Platform | Permission           | Purpose                              |
| -------- | -------------------- | ------------------------------------ |
| macOS    | Accessibility        | Required for CGEvent input injection |
| Android  | INTERNET             | TCP/UDP socket communication         |
| Android  | ACCESS_WIFI_STATE    | Local network discovery              |
| Android  | ACCESS_NETWORK_STATE | Connection status detection          |

## Development Status

v1 -- feature complete, stabilized.

## License

TBD
