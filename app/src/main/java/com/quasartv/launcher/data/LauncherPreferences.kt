package com.quasartv.launcher.data

import android.content.Context
import android.content.SharedPreferences

class LauncherPreferences(context: Context) {
    private val preferences: SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(
            LauncherDefaults.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    val favorites = FavoritesStore(preferences)

    var settings: SettingsState
        get() = readSettings()
        set(value) = writeSettings(value)

    var lastDestination: String
        get() = readLastDestination()
        set(value) = writeLastDestination(value)

    var lastFocus: FocusState
        get() = readLastFocus()
        set(value) = writeLastFocus(value)

    fun readSettings(): SettingsState = SettingsState(
        selectedBackground = normalizeBackground(readString(KEY_BACKGROUND)),
        motion = MotionPreference.fromStorage(readString(KEY_MOTION)),
        density = DensityPreference.fromStorage(readString(KEY_DENSITY)),
        lastDestination = normalizeDestination(readString(KEY_LAST_DESTINATION))
    )

    fun writeSettings(value: SettingsState) {
        preferences.edit()
            .putString(KEY_BACKGROUND, normalizeBackground(value.selectedBackground))
            .putString(KEY_MOTION, value.motion.storageValue)
            .putString(KEY_DENSITY, value.density.storageValue)
            .putString(KEY_LAST_DESTINATION, normalizeDestination(value.lastDestination))
            .apply()
    }

    fun readLastDestination(): String = normalizeDestination(readString(KEY_LAST_DESTINATION))

    fun writeLastDestination(value: String) {
        preferences.edit()
            .putString(KEY_LAST_DESTINATION, normalizeDestination(value))
            .apply()
    }

    fun readLastFocus(): FocusState {
        val packageName = normalizePackageName(readString(KEY_FOCUS_PACKAGE))
            ?: return LauncherDefaults.DEFAULT_FOCUS
        return FocusState(packageName, normalizeSource(readString(KEY_FOCUS_SOURCE)))
    }

    fun writeLastFocus(value: FocusState) {
        val packageName = normalizePackageName(value.packageName)
        val editor = preferences.edit()
        if (packageName == null) {
            editor.remove(KEY_FOCUS_PACKAGE).remove(KEY_FOCUS_SOURCE)
        } else {
            editor.putString(KEY_FOCUS_PACKAGE, packageName)
            val source = normalizeSource(value.source)
            if (source == null) {
                editor.remove(KEY_FOCUS_SOURCE)
            } else {
                editor.putString(KEY_FOCUS_SOURCE, source)
            }
        }
        editor.apply()
    }

    private fun readString(key: String): String? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) {
        null
    }

    private fun normalizeBackground(value: String?): String {
        val normalized = value?.trim()
        return if (normalized != null && normalized in LauncherDefaults.SUPPORTED_BACKGROUND_IDS) {
            normalized
        } else {
            LauncherDefaults.DEFAULT_BACKGROUND
        }
    }

    private fun normalizeDestination(value: String?): String {
        val normalized = value?.trim()
        return if (normalized != null && normalized in LauncherDefaults.SUPPORTED_DESTINATIONS) {
            normalized
        } else {
            LauncherDefaults.DEFAULT_LAST_DESTINATION
        }
    }

    private fun normalizePackageName(value: String?): String? {
        val normalized = value?.trim()
        if (normalized == null || normalized.isEmpty() || normalized.any { it.isWhitespace() || it == '\u001f' }) return null
        return normalized
    }

    private fun normalizeSource(value: String?): String? {
        val normalized = value?.trim()
        return normalized?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val KEY_BACKGROUND = "selected_background"
        const val KEY_MOTION = "motion_preference"
        const val KEY_DENSITY = "density_preference"
        const val KEY_LAST_DESTINATION = "last_destination"
        const val KEY_FOCUS_PACKAGE = "last_focused_package"
        const val KEY_FOCUS_SOURCE = "last_focused_source"
    }
}
