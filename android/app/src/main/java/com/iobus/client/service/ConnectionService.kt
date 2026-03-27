package com.iobus.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.iobus.client.IOBusApplication
import com.iobus.client.MainActivity
import com.iobus.client.network.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Foreground service to keep the connection alive in background.
 * Uses a partial wake lock to prevent Android from aggressively killing
 * the TCP socket when the device is idle or screen is off.
 *
 * Shows a persistent notification while connected, preventing Android
 * from killing the TCP/UDP sockets when the app is backgrounded.
 */
class ConnectionService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "iobus_connection"

        const val ACTION_DISCONNECT = "com.iobus.client.DISCONNECT"

        fun start(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Ignore if service can't start
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            context.stopService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()

        try {
            // Acquire wake lock to keep CPU alive for network operations
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "IOBus::ConnectionWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 60 * 1000L)
            }

            createNotificationChannel()

            // Observe connection state from Application's ConnectionManager
            IOBusApplication.connectionManager.state
                .onEach { state -> updateNotification(state) }
                .launchIn(scope)
        } catch (e: Exception) {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            // User tapped disconnect in notification
            scope.launch {
                IOBusApplication.connectionManager.disconnect()
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // Start as foreground service with current connection state
        try {
            val currentState = IOBusApplication.connectionManager.state.value
            startForeground(NOTIFICATION_ID, createNotification(currentState))
        } catch (e: Exception) {
            // If we can't start foreground, just stop the service
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        scope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "IOBus Connection",
            NotificationManager.IMPORTANCE_DEFAULT  // Default importance for persistent connection
        ).apply {
            description = "Keeps connection to Mac alive in background"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(state: ConnectionState): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val connectionManager = IOBusApplication.connectionManager
        val host = connectionManager.host.takeIf { it.isNotEmpty() } ?: "server"
        val errorMsg = connectionManager.errorMessage.value
        val (title, text, ongoing) = when (state) {
            ConnectionState.CONNECTED -> Triple(
                "Connected to $host",
                "Tap to open controls",
                true
            )
            ConnectionState.CONNECTING, ConnectionState.HANDSHAKING -> Triple(
                "Connecting to $host",
                "Establishing connection...",
                true
            )
            ConnectionState.ERROR -> Triple(
                "Connection lost",
                errorMsg ?: "Tap to reconnect",
                false
            )
            else -> Triple(
                "IOBus",
                "Ready to connect",
                false
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Using bluetooth icon as placeholder
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapPendingIntent)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // High priority prevents service from being killed
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Add disconnect action when connected
        if (state == ConnectionState.CONNECTED) {
            val disconnectIntent = Intent(this, ConnectionService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val disconnectPendingIntent = PendingIntent.getService(
                this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPendingIntent
            )
        }

        return builder.build()
    }

    private fun updateNotification(state: ConnectionState) {
        try {
            val notification = createNotification(state)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)

            when (state) {
                ConnectionState.DISCONNECTED -> {
                    // User disconnected - stop service and remove notification
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                ConnectionState.ERROR -> {
                    // Connection error - keep service alive for auto-reconnect
                    // Notification shows "Connection lost" with reconnect status
                }
                ConnectionState.CONNECTING, ConnectionState.HANDSHAKING -> {
                    // Keep service in foreground during connection attempts
                }
                ConnectionState.CONNECTED -> {
                    // Normal connected state
                }
            }
        } catch (e: Exception) {
            // If notification update fails, stop the service
            stopSelf()
        }
    }
}
