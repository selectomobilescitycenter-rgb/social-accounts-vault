package com.socialvault.app.data

import android.content.Context
import java.security.MessageDigest

/**
 * App preferences (SharedPreferences):
 *  - darkMode: dark colour scheme on/off
 *  - PIN lock: only the SHA-256 hash of the 4-digit PIN is stored, never the PIN.
 *    There is no PIN recovery by design — forgetting the PIN means the vault
 *    cannot be opened again (the user is told this in the UI).
 */
class VaultPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) { prefs.edit().putBoolean("dark_mode", value).apply() }

    val isPinSet: Boolean
        get() = prefs.getString("pin_hash", null) != null

    fun setPin(pin: String) {
        prefs.edit().putString("pin_hash", sha256(pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val stored = prefs.getString("pin_hash", null) ?: return false
        return stored == sha256(pin)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
