package com.iobus.client.ui.control

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.iobus.client.R

/**
 * Centralized Lucide icon resource references.
 *
 * All icons sourced from Lucide Icons (lucide.dev) — ISC License.
 * Imported as SVG → converted to Android VectorDrawable.
 * Consistent 24×24 viewport, 2px stroke, round caps/joins.
 */
object LucideRes {
    // Mode selector
    @DrawableRes val Keyboard = R.drawable.ic_keyboard
    @DrawableRes val Touchpad = R.drawable.ic_touchpad
    @DrawableRes val Combined = R.drawable.ic_combined
    @DrawableRes val ControlCenter = R.drawable.ic_control_center

    // Navigation
    @DrawableRes val Home = R.drawable.ic_home

    // System actions
    @DrawableRes val Lock = R.drawable.ic_lock
    @DrawableRes val Power = R.drawable.ic_power
    @DrawableRes val Moon = R.drawable.ic_moon
    @DrawableRes val RotateCcw = R.drawable.ic_rotate_ccw

    // Brightness
    @DrawableRes val Sun = R.drawable.ic_sun
    @DrawableRes val SunDim = R.drawable.ic_sun_dim

    // Volume
    @DrawableRes val Volume2 = R.drawable.ic_volume_2
    @DrawableRes val Volume1 = R.drawable.ic_volume_1
    @DrawableRes val VolumeX = R.drawable.ic_volume_x

    // Media
    @DrawableRes val SkipBack = R.drawable.ic_skip_back
    @DrawableRes val Play = R.drawable.ic_play
    @DrawableRes val SkipForward = R.drawable.ic_skip_forward

    // Misc
    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val Mic = R.drawable.ic_mic
    @DrawableRes val LayoutGrid = R.drawable.ic_layout_grid
    @DrawableRes val Zap = R.drawable.ic_zap

    /**
     * Function key label → drawable resource ID mapping.
     *
     * Maps F1–F12 to their macOS function key icons:
     * F1=BrightnessDown, F2=BrightnessUp, F3=MissionControl,
     * F4=Spotlight, F5=Dictation, F6=DND, F7=MediaPrev,
     * F8=PlayPause, F9=MediaNext, F10=Mute, F11=VolDown, F12=VolUp
     */
    val fnKeyIcons: Map<String, Int> = mapOf(
        "F1" to SunDim,
        "F2" to Sun,
        "F3" to LayoutGrid,
        "F4" to Search,
        "F5" to Mic,
        "F6" to Moon,
        "F7" to SkipBack,
        "F8" to Play,
        "F9" to SkipForward,
        "F10" to VolumeX,
        "F11" to Volume1,
        "F12" to Volume2,
    )

    /**
     * Maps [InputMode] to its icon resource for the mode selector.
     */
    fun modeIcon(mode: InputMode): Int = when (mode) {
        InputMode.KEYBOARD -> Keyboard
        InputMode.TRACKPAD -> Touchpad
        InputMode.COMBINED -> Combined
        InputMode.CONTROLS -> ControlCenter
        InputMode.HOME -> ControlCenter  // fallback — HOME not shown in selectors
    }
}

/**
 * Standardized icon composable with consistent sizing and tinting.
 *
 * Wraps Material3 [Icon] with Lucide VectorDrawable resources.
 * All icons share identical stroke weight, optical centering,
 * and alignment grid through the VectorDrawable definitions.
 */
@Composable
fun HudIcon(
    @DrawableRes iconRes: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}
