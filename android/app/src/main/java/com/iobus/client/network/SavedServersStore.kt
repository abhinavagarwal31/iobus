package com.iobus.client.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persists named server presets (e.g. "Home Wi-Fi", "Phone Hotspot")
 * using SharedPreferences.  Each entry stores host:port.
 */
data class SavedServer(
    val name: String,
    val host: String,
    val port: Int,
)

class SavedServersStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("iobus_saved_servers", Context.MODE_PRIVATE)

    private val _servers = MutableStateFlow<List<SavedServer>>(emptyList())
    val servers: StateFlow<List<SavedServer>> = _servers

    init {
        reload()
    }

    fun save(name: String, host: String, port: Int) {
        val key = name.trim()
        if (key.isBlank() || host.isBlank()) return
        prefs.edit().putString(key, "$host:$port").apply()
        reload()
    }

    fun delete(name: String) {
        prefs.edit().remove(name).apply()
        reload()
    }

    private fun reload() {
        _servers.value = prefs.all
            .mapNotNull { (name, value) ->
                val str = value as? String ?: return@mapNotNull null
                val parts = str.split(":")
                if (parts.size != 2) return@mapNotNull null
                val port = parts[1].toIntOrNull() ?: return@mapNotNull null
                SavedServer(name = name, host = parts[0], port = port)
            }
            .sortedBy { it.name }
    }
}
