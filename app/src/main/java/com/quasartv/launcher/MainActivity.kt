package com.quasartv.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.KeyEvent
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.TextView
import com.quasartv.launcher.data.AppCatalog
import com.quasartv.launcher.data.AppArtworkLoader
import com.quasartv.launcher.data.ConnectionState
import com.quasartv.launcher.data.FocusState
import com.quasartv.launcher.data.InputCatalog
import com.quasartv.launcher.data.InputSource
import com.quasartv.launcher.data.LaunchResult
import com.quasartv.launcher.data.LauncherDefaults
import com.quasartv.launcher.data.LauncherPreferences
import com.quasartv.launcher.data.SettingsState
import com.quasartv.launcher.ui.LauncherUiCallbacks
import com.quasartv.launcher.ui.AppDiscoveryState
import com.quasartv.launcher.ui.SYSTEM_SETTINGS_DESTINATION
import com.quasartv.launcher.ui.buildAppsScreen
import com.quasartv.launcher.ui.buildHomeScreen
import com.quasartv.launcher.ui.buildSettingsScreen
import com.quasartv.launcher.ui.inputContentDescription
import com.quasartv.launcher.ui.applyFocusStyle
import com.quasartv.launcher.ui.dp
import java.util.Date

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var rootView: View
    private lateinit var contentContainer: FrameLayout
    private lateinit var clockDateRegion: TextView
    private lateinit var dateRegion: TextView
    private lateinit var launcherSettings: ImageButton
    private lateinit var tvSettings: ImageButton

    private lateinit var preferences: LauncherPreferences
    private lateinit var appCatalog: AppCatalog
    private lateinit var inputCatalog: InputCatalog
    private lateinit var artworkLoader: AppArtworkLoader
    private var tvInputManager: TvInputManager? = null

    private var currentDestination: String = LauncherDefaults.DESTINATION_HOME
    private var favoriteSelectionMode = false
    private var appDiscovery: AppDiscoveryState = AppDiscoveryState.Loading
    private var discoveredApps: List<AppCatalog.AppRecord> = emptyList()
    private var inputResult: InputCatalog.Result? = null
    private var settingsState: SettingsState = LauncherDefaults.DEFAULT_SETTINGS
    private var lastLaunchedPackage: String? = null

    private var featuredAppPackage: String? = null
    private var initialRestorePending = true
    private var refreshInFlight = false
    private var refreshGeneration = 0
    private var refreshThread: Thread? = null
    private var destroyed = false
    private var focusGeneration = 0

    private val inputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) {
            val current = inputResult as? InputCatalog.Result.Success ?: return
            val connection = ConnectionState.fromPlatformState(state)
            if (current.sources.none { it.inputId == inputId && it.connectionState != connection }) return
            inputResult = current.copy(sources = current.sources.map {
                if (it.inputId == inputId) it.copy(connectionState = connection) else it
            })
            renderDestination(preserveHeaderFocus = true)
        }

        override fun onInputAdded(inputId: String) = refreshData()
        override fun onInputRemoved(inputId: String) = refreshData()
        override fun onInputUpdated(inputId: String) = refreshData()
    }

    private val uiCallbacks = object : LauncherUiCallbacks {
        override fun onLaunchApp(app: AppCatalog.AppRecord) = launchApp(app)

        override fun onToggleFavorite(app: AppCatalog.AppRecord) = toggleFavorite(app)
        override fun onChooseFavorites() = chooseFavorites()

        override fun onMoveFavorite(app: AppCatalog.AppRecord, targetIndex: Int) =
            moveFavorite(app, targetIndex)

        override fun onActivateInput(source: InputSource) = activateInput(source)

        override fun onOpenInputPicker() = openInputPicker()

        override fun onSettingsChanged(state: SettingsState) = updateSettings(state)

        override fun onSelectDestination(destination: String) = selectDestination(destination)

    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            if (destroyed) return
            updateClock()
            scheduleClock()
        }
    }

    private val focusChangeListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            focusGeneration++
            persistFocus(newFocus)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.setBackgroundDrawable(null)

        rootView = findViewById(R.id.activity_main)
        contentContainer = findViewById(R.id.main_content_container)
        contentContainer.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            // Keep scrolling content below the header while allowing the focus outline into side gutters.
            view.clipBounds = Rect(-dp(24f), 0, right - left + dp(24f), bottom - top)
        }
        clockDateRegion = findViewById(R.id.clock_date_region)
        dateRegion = findViewById(R.id.date_region)
        launcherSettings = findViewById(R.id.launcher_settings)
        tvSettings = findViewById(R.id.tv_settings)
        if (resources.configuration.fontScale > 1.1f) {
            findViewById<View>(R.id.top_information).layoutParams.height = dp(54f)
        }

        preferences = LauncherPreferences(this)
        appCatalog = AppCatalog(this)
        inputCatalog = InputCatalog(this)
        tvInputManager = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
        artworkLoader = AppArtworkLoader(this)
        settingsState = preferences.settings
        applyLauncherBackground(settingsState.selectedBackground)
        updateClock()
        setupSettingsButtons()
        rootView.viewTreeObserver.addOnGlobalFocusChangeListener(focusChangeListener)
        renderDestination()
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        scheduleClock()
        if (!launcherSettings.hasFocus() && !tvSettings.hasFocus()) restoreFocusIfPossible()
    }

    override fun onStart() {
        super.onStart()
        try { tvInputManager?.registerCallback(inputCallback, mainHandler) } catch (_: RuntimeException) { }
        refreshData()
    }

    override fun onStop() {
        try { tvInputManager?.unregisterCallback(inputCallback) } catch (_: RuntimeException) { }
        super.onStop()
    }

    override fun onPause() {
        mainHandler.removeCallbacks(clockRunnable)
        super.onPause()
    }

    override fun onBackPressed() {
        if (currentDestination != LauncherDefaults.DESTINATION_HOME) {
            lastLaunchedPackage = null
            navigate(LauncherDefaults.DESTINATION_HOME)
        } else if (launcherSettings.hasFocus() || tvSettings.hasFocus()) {
            focusContent()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) && currentDestination != LauncherDefaults.DESTINATION_HOME) {
            navigate(LauncherDefaults.DESTINATION_HOME)
        }
    }

    override fun onDestroy() {
        destroyed = true
        refreshGeneration += 1
        refreshThread?.interrupt()
        refreshThread = null
        if (::artworkLoader.isInitialized) artworkLoader.close()
        mainHandler.removeCallbacksAndMessages(null)
        if (::rootView.isInitialized && rootView.viewTreeObserver.isAlive) {
            rootView.viewTreeObserver.removeOnGlobalFocusChangeListener(focusChangeListener)
        }
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && ::artworkLoader.isInitialized) {
            artworkLoader.clear()
        }
    }

    private fun setupSettingsButtons() {
        launcherSettings.setOnClickListener { selectDestination(LauncherDefaults.DESTINATION_SETTINGS) }
        tvSettings.setOnClickListener { selectDestination(SYSTEM_SETTINGS_DESTINATION) }
        listOf(launcherSettings, tvSettings).forEach { button ->
            button.setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                    focusContent()
                    true
                } else false
            }
        }
    }

    private fun selectDestination(destination: String) {
        if (destination == SYSTEM_SETTINGS_DESTINATION) {
            initialRestorePending = false
            lastLaunchedPackage = null
            favoriteSelectionMode = false
            openSystemSettings()
            return
        }
        if (destination !in LauncherDefaults.SUPPORTED_DESTINATIONS) {
            logFailure("This destination is unavailable.")
            return
        }
        initialRestorePending = false
        lastLaunchedPackage = null
        navigate(destination)
    }

    private fun chooseFavorites() {
        initialRestorePending = false
        lastLaunchedPackage = null
        navigate(LauncherDefaults.DESTINATION_APPS, selectionMode = true)
    }

    private fun navigate(destination: String, selectionMode: Boolean = false) {
        val resolvedDestination = destination.takeIf { it in LauncherDefaults.SUPPORTED_DESTINATIONS }
            ?.takeUnless { it == LauncherDefaults.DESTINATION_INPUTS }
            ?: LauncherDefaults.DESTINATION_HOME
        favoriteSelectionMode = selectionMode && resolvedDestination == LauncherDefaults.DESTINATION_APPS
        currentDestination = resolvedDestination
        settingsState = settingsState.copy(lastDestination = resolvedDestination)
        preferences.lastDestination = resolvedDestination
        renderDestination()
    }

    private fun renderDestination(preserveHeaderFocus: Boolean = false) {
        if (destroyed || !::contentContainer.isInitialized) return
        val scrollingPage = currentDestination != LauncherDefaults.DESTINATION_HOME
        val shadow = findViewById<View>(R.id.header_shadow)
        shadow.visibility = if (scrollingPage) View.VISIBLE else View.GONE
        val previousFocusTag = contentContainer.findFocus()?.tag
        val previousHeaderFocus = if (preserveHeaderFocus) {
            listOf(launcherSettings, tvSettings).firstOrNull { it.hasFocus() }
        } else null
        listOf(launcherSettings, tvSettings).forEach {
            it.applyFocusStyle(this, cornerRadiusDp = 24f, motionPreference = settingsState.motion)
        }
        val screen = when (currentDestination) {
            LauncherDefaults.DESTINATION_APPS -> buildAppsScreen(
                context = this,
                apps = discoveredApps,
                favoritePackages = preferences.favorites.getFavorites(),
                callbacks = uiCallbacks,
                favoriteSelectionMode = favoriteSelectionMode,
                discovery = appDiscovery,
                motionPreference = settingsState.motion,
                density = settingsState.density
            )

            LauncherDefaults.DESTINATION_SETTINGS -> buildSettingsScreen(
                context = this,
                state = settingsState,
                callbacks = uiCallbacks
            )

            else -> buildHomeScreen(
                context = this,
                favorites = favoriteApps(),
                inputs = inputSources(),
                callbacks = uiCallbacks,
                inputResult = inputResult,
                discovery = appDiscovery,
                motionPreference = settingsState.motion,
                artworkLoader = artworkLoader,
                initialFeaturedPackage = featuredAppPackage ?: preferences.lastFocus.packageName
            )
        }
        val previousScreen = contentContainer.getChildAt(0)
        contentContainer.addView(screen, FrameLayout.LayoutParams(-1, -1))
        // Move focus before detaching the old content so the header never takes it in between.
        if (previousHeaderFocus == null) {
            val nextTarget = previousFocusTag?.let { tag -> findFocusableView(screen) { it.tag == tag } }
            if (nextTarget?.requestFocus() != true) focusContent(screen)
        }
        if (previousScreen != null) contentContainer.removeView(previousScreen)
        val attachedFocusGeneration = focusGeneration
        mainHandler.post {
            if (screen.parent !== contentContainer) return@post
            // A source refresh must not overwrite a newer focus move.
            if (preserveHeaderFocus && focusGeneration != attachedFocusGeneration && currentFocus != null) return@post
            if (previousHeaderFocus != null) {
                previousHeaderFocus.requestFocus()
                return@post
            }
            val previousTarget = previousFocusTag?.let { tag ->
                findFocusableView(contentContainer) { it.tag == tag }
            }
            if (previousTarget != null) {
                previousTarget.requestFocus()
            } else focusContent()
        }
    }

    private fun focusContent(root: View = contentContainer) {
        if (!restoreFocusIfPossible(root)) findFocusableView(root) { it.isClickable }?.requestFocus()
    }

    private fun favoriteApps(): List<AppCatalog.AppRecord> {
        val appsByPackage = discoveredApps.associateBy { it.packageName }
        return preferences.favorites.getFavorites().mapNotNull { packageName ->
            appsByPackage[packageName]?.takeIf { it.favoriteEligible }
        }
    }

    private fun inputSources(): List<InputSource> = when (val result = inputResult) {
        is InputCatalog.Result.Success -> result.sources
        is InputCatalog.Result.Unsupported -> result.sources
        is InputCatalog.Result.Failed -> emptyList()
        null -> emptyList()
    }

    private fun updateSettings(state: SettingsState) {
        settingsState = state.copy(lastDestination = currentDestination)
        preferences.settings = settingsState
        applyLauncherBackground(settingsState.selectedBackground)
        renderDestination()
    }
    private fun applyLauncherBackground(backgroundId: String) {
        val backgroundResource = when (backgroundId) {
            LauncherDefaults.BACKGROUND_DEEP_PLUM -> R.color.color_deep_plum_background
            LauncherDefaults.BACKGROUND_EMBER_NIGHT -> R.color.color_ember_night_background
            else -> R.color.color_midnight_background
        }
        rootView.setBackgroundResource(backgroundResource)
    }

    private fun toggleFavorite(app: AppCatalog.AppRecord) {
        if (!app.favoriteEligible) {
            logFailure("This app is unavailable.")
            return
        }
        val favorites = preferences.favorites.getFavorites()
        val changed = if (app.packageName in favorites) {
            preferences.favorites.removeFavorite(app.packageName)
        } else {
            preferences.favorites.addFavorite(app.packageName)
        }
        if (!changed) {
            logFailure("Favorite could not be changed.")
            return
        }
        renderDestination()
    }

    private fun moveFavorite(app: AppCatalog.AppRecord, targetIndex: Int) {
        if (!preferences.favorites.reorderFavorite(app.packageName, targetIndex)) {
            logFailure("Favorite order could not be changed.")
            return
        }
        renderDestination()
    }

    private fun launchApp(app: AppCatalog.AppRecord) {
        preferences.lastFocus = FocusState(app.packageName)
        when (val result = appCatalog.launch(this, app)) {
            is LaunchResult.Success -> {
                lastLaunchedPackage = result.packageName
            }

            is LaunchResult.Unavailable -> {
                lastLaunchedPackage = null
                logFailure(result.reason)
            }

            is LaunchResult.Failed -> {
                lastLaunchedPackage = null
                logFailure(result.reason)
            }
        }
    }

    private fun activateInput(source: InputSource) {
        lastLaunchedPackage = null
        if (!source.isSwitchable) {
            fallbackToInputPicker("Direct switching is unavailable.")
            return
        }

        val intent = try {
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(source.inputId))
        } catch (_: RuntimeException) {
            null
        }
        if (intent == null || !canResolve(intent)) {
            fallbackToInputPicker("Direct switching is unavailable.")
            return
        }

        preferences.lastFocus = FocusState(source.inputId, source.inputId)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            fallbackToInputPicker("Direct switching failed.")
        } catch (_: SecurityException) {
            fallbackToInputPicker("Direct switching was denied.")
        } catch (_: RuntimeException) {
            fallbackToInputPicker("Direct switching failed.")
        }
    }

    private fun fallbackToInputPicker(message: String) {
        if (canOpenInputPicker()) {
              logFailure("$message Opening Android input settings.")
            openInputPicker()
        } else {
              logFailure("$message Android input settings are unavailable.")
        }
    }

    private fun inputPickerIntent(): Intent? = when (val result = inputResult) {
        is InputCatalog.Result.Success -> result.systemInputPickerIntent
        is InputCatalog.Result.Unsupported -> result.systemInputPickerIntent
        is InputCatalog.Result.Failed -> result.systemInputPickerIntent
        null -> null
    }

    private fun canOpenInputPicker(): Boolean =
        inputPickerIntent()?.let { canResolve(Intent(it)) } == true

    private fun openInputPicker() {
        val discoveredIntent = inputPickerIntent()
        if (discoveredIntent == null) {
            logFailure("Android input settings are unavailable on this device.")
            return
        }

        val intent = Intent(discoveredIntent)
        if (!canResolve(intent)) {
            logFailure("Android input settings are unavailable on this device.")
            return
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            logFailure("Android input settings are unavailable on this device.")
        } catch (_: SecurityException) {
            logFailure("Android input settings were denied by Android.")
        } catch (_: RuntimeException) {
            logFailure("Android input settings could not be opened.")
        }
    }

    private fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        if (!canResolve(intent)) {
            logFailure("Android system settings are unavailable.")
            return
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            logFailure("Android system settings are unavailable.")
        } catch (_: SecurityException) {
            logFailure("Android system settings were denied.")
        } catch (_: RuntimeException) {
            logFailure("Android system settings could not be opened.")
        }
    }

    private fun canResolve(intent: Intent): Boolean = try {
        packageManager.resolveActivity(intent, 0) != null
    } catch (_: RuntimeException) {
        false
    }

    private fun refreshData() {
        if (destroyed || refreshInFlight) return
        refreshInFlight = true
        val generation = ++refreshGeneration
        val preserveHeaderFocus = appDiscovery !is AppDiscoveryState.Loading
        if (appDiscovery is AppDiscoveryState.Loading) renderDestination()
        val thread = Thread {
            val apps = try {
                AppDiscoveryState.Success(appCatalog.discover())
            } catch (_: RuntimeException) {
                AppDiscoveryState.Failed("Unable to discover apps.")
            }
            val inputs = try {
                inputCatalog.discover()
            } catch (error: RuntimeException) {
                InputCatalog.Result.Failed("Unable to discover TV inputs.", null, error)
            }
            mainHandler.post {
                if (destroyed || generation != refreshGeneration) return@post
                refreshThread = null
                refreshInFlight = false
                appDiscovery = apps
                when (apps) {
                    is AppDiscoveryState.Success -> {
                        discoveredApps = apps.apps
                        preferences.favorites.trimStale(apps.apps.map { it.packageName })
                    }

                    is AppDiscoveryState.Failed -> {
                        discoveredApps = emptyList()
                    }

                    AppDiscoveryState.Loading -> {
                        discoveredApps = emptyList()
                    }
                }
                inputResult = inputs
                if (initialRestorePending) {
                    initialRestorePending = false
                    val storedDestination = preferences.lastDestination
                    currentDestination = storedDestination.takeIf(::destinationAvailable)
                        ?: LauncherDefaults.DESTINATION_HOME
                    settingsState = preferences.settings.copy(lastDestination = currentDestination)
                    preferences.lastDestination = currentDestination
                }
                renderDestination(preserveHeaderFocus = preserveHeaderFocus)
            }
        }.apply { name = "QuasarTV-discovery" }
        refreshThread = thread
        thread.start()
    }

    private fun destinationAvailable(destination: String): Boolean = when (destination) {
        LauncherDefaults.DESTINATION_HOME,
        LauncherDefaults.DESTINATION_SETTINGS -> true

        LauncherDefaults.DESTINATION_APPS -> true
        LauncherDefaults.DESTINATION_INPUTS -> false
        else -> false
    }

    private fun restoreFocusIfPossible(root: View = contentContainer): Boolean {
        if (destroyed || !::contentContainer.isInitialized) return false
        val storedFocus = preferences.lastFocus
        val appTarget = if (
            currentDestination == LauncherDefaults.DESTINATION_HOME ||
            currentDestination == LauncherDefaults.DESTINATION_APPS
        ) {
            val packageName = lastLaunchedPackage
                ?: storedFocus.packageName.takeIf { storedFocus.source == null }
            packageName?.let { packageToFind ->
                findFocusableView(root) { view ->
                    view.tag == packageToFind
                }
            }
        } else {
            null
        }
        val inputTarget = if (
            currentDestination == LauncherDefaults.DESTINATION_HOME ||
            currentDestination == LauncherDefaults.DESTINATION_INPUTS
        ) {
            storedFocus.source?.let { sourceId ->
                val source = inputSources().firstOrNull { it.inputId == sourceId }
                source?.let { inputToFind ->
                    findFocusableView(root) { view ->
                        view.contentDescription?.toString()?.trim()
                            ?.startsWith(inputContentDescription(inputToFind)) == true
                    }
                }
            }
        } else {
            null
        }
        val target = appTarget ?: inputTarget
        if (target != null && target.isFocusable && target.isEnabled) {
            val launchedPackage = lastLaunchedPackage
            val restored = target.requestFocus()
            if (restored && launchedPackage != null && lastLaunchedPackage == launchedPackage) {
                lastLaunchedPackage = null
            }
            return restored
        }
        return false
    }

    private fun findFocusableView(root: View, matches: (View) -> Boolean): View? {
        if (root.isFocusable && root.isEnabled && matches(root)) {
            return root
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findFocusableView(root.getChildAt(index), matches)?.let { return it }
            }
        }
        return null
    }

    private fun persistFocus(focused: View?) {
        if (destroyed || focused == null || !focused.isFocusable || !focused.isEnabled) return
        val description = focused.contentDescription?.toString()?.trim().orEmpty()
        val appPackage = focused.tag as? String
        if (description.isEmpty() && appPackage == null) return

        if (currentDestination == LauncherDefaults.DESTINATION_HOME ||
            currentDestination == LauncherDefaults.DESTINATION_APPS
        ) {
            discoveredApps.firstOrNull { app ->
                app.packageName == appPackage
            }?.let { app ->
                if (currentDestination == LauncherDefaults.DESTINATION_HOME) featuredAppPackage = app.packageName
                preferences.lastFocus = FocusState(app.packageName)
                return
            }
        }

        if (currentDestination == LauncherDefaults.DESTINATION_HOME ||
            currentDestination == LauncherDefaults.DESTINATION_INPUTS
        ) {
            inputSources().firstOrNull { source ->
                description.startsWith(inputContentDescription(source))
            }?.let { source ->
                preferences.lastFocus = FocusState(source.inputId, source.inputId)
            }
        }
    }

    private fun updateClock() {
        if (!::clockDateRegion.isInitialized) return
        val now = Date()
        clockDateRegion.text = android.text.format.DateFormat.getTimeFormat(this).format(now)
        dateRegion.text = android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEE MMM d")
            .let { java.text.SimpleDateFormat(it, java.util.Locale.getDefault()).format(now) }
    }

    private fun scheduleClock() {
        mainHandler.removeCallbacks(clockRunnable)
        val now = System.currentTimeMillis()
        val delay = 60_000L - (now % 60_000L) + 50L
        mainHandler.postDelayed(clockRunnable, delay)
    }

    private fun logFailure(message: String) {
        Log.w("QuasarTV", message)
    }
}
