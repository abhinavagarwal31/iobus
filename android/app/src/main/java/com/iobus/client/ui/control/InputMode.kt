package com.iobus.client.ui.control

/**
 * Available input surface modes.
 *
 * Controls which input panels are visible and the required orientation:
 * - NONE → portrait, shows mode selection menu
 * - KEYBOARD → landscape, keyboard only
 * - TRACKPAD → landscape, trackpad only
 * - COMBINED → landscape, split keyboard + trackpad
 */
enum class InputMode(val label: String, val icon: String, val isLandscape: Boolean) {
    NONE("Home", "⊞", false),
    KEYBOARD("Keyboard", "⌨", true),
    TRACKPAD("Trackpad", "◎", true),
    COMBINED("Combined", "⊞", true),
}
