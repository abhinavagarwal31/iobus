package com.iobus.client.protocol

/**
 * Platform-neutral key codes — mirrors protocol/keycodes.py.
 *
 * These are NOT Android keycodes. The protocol uses its own key code table.
 * Mapping from Android KeyEvent.KEYCODE_* → KeyCodes is done in KeyProcessor.
 */
object KeyCodes {
    // Printable / typing keys (0x0000–0x007F)
    const val KEY_A = 0x0004
    const val KEY_B = 0x0005
    const val KEY_C = 0x0006
    const val KEY_D = 0x0007
    const val KEY_E = 0x0008
    const val KEY_F = 0x0009
    const val KEY_G = 0x000A
    const val KEY_H = 0x000B
    const val KEY_I = 0x000C
    const val KEY_J = 0x000D
    const val KEY_K = 0x000E
    const val KEY_L = 0x000F
    const val KEY_M = 0x0010
    const val KEY_N = 0x0011
    const val KEY_O = 0x0012
    const val KEY_P = 0x0013
    const val KEY_Q = 0x0014
    const val KEY_R = 0x0015
    const val KEY_S = 0x0016
    const val KEY_T = 0x0017
    const val KEY_U = 0x0018
    const val KEY_V = 0x0019
    const val KEY_W = 0x001A
    const val KEY_X = 0x001B
    const val KEY_Y = 0x001C
    const val KEY_Z = 0x001D

    const val KEY_1 = 0x001E
    const val KEY_2 = 0x001F
    const val KEY_3 = 0x0020
    const val KEY_4 = 0x0021
    const val KEY_5 = 0x0022
    const val KEY_6 = 0x0023
    const val KEY_7 = 0x0024
    const val KEY_8 = 0x0025
    const val KEY_9 = 0x0026
    const val KEY_0 = 0x0027

    const val KEY_RETURN = 0x0028
    const val KEY_ENTER = KEY_RETURN  // Alias
    const val KEY_ESCAPE = 0x0029
    const val KEY_BACKSPACE = 0x002A
    const val KEY_TAB = 0x002B
    const val KEY_SPACE = 0x002C

    const val KEY_MINUS = 0x002D
    const val KEY_EQUAL = 0x002E
    const val KEY_LEFT_BRACKET = 0x002F
    const val KEY_RIGHT_BRACKET = 0x0030
    const val KEY_BACKSLASH = 0x0031
    const val KEY_SEMICOLON = 0x0033
    const val KEY_APOSTROPHE = 0x0034
    const val KEY_QUOTE = KEY_APOSTROPHE  // Alias
    const val KEY_GRAVE = 0x0035
    const val KEY_BACKTICK = KEY_GRAVE  // Alias
    const val KEY_COMMA = 0x0036
    const val KEY_PERIOD = 0x0037
    const val KEY_SLASH = 0x0038

    // Navigation and editing (0x0080–0x00FF)
    const val KEY_INSERT = 0x0080
    const val KEY_DELETE = 0x0081
    const val KEY_HOME = 0x0082
    const val KEY_END = 0x0083
    const val KEY_PAGE_UP = 0x0084
    const val KEY_PAGE_DOWN = 0x0085
    const val KEY_ARROW_RIGHT = 0x0086
    const val KEY_RIGHT = KEY_ARROW_RIGHT  // Alias
    const val KEY_ARROW_LEFT = 0x0087
    const val KEY_LEFT = KEY_ARROW_LEFT  // Alias
    const val KEY_ARROW_DOWN = 0x0088
    const val KEY_DOWN = KEY_ARROW_DOWN  // Alias
    const val KEY_ARROW_UP = 0x0089
    const val KEY_UP = KEY_ARROW_UP  // Alias

    // Modifier keys (0x0100–0x017F)
    const val KEY_LEFT_CONTROL = 0x0100
    const val KEY_LEFT_CTRL = KEY_LEFT_CONTROL  // Alias
    const val KEY_LEFT_SHIFT = 0x0101
    const val KEY_LEFT_ALT = 0x0102
    const val KEY_LEFT_META = 0x0103
    const val KEY_LEFT_CMD = KEY_LEFT_META  // Alias
    const val KEY_RIGHT_CONTROL = 0x0104
    const val KEY_RIGHT_SHIFT = 0x0105
    const val KEY_RIGHT_ALT = 0x0106
    const val KEY_RIGHT_META = 0x0107
    const val KEY_RIGHT_CMD = KEY_RIGHT_META  // Alias
    const val KEY_FN = 0x0108
    const val KEY_CAPS_LOCK = 0x0109

    // Function keys (0x0180–0x01FF)
    const val KEY_F1 = 0x0180
    const val KEY_F2 = 0x0181
    const val KEY_F3 = 0x0182
    const val KEY_F4 = 0x0183
    const val KEY_F5 = 0x0184
    const val KEY_F6 = 0x0185
    const val KEY_F7 = 0x0186
    const val KEY_F8 = 0x0187
    const val KEY_F9 = 0x0188
    const val KEY_F10 = 0x0189
    const val KEY_F11 = 0x018A
    const val KEY_F12 = 0x018B

    // Media and system keys (0x0200–0x027F)
    const val KEY_MUTE = 0x0200
    const val KEY_VOLUME_UP = 0x0201
    const val KEY_VOLUME_DOWN = 0x0202
    const val KEY_BRIGHTNESS_UP = 0x0203
    const val KEY_BRIGHTNESS_DOWN = 0x0204
    const val KEY_MEDIA_PLAY_PAUSE = 0x0205
    const val KEY_MEDIA_NEXT = 0x0206
    const val KEY_MEDIA_PREV = 0x0207
}
