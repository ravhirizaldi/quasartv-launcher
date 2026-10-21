package com.quasartv.launcher.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import com.quasartv.launcher.R
import com.quasartv.launcher.data.AppCatalog
import com.quasartv.launcher.data.MotionPreference
import com.quasartv.launcher.data.DensityPreference

sealed class AppDiscoveryState {
    object Loading : AppDiscoveryState()
    data class Success(val apps: List<AppCatalog.AppRecord>) : AppDiscoveryState()
    data class Failed(val reason: String) : AppDiscoveryState()
}

fun buildAppsScreen(
    context: Context,
    apps: List<AppCatalog.AppRecord>,
    favoritePackages: Collection<String>,
    callbacks: LauncherUiCallbacks,
    discovery: AppDiscoveryState = AppDiscoveryState.Success(apps),
    favoriteSelectionMode: Boolean = false,
    motionPreference: MotionPreference = MotionPreference.FULL,
    density: DensityPreference = DensityPreference.RELAXED
): View {
    val root = ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        clipChildren = false
        clipToPadding = false
        contentDescription = if (favoriteSelectionMode) "Choose favorites" else "Installed apps"
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipToPadding = false
        clipChildren = false
        setPadding(context.dp(8f), 0, context.dp(12f), context.dp(16f))
    }
    root.addView(content, FrameLayout.LayoutParams(-1, -2))

    val visibleApps = when (val state = discovery) {
        AppDiscoveryState.Loading -> emptyList()
        is AppDiscoveryState.Success -> state.apps
        is AppDiscoveryState.Failed -> emptyList()
    }
    val favoriteOrder = favoritePackages.toList()
    val favoriteSet = favoriteOrder.toSet()

    content.addView(pageTitle(context,
        if (favoriteSelectionMode) "Choose your favorites" else "Your apps",
        if (favoriteSelectionMode) "Press OK to add or remove. Hold OK to arrange."
        else "Everything installed. Ready to open."
    ))
    content.addView(TextView(context).apply {
        text = when (discovery) {
            AppDiscoveryState.Loading -> "Loading apps…"
            is AppDiscoveryState.Failed -> "App discovery failed"
            is AppDiscoveryState.Success -> if (visibleApps.isEmpty()) {
                "No launchable apps found"
            } else {
                if (favoriteSelectionMode) {
                    "${visibleApps.size} apps · ${favoriteSet.size} favorites"
                } else {
                    "${visibleApps.size} apps · Hold OK for favorite actions"
                }
            }
        }
        applyTextRole(14f, R.color.color_muted_text)
        layoutParams = LinearLayout.LayoutParams(-1, context.dp(32f)).apply {
            bottomMargin = context.dp(10f)
        }
        contentDescription = safeText(text, "Apps status")
    })
    if (discovery is AppDiscoveryState.Loading) {
        content.addView(TextView(context).apply {
            text = "Loading apps…"
            applyTextRole(18f, R.color.color_muted_text)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, context.dp(160f))
            contentDescription = "Loading apps"
        })
        return root
    }

    if (discovery is AppDiscoveryState.Failed) {
        content.addView(TextView(context).apply {
            text = safeText(discovery.reason, "Unable to discover apps.")
            applyTextRole(18f, R.color.color_error)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, context.dp(90f))
            contentDescription = safeText(discovery.reason, "Unable to discover apps")
        })
        val settings = TextView(context).apply {
            text = "Open Android system settings"
            applyTextRole(17f, R.color.color_warm_ivory_text, bold = true)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, context.dp(60f)).apply {
                topMargin = context.dp(10f)
            }
        }
        settings.accessibleFocusableControl(
            description = "Open Android system settings",
            context = context,
            onClick = { callbacks.onSelectDestination(SYSTEM_SETTINGS_DESTINATION) },
            baseColorResource = R.color.color_plum_surface,
            cornerRadiusDp = 10f,
            motionPreference = motionPreference
        )
        content.addView(settings)
        return root
    }


    if (visibleApps.isEmpty()) {
        val empty = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dp(20f), context.dp(24f), context.dp(20f), context.dp(24f))
            layoutParams = LinearLayout.LayoutParams(-1, context.dp(210f))
        }
        empty.addView(TextView(context).apply {
            text = "Android did not report any launchable apps."
            applyTextRole(18f, R.color.color_muted_text)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, context.dp(72f))
            contentDescription = "Android did not report any launchable apps"
        })
        val settings = TextView(context).apply {
            text = "Open Android system settings"
            applyTextRole(17f, R.color.color_warm_ivory_text, bold = true)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, context.dp(60f)).apply {
                topMargin = context.dp(10f)
            }
        }
        settings.accessibleFocusableControl(
            description = "Open Android system settings",
            context = context,
            onClick = { callbacks.onSelectDestination(SYSTEM_SETTINGS_DESTINATION) },
            baseColorResource = R.color.color_plum_surface,
            cornerRadiusDp = 10f,
            motionPreference = motionPreference
        )
        empty.addView(settings)
        content.addView(empty)
        return root
    }

    val columns = appGridColumns(context.resources.displayMetrics.widthPixels, context.resources.configuration.screenWidthDp)
    val gap = if (density == DensityPreference.COMPACT) 10f else 18f
    val enlargedText = context.resources.configuration.fontScale > 1.1f
    val rows = mutableListOf<LinearLayout>()
    val cards = mutableListOf<View>()
    visibleApps.forEachIndexed { index, app ->
        if (index % columns == 0) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(-1, context.dp(if (enlargedText) 190f else 166f)).apply {
                    bottomMargin = context.dp(12f)
                }
            }
            rows += row
            content.addView(row)
        }
        val row = rows.last()
        val favoriteIndex = favoriteOrder.indexOf(app.packageName)
        val isFavorite = app.packageName in favoriteSet
        val card = FocusLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(context.dp(10f), context.dp(10f), context.dp(10f), context.dp(8f))
            minimumHeight = context.dp(152f)
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply {
                if (index % columns > 0) marginStart = context.dp(gap)
            }
        }
        card.addView(appIconView(context, app, 82))
        card.addView(TextView(context).apply {
            text = safeText(app.label, "Application")
            applyTextRole(17f, R.color.color_warm_ivory_text, bold = true)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(-1, context.dp(if (enlargedText) 58f else 42f)).apply {
                topMargin = context.dp(6f)
            }
        })
        card.addView(TextView(context).apply {
            text = if (isFavorite) "Favorite" else "Hold OK to favorite"
            applyTextRole(13f, if (isFavorite) R.color.color_brass_metadata else R.color.color_muted_text)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(-1, context.dp(22f))
        })
        card.accessibleFocusableControl(
            description = if (favoriteSelectionMode) {
                if (isFavorite) {
                    "${appContentDescription(app)}. Favorite. Press OK to remove favorite. Long press for actions."
                } else {
                    "${appContentDescription(app)}. Not a favorite. Press OK to add favorite. Long press for actions."
                }
            } else if (isFavorite) {
                "${appContentDescription(app)}. Favorite. Press OK to launch. Long press for actions."
            } else {
                "${appContentDescription(app)}. Not a favorite. Press OK to launch. Long press for actions."
            },
            context = context,
            onClick = if (favoriteSelectionMode) {
                { callbacks.onToggleFavorite(app) }
            } else {
                { callbacks.onLaunchApp(app) }
            },
            baseColorResource = R.color.color_plum_surface,
            cornerRadiusDp = 10f,
            motionPreference = motionPreference
        )
        card.tag = app.packageName
        fun showActions() {
            val popup = PopupMenu(context, card)
            popup.menu.add(0, ACTION_TOGGLE, 0, if (isFavorite) "Remove favorite" else "Add favorite")
            if (isFavorite && favoriteIndex > 0) {
                popup.menu.add(0, ACTION_MOVE_LEFT, 1, "Move favorite left")
            }
            if (isFavorite && favoriteIndex >= 0 && favoriteIndex < favoriteOrder.lastIndex) {
                popup.menu.add(0, ACTION_MOVE_RIGHT, 2, "Move favorite right")
            }
            popup.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    ACTION_TOGGLE -> callbacks.onToggleFavorite(app)
                    ACTION_MOVE_LEFT -> callbacks.onMoveFavorite(app, favoriteIndex - 1)
                    ACTION_MOVE_RIGHT -> callbacks.onMoveFavorite(app, favoriteIndex + 1)
                }
                true
            }
            popup.show()
        }
        card.setOnLongClickListener {
            showActions()
            true
        }
        card.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_BUTTON_X)
            ) {
                showActions()
                true
            } else {
                false
            }
        }
        cards += card
        row.addView(card)
    }

    val missingColumns = (columns - visibleApps.size % columns) % columns
    repeat(missingColumns) {
        rows.last().addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply {
            marginStart = context.dp(gap)
        })
    }

    cards.forEachIndexed { index, card ->
        val rowIndex = index / columns
        val columnIndex = index % columns
        val rowStart = rowIndex * columns
        val rowEnd = minOf(cards.lastIndex, rowStart + columns - 1)
        card.nextFocusLeftId = if (columnIndex > 0) ensureViewId(cards[index - 1]) else ensureViewId(card)
        if (rowIndex == 0) card.nextFocusUpId = R.id.launcher_settings
        if (columnIndex < rowEnd - rowStart) card.nextFocusRightId = ensureViewId(cards[index + 1])
        val previousRow = rowIndex - 1
        if (previousRow >= 0) {
            val previousStart = previousRow * columns
            val previousEnd = minOf(cards.lastIndex, previousStart + columns - 1)
            val previousIndex = previousStart + minOf(columnIndex, previousEnd - previousStart)
            card.nextFocusUpId = ensureViewId(cards[previousIndex])
        }
        val nextRow = rowIndex + 1
        val rowCount = (cards.size + columns - 1) / columns
        if (nextRow < rowCount) {
            val nextStart = nextRow * columns
            val nextEnd = minOf(cards.lastIndex, nextStart + columns - 1)
            val nextIndex = nextStart + minOf(columnIndex, nextEnd - nextStart)
            card.nextFocusDownId = ensureViewId(cards[nextIndex])
        }
    }
    return root
}

private fun ensureViewId(view: View): Int {
    if (view.id == View.NO_ID) view.id = View.generateViewId()
    return view.id
}

private const val ACTION_TOGGLE = 1
private const val ACTION_MOVE_LEFT = 2
private const val ACTION_MOVE_RIGHT = 3

fun appGridColumns(widthPixels: Int, widthDp: Int): Int =
    if (widthPixels >= 1600 && widthDp >= 900) 5 else 4

