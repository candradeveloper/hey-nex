package com.heynex

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val apiKeyPref = findPreference<EditTextPreference>("groq_api_key")
        apiKeyPref?.setOnBindEditTextListener { editText ->
            editText.maxLines = 1
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        apiKeyPref?.setOnPreferenceChangeListener { _, newValue ->
            val key = newValue as? String
            if (!key.isNullOrBlank()) {
                AppSettings(requireContext()).groqApiKey = key
            }
            true
        }

        val wakePref = findPreference<SeekBarPreference>("wake_word_sensitivity")
        wakePref?.setOnPreferenceChangeListener { _, newValue ->
            val sensitivity = (newValue as? Int)?.div(100f) ?: 0.5f
            AppSettings(requireContext()).wakeWordSensitivity = sensitivity
            true
        }

        val voicePref = findPreference<SeekBarPreference>("voice_speed")
        voicePref?.setOnPreferenceChangeListener { _, newValue ->
            val speed = (newValue as? Int)?.div(100f) ?: 1.0f
            AppSettings(requireContext()).voiceSpeed = speed
            true
        }
    }
}
