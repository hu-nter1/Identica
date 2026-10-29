package com.example.identica.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

data class Session(val adminId: Long, val username: String)

class SessionManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun startSession(adminId: Long, username: String) {
        val expirationTime = System.currentTimeMillis() + SESSION_TIMEOUT_MS

        prefs.edit()
            .putLong(KEY_ADMIN_ID, adminId)
            .putString(KEY_USERNAME, username)
            .putLong(KEY_EXPIRES_AT, expirationTime)
            .apply()
    }

    fun currentSession(): Session? {
        val adminId = prefs.getLong(KEY_ADMIN_ID, NO_ADMIN)
        val username = prefs.getString(KEY_USERNAME, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (adminId == NO_ADMIN || username == null) return null

        if (System.currentTimeMillis() > expiresAt) {
            endSession()
            return null
        }
        return Session(adminId, username)
    }

    fun touch() {
        if (!prefs.contains(KEY_ADMIN_ID)) return

        val newExpirationTime = System.currentTimeMillis() + SESSION_TIMEOUT_MS
        prefs.edit()
            .putLong(KEY_EXPIRES_AT, newExpirationTime)
            .apply()
    }

    fun endSession() {
        prefs.edit()
            .remove(KEY_ADMIN_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    fun lockRemainingMillis(): Long {
        val lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        return (lockedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun registerFailedAttempt() {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            val lockExpirationTime = System.currentTimeMillis() + LOCK_DURATION_MS
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL, lockExpirationTime)
                .apply()
        } else {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, attempts)
                .apply()
        }
    }

    fun clearFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    fun pepper(): ByteArray {
        val savedPepper = prefs.getString(KEY_PEPPER, null)
        if (savedPepper != null) {
            return Base64.decode(savedPepper, Base64.NO_WRAP)
        }

        val newPepper = ByteArray(PEPPER_BYTES)
        SecureRandom().nextBytes(newPepper)

        val encodedPepper = Base64.encodeToString(newPepper, Base64.NO_WRAP)
        prefs.edit()
            .putString(KEY_PEPPER, encodedPepper)
            .commit()

        return newPepper
    }

    private companion object {
        const val FILE_NAME = "identica_secure_prefs"
        const val KEY_ADMIN_ID = "session_admin_id"
        const val KEY_USERNAME = "session_username"
        const val KEY_EXPIRES_AT = "session_expires_at"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_until"
        const val KEY_PEPPER = "national_id_pepper"
        const val NO_ADMIN = -1L
        const val PEPPER_BYTES = 32
        const val SESSION_TIMEOUT_MS = 15L * 60L * 1000L
        const val LOCK_DURATION_MS = 60L * 1000L
        const val MAX_FAILED_ATTEMPTS = 5
    }
}
