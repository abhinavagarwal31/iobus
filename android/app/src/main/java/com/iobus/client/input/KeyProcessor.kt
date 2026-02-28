package com.iobus.client.input

import com.iobus.client.network.ConnectionManager
import com.iobus.client.protocol.KeyAction
import com.iobus.client.protocol.KeyCodes
import com.iobus.client.protocol.ModifierFlag
import com.iobus.client.protocol.SystemActionId

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
        // Re-export protocol constants for callers that reference KeyProcessor.MOD_*
        const val MOD_SHIFT = ModifierFlag.SHIFT
        const val MOD_CTRL = ModifierFlag.CONTROL
        const val MOD_ALT = ModifierFlag.ALT
        const val MOD_CMD = ModifierFlag.META

        const val ACTION_DOWN = KeyAction.KEY_DOWN
        const val ACTION_UP = KeyAction.KEY_UP
    }

    /** Whether shift is currently held. */
    val isShiftActive: Boolean get() = (modifiers and MOD_SHIFT) != 0
    val isCtrlActive: Boolean get() = (modifiers and MOD_CTRL) != 0
    val isAltActive: Boolean get() = (modifiers and MOD_ALT) != 0
    val isCmdActive: Boolean get() = (modifiers and MOD_CMD) != 0

    /**
     * Activate a modifier when the key is pressed down (hold-to-activate).
     * Sets the flag and sends a keyDown event so the server can detect
     * rapid double-taps (e.g. ⌘⌘ → Siri).
     */
    fun holdModifier(modFlag: Int, keyCode: Int) {
        modifiers = modifiers or modFlag
        connection.sendKeyEvent(keyCode, ACTION_DOWN, modifiers)
    }

    /**
     * Deactivate a modifier when the key is released.
     * Clears the flag BEFORE sending keyUp so the server sees the modifier
     * already cleared in the event flags — matching real macOS behaviour.
     */
    fun releaseModifier(modFlag: Int, keyCode: Int) {
        modifiers = modifiers and modFlag.inv()
        connection.sendKeyEvent(keyCode, ACTION_UP, modifiers)
    }

    /**
     * Send a key press (down + up) for a regular key.
     * Current modifier state is included.
     */
    fun pressKey(keyCode: Int) {
        connection.sendKeyEvent(keyCode, ACTION_DOWN, modifiers)
        connection.sendKeyEvent(keyCode, ACTION_UP, modifiers)
    }

    /**
     * Send key-down only (for held keys / repeat).
     *
     * Special case: F4 in media mode (fn not active) triggers Spotlight
     * via system action instead of a regular key event.
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
     * Send Spotlight system action (triggered by F4 in media mode).
     */
    fun triggerSpotlight() {
        connection.sendSystemAction(SystemActionId.SPOTLIGHT)
    }

    /**
     * Reset all modifier state.
     */
    fun reset() {
        modifiers = 0
    }
}
