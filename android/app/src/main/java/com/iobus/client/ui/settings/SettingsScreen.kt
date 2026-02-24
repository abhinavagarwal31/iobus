package com.iobus.client.ui.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iobus.client.IOBusApplication
import com.iobus.client.security.PasscodeStore
import com.iobus.client.settings.AppSettingsStore.HapticLevel
import com.iobus.client.ui.control.HudIcon
import com.iobus.client.ui.control.LucideRes
import com.iobus.client.ui.theme.*

/**
 * Settings screen — HUD-styled, reachable from the Home screen gear button.
 *
 * Sections:
 *  // SECURITY  — Manage passcode (change / create the shutdown gate passcode)
 *  // INPUT     — Haptic feedback intensity selector (OFF / LIGHT / MED / STRONG)
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settingsStore = IOBusApplication.appSettingsStore
    val passcodeStore = IOBusApplication.passcodeStore

    var page by remember { mutableStateOf(SettingsPage.MAIN) }

    val infiniteTransition = rememberInfiniteTransition(label = "settings-glow")
    val headerGlow by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue  = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "header-glow",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBlack)
            .systemBarsPadding(),
    ) {
        when (page) {
            SettingsPage.MAIN -> MainPage(
                settingsStore  = settingsStore,
                headerGlow     = headerGlow,
                onBack         = onBack,
                onChangePasscode = { page = SettingsPage.CHANGE_PASSCODE },
            )

            SettingsPage.CHANGE_PASSCODE -> ChangePasscodePage(
                passcodeStore = passcodeStore,
                onDone        = { page = SettingsPage.MAIN },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// Main settings page
// ─────────────────────────────────────────────────────────

@Composable
private fun MainPage(
    settingsStore: com.iobus.client.settings.AppSettingsStore,
    headerGlow: Float,
    onBack: () -> Unit,
    onChangePasscode: () -> Unit,
) {
    val hapticLevel by settingsStore.hapticIntensity.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawHudCornerBrackets(HudCyanDim.copy(alpha = 0.18f), 2f, 28.dp.toPx())
            }
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))

        // ── Header ──
        Text(
            text = "SETTINGS",
            color = HudCyan,
            fontSize = 22.sp,
            fontWeight = FontWeight.Thin,
            letterSpacing = 6.sp,
            modifier = Modifier.drawBehind {
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            HudCyan.copy(alpha = headerGlow),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        center = center,
                        radius = size.width * 1.2f,
                    ),
                )
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "[ SYSTEM CONFIGURATION ]",
            color = HudTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp,
        )

        Spacer(Modifier.height(28.dp))

        // ── Section: SECURITY ──
        SectionHeader(label = "// SECURITY")
        Spacer(Modifier.height(8.dp))

        SettingsRow(
            iconRes     = LucideRes.Lock,
            iconTint    = HudCyanDim,
            label       = "MANAGE PASSCODE",
            sublabel    = "Shutdown / restart gate",
            onClick     = onChangePasscode,
        )

        Spacer(Modifier.height(24.dp))

        // ── Section: INPUT ──
        SectionHeader(label = "// INPUT")
        Spacer(Modifier.height(8.dp))

        // Haptic intensity card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(HudSurface)
                .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HudIcon(
                    iconRes  = LucideRes.Zap,
                    tint     = HudCyanDim,
                    modifier = Modifier.size(16.dp),
                )
                Column {
                    Text(
                        text  = "HAPTIC INTENSITY",
                        color = HudTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text  = "Keyboard keys and trackpad taps",
                        color = HudTextSecondary,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Segmented selector: OFF / LIGHT / MED / STRONG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val levels = listOf(
                    HapticLevel.OFF    to "OFF",
                    HapticLevel.LIGHT  to "LIGHT",
                    HapticLevel.MEDIUM to "MED",
                    HapticLevel.STRONG to "STRONG",
                )
                for ((level, label) in levels) {
                    val isActive = hapticLevel == level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isActive) HudCyanDim.copy(alpha = 0.28f)
                                else HudSurfaceElevated,
                            )
                            .border(
                                0.5.dp,
                                if (isActive) HudCyanDim else HudSurfaceBorder,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable {
                                settingsStore.setHapticIntensity(level)
                                // Give a sample tap if not turning off
                                if (level != HapticLevel.OFF) {
                                    IOBusApplication.hapticManager.click()
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = label,
                            color = if (isActive) HudCyan else HudTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        // Tech footer
        Text(
            text  = "[ IOBUS v1  \u00b7  NEURAL INTERFACE  \u00b7  BUILD 1 ]",
            color = HudTextDisabled.copy(alpha = 0.55f),
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
    }

    // Floating back button — bottom-left
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .systemBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HudSurfaceElevated)
                .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(6.dp))
                .clickable { onBack() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = "← BACK TO HOME",
                color = HudTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// Change passcode page
// ─────────────────────────────────────────────────────────

private enum class PasscodePhase { VERIFY_OLD, SET_NEW, CONFIRM_NEW, SUCCESS }

@Composable
private fun ChangePasscodePage(
    passcodeStore: PasscodeStore,
    onDone: () -> Unit,
) {
    val hasPasscode = remember { passcodeStore.hasPasscode() }
    var phase by remember {
        mutableStateOf(if (hasPasscode) PasscodePhase.VERIFY_OLD else PasscodePhase.SET_NEW)
    }
    var fieldValue by remember { mutableStateOf("") }
    var newPasscode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .drawBehind {
                    drawHudCornerBrackets(HudCyan.copy(alpha = 0.32f), 2f, 20.dp.toPx())
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HudSurface)
                    .border(0.5.dp, HudCyanDim.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Security indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(HudAmber.copy(alpha = 0.80f)))
                    Text(
                        text = "SECURITY GATE",
                        color = HudAmberDim,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                    )
                    Box(Modifier.size(5.dp).clip(CircleShape).background(HudAmber.copy(alpha = 0.80f)))
                }

                when (phase) {
                    PasscodePhase.VERIFY_OLD -> {
                        Text("VERIFY CURRENT", color = HudCyan, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, textAlign = TextAlign.Center)
                        Text("Enter your existing passcode", color = HudTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                        PasscodeField(value = fieldValue, onValueChange = { fieldValue = it; error = null }, placeholder = "Current passcode")
                        if (error != null) ErrorText(error!!)
                        DialogButtonRow(
                            cancelLabel = "CANCEL",
                            confirmLabel = "VERIFY",
                            onCancel = onDone,
                            onConfirm = {
                                if (passcodeStore.verify(fieldValue)) {
                                    phase = PasscodePhase.SET_NEW
                                    fieldValue = ""
                                    error = null
                                } else {
                                    error = "Incorrect passcode"
                                    fieldValue = ""
                                }
                            },
                        )
                    }

                    PasscodePhase.SET_NEW -> {
                        Text(if (hasPasscode) "SET NEW PASSCODE" else "CREATE PASSCODE", color = HudCyan, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, textAlign = TextAlign.Center)
                        Text("Numeric passcode — minimum 4 digits", color = HudTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                        PasscodeField(value = fieldValue, onValueChange = { fieldValue = it; error = null }, placeholder = "New passcode")
                        if (error != null) ErrorText(error!!)
                        DialogButtonRow(
                            cancelLabel = "CANCEL",
                            confirmLabel = "NEXT",
                            onCancel = onDone,
                            onConfirm = {
                                when {
                                    fieldValue.length < 4 -> error = "Minimum 4 digits"
                                    !fieldValue.all { it.isDigit() } -> error = "Numeric only"
                                    else -> {
                                        newPasscode = fieldValue
                                        fieldValue = ""
                                        phase = PasscodePhase.CONFIRM_NEW
                                    }
                                }
                            },
                        )
                    }

                    PasscodePhase.CONFIRM_NEW -> {
                        Text("CONFIRM PASSCODE", color = HudCyan, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, textAlign = TextAlign.Center)
                        Text("Re-enter new passcode to confirm", color = HudTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                        PasscodeField(value = fieldValue, onValueChange = { fieldValue = it; error = null }, placeholder = "Confirm passcode")
                        if (error != null) ErrorText(error!!)
                        DialogButtonRow(
                            cancelLabel = "BACK",
                            confirmLabel = "COMMIT",
                            onCancel = { phase = PasscodePhase.SET_NEW; fieldValue = ""; error = null },
                            onConfirm = {
                                if (fieldValue != newPasscode) {
                                    error = "Passcodes do not match"
                                    fieldValue = ""
                                } else {
                                    passcodeStore.setPasscode(newPasscode)
                                    phase = PasscodePhase.SUCCESS
                                    fieldValue = ""
                                }
                            },
                        )
                    }

                    PasscodePhase.SUCCESS -> {
                        Text("PASSCODE UPDATED", color = HudGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, textAlign = TextAlign.Center)
                        Text("Your new passcode is active", color = HudTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(HudCyanDim.copy(alpha = 0.28f))
                                .border(0.5.dp, HudCyanDim, RoundedCornerShape(8.dp))
                                .clickable { onDone() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("DONE", color = HudCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// Shared sub-components
// ─────────────────────────────────────────────────────────

private enum class SettingsPage { MAIN, CHANGE_PASSCODE }

@Composable
private fun SectionHeader(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text  = label,
            color = HudCyanDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(HudSurfaceBorder),
        )
    }
}

@Composable
private fun SettingsRow(
    iconRes: Int,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    sublabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HudSurface)
            .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HudIcon(iconRes = iconRes, tint = iconTint, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = HudTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
            Text(text = sublabel, color = HudTextSecondary, fontSize = 9.sp, letterSpacing = 0.5.sp)
        }
        HudIcon(
            iconRes  = LucideRes.ChevronRight,
            tint     = HudTextDisabled,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun PasscodeField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value          = value,
        onValueChange  = onValueChange,
        placeholder    = { Text(placeholder, color = HudTextDisabled, fontSize = 12.sp) },
        singleLine     = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor       = HudCyan,
            unfocusedBorderColor     = HudSurfaceBorder,
            cursorColor              = HudCyan,
            focusedTextColor         = HudTextPrimary,
            unfocusedTextColor       = HudTextPrimary,
            focusedContainerColor    = HudSurfaceElevated,
            unfocusedContainerColor  = HudSurfaceElevated,
        ),
    )
}

@Composable
private fun ErrorText(message: String) {
    Text(text = message, color = HudRedDim, fontSize = 10.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
}

@Composable
private fun DialogButtonRow(
    cancelLabel: String, confirmLabel: String,
    onCancel: () -> Unit, onConfirm: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .weight(1f).clip(RoundedCornerShape(8.dp))
                .background(HudSurfaceElevated)
                .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(8.dp))
                .clickable { onCancel() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(cancelLabel, color = HudTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        }
        Box(
            modifier = Modifier
                .weight(1f).clip(RoundedCornerShape(8.dp))
                .background(HudCyanDim.copy(alpha = 0.28f))
                .border(0.5.dp, HudCyanDim, RoundedCornerShape(8.dp))
                .clickable { onConfirm() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(confirmLabel, color = HudCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        }
    }
}
