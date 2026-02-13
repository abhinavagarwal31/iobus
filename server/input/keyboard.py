"""
Keyboard input injection via CGEvent API.

Responsibilities:
- Translate protocol key events into CGEvent keyboard calls
- Map protocol key codes → macOS CGKeyCode values
- Handle key down / key up as separate events
- Handle modifier keys (Cmd, Option, Control, Shift, Fn)
- Use Quartz.CGEventCreateKeyboardEvent
"""

from __future__ import annotations

import logging

import Quartz

from protocol.keycodes import ProtocolKeyCode
from protocol.messages import KeyAction, KeyEvent, ModifierFlag

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Protocol key code → macOS CGKeyCode mapping
#
# macOS virtual key codes are hardware-layout-based (US ANSI keyboard).
# Reference: Events.h / Carbon HIToolbox
# ---------------------------------------------------------------------------

_KEYCODE_MAP: dict[int, int] = {
    # Letters (A-Z)
    ProtocolKeyCode.KEY_A: 0x00,
    ProtocolKeyCode.KEY_S: 0x01,
    ProtocolKeyCode.KEY_D: 0x02,
    ProtocolKeyCode.KEY_F: 0x03,
    ProtocolKeyCode.KEY_H: 0x04,
    ProtocolKeyCode.KEY_G: 0x05,
    ProtocolKeyCode.KEY_Z: 0x06,
    ProtocolKeyCode.KEY_X: 0x07,
    ProtocolKeyCode.KEY_C: 0x08,
    ProtocolKeyCode.KEY_V: 0x09,
    ProtocolKeyCode.KEY_B: 0x0B,
    ProtocolKeyCode.KEY_Q: 0x0C,
    ProtocolKeyCode.KEY_W: 0x0D,
    ProtocolKeyCode.KEY_E: 0x0E,
    ProtocolKeyCode.KEY_R: 0x0F,
    ProtocolKeyCode.KEY_Y: 0x10,
    ProtocolKeyCode.KEY_T: 0x11,
    ProtocolKeyCode.KEY_1: 0x12,
    ProtocolKeyCode.KEY_2: 0x13,
    ProtocolKeyCode.KEY_3: 0x14,
    ProtocolKeyCode.KEY_4: 0x15,
    ProtocolKeyCode.KEY_6: 0x16,
    ProtocolKeyCode.KEY_5: 0x17,
    ProtocolKeyCode.KEY_EQUAL: 0x18,
    ProtocolKeyCode.KEY_9: 0x19,
    ProtocolKeyCode.KEY_7: 0x1A,
    ProtocolKeyCode.KEY_MINUS: 0x1B,
    ProtocolKeyCode.KEY_8: 0x1C,
    ProtocolKeyCode.KEY_0: 0x1D,
    ProtocolKeyCode.KEY_RIGHT_BRACKET: 0x1E,
    ProtocolKeyCode.KEY_O: 0x1F,
    ProtocolKeyCode.KEY_U: 0x20,
    ProtocolKeyCode.KEY_LEFT_BRACKET: 0x21,
    ProtocolKeyCode.KEY_I: 0x22,
    ProtocolKeyCode.KEY_P: 0x23,
    ProtocolKeyCode.KEY_L: 0x25,
    ProtocolKeyCode.KEY_J: 0x26,
    ProtocolKeyCode.KEY_APOSTROPHE: 0x27,
    ProtocolKeyCode.KEY_K: 0x28,
    ProtocolKeyCode.KEY_SEMICOLON: 0x29,
    ProtocolKeyCode.KEY_BACKSLASH: 0x2A,
    ProtocolKeyCode.KEY_COMMA: 0x2B,
    ProtocolKeyCode.KEY_SLASH: 0x2C,
    ProtocolKeyCode.KEY_N: 0x2D,
    ProtocolKeyCode.KEY_M: 0x2E,
    ProtocolKeyCode.KEY_PERIOD: 0x2F,

    # Special keys
    ProtocolKeyCode.KEY_RETURN: 0x24,
    ProtocolKeyCode.KEY_TAB: 0x30,
    ProtocolKeyCode.KEY_SPACE: 0x31,
    ProtocolKeyCode.KEY_BACKSPACE: 0x33,  # Delete (backspace) on Mac
    ProtocolKeyCode.KEY_ESCAPE: 0x35,
    ProtocolKeyCode.KEY_GRAVE: 0x32,

    # Modifier keys (as key codes, for standalone key events)
    ProtocolKeyCode.KEY_LEFT_META: 0x37,      # Left Cmd
    ProtocolKeyCode.KEY_LEFT_SHIFT: 0x38,
    ProtocolKeyCode.KEY_LEFT_ALT: 0x3A,       # Left Option
    ProtocolKeyCode.KEY_LEFT_CONTROL: 0x3B,
    ProtocolKeyCode.KEY_RIGHT_SHIFT: 0x3C,
    ProtocolKeyCode.KEY_RIGHT_ALT: 0x3D,      # Right Option
    ProtocolKeyCode.KEY_RIGHT_CONTROL: 0x3E,
    ProtocolKeyCode.KEY_RIGHT_META: 0x36,     # Right Cmd
    ProtocolKeyCode.KEY_FN: 0x3F,

    # Function keys
    ProtocolKeyCode.KEY_F1: 0x7A,
    ProtocolKeyCode.KEY_F2: 0x78,
    ProtocolKeyCode.KEY_F3: 0x63,
    ProtocolKeyCode.KEY_F4: 0x76,
    ProtocolKeyCode.KEY_F5: 0x60,
    ProtocolKeyCode.KEY_F6: 0x61,
    ProtocolKeyCode.KEY_F7: 0x62,
    ProtocolKeyCode.KEY_F8: 0x64,
    ProtocolKeyCode.KEY_F9: 0x65,
    ProtocolKeyCode.KEY_F10: 0x6D,
    ProtocolKeyCode.KEY_F11: 0x67,
    ProtocolKeyCode.KEY_F12: 0x6F,

    # Navigation
    ProtocolKeyCode.KEY_HOME: 0x73,
    ProtocolKeyCode.KEY_END: 0x77,
    ProtocolKeyCode.KEY_PAGE_UP: 0x74,
    ProtocolKeyCode.KEY_PAGE_DOWN: 0x79,
    ProtocolKeyCode.KEY_DELETE: 0x75,         # Forward delete
    ProtocolKeyCode.KEY_ARROW_LEFT: 0x7B,
    ProtocolKeyCode.KEY_ARROW_RIGHT: 0x7C,
    ProtocolKeyCode.KEY_ARROW_DOWN: 0x7D,
    ProtocolKeyCode.KEY_ARROW_UP: 0x7E,
}


# Media key codes use NX_KEYTYPE_* values with IOKit HID events
_MEDIA_KEY_MAP: dict[int, int] = {
    ProtocolKeyCode.KEY_MEDIA_PLAY_PAUSE: 16,  # NX_KEYTYPE_PLAY
    ProtocolKeyCode.KEY_MEDIA_NEXT: 17,        # NX_KEYTYPE_NEXT
    ProtocolKeyCode.KEY_MEDIA_PREV: 18,        # NX_KEYTYPE_PREVIOUS
    ProtocolKeyCode.KEY_MUTE: 7,               # NX_KEYTYPE_MUTE
    ProtocolKeyCode.KEY_VOLUME_UP: 0,          # NX_KEYTYPE_SOUND_UP
    ProtocolKeyCode.KEY_VOLUME_DOWN: 1,        # NX_KEYTYPE_SOUND_DOWN
    ProtocolKeyCode.KEY_BRIGHTNESS_UP: 2,      # NX_KEYTYPE_BRIGHTNESS_UP
    ProtocolKeyCode.KEY_BRIGHTNESS_DOWN: 3,    # NX_KEYTYPE_BRIGHTNESS_DOWN
}


# CGEvent modifier flag bits (CGEventFlags)
_CG_MOD_SHIFT = Quartz.kCGEventFlagMaskShift
_CG_MOD_CONTROL = Quartz.kCGEventFlagMaskControl
_CG_MOD_ALT = Quartz.kCGEventFlagMaskAlternate
_CG_MOD_CMD = Quartz.kCGEventFlagMaskCommand

_MODIFIER_TO_CG: dict[int, int] = {
    ModifierFlag.SHIFT: _CG_MOD_SHIFT,
    ModifierFlag.CONTROL: _CG_MOD_CONTROL,
    ModifierFlag.ALT: _CG_MOD_ALT,
    ModifierFlag.META: _CG_MOD_CMD,
    # FN is handled via NX_DEVICELCMDKEYMASK or not mapped to CGEventFlags
}


def _build_cg_flags(protocol_modifiers: int) -> int:
    """Convert protocol modifier bitmask to CGEventFlags."""
    flags = 0
    for proto_bit, cg_flag in _MODIFIER_TO_CG.items():
        if protocol_modifiers & proto_bit:
            flags |= cg_flag
    return flags


class KeyboardController:
    """Injects keyboard events via the macOS CGEvent API."""

    def _post_media_key(self, key_type: int, key_down: bool) -> None:
        """Post a media key event using NSEvent / IOKit HID system key event."""
        flags = 0x0A00 if key_down else 0x0B00
        data1 = (key_type << 16) | flags
        event = Quartz.NSEvent.otherEventWithType_location_modifierFlags_timestamp_windowNumber_context_subtype_data1_data2_(
            14,  # NSEventTypeSystemDefined
            (0, 0),
            0,
            0,
            0,
            None,
            8,  # NX_SUBTYPE_AUX_CONTROL_BUTTONS
            data1,
            -1,
        )
        cg_event = event.CGEvent()
        Quartz.CGEventPost(Quartz.kCGHIDEventTap, cg_event)

    def handle_key_event(self, msg: KeyEvent) -> None:
        """Process a key down or key up event."""
        # Check if it's a media key first
        media_key = _MEDIA_KEY_MAP.get(msg.keycode)
        if media_key is not None:
            key_down = msg.action == KeyAction.KEY_DOWN
            self._post_media_key(media_key, key_down)
            # Also post key-up immediately for single-press media keys
            if key_down:
                self._post_media_key(media_key, False)
            return

        cg_keycode = _KEYCODE_MAP.get(msg.keycode)
        if cg_keycode is None:
            logger.warning("Unmapped protocol keycode: 0x%04X — ignoring", msg.keycode)
            return

        key_down = msg.action == KeyAction.KEY_DOWN

        event = Quartz.CGEventCreateKeyboardEvent(None, cg_keycode, key_down)

        # Apply modifier flags if any
        if msg.modifiers:
            cg_flags = _build_cg_flags(msg.modifiers)
            Quartz.CGEventSetFlags(event, cg_flags)

        Quartz.CGEventPost(Quartz.kCGHIDEventTap, event)

    def inject_key_combo(
        self,
        keycode: int,
        modifiers: int = 0,
    ) -> None:
        """Inject a full key-down then key-up with modifiers.

        Args:
            keycode: Protocol key code (will be mapped to CGKeyCode).
            modifiers: Protocol modifier bitmask.

        Used by system actions (e.g., Ctrl+Cmd+Q for lock screen).
        """
        self.handle_key_event(
            KeyEvent(timestamp=0, action=KeyAction.KEY_DOWN, keycode=keycode, modifiers=modifiers)
        )
        self.handle_key_event(
            KeyEvent(timestamp=0, action=KeyAction.KEY_UP, keycode=keycode, modifiers=modifiers)
        )
