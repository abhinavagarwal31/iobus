"""
System / power actions.

Responsibilities:
- Lock screen: inject Ctrl + Cmd + Q
- Show power dialog: inject Ctrl + Eject (simulated)
- Sleep: invoke system sleep via pmset
- Compose actions from keyboard.py primitives where possible
- These are software-equivalent actions, not hardware control
"""

from __future__ import annotations

import ctypes
import logging
import re
import subprocess

from protocol.keycodes import ProtocolKeyCode
from protocol.messages import ModifierFlag
from server.input.keyboard import KeyboardController

logger = logging.getLogger(__name__)


def _run_applescript(script: str, action_name: str, timeout: int = 5) -> bool:
    """Run an AppleScript command, logging success/failure.

    Returns True on success, False on failure.
    """
    try:
        subprocess.run(
            ["osascript", "-e", script],
            check=True,
            capture_output=True,
            timeout=timeout,
        )
        return True
    except (subprocess.SubprocessError, FileNotFoundError):
        logger.error("Failed to %s via AppleScript", action_name)
        return False


class SystemActions:
    """High-level macOS system actions composed from keyboard shortcuts or shell commands."""

    def __init__(self, keyboard: KeyboardController) -> None:
        self._kb = keyboard

    @staticmethod
    def get_brightness() -> float:
        """Get the current display brightness (0.0–1.0).

        Strategy 1: DisplayServices private framework (ctypes) — works on Apple Silicon.
        Strategy 2: Quartz CoreGraphics (pyobjc) — works on Intel Macs.
        Strategy 3: ioreg — legacy fallback for older Apple displays.
        Falls back to 0.5 if all fail.
        """
        # Strategy 1: DisplayServices (no extra deps, works on Apple Silicon M-series)
        try:
            ds = ctypes.cdll.LoadLibrary(
                '/System/Library/PrivateFrameworks/DisplayServices.framework/DisplayServices'
            )
            ds.DisplayServicesGetBrightness.restype = ctypes.c_int
            ds.DisplayServicesGetBrightness.argtypes = [ctypes.c_uint32, ctypes.POINTER(ctypes.c_float)]
            val = ctypes.c_float()
            # Display ID 1 = built-in retina display on Apple Silicon
            ret = ds.DisplayServicesGetBrightness(1, ctypes.byref(val))
            if ret == 0 and 0.0 <= val.value <= 1.0:
                return float(val.value)
        except Exception:
            pass

        # Strategy 2: Quartz (pyobjc-framework-Quartz, works on Intel Macs)
        try:
            import Quartz  # type: ignore[import]
            display_id = Quartz.CGMainDisplayID()
            brightness = Quartz.CGDisplayGetBrightness(display_id)
            if 0.0 <= brightness <= 1.0:
                return float(brightness)
        except Exception:
            pass

        # Strategy 3: ioreg — legacy fallback for older Apple displays
        try:
            for cls in ("AppleBacklightDisplay", "AppleM1BacklightDisplay",
                        "AppleM2BacklightDisplay", "AppleM3BacklightDisplay"):
                result = subprocess.run(
                    ["ioreg", "-c", cls, "-r", "-d", "2"],
                    capture_output=True, text=True, timeout=3,
                )
                if result.returncode == 0 and result.stdout.strip():
                    m = re.search(r'"brightness"\s*=\s*([\d.]+)', result.stdout)
                    if m:
                        return max(0.0, min(1.0, float(m.group(1))))
        except Exception:
            pass

        return 0.5  # safe default

    @staticmethod
    def get_volume() -> float:
        """Get the current system volume (0.0–1.0).

        Uses AppleScript to read the output volume.
        Returns 0.5 as a fallback if the query fails.
        """
        try:
            result = subprocess.run(
                ["osascript", "-e", "output volume of (get volume settings)"],
                capture_output=True, text=True, timeout=3,
            )
            if result.returncode == 0 and result.stdout.strip():
                vol = int(result.stdout.strip())  # 0-100
                return vol / 100.0
        except (subprocess.SubprocessError, ValueError, FileNotFoundError):
            pass
        return 0.5  # safe default

    @staticmethod
    def get_mute() -> bool:
        """Return True if the system output is muted."""
        try:
            result = subprocess.run(
                ["osascript", "-e", "output muted of (get volume settings)"],
                capture_output=True, text=True, timeout=3,
            )
            if result.returncode == 0:
                return result.stdout.strip().lower() == "true"
        except (subprocess.SubprocessError, FileNotFoundError):
            pass
        return False

    @staticmethod
    def get_battery_status() -> tuple[float, bool]:
        """Get (battery_percentage, is_charging) in a single `pmset` call.

        Parses `pmset -g batt` output, e.g.:
            "Now drawing from 'AC Power'\\n -InternalBattery-0 (id=...) 99%; finishing charge; ..."
        Returns (1.0, False) as a fallback (e.g. desktop Macs with no battery, or parse failure).
        """
        try:
            result = subprocess.run(
                ["pmset", "-g", "batt"],
                capture_output=True, text=True, timeout=3,
            )
            if result.returncode == 0:
                lines = result.stdout.splitlines()
                is_charging = "AC Power" in lines[0] if lines else False
                m = re.search(r'(\d+)%', result.stdout)
                percentage = max(0.0, min(1.0, int(m.group(1)) / 100.0)) if m else 1.0
                return percentage, is_charging
        except (subprocess.SubprocessError, FileNotFoundError, IndexError):
            pass
        return 1.0, False  # safe default

    @staticmethod
    def get_screen_lock_status() -> bool:
        """Check if the screen is locked.

        Uses Quartz CGSessionCopyCurrentDictionary to detect screen lock state.
        Returns False as fallback if detection fails.
        """
        try:
            import Quartz  # type: ignore[import]
            session_dict = Quartz.CGSessionCopyCurrentDictionary()
            if session_dict:
                return bool(session_dict.get("CGSSessionScreenIsLocked", False))
        except Exception:
            logger.debug("Failed to get screen lock status", exc_info=True)
        return False

    @staticmethod
    def get_idle_time() -> float:
        """Get idle time in seconds since last user activity.

        Uses Quartz CGEventSourceSecondsSinceLastEventType to measure time
        since the last keyboard or mouse event.
        Returns 0.0 as fallback if detection fails.
        """
        try:
            import Quartz  # type: ignore[import]
            idle_seconds = Quartz.CGEventSourceSecondsSinceLastEventType(
                Quartz.kCGEventSourceStateHIDSystemState,
                Quartz.kCGAnyInputEventType
            )
            return float(idle_seconds)
        except Exception:
            logger.debug("Failed to get idle time", exc_info=True)
        return 0.0

    @staticmethod
    def get_activity_status() -> str:
        """Get activity status based on idle time.

        Returns:
            'active' - Currently using keyboard/mouse (< 2s idle)
            'idle' - Stepped away from keyboard (2s-5min)
            'away' - Left the Mac (> 5min)
        """
        idle_time = SystemActions.get_idle_time()

        if idle_time < 2:
            return "active"
        elif idle_time < 300:  # 5 minutes
            return "idle"
        else:
            return "away"

    def lock_screen(self) -> None:
        """Lock the screen via Ctrl + Cmd + Q."""
        logger.info("Action: lock screen (Ctrl+Cmd+Q)")
        self._kb.inject_key_combo(
            keycode=ProtocolKeyCode.KEY_Q,
            modifiers=ModifierFlag.CONTROL | ModifierFlag.META,
        )

    def show_power_dialog(self) -> None:
        """Show the power dialog (Shut Down / Restart / Sleep).

        On modern macOS this is Ctrl + Power button.  Since we can't simulate
        the physical Power key via CGEvent, we use the media key approach or
        fall back to an AppleScript invocation.
        """
        logger.info("Action: show power dialog")
        _run_applescript(
            'tell application "loginwindow" to «event aevtrsdn»',
            "show power dialog",
        )

    def sleep(self) -> None:
        """Put the Mac to sleep via pmset.

        This is a one-way action — the phone cannot wake the Mac remotely.
        """
        logger.info("Action: sleep (pmset sleepnow)")
        try:
            subprocess.run(
                ["pmset", "sleepnow"],
                check=True,
                capture_output=True,
                timeout=5,
            )
        except (subprocess.SubprocessError, FileNotFoundError):
            logger.error("Failed to invoke sleep via pmset")

    def shutdown(self) -> None:
        """Shut down the Mac via AppleScript.

        Uses 'tell app "System Events" to shut down' which triggers a
        graceful shutdown, prompting the user to save unsaved work.
        """
        logger.info("Action: shutdown")
        _run_applescript(
            'tell application "System Events" to shut down',
            "invoke shutdown",
        )

    def restart(self) -> None:
        """Restart the Mac via AppleScript.

        Uses 'tell app "System Events" to restart' which triggers a
        graceful restart, prompting the user to save unsaved work.
        """
        logger.info("Action: restart")
        _run_applescript(
            'tell application "System Events" to restart',
            "invoke restart",
        )

    def trigger_siri_voice(self) -> None:
        """Activate Siri in voice mode.

        Uses AppleScript to directly activate the Siri application.
        This is the most reliable cross-version method.
        """
        logger.info("Action: trigger Siri voice mode")
        if not _run_applescript('tell application "Siri" to activate', "trigger Siri", timeout=3):
            # Fallback: open Siri via launch services
            try:
                subprocess.run(
                    ["open", "-a", "Siri"],
                    check=True,
                    capture_output=True,
                    timeout=3,
                )
            except (subprocess.SubprocessError, FileNotFoundError):
                logger.error("Failed to trigger Siri voice mode via all methods")

    def trigger_spotlight(self) -> None:
        """Activate Spotlight search.

        Uses Cmd+Space keyboard shortcut, which is the standard macOS
        Spotlight activation method. Fast and reliable via CGEvent.
        """
        logger.info("Action: trigger Spotlight (Cmd+Space)")
        self._kb.inject_key_combo(
            keycode=ProtocolKeyCode.KEY_SPACE,
            modifiers=ModifierFlag.META,
        )

    def launch_app(self, app_name: str) -> None:
        """Launch a macOS application by name.

        Uses 'open -a <AppName>' which respects the standard macOS
        application resolution (Applications folder, Spotlight index).
        """
        logger.info("Action: launch app '%s'", app_name)
        try:
            subprocess.Popen(
                ["open", "-a", app_name],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        except (subprocess.SubprocessError, FileNotFoundError):
            logger.error("Failed to launch app '%s'", app_name)
