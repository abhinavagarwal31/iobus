package com.iobus.client

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iobus.client.haptics.HapticManager
import com.iobus.client.network.ConnectionManager
import com.iobus.client.network.ConnectionState
import com.iobus.client.network.SavedServersStore
import com.iobus.client.security.PasscodeStore
import com.iobus.client.service.ConnectionService
import com.iobus.client.settings.AppSettingsStore
import com.iobus.client.ui.connection.ConnectionScreen
import com.iobus.client.ui.control.ControlScreen
import com.iobus.client.ui.settings.SettingsScreen
import com.iobus.client.ui.theme.IOBusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application-level singleton stores.
 */
class IOBusApplication : Application() {
    companion object {
        lateinit var connectionManager: ConnectionManager
            private set
        lateinit var savedServersStore: SavedServersStore
            private set
        lateinit var passcodeStore: PasscodeStore
            private set
        lateinit var appSettingsStore: AppSettingsStore
            private set
        lateinit var hapticManager: HapticManager
            private set
    }

    override fun onCreate() {
        super.onCreate()
        savedServersStore = SavedServersStore(this)
        connectionManager = ConnectionManager(this, savedServersStore)
        passcodeStore = PasscodeStore(this)
        appSettingsStore = AppSettingsStore(this)
        hapticManager = HapticManager(this, appSettingsStore)

        // Auto-reconnect to last server on app startup
        val lastConnection = savedServersStore.getLastConnection()
        if (lastConnection != null) {
            CoroutineScope(Dispatchers.IO).launch {
                connectionManager.connect(
                    host = lastConnection.host,
                    tcpPort = lastConnection.port,
                    enableAutoReconnect = true
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, service can now start
        } else {
            // Permission denied - service won't be able to show notification
            // App will still work but connection may be killed in background
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if optimization is now disabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // Successfully disabled battery optimization
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request battery optimization exemption for persistent connection (Android 6+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                batteryOptimizationLauncher.launch(intent)
            }
        }

        // Start in portrait — ControlScreen will switch to landscape when needed
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Fullscreen immersive — hide system bars
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            IOBusTheme {
                IOBusApp()
            }
        }
    }
}

@Composable
private fun IOBusApp() {
    val connectionManager = IOBusApplication.connectionManager
    val navController = rememberNavController()
    val state = connectionManager.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Start on control screen if already connected
    val startDest = if (state.value == ConnectionState.CONNECTED) "control" else "connect"

    NavHost(
        navController = navController,
        startDestination = startDest,
    ) {
        composable("connect") {
            ConnectionScreen(
                connectionManager = connectionManager,
                onConnected = {
                    navController.navigate("control") {
                        popUpTo("connect") { inclusive = true }
                    }
                    // Start foreground service after navigation (if permission granted)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                ConnectionService.start(context)
                            }
                        } else {
                            // Pre-Android 13, no runtime permission needed
                            ConnectionService.start(context)
                        }
                    } catch (e: Exception) {
                        // Service failed to start, but app can continue without it
                        // Connection will work but may not survive backgrounding
                    }
                },
            )
        }
        composable("control") {
            ControlScreen(
                connectionManager = connectionManager,
                onDisconnected = {
                    // Service will stop itself when state becomes DISCONNECTED
                    // Don't manually stop it here - let it show error notifications
                    navController.navigate("connect") {
                        popUpTo("control") { inclusive = true }
                    }
                },
                onSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
