package com.iobus.client.ui.control

import com.iobus.client.protocol.KeyCodes

/**
 * MacBook Air M4 keyboard layout — accurate key placement and proportions.
 *
 * Reference: Apple MacBook Air 15″ (M4, 2024)
 * - 78 keys, ANSI US layout
 * - Function row: half-height keys with dual labels (fn symbol + media icon)
 * - Arrow keys: inverted-T cluster (half-height up/down, full left/right)
 * - Modifier keys labeled with Apple symbols: ⌘ ⌥ ⌃ ⇧ fn
 */
data class KeyDef(
    val label: String,
    val keyCode: Int,
    val width: Float = 1.0f,
    val type: KeyType = KeyType.REGULAR,
    val shiftLabel: String? = null,
    val modifierFlag: Int = 0,
    val secondaryLabel: String? = null,  // e.g. media icon on function keys
)

enum class KeyType {
    REGULAR,
    MODIFIER,
    FUNCTION,
    SPECIAL,
    ACCENT,
    HALF_HEIGHT,    // for arrow up/down keys
    FUN_KEY,        // custom visual-only fun key (post-F12)
}

/**
 * Full MacBook Air M4 keyboard layout.
 *
 * Row heights:
 *  - Function row: ~0.6x standard height
 *  - All others: 1x standard height
 *  - Arrow up/down: 0.5x standard height each (stacked)
 *
 * Key widths use real proportional ratios from the physical keyboard.
 */
object KeyboardLayout {

    // Row 1: Function row (half-height, dual-label: fn name + media symbol)
    val functionRow = listOf(
        KeyDef("esc", KeyCodes.KEY_ESCAPE, width = 1.0f, type = KeyType.FUNCTION),
        KeyDef("F1", KeyCodes.KEY_F1, type = KeyType.FUNCTION, secondaryLabel = "BrDn"),
        KeyDef("F2", KeyCodes.KEY_F2, type = KeyType.FUNCTION, secondaryLabel = "BrUp"),
        KeyDef("F3", KeyCodes.KEY_F3, type = KeyType.FUNCTION, secondaryLabel = "MC"),    // Mission Control
        KeyDef("F4", KeyCodes.KEY_F4, type = KeyType.FUNCTION, secondaryLabel = "SL"),    // Spotlight
        KeyDef("F5", KeyCodes.KEY_F5, type = KeyType.FUNCTION, secondaryLabel = "DI"),    // Dictation
        KeyDef("F6", KeyCodes.KEY_F6, type = KeyType.FUNCTION, secondaryLabel = "DN"),    // Do Not Disturb
        KeyDef("F7", KeyCodes.KEY_F7, type = KeyType.FUNCTION, secondaryLabel = "Prev"),
        KeyDef("F8", KeyCodes.KEY_F8, type = KeyType.FUNCTION, secondaryLabel = "Play"),
        KeyDef("F9", KeyCodes.KEY_F9, type = KeyType.FUNCTION, secondaryLabel = "Next"),
        KeyDef("F10", KeyCodes.KEY_F10, type = KeyType.FUNCTION, secondaryLabel = "Mute"),
        KeyDef("F11", KeyCodes.KEY_F11, type = KeyType.FUNCTION, secondaryLabel = "VDn"),
        KeyDef("F12", KeyCodes.KEY_F12, type = KeyType.FUNCTION, secondaryLabel = "VUp"),
        // Custom "fun key" replaces the power button position
        KeyDef("FN*", 0, width = 1.0f, type = KeyType.FUN_KEY),
    )

    // Row 2: Number row
    val numberRow = listOf(
        KeyDef("`", KeyCodes.KEY_BACKTICK, shiftLabel = "~"),
        KeyDef("1", KeyCodes.KEY_1, shiftLabel = "!"),
        KeyDef("2", KeyCodes.KEY_2, shiftLabel = "@"),
        KeyDef("3", KeyCodes.KEY_3, shiftLabel = "#"),
        KeyDef("4", KeyCodes.KEY_4, shiftLabel = "\$"),
        KeyDef("5", KeyCodes.KEY_5, shiftLabel = "%"),
        KeyDef("6", KeyCodes.KEY_6, shiftLabel = "^"),
        KeyDef("7", KeyCodes.KEY_7, shiftLabel = "&"),
        KeyDef("8", KeyCodes.KEY_8, shiftLabel = "*"),
        KeyDef("9", KeyCodes.KEY_9, shiftLabel = "("),
        KeyDef("0", KeyCodes.KEY_0, shiftLabel = ")"),
        KeyDef("-", KeyCodes.KEY_MINUS, shiftLabel = "_"),
        KeyDef("=", KeyCodes.KEY_EQUAL, shiftLabel = "+"),
        KeyDef("⌫", KeyCodes.KEY_BACKSPACE, width = 1.5f, type = KeyType.SPECIAL),
    )

    // Row 3: QWERTY top row
    val topRow = listOf(
        KeyDef("tab", KeyCodes.KEY_TAB, width = 1.5f, type = KeyType.SPECIAL),
        KeyDef("Q", KeyCodes.KEY_Q),
        KeyDef("W", KeyCodes.KEY_W),
        KeyDef("E", KeyCodes.KEY_E),
        KeyDef("R", KeyCodes.KEY_R),
        KeyDef("T", KeyCodes.KEY_T),
        KeyDef("Y", KeyCodes.KEY_Y),
        KeyDef("U", KeyCodes.KEY_U),
        KeyDef("I", KeyCodes.KEY_I),
        KeyDef("O", KeyCodes.KEY_O),
        KeyDef("P", KeyCodes.KEY_P),
        KeyDef("[", KeyCodes.KEY_LEFT_BRACKET, shiftLabel = "{"),
        KeyDef("]", KeyCodes.KEY_RIGHT_BRACKET, shiftLabel = "}"),
        KeyDef("\\", KeyCodes.KEY_BACKSLASH, shiftLabel = "|"),
    )

    // Row 4: Home row
    val homeRow = listOf(
        KeyDef("caps lock", KeyCodes.KEY_CAPS_LOCK, width = 1.8f, type = KeyType.MODIFIER),
        KeyDef("A", KeyCodes.KEY_A),
        KeyDef("S", KeyCodes.KEY_S),
        KeyDef("D", KeyCodes.KEY_D),
        KeyDef("F", KeyCodes.KEY_F),
        KeyDef("G", KeyCodes.KEY_G),
        KeyDef("H", KeyCodes.KEY_H),
        KeyDef("J", KeyCodes.KEY_J),
        KeyDef("K", KeyCodes.KEY_K),
        KeyDef("L", KeyCodes.KEY_L),
        KeyDef(";", KeyCodes.KEY_SEMICOLON, shiftLabel = ":"),
        KeyDef("'", KeyCodes.KEY_QUOTE, shiftLabel = "\""),
        KeyDef("return", KeyCodes.KEY_ENTER, width = 1.8f, type = KeyType.ACCENT),
    )

    // Row 5: Bottom / shift row
    val bottomRow = listOf(
        KeyDef("⇧", KeyCodes.KEY_LEFT_SHIFT, width = 2.3f, type = KeyType.MODIFIER,
            modifierFlag = 0x01),
        KeyDef("Z", KeyCodes.KEY_Z),
        KeyDef("X", KeyCodes.KEY_X),
        KeyDef("C", KeyCodes.KEY_C),
        KeyDef("V", KeyCodes.KEY_V),
        KeyDef("B", KeyCodes.KEY_B),
        KeyDef("N", KeyCodes.KEY_N),
        KeyDef("M", KeyCodes.KEY_M),
        KeyDef(",", KeyCodes.KEY_COMMA, shiftLabel = "<"),
        KeyDef(".", KeyCodes.KEY_PERIOD, shiftLabel = ">"),
        KeyDef("/", KeyCodes.KEY_SLASH, shiftLabel = "?"),
        KeyDef("⇧", KeyCodes.KEY_RIGHT_SHIFT, width = 2.3f, type = KeyType.MODIFIER,
            modifierFlag = 0x01),
    )

    // Row 6: Space bar row — Apple symbol placements
    // fn, ⌃, ⌥, ⌘, [space], ⌘, ⌥, ←, ↑↓, →
    val spaceRow = listOf(
        KeyDef("fn", KeyCodes.KEY_FN, width = 1.0f, type = KeyType.MODIFIER),
        KeyDef("⌃", KeyCodes.KEY_LEFT_CTRL, width = 1.0f, type = KeyType.MODIFIER,
            modifierFlag = 0x02),
        KeyDef("⌥", KeyCodes.KEY_LEFT_ALT, width = 1.25f, type = KeyType.MODIFIER,
            modifierFlag = 0x04),
        KeyDef("⌘", KeyCodes.KEY_LEFT_CMD, width = 1.25f, type = KeyType.MODIFIER,
            modifierFlag = 0x08),
        KeyDef("", KeyCodes.KEY_SPACE, width = 5.25f, type = KeyType.SPECIAL),
        KeyDef("⌘", KeyCodes.KEY_RIGHT_CMD, width = 1.25f, type = KeyType.MODIFIER,
            modifierFlag = 0x08),
        KeyDef("⌥", KeyCodes.KEY_RIGHT_ALT, width = 1.25f, type = KeyType.MODIFIER,
            modifierFlag = 0x04),
        // Arrow keys: left, up+down stacked (handled in renderer), right
        KeyDef("←", KeyCodes.KEY_LEFT, width = 1.0f, type = KeyType.SPECIAL),
        KeyDef("↑", KeyCodes.KEY_UP, width = 1.0f, type = KeyType.HALF_HEIGHT),
        KeyDef("↓", KeyCodes.KEY_DOWN, width = 1.0f, type = KeyType.HALF_HEIGHT),
        KeyDef("→", KeyCodes.KEY_RIGHT, width = 1.0f, type = KeyType.SPECIAL),
    )

    /** Standard rows for iteration by KeyboardPanel. */
    val allRows = listOf(functionRow, numberRow, topRow, homeRow, bottomRow, spaceRow)

    /**
     * Media key actions — triggered as function-key secondary actions.
     * Mapped separately so the keyboard renderer can show them as Fn-layer icons.
     */
    val fnMediaMap = mapOf(
        KeyCodes.KEY_F1 to KeyCodes.KEY_BRIGHTNESS_DOWN,
        KeyCodes.KEY_F2 to KeyCodes.KEY_BRIGHTNESS_UP,
        // F3–F6: deferred to v2 (show HUD toast in v1)
        KeyCodes.KEY_F7 to KeyCodes.KEY_MEDIA_PREV,
        KeyCodes.KEY_F8 to KeyCodes.KEY_MEDIA_PLAY_PAUSE,
        KeyCodes.KEY_F9 to KeyCodes.KEY_MEDIA_NEXT,
        KeyCodes.KEY_F10 to KeyCodes.KEY_MUTE,
        KeyCodes.KEY_F11 to KeyCodes.KEY_VOLUME_DOWN,
        KeyCodes.KEY_F12 to KeyCodes.KEY_VOLUME_UP,
    )

    /**
     * Keys deferred to v2 — pressing these in media mode (fn off) shows a HUD toast
     * instead of sending any message to the server.
     */
    val deferredKeys: Set<Int> = setOf(
        KeyCodes.KEY_F3,  // Mission Control
        KeyCodes.KEY_F4,  // Spotlight
        KeyCodes.KEY_F5,  // Dictation
        KeyCodes.KEY_F6,  // Do Not Disturb
        KeyCodes.KEY_CAPS_LOCK,  // Unreliable via synthetic injection
    )
}
