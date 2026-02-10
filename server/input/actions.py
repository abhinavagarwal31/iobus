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
