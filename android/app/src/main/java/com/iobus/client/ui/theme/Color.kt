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

// Primary accent — electric cyan/blue
val HudCyan = Color(0xFF00D4FF)
val HudCyanDim = Color(0xFF0088AA)
val HudCyanGlow = Color(0x4000D4FF)
val HudCyanSubtle = Color(0x1A00D4FF)
val HudCyanMicro = Color(0x0D00D4FF)   // very faint glow layer

// Secondary accent — warm amber
val HudAmber = Color(0xFFFFAA00)
val HudAmberDim = Color(0xFFAA7700)
val HudAmberGlow = Color(0x40FFAA00)

// Status
val HudGreen = Color(0xFF00FF88)
val HudGreenDim = Color(0xFF00AA55)
val HudRed = Color(0xFFFF3355)
val HudRedDim = Color(0xFFAA2244)

// Text
val HudTextPrimary = Color(0xFFE8E8F0)
val HudTextSecondary = Color(0xFF888899)
val HudTextDisabled = Color(0xFF555566)
val HudTextMicro = Color(0xFF333344)  // barely visible watermark text

// Key surfaces (keyboard) — refined for Iron Man HUD
val HudKeySurface = Color(0xFF13131F)
val HudKeyPressed = Color(0xFF00284A)
val HudKeyPressedGlow = Color(0x6000D4FF)  // outer glow on press
val HudKeyModifierActive = Color(0xFF1A3350)
val HudKeyBorder = Color(0xFF222233)
val HudKeyBorderActive = Color(0xFF00D4FF)
val HudKeyFnSurface = Color(0xFF0F0F18)   // function row slightly dimmer

// Trackpad surface
val HudTrackpadSurface = Color(0xFF0C0C14)
val HudTrackpadBorder = Color(0xFF1A1A2A)
val HudTrackpadTouch = Color(0x4000D4FF)
val HudTrackpadGrid = Color(0x08FFFFFF)    // faint grid overlay

// Mode selector
val HudModeActive = Color(0xFF00D4FF)
val HudModeInactive = Color(0xFF333344)
val HudModeSurface = Color(0xFF0E0E16)
