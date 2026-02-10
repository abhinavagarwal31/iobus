# IOBus

Low-latency wireless Android → macOS remote control system.

A phone acts as a full MacBook-style keyboard and trackpad for your Mac,
communicating over local Wi-Fi with no internet dependency.

## Project Status

**v1 — Complete (server + Android client implemented)**

## Architecture

```
┌────────────────────┐         Wi-Fi LAN          ┌────────────────────┐
│   Android Phone    │  ──── UDP (input events) ──→│   macOS Server     │
│   (Kotlin/Compose) │  ──── TCP (control/hs)  ──→│   (Python 3.12)    │
│                    │                             │                    │
│  ┌──────────────┐  │                             │  ┌──────────────┐  │
│  │  Trackpad UI │  │   MouseMove/Click/Scroll    │  │ MouseController │
│  │  Keyboard UI │  │   KeyEvent (down/up+mods)   │  │ KeyboardController│
│  │  Connection  │  │   Handshake/Keepalive/Disc  │  │ SystemActions │  │
│  └──────────────┘  │                             │  └──────────────┘  │
└────────────────────┘                             └────────────────────┘
```

## Project Structure

```
IOBus/
├── server/                  # macOS host (Python 3.12)
│   ├── transport/           # TCP control plane + UDP data plane
│   │   ├── tcp_server.py    # Handshake, keepalive, single-client
│   │   └── udp_server.py    # Stateless input event dispatch
│   ├── input/               # macOS input injection (CGEvent API)
│   │   ├── mouse.py         # Move, click, scroll, drag
│   │   ├── keyboard.py      # Key down/up with full modifier support
│   │   └── actions.py       # Lock screen, power dialog, sleep
│   ├── main.py              # Entry point with argparse
│   ├── config.py            # ServerConfig dataclass
│   ├── permissions.py       # Accessibility permission check
│   └── discovery.py         # LAN IP detection + banner
├── protocol/                # Shared protocol (platform-neutral)
│   ├── messages.py          # 12 message types, binary encode/decode
│   ├── keycodes.py          # 95 platform-neutral key codes
│   └── constants.py         # Version, ports, timeouts
├── android/                 # Android client (Kotlin + Jetpack Compose)
│   └── app/src/main/java/com/iobus/client/
│       ├── MainActivity.kt  # Entry + navigation (NavHost)
│       ├── protocol/        # Constants, KeyCodes, Messages (mirrors Python)
│       ├── network/         # TcpClient, UdpClient, ConnectionManager
│       ├── input/           # TouchProcessor, KeyProcessor
│       └── ui/
│           ├── theme/       # HUD dark theme (Color, Type, Shape, Theme)
│           ├── connection/  # ConnectionScreen (IP input, status)
│           └── control/     # ControlScreen, TrackpadSurface, KeyboardPanel
├── notes/                   # Architecture docs, decision log
└── .venv/                   # Python virtual environment (not committed)
```

## Requirements

- **macOS server**: Python 3.12, macOS Accessibility permission granted
- **Android client**: Android 10+ (API 29+), landscape phone
- **Network**: Same Wi-Fi network (or phone hotspot)

## Quick Start — Server

```bash
# Activate virtual environment
source .venv/bin/activate

# Run server
python -m server

# With options
python -m server --bind 0.0.0.0 --tcp-port 9800 --log-level DEBUG
```

The server will print the connection IP and ports. Grant Accessibility permission
when prompted (System Settings → Privacy & Security → Accessibility).

## Quick Start — Android Client

1. Open `android/` in Android Studio
2. Build and run on your phone
3. Enter the server IP shown in the terminal
4. Tap CONNECT

## Protocol

Binary, 4-byte header: `[version:u8][type:u8][payload_len:u16be]`

- **TCP** (port 9800): Handshake, keepalive (PING/PONG), disconnect
- **UDP** (port 9801): MouseMove, MouseClick, MouseScroll, MouseDrag, KeyEvent

## License

TBD
