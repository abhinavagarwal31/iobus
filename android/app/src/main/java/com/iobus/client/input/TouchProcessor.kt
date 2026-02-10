package com.iobus.client.input

import com.iobus.client.network.ConnectionManager

/**
 * Processes raw touch gesture data into protocol mouse events.
 *
 * Gesture mappings:
 *  - 1-finger move → MouseMove (relative dx, dy)
 *  - 1-finger tap  → MouseClick (LEFT, DOWN then UP)
 *  - 2-finger tap  → MouseClick (RIGHT, DOWN then UP)
 *  - 2-finger drag → MouseScroll (vertical/horizontal)
 *  - 1-finger long-press + move → MouseDrag
 *
 * Sensitivity is applied as a multiplier to raw deltas.
 */
class TouchProcessor(
    private val connection: ConnectionManager,
) {
    /** Mouse sensitivity multiplier (1.0 = raw pixel delta). */
    var sensitivity: Float = 1.8f

    /** Scroll sensitivity multiplier. */
    var scrollSensitivity: Float = 1.0f

    // State tracking
    private var isDragging = false

    // ------------------------------------------------
    // Called from Compose gesture callbacks
    // ------------------------------------------------

    /**
     * Single-finger move delta.
     */
    fun onMove(dx: Float, dy: Float) {
        if (isDragging) {
            connection.sendMouseDrag(0, dx * sensitivity, dy * sensitivity)  // 0 = LEFT button
        } else {
            connection.sendMouseMove(dx * sensitivity, dy * sensitivity)
        }
    }

    /**
     * Single-finger tap.
     */
    fun onTap() {
        // LEFT = 0, DOWN = 0, UP = 1
        connection.sendMouseClick(0, 0)
        connection.sendMouseClick(0, 1)
    }

    /**
     * Two-finger tap → right click.
     */
    fun onSecondaryTap() {
        // RIGHT = 1, DOWN = 0, UP = 1
        connection.sendMouseClick(1, 0)
        connection.sendMouseClick(1, 1)
    }

    /**
     * Two-finger scroll.
     */
    fun onScroll(dx: Float, dy: Float) {
        connection.sendMouseScroll(
            dx * scrollSensitivity,
            dy * scrollSensitivity,
        )
    }

    /**
     * Long press detected — enter drag mode.
     */
    fun onDragStart() {
        isDragging = true
        // Press left button down
        connection.sendMouseClick(0, 0)
    }

    /**
     * Drag ended — release.
     */
    fun onDragEnd() {
        if (isDragging) {
            // Release left button
            connection.sendMouseClick(0, 1)
            isDragging = false
        }
    }

    /**
     * Reset state (e.g. on disconnect).
     */
    fun reset() {
        isDragging = false
    }
}
