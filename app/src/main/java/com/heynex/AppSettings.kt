package com.heynex

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages user preferences and the encrypted Groq API key.
 */
class AppSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "heynex_prefs"
        private const val KEY_WAKE_WORD_SENSITIVITY = "wake_word_sensitivity"
        private const val KEY_VOICE_SPEED = "voice_speed"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_LISTENING_ENABLED = "listening_enabled"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "heynex_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var wakeWordSensitivity: Float
        get() = prefs.getFloat(KEY_WAKE_WORD_SENSITIVITY, 0.5f)
        set(value) = prefs.edit { putFloat(KEY_WAKE_WORD_SENSITIVITY, value) }

    var voiceSpeed: Float
        get() = prefs.getFloat(KEY_VOICE_SPEED, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_VOICE_SPEED, value) }

    var listeningEnabled: Boolean
        get() = prefs.getBoolean(KEY_LISTENING_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_LISTENING_ENABLED, value) }

    var groqApiKey: String?
        get() = encryptedPrefs.getString(KEY_GROQ_API_KEY, null)
        set(value) = encryptedPrefs.edit { putString(KEY_GROQ_API_KEY, value) }

    fun clearApiKey() {
        encryptedPrefs.edit { remove(KEY_GROQ_API_KEY) }
    }
}
