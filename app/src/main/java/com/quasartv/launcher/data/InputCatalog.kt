package com.quasartv.launcher.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build

class InputCatalog(private val context: Context) {
    fun discover(): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return Result.Unsupported("TV input APIs are unavailable on this Android version.")
        }

        val pickerIntent = inputPickerIntent()
        val manager = try {
            context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        } catch (error: RuntimeException) {
            return Result.Failed("Unable to access the TV input service.", pickerIntent, error)
        }

        if (manager == null) {
            return Result.Unsupported(
                reason = "TV input service is unavailable.",
                systemInputPickerIntent = pickerIntent
            )
        }

        return try {
            val sources = manager.tvInputList.orEmpty()
                .mapNotNull { info -> source(info, manager) }
                .sortedWith(compareBy<InputSource>({ !it.isActive }, { it.type }, { it.inputId }))
            Result.Success(sources, pickerIntent)
        } catch (error: RuntimeException) {
            Result.Failed("Unable to discover TV inputs.", pickerIntent, error)
        }
    }

    private fun source(info: TvInputInfo, manager: TvInputManager): InputSource? {
        val inputId = try {
            info.id.takeIf { it.isNotBlank() }
        } catch (_: RuntimeException) {
            null
        } ?: return null

        val customLabel = try {
            info.loadCustomLabel(context)
        } catch (_: RuntimeException) {
            null
        }
        val providerLabel = try {
            info.loadLabel(context)
        } catch (_: RuntimeException) {
            null
        }
        val type = try {
            info.type
        } catch (_: RuntimeException) {
            -1
        }
        val connectionState = try {
            ConnectionState.fromPlatformState(manager.getInputState(inputId))
        } catch (_: RuntimeException) {
            ConnectionState.UNKNOWN
        }
        val isSwitchable = try {
            info.isPassthroughInput
        } catch (_: RuntimeException) {
            false
        }

        return InputSource(
            inputId = inputId,
            label = inputDisplayLabel(customLabel, providerLabel, type, inputId),
            type = type,
            connectionState = connectionState,
            isSwitchable = isSwitchable
        )
    }

    private fun inputPickerIntent(): Intent? {
        val intent = Intent(TvInputManager.ACTION_SETUP_INPUTS)
        return try {
            intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
        } catch (_: RuntimeException) {
            null
        }
    }

    sealed class Result {
        data class Success(
            val sources: List<InputSource>,
            val systemInputPickerIntent: Intent?
        ) : Result()

        data class Unsupported(
            val reason: String,
            val sources: List<InputSource> = emptyList(),
            val systemInputPickerIntent: Intent? = null
        ) : Result()

        data class Failed(
            val reason: String,
            val systemInputPickerIntent: Intent? = null,
            val cause: Throwable? = null
        ) : Result()
    }
}

internal fun inputDisplayLabel(customLabel: CharSequence?, providerLabel: CharSequence?, type: Int, inputId: String): String {
    customLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val label = providerLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    // Xiaomi presents this internal tuner provider as Channel in its own source list.
    return if (type == TvInputInfo.TYPE_TUNER && label == "DroidLogicTvInput") "Channel" else label ?: inputId
}

