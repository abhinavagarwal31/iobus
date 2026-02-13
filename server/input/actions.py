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

import logging
import subprocess

from protocol.messages import ModifierFlag
from server.input.keyboard import KeyboardController

logger = logging.getLogger(__name__)


class SystemActions:
    """High-level macOS system actions composed from keyboard shortcuts or shell commands."""

    def __init__(self, keyboard: KeyboardController) -> None:
        self._kb = keyboard

    @staticmethod
    def get_brightness() -> float:
        """Get the current display brightness (0.0–1.0).

        Uses AppleScript to query the system brightness.
        Returns 0.5 as a fallback if the query fails.
        """
        try:
            result = subprocess.run(
                [
                    "osascript", "-e",
                    'tell application "System Events" to get the value of slider 1 '
                    'of group 1 of group 2 of toolbar 1 of window 1 of '
                    'application process "System Preferences"',
                ],
                capture_output=True, text=True, timeout=3,
            )
            if result.returncode == 0 and result.stdout.strip():
                return float(result.stdout.strip())
        except (subprocess.SubprocessError, ValueError, FileNotFoundError):
            pass
        # Fallback: try using CoreGraphics
        try:
            from CoreGraphics import CGDisplayGetBrightness  # type: ignore[import]
            return CGDisplayGetBrightness(0)
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

    def lock_screen(self) -> None:
        """Lock the screen via Ctrl + Cmd + Q."""
        logger.info("Action: lock screen (Ctrl+Cmd+Q)")
        self._kb.inject_key_combo(
            keycode=0x14,  # KEY_Q
            modifiers=ModifierFlag.CONTROL | ModifierFlag.META,
        )

    def show_power_dialog(self) -> None:
        """Show the power dialog (Shut Down / Restart / Sleep).

        On modern macOS this is Ctrl + Power button.  Since we can't simulate
        the physical Power key via CGEvent, we use the media key approach or
        fall back to an AppleScript invocation.
        """
        logger.info("Action: show power dialog")
        try:
            subprocess.run(
                [
                    "osascript", "-e",
                    'tell application "loginwindow" to «event aevtrsdn»',
                ],
                check=True,
                capture_output=True,
                timeout=5,
            )
        except (subprocess.SubprocessError, FileNotFoundError):
            logger.error("Failed to show power dialog via AppleScript")

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
        try:
            subprocess.run(
                [
                    "osascript", "-e",
                    'tell application "System Events" to shut down',
                ],
                check=True,
                capture_output=True,
                timeout=5,
            )
        except (subprocess.SubprocessError, FileNotFoundError):
            logger.error("Failed to invoke shutdown via AppleScript")

    def restart(self) -> None:
        """Restart the Mac via AppleScript.

        Uses 'tell app "System Events" to restart' which triggers a
        graceful restart, prompting the user to save unsaved work.
        """
        logger.info("Action: restart")
        try:
            subprocess.run(
                [
                    "osascript", "-e",
                    'tell application "System Events" to restart',
                ],
                check=True,
                capture_output=True,
                timeout=5,
            )
        except (subprocess.SubprocessError, FileNotFoundError):
            logger.error("Failed to invoke restart via AppleScript")

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
