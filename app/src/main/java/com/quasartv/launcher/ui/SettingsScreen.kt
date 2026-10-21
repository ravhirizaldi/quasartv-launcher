package com.quasartv.launcher.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.quasartv.launcher.R
import com.quasartv.launcher.data.DensityPreference
import com.quasartv.launcher.data.LauncherDefaults
import com.quasartv.launcher.data.MotionPreference
import com.quasartv.launcher.data.SettingsState

fun buildSettingsScreen(context: Context, state: SettingsState, callbacks: LauncherUiCallbacks): View {
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(context.dp(8f), 0, context.dp(16f), context.dp(20f))
    }
    val root = ScrollView(context).apply {
        isFillViewport = true
        clipToPadding = false
        clipChildren = false
        isVerticalScrollBarEnabled = false
        addView(content, FrameLayout.LayoutParams(-1, -2))
    }
    content.addView(pageTitle(context, "Settings", "A little more your own."))

    fun section(label: String) {
        content.addView(TextView(context).apply {
            text = label
            applyTextRole(18f, bold = true)
            isAccessibilityHeading = true
            setPadding(0, context.dp(12f), 0, context.dp(12f))
        })
    }
    fun choice(title: String, labels: Array<String>, selected: Int, choose: (Int) -> Unit) {
        AlertDialog.Builder(context).setTitle(title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                dialog.dismiss()
                choose(which)
            }.setNegativeButton("Cancel", null).show()
    }
    fun row(key: String, title: String, detail: String, value: String = "", action: () -> Unit): View {
        val control = FocusLayout(context).apply {
            tag = key
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(18f), context.dp(12f), context.dp(18f), context.dp(12f))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(8f) }
            minimumHeight = context.dp(68f)
            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { text = title; applyTextRole(18f, bold = true) })
                addView(TextView(context).apply {
                    text = detail
                    applyTextRole(14f, R.color.color_muted_text)
                    setPadding(0, context.dp(5f), 0, 0)
                })
            }
            addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply {
                text = value
                applyTextRole(15f, R.color.color_brass_metadata)
                gravity = Gravity.END
                setPadding(context.dp(20f), 0, 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))
            accessibleFocusableControl("$title. $detail. $value", context, action,
                cornerRadiusDp = 8f, motionPreference = state.motion)
            if (key == "settings_background") nextFocusUpId = R.id.launcher_settings
        }
        content.addView(control)
        return control
    }

    section("Appearance")
    val backgrounds = listOf(LauncherDefaults.BACKGROUND_MIDNIGHT, LauncherDefaults.BACKGROUND_DEEP_PLUM, LauncherDefaults.BACKGROUND_EMBER_NIGHT)
    val backgroundLabels = arrayOf("Midnight", "Muted plum", "Ember night")
    val backgroundIndex = backgrounds.indexOf(state.selectedBackground).coerceAtLeast(0)
    row("settings_background", "Background", "Quiet, static color palettes", backgroundLabels[backgroundIndex]) {
        choice("Background", backgroundLabels, backgroundIndex) { index ->
            callbacks.onSettingsChanged(state.copy(selectedBackground = backgrounds[index]))
        }
    }
    val motionLabels = arrayOf("Full motion", "Reduced motion", "Motion off")
    row("settings_motion", "Focus motion", "Choose how focus moves", motionLabels[state.motion.ordinal]) {
        choice("Focus motion", motionLabels, state.motion.ordinal) { index ->
            callbacks.onSettingsChanged(state.copy(motion = MotionPreference.entries[index]))
        }
    }
    val densityLabels = arrayOf("Relaxed", "Compact")
    row("settings_density", "App spacing", "Space between apps in your library", densityLabels[state.density.ordinal]) {
        choice("App spacing", densityLabels, state.density.ordinal) { index ->
            callbacks.onSettingsChanged(state.copy(density = DensityPreference.entries[index]))
        }
    }
    section("Favorites")
    row("settings_favorites", "Manage favorites", "Choose and arrange the apps on Home") { callbacks.onChooseFavorites() }
    section("About")
    content.addView(creatorCard(context, state.motion))
    return root
}

private fun creatorCard(context: Context, motion: MotionPreference): View = FocusLayout(context).apply {
    tag = "settings_about"
    orientation = LinearLayout.VERTICAL
    minimumHeight = context.dp(132f)
    setPadding(context.dp(20f), context.dp(16f), context.dp(20f), context.dp(16f))
    layoutParams = LinearLayout.LayoutParams(-1, -2)
    fun line(label: String, size: Float, muted: Boolean = false, bold: Boolean = false, gap: Float = 0f) {
        addView(TextView(context).apply {
            text = label
            applyTextRole(size, if (muted) R.color.color_muted_text else R.color.color_warm_ivory_text, bold)
            setPadding(0, context.dp(gap), 0, 0)
            autoLinkMask = 0
            linksClickable = false
        })
    }
    line("Made for the moments in between.", 14f, muted = true)
    line("Ravhi Rizaldi", 24f, bold = true, gap = 6f)
    line("https://github.com/ravhirizaldi", 14f, muted = true, gap = 8f)
    var clicks = 0
    accessibleFocusableControl("Made for the moments in between. Ravhi Rizaldi. https://github.com/ravhirizaldi", context, {
        if (++clicks == 10) {
            clicks = 0
            showCreatorPhoto(context, motion, this)
        }
    }, motionPreference = motion)
}

private fun showCreatorPhoto(context: Context, motion: MotionPreference, card: View) {
    val dialog = Dialog(context, R.style.Theme_QuasarTV)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val content = LayoutInflater.from(context).inflate(R.layout.dialog_creator_photo, null)
    val photo = content.findViewById<ImageView>(R.id.creator_photo)
    val close = content.findViewById<TextView>(R.id.creator_photo_close)
    close.accessibleFocusableControl("Close photo", context, { dialog.dismiss() }, motionPreference = motion)
    var bitmap: Bitmap? = null
    dialog.setContentView(content)
    dialog.setOnDismissListener {
        photo.setImageDrawable(null)
        bitmap?.recycle()
        bitmap = null
        if (card.isAttachedToWindow) card.requestFocus()
    }
    dialog.show()
    dialog.window?.setLayout(-1, -1)
    close.requestFocus()
    val main = Handler(Looper.getMainLooper())
    Thread({
        val decoded = BitmapFactory.decodeResource(context.resources, R.drawable.creator_easter_egg,
            BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.RGB_565 })
        main.post {
            if (dialog.isShowing) {
                bitmap = decoded
                photo.setImageBitmap(decoded)
                if (decoded != null) photo.contentDescription = "Easter egg photo"
            } else decoded?.recycle()
        }
    }, "QuasarTV-photo").start()
}
