package com.iobus.client.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Secure PIN storage using Android Keystore + EncryptedSharedPreferences.
 *
 * Stores PINs per server address (hostname:port) so the app can reconnect
 * automatically without asking again.
 *
 * v1.6.0 security design:
 * - PIN stored encrypted at rest using Android Keystore
 * - Key derived from hostname to allow per-server PINs
 * - SHA-256 hash computed on-demand with server's salt+challenge
 */
class PinStore(context: Context) {
    companion object {
        private const val TAG = "PinStore"
        private const val PREFS_NAME = "iobus_pin_storage"
        private const val KEY_PREFIX = "pin_"
    }

    private val prefs: SharedPreferences

    init {
        // Use Android's recommended EncryptedSharedPreferences
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted preferences, falling back to regular prefs", e)
            // Fallback to unencrypted (not ideal but better than crash)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Store PIN for a server (hostname:port).
     * @param serverAddress Server hostname or IP (e.g., "192.168.1.10:9800" or "MacBook-Pro.local:9800")
     * @param pin 6-digit PIN (000000-999999)
     */
    fun storePin(serverAddress: String, pin: String) {
        require(pin.length == 6 && pin.all { it.isDigit() }) {
            "PIN must be exactly 6 digits"
        }
        prefs.edit().putString(KEY_PREFIX + serverAddress, pin).apply()
        Log.d(TAG, "Stored PIN for $serverAddress")
    }

    /**
     * Retrieve stored PIN for a server.
     * @return 6-digit PIN or null if not found
     */
    fun getPin(serverAddress: String): String? {
        return prefs.getString(KEY_PREFIX + serverAddress, null)
    }

    /**
     * Check if a PIN is stored for a server.
     */
    fun hasPin(serverAddress: String): Boolean {
        return prefs.contains(KEY_PREFIX + serverAddress)
    }

    /**
     * Remove stored PIN for a server.
     */
    fun removePin(serverAddress: String) {
        prefs.edit().remove(KEY_PREFIX + serverAddress).apply()
        Log.d(TAG, "Removed PIN for $serverAddress")
    }

    /**
     * Compute PIN hash for authentication.
     * Hash = SHA-256(pin + salt + challenge)
     *
     * @param pin 6-digit PIN string
     * @param salt 16-byte salt from server
     * @param challenge 4-byte challenge from server
     * @return 32-byte SHA-256 hash
     */
    fun computePinHash(pin: String, salt: ByteArray, challenge: ByteArray): ByteArray {
        require(pin.length == 6 && pin.all { it.isDigit() }) {
            "PIN must be exactly 6 digits"
        }
        require(salt.size == 16) { "Salt must be 16 bytes" }
        require(challenge.size == 4) { "Challenge must be 4 bytes" }

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(pin.toByteArray(Charsets.UTF_8))
        digest.update(salt)
        digest.update(challenge)
        return digest.digest()
    }

    /**
     * Clear all stored PINs (e.g., for logout/reset).
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all stored PINs")
    }
}
