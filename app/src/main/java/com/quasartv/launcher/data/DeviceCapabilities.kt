package com.quasartv.launcher.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvInputManager
import android.os.Build

sealed class DeviceCapabilities {
    data class Success(
        val appDiscoveryAvailable: Boolean,
        val tvInputServiceAvailable: Boolean,
        val systemInputPickerIntent: Intent?
    ) : DeviceCapabilities()

    data class Unavailable(val reason: String) : DeviceCapabilities()

    data class Failed(
        val reason: String,
        val cause: Throwable? = null
    ) : DeviceCapabilities()

    companion object {
        fun detect(context: Context): DeviceCapabilities {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return Unavailable("TV input APIs require Android 7.0 or newer.")
            }

            return try {
                val packageManager = context.packageManager
                val tvInputManager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
                val pickerIntent = if (tvInputManager == null) {
                    null
                } else {
                    Intent(TvInputManager.ACTION_SETUP_INPUTS).takeIf { intent ->
                        packageManager.resolveActivity(intent, 0) != null
                    }
                }
                Success(
                    appDiscoveryAvailable = true,
                    tvInputServiceAvailable = tvInputManager != null,
                    systemInputPickerIntent = pickerIntent
                )
            } catch (error: Exception) {
                Failed("Unable to inspect device capabilities.", error)
            }
        }
    }
}
