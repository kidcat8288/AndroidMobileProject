package com.moneymate.app.auth

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// register() and verify() run PBKDF2 and block for a while; call them off the main thread.
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("moneymate_auth", Context.MODE_PRIVATE)
    val displayName: String get() = prefs.getString("name", "") ?: ""
    // [ADDED] email + "Email code at login" setting
    val email: String get() = prefs.getString("email", "") ?: ""
    var emailCodeAtLogin: Boolean
        get() = email.isNotBlank() && prefs.getBoolean("email_code_at_login", true)
        set(value) { prefs.edit().putBoolean("email_code_at_login", value).apply() }
    fun hasAccount(): Boolean = prefs.contains("hash") && prefs.contains("salt")
    fun register(name: String, email: String, password: String) { // [CHANGED]
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("name", name.trim())
            .putString("email", email.trim())               // [ADDED]
            .putBoolean("email_code_at_login", true)        // [ADDED]
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(password, salt), Base64.NO_WRAP))
            .commit()
    }
    fun verify(password: String): Boolean {
        if (password.isEmpty()) return false
        return runCatching {
            val salt = Base64.decode(prefs.getString("salt", null) ?: return false, Base64.NO_WRAP)
            val expected = Base64.decode(prefs.getString("hash", null) ?: return false, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, hash(password, salt)) // constant-time compare
        }.getOrDefault(false)
    }
    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256))
            .encoded
}
