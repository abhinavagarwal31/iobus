"""
Platform-neutral key code definitions.

These are NOT Android keycodes and NOT macOS CGKeyCodes.
This is the protocol's own key code table — both platforms map to/from it.

Android client: Android KeyEvent.KEYCODE_* → ProtocolKeyCode
macOS server:   ProtocolKeyCode → macOS CGKeyCode (virtual key code)
"""

from enum import IntEnum


class ProtocolKeyCode(IntEnum):
    """
    Platform-neutral key codes for the wire protocol.

    Ranges:
      0x0000–0x007F  Printable ASCII-compatible keys
      0x0080–0x00FF  Navigation and editing keys
      0x0100–0x017F  Modifier keys
      0x0180–0x01FF  Function keys
      0x0200–0x027F  Media and system keys
      0x0280–0x02FF  Numpad keys
    """

    # --- Printable / typing keys (0x0000–0x007F) ---
    KEY_A = 0x0004
    KEY_B = 0x0005
    KEY_C = 0x0006
    KEY_D = 0x0007
    KEY_E = 0x0008
    KEY_F = 0x0009
    KEY_G = 0x000A
    KEY_H = 0x000B
    KEY_I = 0x000C
    KEY_J = 0x000D
    KEY_K = 0x000E
    KEY_L = 0x000F
    KEY_M = 0x0010
    KEY_N = 0x0011
    KEY_O = 0x0012
    KEY_P = 0x0013
    KEY_Q = 0x0014
    KEY_R = 0x0015
    KEY_S = 0x0016
    KEY_T = 0x0017
    KEY_U = 0x0018
    KEY_V = 0x0019
    KEY_W = 0x001A
    KEY_X = 0x001B
    KEY_Y = 0x001C
    KEY_Z = 0x001D

    KEY_1 = 0x001E
    KEY_2 = 0x001F
    KEY_3 = 0x0020
    KEY_4 = 0x0021
    KEY_5 = 0x0022
    KEY_6 = 0x0023
    KEY_7 = 0x0024
    KEY_8 = 0x0025
    KEY_9 = 0x0026
    KEY_0 = 0x0027

    KEY_RETURN = 0x0028
    KEY_ESCAPE = 0x0029
    KEY_BACKSPACE = 0x002A
    KEY_TAB = 0x002B
    KEY_SPACE = 0x002C

    KEY_MINUS = 0x002D
    KEY_EQUAL = 0x002E
    KEY_LEFT_BRACKET = 0x002F
    KEY_RIGHT_BRACKET = 0x0030
    KEY_BACKSLASH = 0x0031
    KEY_SEMICOLON = 0x0033
    KEY_APOSTROPHE = 0x0034
    KEY_GRAVE = 0x0035
    KEY_COMMA = 0x0036
    KEY_PERIOD = 0x0037
    KEY_SLASH = 0x0038

    # --- Navigation and editing (0x0080–0x00FF) ---
    KEY_INSERT = 0x0080
    KEY_DELETE = 0x0081
    KEY_HOME = 0x0082
    KEY_END = 0x0083
    KEY_PAGE_UP = 0x0084
    KEY_PAGE_DOWN = 0x0085

    KEY_ARROW_RIGHT = 0x0086
    KEY_ARROW_LEFT = 0x0087
    KEY_ARROW_DOWN = 0x0088
    KEY_ARROW_UP = 0x0089

    # --- Modifier keys (0x0100–0x017F) ---
    KEY_LEFT_CONTROL = 0x0100
    KEY_LEFT_SHIFT = 0x0101
    KEY_LEFT_ALT = 0x0102     # Option on macOS
    KEY_LEFT_META = 0x0103    # Cmd on macOS
    KEY_RIGHT_CONTROL = 0x0104
    KEY_RIGHT_SHIFT = 0x0105
    KEY_RIGHT_ALT = 0x0106
    KEY_RIGHT_META = 0x0107
    KEY_FN = 0x0108
    KEY_CAPS_LOCK = 0x0109

    # --- Function keys (0x0180–0x01FF) ---
    KEY_F1 = 0x0180
    KEY_F2 = 0x0181
    KEY_F3 = 0x0182
    KEY_F4 = 0x0183
    KEY_F5 = 0x0184
    KEY_F6 = 0x0185
    KEY_F7 = 0x0186
    KEY_F8 = 0x0187
    KEY_F9 = 0x0188
    KEY_F10 = 0x0189
    KEY_F11 = 0x018A
    KEY_F12 = 0x018B

    # --- Media and system keys (0x0200–0x027F) ---
    KEY_MUTE = 0x0200
    KEY_VOLUME_UP = 0x0201
    KEY_VOLUME_DOWN = 0x0202
    KEY_BRIGHTNESS_UP = 0x0203
    KEY_BRIGHTNESS_DOWN = 0x0204
    KEY_MEDIA_PLAY_PAUSE = 0x0205
    KEY_MEDIA_NEXT = 0x0206
    KEY_MEDIA_PREV = 0x0207
    KEY_PRINT_SCREEN = 0x0210
    KEY_SCROLL_LOCK = 0x0211
    KEY_PAUSE = 0x0212
