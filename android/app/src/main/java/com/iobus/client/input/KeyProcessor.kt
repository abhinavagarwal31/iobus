package com.iobus.client.input

import com.iobus.client.network.ConnectionManager

/**
 * Maps keyboard UI key presses to protocol key events.
 *
 * Tracks modifier state (shift, ctrl, alt, cmd) and sends
 * proper key down/up events with modifier bitmask.
 */
class KeyProcessor(
    private val connection: ConnectionManager,
) {
    // Modifier state flags (bitmask matches protocol ModifierFlag)
    private var modifiers: Int = 0

    companion object {
        const val MOD_SHIFT = 0x01
        const val MOD_CTRL = 0x02
        const val MOD_ALT = 0x04
        const val MOD_CMD = 0x08

        // Key action constants (match protocol)
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
    }

    /** Whether shift is currently held. */
    val isShiftActive: Boolean get() = (modifiers and MOD_SHIFT) != 0
    val isCtrlActive: Boolean get() = (modifiers and MOD_CTRL) != 0
    val isAltActive: Boolean get() = (modifiers and MOD_ALT) != 0
    val isCmdActive: Boolean get() = (modifiers and MOD_CMD) != 0

    /**
     * Toggle a modifier key (sticky behaviour like on-screen keyboards).
     * Returns the new active state.
     */
    fun toggleModifier(modFlag: Int): Boolean {
        modifiers = modifiers xor modFlag
        return (modifiers and modFlag) != 0
    }

    /**
     * Send a key press (down + up) for a regular key.
     * Current modifier state is included.
     */
    fun pressKey(keyCode: Int) {
        connection.sendKeyEvent(keyCode, ACTION_DOWN, modifiers)
        connection.sendKeyEvent(keyCode, ACTION_UP, modifiers)
        // Auto-release shift after a non-modifier key (standard keyboard behaviour)
        if (modifiers and MOD_SHIFT != 0) {
            modifiers = modifiers and MOD_SHIFT.inv()
        }
    }

    /**
     * Send key-down only (for held keys / repeat).
     */
    fun keyDown(keyCode: Int) {
        connection.sendKeyEvent(keyCode, ACTION_DOWN, modifiers)
    }

    /**
     * Send key-up only.
     */
    fun keyUp(keyCode: Int) {
        connection.sendKeyEvent(keyCode, ACTION_UP, modifiers)
    }

    /**
     * Reset all modifier state.
     */
    fun reset() {
        modifiers = 0
    }
}
