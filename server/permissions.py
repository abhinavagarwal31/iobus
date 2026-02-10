"""
macOS Accessibility permission checks.

Responsibilities:
- Check if the process has Accessibility access (AXIsProcessTrustedWithOptions)
- Prompt the user to grant access if not yet authorized
- Provide clear status reporting: granted / denied / unknown
- Never assume permissions are already granted
"""

from __future__ import annotations

import logging
import sys
from enum import Enum, auto

logger = logging.getLogger(__name__)


class PermissionStatus(Enum):
    """Result of an Accessibility permission check."""
    GRANTED = auto()
    DENIED = auto()
    UNAVAILABLE = auto()  # Not on macOS or framework missing


def check_accessibility(prompt: bool = True) -> PermissionStatus:
    """Check whether this process has macOS Accessibility access.

    Args:
        prompt: If *True* and access has not been granted, macOS will show
                the system dialog asking the user to enable it in
                System Settings → Privacy & Security → Accessibility.

    Returns:
        PermissionStatus indicating the current state.
    """
    if sys.platform != "darwin":
        logger.warning("Accessibility check skipped — not running on macOS")
        return PermissionStatus.UNAVAILABLE

    try:
        from ApplicationServices import AXIsProcessTrustedWithOptions
        from CoreFoundation import (
            CFDictionaryCreate,
            kCFAllocatorDefault,
            kCFBooleanTrue,
            kCFBooleanFalse,
        )
    except ImportError:
        logger.error(
            "pyobjc-framework-ApplicationServices is not installed. "
            "Run: pip install pyobjc-framework-ApplicationServices"
        )
        return PermissionStatus.UNAVAILABLE

    # Build the options dictionary.
    # kAXTrustedCheckOptionPrompt = "AXTrustedCheckOptionPrompt"
    prompt_key = "AXTrustedCheckOptionPrompt"
    prompt_value = kCFBooleanTrue if prompt else kCFBooleanFalse

    options = CFDictionaryCreate(
        kCFAllocatorDefault,
        [prompt_key],
        [prompt_value],
        1,
        None,
        None,
    )

    trusted: bool = AXIsProcessTrustedWithOptions(options)

    if trusted:
        logger.info("Accessibility access: GRANTED")
        return PermissionStatus.GRANTED

    logger.warning(
        "Accessibility access: DENIED — "
        "Grant access in System Settings → Privacy & Security → Accessibility"
    )
    return PermissionStatus.DENIED


def require_accessibility() -> None:
    """Check Accessibility access and exit if not granted.

    Intended for use at server startup — the server cannot function
    without the ability to inject input events.
    """
    status = check_accessibility(prompt=True)

    if status == PermissionStatus.GRANTED:
        return

    if status == PermissionStatus.DENIED:
        logger.critical(
            "\n"
            "═══════════════════════════════════════════════════════\n"
            "  Accessibility access is REQUIRED to inject input.\n"
            "\n"
            "  1. Open System Settings\n"
            "  2. Go to Privacy & Security → Accessibility\n"
            "  3. Enable access for this terminal / Python\n"
            "  4. Restart the server\n"
            "═══════════════════════════════════════════════════════"
        )
        sys.exit(1)

    if status == PermissionStatus.UNAVAILABLE:
        logger.critical("Cannot verify Accessibility permissions — aborting")
        sys.exit(1)
