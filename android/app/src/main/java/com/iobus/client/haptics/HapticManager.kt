package com.iobus.client.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.iobus.client.settings.AppSettingsStore
import com.iobus.client.settings.AppSettingsStore.HapticLevel

/**
 * Centralised haptic feedback manager.
 *
 * - Reads [AppSettingsStore.hapticIntensity] at call-time (no recomposition needed).
 * - Gracefully no-ops when [Vibrator.hasVibrator] returns false.
 * - Uses [VibrationEffect] predefined effects on API 29+; falls back to timed vibration below.
 * - Uses [VibratorManager] on API 31+ to obtain the default vibrator.
 *
 * Intensity mapping:
 *   OFF    → no-op
 *   LIGHT  → EFFECT_TICK (API 29+) / ~6 ms fallback
 *   MEDIUM → per-method effect (EFFECT_CLICK for click, EFFECT_TICK for key tap)
 *   STRONG → EFFECT_HEAVY_CLICK (API 29+) / ~28 ms fallback
 */
class HapticManager(context: Context, private val settings: AppSettingsStore) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val hasVibrator = vibrator.hasVibrator()

    /** Short tick — fired on every keyboard key press. */
    fun keyTap() = vibrate(
        lightEffect    = VibrationEffect.EFFECT_TICK,
        mediumEffect   = VibrationEffect.EFFECT_TICK,
        strongEffect   = VibrationEffect.EFFECT_CLICK,
        lightMs        = 6L,
        mediumMs       = 10L,
        strongMs       = 20L,
    )

    /** Clean click — fired on trackpad single-tap and right-tap. */
    fun click() = vibrate(
        lightEffect    = VibrationEffect.EFFECT_TICK,
        mediumEffect   = VibrationEffect.EFFECT_CLICK,
        strongEffect   = VibrationEffect.EFFECT_HEAVY_CLICK,
        lightMs        = 8L,
        mediumMs       = 16L,
        strongMs       = 28L,
    )

    @SuppressLint("NewApi")
    private fun vibrate(
        lightEffect: Int, mediumEffect: Int, strongEffect: Int,
        lightMs: Long,   mediumMs: Long,  strongMs: Long,
    ) {
        if (!hasVibrator) return
        when (settings.hapticIntensity.value) {
            HapticLevel.OFF    -> return
            HapticLevel.LIGHT  -> vibrateImpl(lightEffect,  lightMs)
            HapticLevel.MEDIUM -> vibrateImpl(mediumEffect, mediumMs)
            HapticLevel.STRONG -> vibrateImpl(strongEffect, strongMs)
        }
    }

    @SuppressLint("NewApi")
    private fun vibrateImpl(effectId: Int, fallbackMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(effectId))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(fallbackMs)
        }
    }
}
