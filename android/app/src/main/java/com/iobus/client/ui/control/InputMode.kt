package com.iobus.client.ui.control

/**
 * Available input surface modes.
 *
 * Controls which input panels are visible and the required orientation:
 * - HOME → portrait, navigation-only landing screen (no content)
 * - CONTROLS → sensor orientation (adapts to portrait/landscape), full-screen control center
 * - KEYBOARD → landscape, keyboard only
 * - TRACKPAD → sensor orientation (adapts to portrait/landscape) — trackpad only,
 *   portrait enables comfortable one-handed use
 * - COMBINED → landscape, split keyboard + trackpad
 */
enum class InputMode(val label: String, val isLandscape: Boolean) {
    HOME("Home", false),
    CONTROLS("Controls", false),
    KEYBOARD("Keyboard", true),
    TRACKPAD("Trackpad", false),  // sensor-oriented — see ControlScreen's orientation special-case
    COMBINED("Combined", true),
}
