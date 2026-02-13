package com.iobus.client.ui.connection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iobus.client.IOBusApplication
import com.iobus.client.network.ConnectionManager
import com.iobus.client.network.ConnectionState
import com.iobus.client.network.SavedServer
import com.iobus.client.protocol.Constants
import com.iobus.client.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Connection setup screen — saved server presets, IP input, connect button, status display.
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
    val savedServersStore = IOBusApplication.savedServersStore
    val savedServers by savedServersStore.servers.collectAsState()

    var hostInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf(Constants.TCP_PORT.toString()) }
    var saveNameInput by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

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

            // ── Saved Servers ──────────────────────────────
            if (savedServers.isNotEmpty()) {
                Text(
                    text = "SAVED SERVERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = HudTextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                savedServers.forEach { server ->
                    SavedServerCard(
                        server = server,
                        isConnecting = state == ConnectionState.CONNECTING || state == ConnectionState.HANDSHAKING,
                        onTap = {
                            hostInput = server.host
                            portInput = server.port.toString()
                            scope.launch {
                                connectionManager.connect(server.host, server.port)
                            }
                        },
                        onDelete = { savedServersStore.delete(server.name) },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── Manual Entry ───────────────────────────────
            Text(
                text = "MANUAL CONNECT",
                style = MaterialTheme.typography.labelMedium,
                color = HudTextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )

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

            // Connect + Save buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                        .weight(1f)
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

                // Save button
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    enabled = hostInput.isNotBlank(),
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (hostInput.isNotBlank()) HudAmber else HudTextDisabled,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = HudAmber,
                        disabledContentColor = HudTextDisabled,
                    ),
                ) {
                    Text("SAVE", style = MaterialTheme.typography.labelLarge)
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

    // ── Save dialog ─────────────────────────────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = HudSurface,
            titleContentColor = HudTextPrimary,
            textContentColor = HudTextSecondary,
            title = { Text("Save Server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${hostInput.trim()}:${portInput.ifBlank { Constants.TCP_PORT.toString() }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudCyan,
                    )
                    OutlinedTextField(
                        value = saveNameInput,
                        onValueChange = { saveNameInput = it },
                        placeholder = { Text("e.g. Home Wi-Fi", color = HudTextDisabled) },
                        singleLine = true,
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val port = portInput.toIntOrNull() ?: Constants.TCP_PORT
                        savedServersStore.save(saveNameInput.trim(), hostInput.trim(), port)
                        saveNameInput = ""
                        showSaveDialog = false
                    },
                    enabled = saveNameInput.isNotBlank(),
                ) {
                    Text("SAVE", color = if (saveNameInput.isNotBlank()) HudCyan else HudTextDisabled)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("CANCEL", color = HudTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun SavedServerCard(
    server: SavedServer,
    isConnecting: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, HudSurfaceBorder, RoundedCornerShape(8.dp))
            .background(HudSurface)
            .clickable(enabled = !isConnecting) { onTap() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HudCyanDim),
        )
        Spacer(Modifier.width(12.dp))

        // Name + address
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.bodyMedium,
                color = HudTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${server.host}:${server.port}",
                style = MaterialTheme.typography.bodySmall,
                color = HudTextSecondary,
            )
        }

        // Delete button
        TextButton(
            onClick = onDelete,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("✕", color = HudRed, style = MaterialTheme.typography.labelMedium)
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
        ConnectionState.ERROR -> "ERROR" to HudRed
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
