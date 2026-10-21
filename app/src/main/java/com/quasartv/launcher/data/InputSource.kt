package com.quasartv.launcher.data

import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

enum class ConnectionState {
    CONNECTED,
    CONNECTED_STANDBY,
    DISCONNECTED,
    UNKNOWN;

    companion object {
        fun fromPlatformState(state: Int): ConnectionState = when (state) {
            TvInputManager.INPUT_STATE_CONNECTED -> CONNECTED
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> CONNECTED_STANDBY
            TvInputManager.INPUT_STATE_DISCONNECTED -> DISCONNECTED
            else -> UNKNOWN
        }
    }
}

data class InputSource(
    val inputId: String,
    val label: String,
    val type: Int,
    val connectionState: ConnectionState,
    val isSwitchable: Boolean
) {
    val isActive: Boolean
        get() = isSwitchable && connectionState == ConnectionState.CONNECTED

    val typeName: String
        get() = when (type) {
            TvInputInfo.TYPE_TUNER -> "Tuner"
            TvInputInfo.TYPE_COMPOSITE -> "Composite"
            TvInputInfo.TYPE_SVIDEO -> "S-Video"
            TvInputInfo.TYPE_COMPONENT -> "Component"
            TvInputInfo.TYPE_VGA -> "VGA"
            TvInputInfo.TYPE_DVI -> "DVI"
            TvInputInfo.TYPE_HDMI -> "HDMI"
            TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
            TvInputInfo.TYPE_SCART -> "SCART"
            TvInputInfo.TYPE_OTHER -> "Other"
            else -> "Unknown"
        }
}
