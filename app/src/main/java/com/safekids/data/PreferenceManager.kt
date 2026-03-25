package com.safekids.data

import android.content.Context
import android.content.SharedPreferences
import com.safekids.core.ContentClassifier

/**
 * PreferenceManager — simple SharedPreferences wrapper for app settings.
 */
class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("safekids_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN = "parent_pin"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_SENSITIVITY = "sensitivity_level"
        private const val KEY_PROTECTION_ENABLED = "protection_enabled"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    var parentPin: String
        get() = prefs.getString(KEY_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var protectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, value).apply()

    var sensitivityLevel: ContentClassifier.SensitivityLevel
        get() {
            val name = prefs.getString(KEY_SENSITIVITY, ContentClassifier.SensitivityLevel.BALANCED.name)
            return ContentClassifier.SensitivityLevel.valueOf(name ?: "BALANCED")
        }
        set(value) = prefs.edit().putString(KEY_SENSITIVITY, value.name).apply()

    val isFirstLaunch: Boolean
        get() {
            val first = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
            if (first) prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            return first
        }

    fun isPinSet(): Boolean = parentPin.isNotEmpty()

    fun verifyPin(input: String): Boolean = parentPin == input
}
