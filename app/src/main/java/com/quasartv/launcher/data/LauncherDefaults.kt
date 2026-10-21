package com.quasartv.launcher.data

object LauncherDefaults {
    const val PREFERENCES_NAME = "quasartv_launcher"

    const val BACKGROUND_MIDNIGHT = "color_midnight_background"
    const val BACKGROUND_DEEP_PLUM = "color_deep_plum_background"
    const val BACKGROUND_EMBER_NIGHT = "color_ember_night_background"
    const val DEFAULT_BACKGROUND = BACKGROUND_MIDNIGHT

    const val DESTINATION_HOME = "home"
    const val DESTINATION_APPS = "apps"
    const val DESTINATION_INPUTS = "inputs"
    const val DESTINATION_SETTINGS = "settings"
    const val DEFAULT_LAST_DESTINATION = DESTINATION_HOME

    val SUPPORTED_BACKGROUND_IDS: Set<String> = setOf(
        BACKGROUND_MIDNIGHT,
        BACKGROUND_DEEP_PLUM,
        BACKGROUND_EMBER_NIGHT
    )
    val SUPPORTED_DESTINATIONS: Set<String> = setOf(
        DESTINATION_HOME,
        DESTINATION_APPS,
        DESTINATION_INPUTS,
        DESTINATION_SETTINGS
    )

    val DEFAULT_SETTINGS: SettingsState
        get() = SettingsState(
            selectedBackground = DEFAULT_BACKGROUND,
            motion = MotionPreference.FULL,
            density = DensityPreference.RELAXED,
            lastDestination = DEFAULT_LAST_DESTINATION
        )

    val DEFAULT_FOCUS: FocusState
        get() = FocusState()
}
