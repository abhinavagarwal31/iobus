package com.iobus.client.ui.control

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iobus.client.security.PasscodeStore
import com.iobus.client.ui.theme.*

/**
 * Power confirmation modal with passcode gate.
 *
 * Flow:
 *  1. No passcode set → create one first.
 *  2. Passcode exists → verify it.
 *  3. After verification → show Shutdown / Restart / Sleep options.
 *
 * UI: dark translucent overlay with centered HUD-styled panel.
 */
@Composable
fun ShutdownConfirmDialog(
    passcodeStore: PasscodeStore,
    onShutdown: () -> Unit,
    onRestart: () -> Unit,
    onSleep: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasPasscode = remember { passcodeStore.hasPasscode() }
    var phase by remember { mutableStateOf(if (hasPasscode) Phase.VERIFY else Phase.CREATE) }
    var passcode by remember { mutableStateOf("") }
    var confirmPasscode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Full-screen translucent overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBlack.copy(alpha = 0.88f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* consume background touches */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HudSurface)
                .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (phase) {
                Phase.CREATE -> CreatePasscodeContent(
                    passcode = passcode,
                    confirmPasscode = confirmPasscode,
                    error = error,
                    onPasscodeChange = { passcode = it; error = null },
                    onConfirmChange = { confirmPasscode = it; error = null },
                    onSave = {
                        when {
                            passcode.length < 4 -> error = "Passcode must be at least 4 digits"
                            !passcode.all { it.isDigit() } -> error = "Passcode must be numeric"
                            passcode != confirmPasscode -> error = "Passcodes do not match"
                            else -> {
                                passcodeStore.setPasscode(passcode)
                                phase = Phase.VERIFY
                                passcode = ""
                                confirmPasscode = ""
                                error = null
                            }
                        }
                    },
                    onCancel = onDismiss,
                )

                Phase.VERIFY -> VerifyPasscodeContent(
                    passcode = passcode,
                    error = error,
                    onPasscodeChange = { passcode = it; error = null },
                    onVerify = {
                        if (passcodeStore.verify(passcode)) {
                            phase = Phase.OPTIONS
                            passcode = ""
                            error = null
                        } else {
                            error = "Incorrect passcode"
                            passcode = ""
                        }
                    },
                    onCancel = onDismiss,
                )

                Phase.OPTIONS -> PowerOptionsContent(
                    onShutdown = { onShutdown(); },
                    onRestart = { onRestart(); },
                    onSleep = { onSleep(); },
                    onCancel = onDismiss,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// Dialog phases
// ─────────────────────────────────────────────────────────

private enum class Phase { CREATE, VERIFY, OPTIONS }

// ─────────────────────────────────────────────────────────
// Phase 1: Create passcode
// ─────────────────────────────────────────────────────────

@Composable
private fun CreatePasscodeContent(
    passcode: String,
    confirmPasscode: String,
    error: String?,
    onPasscodeChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "SET POWER PASSCODE",
        color = HudCyan,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
    )

    Text(
        text = "Create a numeric passcode to secure power actions",
        color = HudTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.Center,
        lineHeight = 16.sp,
    )

    PasscodeField(value = passcode, onValueChange = onPasscodeChange, placeholder = "Passcode")
    PasscodeField(value = confirmPasscode, onValueChange = onConfirmChange, placeholder = "Confirm passcode")

    if (error != null) {
        Text(text = error, color = HudRedDim, fontSize = 10.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
    }

    DialogButtonRow(
        cancelLabel = "CANCEL",
        confirmLabel = "SAVE",
        onCancel = onCancel,
        onConfirm = onSave,
    )
}

// ─────────────────────────────────────────────────────────
// Phase 2: Verify passcode
// ─────────────────────────────────────────────────────────

@Composable
private fun VerifyPasscodeContent(
    passcode: String,
    error: String?,
    onPasscodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "ENTER PASSCODE",
        color = HudCyan,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
    )

    Text(
        text = "Authenticate to access power controls",
        color = HudTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.Center,
    )

    PasscodeField(value = passcode, onValueChange = onPasscodeChange, placeholder = "Passcode")

    if (error != null) {
        Text(text = error, color = HudRedDim, fontSize = 10.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
    }

    DialogButtonRow(
        cancelLabel = "CANCEL",
        confirmLabel = "UNLOCK",
        onCancel = onCancel,
        onConfirm = onVerify,
    )
}

// ─────────────────────────────────────────────────────────
// Phase 3: Power options (post-auth)
// ─────────────────────────────────────────────────────────

@Composable
private fun PowerOptionsContent(
    onShutdown: () -> Unit,
    onRestart: () -> Unit,
    onSleep: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "POWER OPTIONS",
        color = HudCyan,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PowerOptionRow(
            label = "SHUT DOWN",
            iconRes = LucideRes.Power,
            iconColor = HudRedSoft,
            textColor = HudRedSoft,
            onClick = onShutdown,
        )
        PowerOptionRow(
            label = "RESTART",
            iconRes = LucideRes.RotateCcw,
            iconColor = HudCyanDim,
            textColor = HudCyanDim,
            onClick = onRestart,
        )
        PowerOptionRow(
            label = "SLEEP",
            iconRes = LucideRes.Moon,
            iconColor = HudCyanDim,
            textColor = HudCyanDim,
            onClick = onSleep,
        )
    }

    Spacer(Modifier.height(4.dp))

    // Cancel button
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HudSurfaceElevated)
            .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(8.dp))
            .clickable { onCancel() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "CANCEL",
            color = HudTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun PowerOptionRow(
    label: String,
    @DrawableRes iconRes: Int,
    iconColor: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HudSurfaceElevated)
            .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HudIcon(
            iconRes = iconRes,
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────
// Shared sub-components
// ─────────────────────────────────────────────────────────

@Composable
private fun DialogButtonRow(
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(HudSurfaceElevated)
                .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(8.dp))
                .clickable { onCancel() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cancelLabel,
                color = HudTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(HudCyanDim.copy(alpha = 0.3f))
                .border(0.5.dp, HudCyanDim, RoundedCornerShape(8.dp))
                .clickable { onConfirm() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = confirmLabel,
                color = HudCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun PasscodeField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = HudTextDisabled, fontSize = 12.sp) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HudCyan,
            unfocusedBorderColor = HudSurfaceBorder,
            cursorColor = HudCyan,
            focusedTextColor = HudTextPrimary,
            unfocusedTextColor = HudTextPrimary,
            focusedContainerColor = HudSurfaceElevated,
            unfocusedContainerColor = HudSurfaceElevated,
        ),
    )
}
