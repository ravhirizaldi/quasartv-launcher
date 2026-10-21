package com.quasartv.launcher.ui

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.quasartv.launcher.R
import com.quasartv.launcher.data.AppCatalog
import com.quasartv.launcher.data.InputSource
import com.quasartv.launcher.data.SettingsState
import com.quasartv.launcher.data.MotionPreference
import java.util.Locale
import kotlin.math.roundToInt

/** Actions owned by MainActivity. Screen builders only render data and emit intent. */
interface LauncherUiCallbacks {
    fun onLaunchApp(app: AppCatalog.AppRecord)
    fun onToggleFavorite(app: AppCatalog.AppRecord)
    fun onChooseFavorites()
    fun onMoveFavorite(app: AppCatalog.AppRecord, targetIndex: Int)
    fun onActivateInput(source: InputSource)
    fun onOpenInputPicker()
    fun onSettingsChanged(state: SettingsState)
    fun onSelectDestination(destination: String)
}

/** Destination token consumed by MainActivity for the platform Settings screen. */
const val SYSTEM_SETTINGS_DESTINATION: String = "android_settings"

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

fun Context.sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

fun Context.dimen(resourceId: Int): Int = resources.getDimensionPixelSize(resourceId)

fun TextView.applyTextRole(
    sizeSp: Float,
    colorResource: Int = R.color.color_warm_ivory_text,
    bold: Boolean = false
): TextView {
    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(context.getColor(colorResource))
    typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    includeFontPadding = false
    return this
}

fun TextView.applyBodyRole(): TextView = applyTextRole(
    sizeSp = context.resources.getDimension(R.dimen.text_body) / context.resources.displayMetrics.scaledDensity,
    colorResource = R.color.color_warm_ivory_text
)

fun TextView.applyMetadataRole(): TextView = applyTextRole(
    sizeSp = context.resources.getDimension(R.dimen.text_metadata) / context.resources.displayMetrics.scaledDensity,
    colorResource = R.color.color_brass_metadata
)

fun divider(context: Context, vertical: Boolean = false): View = View(context).apply {
    setBackgroundColor(context.getColor(R.color.color_outline))
    layoutParams = if (vertical) {
        android.widget.LinearLayout.LayoutParams(context.dp(1f), context.dp(32f))
    } else {
        android.widget.LinearLayout.LayoutParams(-1, context.dp(1f))
    }
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

private fun blendColors(base: Int, tint: Int, amount: Float): Int {
    val ratio = amount.coerceIn(0f, 1f)
    val inverse = 1f - ratio
    return Color.rgb(
        (Color.red(base) * inverse + Color.red(tint) * ratio).roundToInt(),
        (Color.green(base) * inverse + Color.green(tint) * ratio).roundToInt(),
        (Color.blue(base) * inverse + Color.blue(tint) * ratio).roundToInt()
    )
}

/** Applies tonal lift and an ember outline with the requested focus scale. */
fun View.applyFocusStyle(
    context: Context,
    baseColorResource: Int = R.color.color_plum_surface,
    cornerRadiusDp: Float = 8f,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    motionPreference: MotionPreference = MotionPreference.FULL
): View {
    stateListAnimator = null
    val baseColor = context.getColor(baseColorResource)
    val emberColor = context.getColor(R.color.color_ember_primary)
    val focusOutlineColor = context.getColor(R.color.color_ember_focus)
    val focusBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dp(cornerRadiusDp).toFloat()
    }
    background = focusBackground
    val effectiveMotion = if (ValueAnimator.areAnimatorsEnabled()) motionPreference else MotionPreference.OFF
    val (focusedScale, durationMillis) = focusMotion(effectiveMotion)
    val focusedStrokeWidth = context.dimen(R.dimen.focus_stroke_width)
    fun render(focused: Boolean, animateScale: Boolean) {
        focusBackground.setColor(
            if (focused) blendColors(baseColor, emberColor, 0.12f) else baseColor
        )
        focusBackground.setStroke(
            focusedStrokeWidth,
            if (focused) focusOutlineColor else Color.TRANSPARENT
        )
        val scale = if (focused) focusedScale else 1f
        animate().cancel()
        if (animateScale && durationMillis > 0L) {
            animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(durationMillis)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            animate().cancel()
            scaleX = scale
            scaleY = scale
        }
        onFocusChanged?.invoke(focused)
    }
    setOnFocusChangeListener { _, focused -> render(focused, animateScale = true) }
    render(isFocused, animateScale = false)
    return this
}

/** Makes any standard View usable by a D-pad and exposes a non-null spoken label. */
fun <T : View> T.accessibleFocusableControl(
    description: CharSequence,
    context: Context,
    onClick: (() -> Unit)? = null,
    baseColorResource: Int = R.color.color_plum_surface,
    cornerRadiusDp: Float = 8f,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    motionPreference: MotionPreference = MotionPreference.FULL
): T {
    isFocusable = true
    isFocusableInTouchMode = true
    isClickable = onClick != null
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    contentDescription = description.toString().trim().ifEmpty { "Control" }
    if (onClick != null) setOnClickListener { onClick() }
    applyFocusStyle(context, baseColorResource, cornerRadiusDp, onFocusChanged, motionPreference)
    return this
}

fun TextView.tvLabel(
    context: Context,
    text: CharSequence,
    sizeSp: Float,
    colorResource: Int = R.color.color_warm_ivory_text,
    bold: Boolean = false
): TextView = TextView(context).apply {
    this.text = text
    applyTextRole(sizeSp, colorResource, bold)
    gravity = Gravity.CENTER_VERTICAL
}

fun appIconView(
    context: Context,
    app: AppCatalog.AppRecord,
    sizeDp: Int = 68
): View {
    val description = "${safeText(app.label, "Application")} icon"
    val icon = app.icon
    if (icon != null) {
        return ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(context.dp(sizeDp.toFloat()), context.dp(sizeDp.toFloat()))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(context.dp(6f), context.dp(6f), context.dp(6f), context.dp(6f))
            setImageDrawable(icon.constantState?.newDrawable(context.resources)?.mutate() ?: icon)
            contentDescription = description
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
    return TextView(context).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(context.dp(sizeDp.toFloat()), context.dp(sizeDp.toFloat()))
        text = safeText(app.label, "?").first().uppercase(Locale.getDefault())
        applyTextRole(sizeSp = 30f, colorResource = R.color.color_warm_ivory_text, bold = true)
        gravity = Gravity.CENTER
        contentDescription = description
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(8f).toFloat()
            setColor(context.getColor(R.color.color_midnight_background))
            setStroke(context.dp(1f), context.getColor(R.color.color_outline))
        }
    }
}

fun safeText(value: CharSequence?, fallback: String): String =
    value?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

fun appContentDescription(app: AppCatalog.AppRecord): String =
    safeText(app.label, "Application")

fun inputContentDescription(source: InputSource): String =
    "${safeText(source.label, "Input source")}, ${safeText(source.typeName, "Unknown type")}, ${source.connectionState.name.lowercase(Locale.ROOT)}"

fun focusMotion(preference: MotionPreference): Pair<Float, Long> = when (preference) {
    MotionPreference.FULL -> 1.04f to 140L
    MotionPreference.REDUCED -> 1.015f to 70L
    MotionPreference.OFF -> 1f to 0L
}

class FocusLayout(context: Context) : LinearLayout(context) {
    override fun getDrawingRect(rect: Rect) {
        super.getDrawingRect(rect)
        // Include the shelf's 6dp viewport padding and scaled outline in native focus scrolling.
        val insetX = (width * 0.02f).roundToInt() + context.dp(10f)
        val insetY = (height * 0.02f).roundToInt() + context.dp(4f)
        rect.inset(-insetX, -insetY)
    }
}

fun pageTitle(context: Context, title: String, subtitle: String): android.widget.LinearLayout =
    android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(0, context.dp(16f), 0, context.dp(22f))
        addView(TextView(context).apply {
            text = title
            applyTextRole(34f, bold = true)
            isAccessibilityHeading = true
        })
        addView(TextView(context).apply {
            text = subtitle
            applyTextRole(15f, R.color.color_muted_text)
            setPadding(0, context.dp(8f), 0, 0)
        })
    }

fun actionButton(context: Context, label: String, motion: MotionPreference, action: () -> Unit): TextView =
    TextView(context).apply {
        text = label
        applyTextRole(16f, bold = true)
        gravity = Gravity.CENTER
        setPadding(context.dp(20f), 0, context.dp(20f), 0)
        minimumHeight = context.dp(48f)
        accessibleFocusableControl(label, context, action, cornerRadiusDp = 8f, motionPreference = motion)
    }
