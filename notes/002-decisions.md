# Decision Log

> All non-trivial technical decisions are recorded here with rationale.

---

## DEC-001: Python Version — 3.12

**Date**: 2026-02-06
**Status**: Accepted

**Context**:
The macOS server needs a Python version that is:

- Stable and well-supported on macOS (Apple Silicon / ARM64)
- Compatible with `pyobjc` for Objective-C bridging
- Compatible with Quartz/CoreGraphics for input injection
- Modern enough for type hints, match statements, and async patterns

**Options considered**:
| Version | Available | Notes |
|---------|-----------|-----------------------------------------------------|
| 3.10 | Yes | Minimum viable. match statement introduced. |
| 3.11 | Yes | Faster. Good stability. |
| 3.12 | Yes | Current stable. Best perf. Improved error messages. |
| 3.14 | Yes | Too new. Libraries may not fully support. |

**Decision**: Python 3.12

**Rationale**:

- 3.12 is the current long-term stable release as of early 2026
- Excellent library ecosystem compatibility (pyobjc, etc.)
- Significant performance improvements over 3.10/3.11
- f-string improvements and better error messages aid development
- Avoids the bleeding edge of 3.14 where third-party packages may lag
- Available via Homebrew on the host system (`python3.12`)

---

## DEC-002: Transport Protocol — UDP for Input, TCP for Control

**Date**: 2026-02-06
**Status**: Accepted

**Context**:
We need sub-20ms latency for mouse/keyboard input. TCP's head-of-line
blocking and retransmission delays are unacceptable for high-frequency
input events (60+ mouse moves per second).

**Decision**: Dual-transport

- **UDP** for mouse movement, clicks, key events (lossy OK, low latency)
- **TCP** for handshake, version negotiation, keepalive (reliable, infrequent)

**Rationale**:

- A dropped mouse move is corrected by the next event within ~16ms
- A dropped key press is more noticeable, but UDP over LAN has ~0.01% loss
- TCP adds ~1-5ms overhead per packet under load due to Nagle/ACK delays
- The dual-transport approach is used by professional remote desktop tools

---

## DEC-003: Input Injection — Quartz Event Services (CGEvent API)

**Date**: 2026-02-06
**Status**: Accepted

**Context**:
macOS provides several ways to inject input events:

1. **CGEvent API** (Quartz Event Services) — C-level, fast, well-documented
2. **NSEvent** (AppKit) — Higher-level, requires a running NSApplication
3. **AppleScript** — Slow, limited, not suitable for real-time input
4. **HID device emulation** — Kernel-level, overly complex for this project

**Decision**: CGEvent API via Python's `Quartz` module (from `pyobjc`)

**Rationale**:

- Lowest latency path for synthetic input events
- Direct access to mouse move, click, scroll, key down/up
- Does not require an NSApplication run loop for basic usage
- Well-proven in tools like Karabiner-Elements (which also uses CGEvent)
- Requires Accessibility permission (expected and documented)

---

## DEC-004: Project Structure — Monorepo with Shared Protocol

**Date**: 2026-02-06
**Status**: Accepted

**Context**:
The system has three logical components: Android client, macOS server, and shared
protocol definitions. These could live in separate repos or a monorepo.

**Decision**: Monorepo

**Rationale**:

- Protocol changes must be synchronized between client and server
- A monorepo ensures protocol definitions are always in sync
- Simplifies CI/CD when it's added later
- Android and server have independent build systems, so no tooling conflict
- The `protocol/` directory serves as the shared contract

---

## DEC-005: No Encryption in v1

**Date**: 2026-02-06
**Status**: Accepted (will revisit in v2)

**Context**:
Encryption (TLS/DTLS) adds complexity and latency. v1 targets trusted local
networks only (home Wi-Fi, phone hotspot).

**Decision**: No encryption in v1.

**Rationale**:

- Scope control — v1 focuses on correctness and latency
- Local network traffic is not routeable from the internet
- Adding DTLS later is a transport-layer concern and won't affect the protocol
- The protocol is designed to be encryption-agnostic

---

## DEC-006: UI Design — Dark Futuristic HUD Theme

**Date**: 2026-02-06
**Status**: Accepted

**Context**:
The user explicitly requires a dark, futuristic, Tony Stark / Iron Man / HUD-style
aesthetic. Design is a first-class feature, not an afterthought.

**Decision**: The Android client will use:

- Dark background (#0A0A0A to #1A1A1A range)
- Accent colors: electric blue, cyan, subtle amber for highlights
- Thin-weight geometric sans-serif typography
- Subtle glow effects on interactive elements
- Minimal chrome, high information density
- HUD-style corners, scan lines, and data overlays as decorative elements

---

## DEC-007: async I/O — asyncio for Server

**Date**: 2026-02-06
**Status**: Accepted

**Context**:
The server must handle both TCP (control) and UDP (input) concurrently, plus
manage keepalive timers and permission checks.

**Decision**: Use Python's built-in `asyncio` module.

**Rationale**:

- Standard library — no external dependency
- Native support for UDP and TCP protocols
- Event loop integrates well with the reactive nature of input processing
- Avoids threading complexity for I/O-bound work
- `asyncio.DatagramProtocol` and `asyncio.Protocol` map cleanly to our transports
