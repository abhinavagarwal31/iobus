package com.iobus.client.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * HUD-style dark futuristic color palette.
 *
 * Design philosophy: Tony Stark / Iron Man HUD aesthetic.
 * - Deep black backgrounds
 * - Electric blue/cyan accents
 * - Subtle amber highlights for warnings/active states
 * - Minimal surface variation to maintain depth
 */

// Backgrounds
val HudBlack = Color(0xFF0A0A0E)
val HudSurface = Color(0xFF111118)
val HudSurfaceElevated = Color(0xFF1A1A24)
val HudSurfaceBorder = Color(0xFF252535)
val HudTopBarSurface = Color(0xFF0D0D14)    // top bar — slightly darker than HudSurface

// Primary accent — electric cyan/blue
val HudCyan = Color(0xFF00D4FF)
val HudCyanDim = Color(0xFF0088AA)

// Secondary accent — warm amber
val HudAmber = Color(0xFFFFAA00)
val HudAmberDim = Color(0xFFAA7700)

// Status
val HudGreen = Color(0xFF00FF88)
val HudGreenDim = Color(0xFF00AA55)
val HudRed = Color(0xFFFF3355)
val HudRedDim = Color(0xFFAA2244)
val HudRedSoft = Color(0xFFA3384F)           // muted disconnect red — reduced saturation

// Text
val HudTextPrimary = Color(0xFFE8E8F0)
val HudTextSecondary = Color(0xFF888899)
val HudTextDisabled = Color(0xFF555566)
val HudTextFnKey = Color(0xFF6E6E82)         // function row — slightly dimmer than secondary
val HudTextMicro = Color(0xFF333344)  // barely visible watermark text

// Key surfaces (keyboard) — refined for Iron Man HUD
val HudKeySurface = Color(0xFF13131F)
val HudKeyPressed = Color(0xFF00284A)
val HudKeyModifierActive = Color(0xFF1A3350)
val HudKeyBorder = Color(0xFF222233)
val HudKeyFnSurface = Color(0xFF0F0F18)   // function row slightly dimmer
val HudKeyboardBg = Color(0xFF0D0D13)       // keyboard panel bg — slightly lighter than HudBlack

// Trackpad surface
val HudTrackpadBorder = Color(0xFF1A1A2A)
val HudTrackpadGrid = Color(0x08FFFFFF)    // faint grid overlay
val HudTrackpadGradientTop = Color(0xFF0E0E17)
val HudTrackpadGradientBot = Color(0xFF0A0A11)

// Mode selector
val HudModeInactive = Color(0xFF333344)
val HudModeSurface = Color(0xFF0E0E16)

// Continuous slider controls
val HudSliderTrack = Color(0xFF0F0F1A)        // dark track background
val HudSliderBorder = Color(0xFF1E1E30)       // subtle track border
val HudSliderGradientTop = Color(0xFF00D4FF)  // full — cyan
val HudSliderGradientMid = Color(0xFF0088DD)  // midpoint blend
val HudSliderGradientBot = Color(0xFF0050AA)  // low — electric blue
