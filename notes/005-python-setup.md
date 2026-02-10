# Python Setup Notes

> Python version selection, virtual environment, and dependency strategy.

---

## Python Version: 3.12

### Why 3.12?

**System inventory** (checked 2026-02-06 on this Mac):

- Python 3.14.2 — available (too new, library compatibility risk)
- Python 3.12.12 — available ✓ (selected)
- Python 3.11.14 — available (viable but older)
- Python 3.10.19 — available (minimum, lacks perf improvements)

**Selection criteria**:

1. **Stability**: 3.12 has been stable for over 2 years. Battle-tested.
2. **Performance**: 3.12 includes significant interpreter speedups (adaptive
   specialization, comprehension inlining) — matters for our event loop.
3. **Library support**: `pyobjc` (our critical dependency for macOS integration)
   fully supports 3.12. Some packages haven't caught up to 3.14 yet.
4. **Developer experience**: Better error messages, improved f-strings, type
   parameter syntax for generics.
5. **macOS compatibility**: Fully supported on Apple Silicon via Homebrew.

### What about 3.14?

Python 3.14 is available on this system, but:

- It was released relatively recently and some third-party packages
  (especially compiled ones like pyobjc) may have issues
- We gain nothing from 3.14 that justifies the risk
- If needed later, upgrading 3.12 → 3.14 is straightforward

---

## Virtual Environment

### Location

```
IOBus/.venv/     (gitignored, local to project)
```

### Creation Command

```bash
python3.12 -m venv .venv
```

### Activation

```bash
source .venv/bin/activate
```

### Why `.venv` in the project root?

- Convention recognized by VS Code, PyCharm, and most editors
- Auto-detected by VS Code's Python extension
- Easy to delete and recreate
- Clearly signals "this is project-local, not system-wide"

---

## Dependencies

### Core (required)

| Package                              | Purpose                                  |
| ------------------------------------ | ---------------------------------------- |
| pyobjc-core                          | Base Objective-C bridge for Python       |
| pyobjc-framework-Quartz              | CGEvent API for mouse/keyboard injection |
| pyobjc-framework-ApplicationServices | AXIsProcessTrusted (permissions check)   |

### Why pyobjc?

- The only reliable way to access macOS CGEvent API from Python
- Maintained, well-documented, included in many macOS Python tools
- Alternatives (ctypes to CoreGraphics) are fragile and poorly documented
- pyobjc is effectively the standard for Python ↔ macOS integration

### Development (optional, for later)

| Package | Purpose                |
| ------- | ---------------------- |
| pytest  | Testing                |
| ruff    | Linting and formatting |
| mypy    | Static type checking   |

### What we deliberately avoid

- **pynput**: Abstracts too much, adds latency, cross-platform overhead we don't need
- **keyboard**: Requires root on some systems, limited macOS support
- **mouse**: Same concerns as keyboard
- **websockets**: We need raw UDP, not WebSocket framing
- **flask/fastapi**: We're not building an HTTP server

---

## Requirements File

Dependencies are pinned in `server/requirements.txt`.

Format:

```
# Core dependencies
pyobjc-core>=10.0
pyobjc-framework-Quartz>=10.0
pyobjc-framework-ApplicationServices>=10.0
```

We pin minimum versions, not exact versions, to allow patch updates
while preventing major version surprises.
