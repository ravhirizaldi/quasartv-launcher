package com.quasartv.launcher.data

enum class MotionPreference(val storageValue: String) {
    FULL("full"),
    REDUCED("reduced"),
    OFF("off");

    companion object {
        fun fromStorage(value: String?): MotionPreference = when (value?.trim()) {
            FULL.storageValue -> FULL
            REDUCED.storageValue -> REDUCED
            OFF.storageValue -> OFF
            else -> FULL
        }
    }
}

enum class DensityPreference(val storageValue: String) {
    RELAXED("relaxed"),
    COMPACT("compact");

    companion object {
        fun fromStorage(value: String?): DensityPreference = when (value?.trim()) {
            RELAXED.storageValue -> RELAXED
            COMPACT.storageValue -> COMPACT
            else -> RELAXED
        }
    }
}

data class SettingsState(
    val selectedBackground: String = LauncherDefaults.DEFAULT_BACKGROUND,
    val motion: MotionPreference = MotionPreference.FULL,
    val density: DensityPreference = DensityPreference.RELAXED,
    val lastDestination: String = LauncherDefaults.DEFAULT_LAST_DESTINATION
)
