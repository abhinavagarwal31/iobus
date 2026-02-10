package com.iobus.client.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * HUD shapes — subtle rounding, sharp precision.
 */

val HudShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // key caps
    small = RoundedCornerShape(6.dp),        // small buttons
    medium = RoundedCornerShape(8.dp),       // cards, fields
    large = RoundedCornerShape(12.dp),       // panels
    extraLarge = RoundedCornerShape(16.dp),  // dialogs
)
