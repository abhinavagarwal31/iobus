package com.iobus.client.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * mDNS service discovery for automatic server detection.
 *
 * Discovers IOBus servers advertising "_iobus._tcp.local." service.
 * Extracts hostname, port, and auth requirements from service properties.
 *
 * v1.6.0: Server advertises properties:
 * - version: Protocol version (e.g., "2")
 * - auth: "pin" or "none"
 * - hostname: Server hostname (e.g., "MacBook-Pro")
 */
class MdnsDiscovery(context: Context) {
    companion object {
        private const val TAG = "MdnsDiscovery"
        private const val SERVICE_TYPE = "_iobus._tcp." // NSD wants no "local."
    }

    data class DiscoveredServer(
        val serviceName: String,
        val hostname: String,
        val port: Int,
        val protocolVersion: Int,
        val authRequired: Boolean,
    )

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null

    /**
     * Start discovering IOBus servers on the local network.
     *
     * @param onServerFound Callback invoked when a server is discovered and resolved
     * @param onServerLost Callback invoked when a server disappears
     */
    fun startDiscovery(
        onServerFound: (DiscoveredServer) -> Unit,
        onServerLost: (String) -> Unit
    ) {
        if (discoveryListener != null) {
            Log.w(TAG, "Discovery already running")
            return
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to start: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to stop: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                // Resolve to get host/port/attributes
                resolveService(serviceInfo, onServerFound)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                onServerLost(serviceInfo.serviceName)
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            discoveryListener = null
        }
    }

    /**
     * Stop discovery.
     */
    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery", e)
            }
            discoveryListener = null
        }
    }

    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        onResolved: (DiscoveredServer) -> Unit
    ) {
        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Resolved: ${serviceInfo.serviceName} at ${serviceInfo.host}:${serviceInfo.port}")

                // Extract properties (may be null on older Android versions)
                val attributes = serviceInfo.attributes
                val versionStr = attributes["version"]?.decodeToString() ?: "1"
                val authStr = attributes["auth"]?.decodeToString() ?: "none"
                val hostname = attributes["hostname"]?.decodeToString()
                    ?: serviceInfo.host.hostAddress ?: "unknown"

                val server = DiscoveredServer(
                    serviceName = serviceInfo.serviceName,
                    hostname = serviceInfo.host.hostAddress ?: "unknown",
                    port = serviceInfo.port,
                    protocolVersion = versionStr.toIntOrNull() ?: 1,
                    authRequired = authStr == "pin",
                )

                onResolved(server)
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve service", e)
        }
    }
}
