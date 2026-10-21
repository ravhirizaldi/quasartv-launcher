package com.quasartv.launcher.ui

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import com.quasartv.launcher.R
import com.quasartv.launcher.data.AppCatalog
import com.quasartv.launcher.data.InputCatalog
import com.quasartv.launcher.data.InputSource
import com.quasartv.launcher.data.LauncherDefaults
import com.quasartv.launcher.data.MotionPreference
import com.quasartv.launcher.data.AppArtworkLoader

fun buildHomeScreen(
    context: Context,
    favorites: List<AppCatalog.AppRecord>,
    inputs: List<InputSource>,
    callbacks: LauncherUiCallbacks,
    inputResult: InputCatalog.Result? = InputCatalog.Result.Success(inputs, null),
    discovery: AppDiscoveryState = AppDiscoveryState.Success(favorites),
    motionPreference: MotionPreference = MotionPreference.FULL,
    artworkLoader: AppArtworkLoader? = null,
    initialFeaturedPackage: String? = null
): View {
    val root = LayoutInflater.from(context).inflate(R.layout.screen_home, null)
    val title = root.findViewById<TextView>(R.id.featured_title)
    val subtitle = root.findViewById<TextView>(R.id.featured_subtitle)
    val emptyAction = root.findViewById<TextView>(R.id.empty_action)
    val artwork = root.findViewById<ImageView>(R.id.featured_artwork)
    val imageBounds = RectF()
    val clipBounds = Rect()
    val cornerRadius = context.resources.getDimension(R.dimen.tile_radius)
    artwork.clipToOutline = true
    artwork.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val drawable = artwork.drawable ?: return outline.setEmpty()
            imageBounds.set(drawable.bounds)
            artwork.imageMatrix.mapRect(imageBounds)
            imageBounds.offset(artwork.paddingLeft.toFloat(), artwork.paddingTop.toFloat())
            imageBounds.round(clipBounds)
            outline.setRoundRect(clipBounds, cornerRadius)
        }
    }
    artwork.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> artwork.invalidateOutline() }
    val row = root.findViewById<LinearLayout>(R.id.favorites_row)
    var featuredPackage: String? = null
    fun feature(app: AppCatalog.AppRecord) {
        if (featuredPackage == app.packageName) return
        featuredPackage = app.packageName
        title.text = safeText(app.label, "Application")
        subtitle.text = "Favorite app · Ready when you are"
        artwork.setImageDrawable(app.icon?.constantState?.newDrawable(context.resources)?.mutate() ?: app.icon)
        artwork.invalidateOutline()
        artworkLoader?.load(app) { drawable ->
            if (featuredPackage == app.packageName) {
                artwork.setImageDrawable(drawable)
                artwork.invalidateOutline()
            }
        }
    }

    if (favorites.isNotEmpty() && discovery is AppDiscoveryState.Success) {
        feature(favorites.firstOrNull { it.packageName == initialFeaturedPackage } ?: favorites.first())
        favorites.forEach { app ->
            val card = FocusLayout(context).apply {
                id = View.generateViewId()
                tag = app.packageName
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(context.dp(10f), context.dp(4f), context.dp(10f), context.dp(8f))
                layoutParams = LinearLayout.LayoutParams(context.dp(166f), -1).apply {
                    marginEnd = context.dp(14f)
                }
                addView(appIconView(context, app, 66))
                addView(TextView(context).apply {
                    text = safeText(app.label, "Application")
                    applyTextRole(16f, bold = true)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(-1, context.dp(26f)))
            }
            card.accessibleFocusableControl(
                "${appContentDescription(app)}. Favorite. Press OK to launch.", context,
                { callbacks.onLaunchApp(app) }, cornerRadiusDp = 10f,
                motionPreference = motionPreference,
                onFocusChanged = { focused -> if (focused) feature(app) }
            )
            card.nextFocusLeftId = if (row.childCount == 0) card.id else row.getChildAt(row.childCount - 1).id
            card.nextFocusUpId = R.id.launcher_settings
            row.addView(card)
        }
        val allApps = FocusLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(context.dp(126f), -1).apply {
                marginEnd = context.dp(14f)
            }
            nextFocusUpId = R.id.launcher_settings
            addView(android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_apps)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(context.dp(28f), context.dp(28f)).apply { bottomMargin = context.dp(12f) })
            addView(TextView(context).apply {
                text = "All apps"
                applyTextRole(16f, bold = true)
                gravity = Gravity.CENTER
            })
            accessibleFocusableControl("All apps", context, {
                callbacks.onSelectDestination(LauncherDefaults.DESTINATION_APPS)
            }, cornerRadiusDp = 10f, motionPreference = motionPreference)
        }
        row.addView(allApps)
    } else {
        artwork.visibility = View.GONE
        root.findViewById<View>(R.id.favorites_heading).visibility = View.GONE
        root.findViewById<View>(R.id.favorites_scroll).visibility = View.GONE
        title.text = when (discovery) {
            AppDiscoveryState.Loading -> "Welcome home."
            is AppDiscoveryState.Failed -> "Apps couldn’t be loaded."
            is AppDiscoveryState.Success -> "Make yourself at home."
        }
        subtitle.text = when (discovery) {
            AppDiscoveryState.Loading -> "Finding your applications…"
            is AppDiscoveryState.Failed -> safeText(discovery.reason, "Open Android settings to check your apps.")
            is AppDiscoveryState.Success -> "Your favorite apps belong here. Choose a few to make this space yours."
        }
        if (discovery is AppDiscoveryState.Loading) {
            emptyAction.visibility = View.GONE
        } else {
            emptyAction.visibility = View.VISIBLE
            emptyAction.text = if (discovery is AppDiscoveryState.Failed) "Android settings" else "Browse apps"
            emptyAction.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_apps, 0, 0, 0)
            emptyAction.accessibleFocusableControl(emptyAction.text, context, {
                if (discovery is AppDiscoveryState.Failed) callbacks.onSelectDestination(SYSTEM_SETTINGS_DESTINATION)
                else callbacks.onChooseFavorites()
            }, motionPreference = motionPreference)
            emptyAction.nextFocusUpId = R.id.launcher_settings
        }
    }

    val inputRow = root.findViewById<LinearLayout>(R.id.home_inputs)
    val sources = inputResult.sources()
    if (sources.isEmpty()) {
        inputRow.addView(TextView(context).apply {
            text = when (inputResult) {
                null -> "Finding input sources…"
                is InputCatalog.Result.Success -> "No input sources detected"
                is InputCatalog.Result.Unsupported -> inputResult.reason
                is InputCatalog.Result.Failed -> inputResult.reason
            }
            applyTextRole(14f, R.color.color_muted_text)
        })
    } else {
        val chips = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(context.dp(4f), context.dp(4f), context.dp(4f), context.dp(4f))
        }
        sources.forEach { source ->
            chips.addView(inputSourceControl(context, source, callbacks, motionPreference, compact = true))
        }
        inputRow.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = true
            clipChildren = true
            setPadding(context.dp(6f), 0, context.dp(6f), 0)
            addView(chips)
        }, LinearLayout.LayoutParams(0, context.dp(98f), 1f))
    }
    return root
}
