package com.iobus.client.security

import android.content.Context
import java.security.MessageDigest

/**
 * Secure passcode storage for shutdown confirmation.
 *
 * Passcode is SHA-256 hashed before storage — never stored in plain text.
 * Uses Android SharedPreferences (MODE_PRIVATE) for local-only access.
 */
class PasscodeStore(context: Context) {

    private val prefs = context.getSharedPreferences("iobus_security", Context.MODE_PRIVATE)

    /** Whether a shutdown passcode has been configured. */
    fun hasPasscode(): Boolean = prefs.contains(KEY_HASH)

    /** Store a new passcode (hashed with SHA-256). */
    fun setPasscode(passcode: String) {
        prefs.edit().putString(KEY_HASH, sha256(passcode)).apply()
    }

    /** Verify an input passcode against the stored hash. */
    fun verify(passcode: String): Boolean {
        val stored = prefs.getString(KEY_HASH, null) ?: return false
        return sha256(passcode) == stored
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_HASH = "shutdown_passcode_sha256"
    }
}
