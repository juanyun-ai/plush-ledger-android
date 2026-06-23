package com.plushledger.auth

import com.plushledger.security.PinHasher
import com.plushledger.security.SecureStore

data class UserSession(
    val userId: String,
    val displayName: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val phone: String? = null,
    val email: String? = null
)

class SessionStore(private val secureStore: SecureStore) {
    fun ensureVersion2Defaults() {
        if (secureStore.getBoolean("v2_defaults_applied")) return
        secureStore.putBoolean("secure_screen", false)
        secureStore.putBoolean("lock_on_launch", false)
        secureStore.putBoolean("biometric_unlock", false)
        secureStore.putBoolean("v2_defaults_applied", true)
    }

    fun currentSession(): UserSession? {
        val userId = secureStore.getString("user_id") ?: return null
        return UserSession(
            userId = userId,
            displayName = secureStore.getString("display_name") ?: "本地账本",
            accessToken = secureStore.getString("access_token"),
            refreshToken = secureStore.getString("refresh_token"),
            phone = secureStore.getString("phone"),
            email = secureStore.getString("email")
        )
    }

    fun saveSession(session: UserSession) {
        secureStore.putString("user_id", session.userId)
        secureStore.putString("display_name", session.displayName)
        secureStore.putString("access_token", session.accessToken)
        secureStore.putString("refresh_token", session.refreshToken)
        secureStore.putString("phone", session.phone)
        secureStore.putString("email", session.email)
    }

    fun updateDisplayName(displayName: String) {
        secureStore.putString("display_name", displayName)
    }

    fun updateIdentity(email: String?, phone: String?) {
        secureStore.putString("email", email)
        secureStore.putString("phone", phone)
    }

    fun clearSession() {
        secureStore.remove("user_id", "display_name", "access_token", "refresh_token", "phone", "email")
    }

    fun hasLocalPassword(username: String): Boolean {
        val key = localCredentialKey(username)
        return secureStore.getString("${key}_salt") != null && secureStore.getString("${key}_hash") != null
    }

    fun saveLocalPassword(username: String, password: String) {
        val key = localCredentialKey(username)
        val record = PinHasher.createPassword(password)
        secureStore.putString("${key}_salt", record.salt)
        secureStore.putString("${key}_hash", record.hash)
    }

    fun verifyLocalPassword(username: String, password: String): Boolean {
        val key = localCredentialKey(username)
        val salt = secureStore.getString("${key}_salt") ?: return false
        val hash = secureStore.getString("${key}_hash") ?: return false
        return PinHasher.verify(password, salt, hash)
    }

    fun hasPin(): Boolean = secureStore.getString("pin_hash") != null && secureStore.getString("pin_salt") != null

    fun setPin(pin: String) {
        val record = PinHasher.create(pin)
        secureStore.putString("pin_salt", record.salt)
        secureStore.putString("pin_hash", record.hash)
    }

    fun verifyPin(pin: String): Boolean {
        val salt = secureStore.getString("pin_salt") ?: return false
        val hash = secureStore.getString("pin_hash") ?: return false
        return PinHasher.verify(pin, salt, hash)
    }

    fun isSecureScreenEnabled(): Boolean = secureStore.getBoolean("secure_screen", false)

    fun setSecureScreenEnabled(enabled: Boolean) {
        secureStore.putBoolean("secure_screen", enabled)
    }

    fun isLockOnLaunchEnabled(): Boolean = secureStore.getBoolean("lock_on_launch", false)

    fun setLockOnLaunchEnabled(enabled: Boolean) {
        secureStore.putBoolean("lock_on_launch", enabled)
    }

    fun isBiometricUnlockEnabled(): Boolean = secureStore.getBoolean("biometric_unlock", false)

    fun setBiometricUnlockEnabled(enabled: Boolean) {
        secureStore.putBoolean("biometric_unlock", enabled)
    }

    fun isDarkModeEnabled(): Boolean = secureStore.getBoolean("dark_mode", false)

    fun setDarkModeEnabled(enabled: Boolean) {
        secureStore.putBoolean("dark_mode", enabled)
    }

    fun themeTone(): String = secureStore.getString("theme_tone") ?: "warm"

    fun setThemeTone(tone: String) {
        secureStore.putString("theme_tone", tone)
    }

    fun areAutomaticUpdatePromptsEnabled(): Boolean = secureStore.getBoolean("automatic_update_prompts", true)

    fun setAutomaticUpdatePromptsEnabled(enabled: Boolean) {
        secureStore.putBoolean("automatic_update_prompts", enabled)
    }

    fun defaultAccountId(userId: String): String? = secureStore.getString("default_account_$userId")

    fun setDefaultAccountId(userId: String, accountId: String) {
        secureStore.putString("default_account_$userId", accountId)
    }

    private fun localCredentialKey(username: String): String =
        PinHasher.stableLocalUserId("credential:${username.trim().lowercase()}")
}
