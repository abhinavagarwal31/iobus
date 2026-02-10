package com.iobus.client.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iobus.client.network.ConnectionManager
import com.iobus.client.network.ConnectionState
import com.iobus.client.protocol.Constants
import com.iobus.client.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Connection setup screen — IP input, connect button, status display.
 * Dark HUD aesthetic with minimal, purposeful UI.
 */
@Composable
fun ConnectionScreen(
    connectionManager: ConnectionManager,
    onConnected: () -> Unit,
) {
    val state by connectionManager.state.collectAsState()
    val error by connectionManager.errorMessage.collectAsState()
    val scope = rememberCoroutineScope()

    var hostInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf(Constants.TCP_PORT.toString()) }

    // Navigate when connected
    LaunchedEffect(state) {
        if (state == ConnectionState.CONNECTED) {
            onConnected()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBlack)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title
            Text(
                text = "IOBUS",
                style = MaterialTheme.typography.displayLarge,
                color = HudCyan,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "REMOTE CONTROL",
                style = MaterialTheme.typography.titleSmall,
                color = HudTextSecondary,
                textAlign = TextAlign.Center,
                letterSpacing = MaterialTheme.typography.titleSmall.letterSpacing,
            )

            Spacer(Modifier.height(8.dp))

            // Host input
            HudTextField(
                value = hostInput,
                onValueChange = { hostInput = it },
                placeholder = "Server IP address",
                keyboardType = KeyboardType.Uri,
            )

            // Port input
            HudTextField(
                value = portInput,
                onValueChange = { portInput = it },
                placeholder = "TCP port",
                keyboardType = KeyboardType.Number,
            )

            // Connect button
            val isConnecting = state == ConnectionState.CONNECTING || state == ConnectionState.HANDSHAKING
            Button(
                onClick = {
                    if (hostInput.isNotBlank()) {
                        val port = portInput.toIntOrNull() ?: Constants.TCP_PORT
                        scope.launch {
                            connectionManager.connect(hostInput.trim(), port)
                        }
                    }
                },
                enabled = !isConnecting && hostInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HudCyanDim,
                    contentColor = HudTextPrimary,
                    disabledContainerColor = HudSurfaceElevated,
                    disabledContentColor = HudTextDisabled,
                ),
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = HudCyan,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("CONNECTING…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text("CONNECT", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Status / Error
            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = HudRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Connection state indicator
            StatusBadge(state)
        }
    }
}

@Composable
private fun HudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, color = HudTextDisabled)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HudCyan,
            unfocusedBorderColor = HudSurfaceBorder,
            cursorColor = HudCyan,
            focusedTextColor = HudTextPrimary,
            unfocusedTextColor = HudTextPrimary,
            focusedContainerColor = HudSurface,
            unfocusedContainerColor = HudSurface,
        ),
    )
}

@Composable
private fun StatusBadge(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.DISCONNECTED -> "OFFLINE" to HudTextDisabled
        ConnectionState.CONNECTING -> "CONNECTING" to HudAmber
        ConnectionState.HANDSHAKING -> "HANDSHAKING" to HudAmber
        ConnectionState.CONNECTED -> "CONNECTED" to HudGreen
        ConnectionState.RECONNECTING -> "RECONNECTING" to HudAmber
        ConnectionState.ERROR -> "ERROR" to HudRed
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
