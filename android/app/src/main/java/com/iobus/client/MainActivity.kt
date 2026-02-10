package com.iobus.client

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iobus.client.network.ConnectionManager
import com.iobus.client.network.ConnectionState
import com.iobus.client.ui.connection.ConnectionScreen
import com.iobus.client.ui.control.ControlScreen
import com.iobus.client.ui.theme.IOBusTheme

/**
 * Application-level singleton to keep ConnectionManager alive
 * across activity recreations (home screen, rotation, etc.).
 */
class IOBusApplication : Application() {
    companion object {
        val connectionManager = ConnectionManager()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
    val navController = rememberNavController()
    val connectionManager = IOBusApplication.connectionManager
    val state = connectionManager.state.collectAsState()

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
                },
            )
        }
        composable("control") {
            ControlScreen(
                connectionManager = connectionManager,
                onDisconnected = {
                    navController.navigate("connect") {
                        popUpTo("control") { inclusive = true }
                    }
                },
            )
        }
    }
}
