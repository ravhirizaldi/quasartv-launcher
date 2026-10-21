package com.quasartv.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.media.tv.TvInputInfo
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.quasartv.launcher.R
import com.quasartv.launcher.data.ConnectionState
import com.quasartv.launcher.data.InputCatalog
import com.quasartv.launcher.data.InputSource
import com.quasartv.launcher.data.MotionPreference

fun InputCatalog.Result?.sources(): List<InputSource> = when (this) {
    is InputCatalog.Result.Success -> sources
    is InputCatalog.Result.Unsupported -> sources
    else -> emptyList()
}

fun InputCatalog.Result?.hasInputSetup(): Boolean = when (this) {
    is InputCatalog.Result.Success -> systemInputPickerIntent != null
    is InputCatalog.Result.Unsupported -> systemInputPickerIntent != null
    is InputCatalog.Result.Failed -> systemInputPickerIntent != null
    null -> false
}

fun InputCatalog.Result?.activeSources(): List<InputSource> = sources().filter { it.isActive }

fun buildInputsScreen(
    context: Context,
    result: InputCatalog.Result?,
    callbacks: LauncherUiCallbacks,
    motionPreference: MotionPreference = MotionPreference.FULL
): View {
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(context.dp(8f), 0, context.dp(8f), context.dp(16f))
    }
    val root = ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        clipChildren = false
        addView(content, FrameLayout.LayoutParams(-1, -2))
    }
    content.addView(pageTitle(context, "Inputs", "Your devices, on the big screen."))
    val sources = result.sources()
    val setupAvailable = result.hasInputSetup()

    if (sources.isEmpty()) {
        val empty = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(4f), context.dp(28f), 0, context.dp(26f))
        }
        empty.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_inputs)
            imageTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.color_muted_text))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(42f), context.dp(42f)))
        empty.addView(TextView(context).apply {
            text = when (result) {
                null -> "Finding your sources…"
                is InputCatalog.Result.Success -> "No input sources detected"
                is InputCatalog.Result.Unsupported -> "TV inputs aren’t available here"
                is InputCatalog.Result.Failed -> "Inputs couldn’t be loaded"
            }
            applyTextRole(26f, bold = true)
            setPadding(0, context.dp(18f), 0, context.dp(10f))
        })
        empty.addView(TextView(context).apply {
            text = when (result) {
                null -> "Checking the TV’s input service."
                is InputCatalog.Result.Success -> "Connect a source to see it here."
                is InputCatalog.Result.Unsupported -> result.reason
                is InputCatalog.Result.Failed -> result.reason
            }
            applyTextRole(16f, R.color.color_muted_text)
        })
        content.addView(empty)
    } else {
        val columns = if (context.resources.configuration.screenWidthDp >= 1100) 3 else 2
        sources.chunked(columns).forEach { group ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(-1, context.dp(144f)).apply {
                    bottomMargin = context.dp(16f)
                }
            }
            group.forEach { source ->
                val tile = inputSourceControl(context, source, callbacks, motionPreference)
                tile.layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginEnd = context.dp(16f)
                }
                row.addView(tile)
            }
            repeat(columns - group.size) { row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
            content.addView(row)
        }
    }
    if (result != null && setupAvailable) {
        val setup = actionButton(context, "Android input settings", motionPreference, callbacks::onOpenInputPicker)
        content.addView(setup, LinearLayout.LayoutParams(-2, context.dp(48f)).apply {
            topMargin = context.dp(12f)
        })
    } else if (result != null) {
        content.addView(TextView(context).apply {
            text = "Android input settings are unavailable on this device."
            applyTextRole(14f, R.color.color_muted_text)
            setPadding(0, context.dp(14f), 0, context.dp(12f))
        })
    }
    if (result is InputCatalog.Result.Failed || result is InputCatalog.Result.Unsupported) {
        val settings = actionButton(context, "Android system settings", motionPreference) {
            callbacks.onSelectDestination(SYSTEM_SETTINGS_DESTINATION)
        }
        content.addView(settings, LinearLayout.LayoutParams(-2, context.dp(48f)).apply { topMargin = context.dp(12f) })
    }
    return root
}

fun inputSourceControl(
    context: Context,
    source: InputSource,
    callbacks: LauncherUiCallbacks,
    motion: MotionPreference,
    compact: Boolean = false
): View {
    val available = source.isActive
    val status = when {
        available -> "${source.typeName} · Switch input"
        !source.isSwitchable -> "${source.typeName} · Switching unavailable"
        else -> "${source.typeName} · ${source.connectionState.name.lowercase().replace('_', ' ')}"
    }
    val labelColor = if (available) R.color.color_warm_ivory_text else R.color.color_muted_text
    return FocusLayout(context).apply {
        id = View.generateViewId()
        tag = source.inputId
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(12f), context.dp(10f), context.dp(12f), context.dp(10f))
        layoutParams = LinearLayout.LayoutParams(context.dp(if (compact) 136f else 240f), -1).apply {
            marginEnd = context.dp(14f)
        }
        addView(ImageView(context).apply {
            setImageResource(inputIconResource(source.type))
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = ColorStateList.valueOf(context.getColor(labelColor))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(40f), context.dp(25f)).apply { bottomMargin = context.dp(12f) })
        addView(TextView(context).apply {
            text = safeText(source.label, "Input source")
            applyTextRole(if (compact) 16f else 22f, labelColor)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (!compact) addView(TextView(context).apply {
            text = status
            applyTextRole(if (compact) 12f else 14f, R.color.color_muted_text)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, context.dp(4f), 0, 0)
        })
        accessibleFocusableControl("${inputContentDescription(source)}. $status", context,
            onClick = when {
                !available -> null
                else -> ({ callbacks.onActivateInput(source) })
            }, cornerRadiusDp = 10f, motionPreference = motion)
        if (compact) nextFocusDownId = id
    }
}

internal fun inputIconResource(type: Int): Int = when (type) {
    TvInputInfo.TYPE_HDMI -> R.drawable.patchwall_input_hdmi
    TvInputInfo.TYPE_TUNER -> R.drawable.patchwall_input_tv
    TvInputInfo.TYPE_COMPOSITE, TvInputInfo.TYPE_COMPONENT, TvInputInfo.TYPE_SVIDEO -> R.drawable.patchwall_input_av
    else -> R.drawable.ic_inputs
}
