package com.iobus.client.ui.control

/**
 * Available input surface modes.
 *
 * Controls which input panels are visible and the required orientation:
 * - HOME → portrait, navigation-only landing screen (no content)
 * - CONTROLS → portrait, full-screen control center (brightness, volume, media, lock, power)
 * - KEYBOARD → landscape, keyboard only
 * - TRACKPAD → landscape, trackpad only
 * - COMBINED → landscape, split keyboard + trackpad
 */
enum class InputMode(val label: String, val isLandscape: Boolean) {
    HOME("Home", false),
    CONTROLS("Controls", false),
    KEYBOARD("Keyboard", true),
    TRACKPAD("Trackpad", true),
    COMBINED("Combined", true),
}
