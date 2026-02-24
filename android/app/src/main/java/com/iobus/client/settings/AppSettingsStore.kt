package com.iobus.client.settings

import android.content.Context
import android.content.Context.MODE_PRIVATE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists user-configurable app settings using SharedPreferences.
 * Exposes each setting as a [StateFlow] so Compose screens can observe changes.
 */
class AppSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("iobus_settings", MODE_PRIVATE)

    // ─────────────────────────────────────────────────────────
    // Haptic intensity
    // ─────────────────────────────────────────────────────────

    object HapticLevel {
        const val OFF    = 0
        const val LIGHT  = 1
        const val MEDIUM = 2
        const val STRONG = 3
    }

    private val _hapticIntensity = MutableStateFlow(
        prefs.getInt(KEY_HAPTIC_INTENSITY, HapticLevel.MEDIUM),
    )
    val hapticIntensity: StateFlow<Int> = _hapticIntensity.asStateFlow()

    fun setHapticIntensity(level: Int) {
        prefs.edit().putInt(KEY_HAPTIC_INTENSITY, level).apply()
        _hapticIntensity.value = level
    }

    companion object {
        private const val KEY_HAPTIC_INTENSITY = "haptic_intensity"
    }
}
