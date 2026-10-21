package com.quasartv.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.TextView
import com.quasartv.launcher.data.*
import com.quasartv.launcher.ui.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.graphics.drawable.BitmapDrawable
import android.view.accessibility.AccessibilityNodeInfo

/** Device-only fixtures never enter the launcher APK or its saved favorites. */
class VisualSmokeTest : Instrumentation() {
    private lateinit var activity: Activity
    private var variant = "720p"
    private var skipExternalApps = false
    private val passed = mutableListOf<String>()
    private val callbacks = object : LauncherUiCallbacks {
        override fun onLaunchApp(app: AppCatalog.AppRecord) = Unit
        override fun onToggleFavorite(app: AppCatalog.AppRecord) = Unit
        override fun onChooseFavorites() = Unit
        override fun onMoveFavorite(app: AppCatalog.AppRecord, targetIndex: Int) = Unit
        override fun onActivateInput(source: InputSource) = Unit
        override fun onOpenInputPicker() = Unit
        override fun onSettingsChanged(state: SettingsState) = Unit
        override fun onSelectDestination(destination: String) = Unit
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        variant = arguments?.getString("variant") ?: variant
        skipExternalApps = arguments?.getString("skipExternalApps") == "true"
        start()
    }

    override fun onStart() {
        Thread {
            try {
                val storedFocus = LauncherPreferences(targetContext).lastFocus
                val storedFavorite = storedFocus.packageName.takeIf {
                    storedFocus.source == null && it in LauncherPreferences(targetContext).favorites.getFavorites()
                }
                activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                settle()
                key(KeyEvent.KEYCODE_BACK)
                val discoveryDeadline = SystemClock.uptimeMillis() + 10000
                while (SystemClock.uptimeMillis() < discoveryDeadline) {
                    val title = activity.findViewById<TextView>(R.id.featured_title)
                    if (title == null) key(KeyEvent.KEYCODE_BACK)
                    else if (title.text == "Welcome home.") settle()
                    else break
                }
                check(activity.findViewById<TextView>(R.id.featured_title)?.text?.let { it != "Welcome home." } == true) {
                    "Installed apps must finish loading"
                }
                if (storedFavorite != null) check(activity.currentFocus?.tag == storedFavorite) {
                    "A saved favorite must restore before the default control can overwrite it"
                }
                capture("home")
                val launch = activity.findViewById<ViewGroup>(R.id.favorites_row)?.getChildAt(0)
                onMain { launch?.requestFocus() }
                if (!skipExternalApps && launch?.isShown == true && launch.tag is String) {
                    key(KeyEvent.KEYCODE_DPAD_CENTER)
                    check(awaitWindow(launch.tag as String)) { "Favorite app must open" }
                    var returned = false
                    var backPresses = 0
                    while (!returned && backPresses < 4) {
                        key(KeyEvent.KEYCODE_BACK)
                        backPresses++
                        returned = awaitWindow(targetContext.packageName, 2000)
                    }
                    check(returned) { "Back from the favorite must return to the launcher" }
                    check(activity.window.decorView.hasWindowFocus()) { "Back from app must restore launcher" }
                    record("Favorite launches by OK and returns after $backPresses Back press(es)")
                } else if (skipExternalApps) {
                    sendStatus(0, Bundle().apply { putString("stream", "External streaming-app round trip excluded: Apple TV retained foreground after Back in the preceding hardware run.\n") })
                }
                val favorites = activity.findViewById<ViewGroup>(R.id.favorites_row)
                val bannerFavorite = (0 until favorites.childCount).map { favorites.getChildAt(it) }
                    .firstOrNull { it.tag?.toString()?.contains("netflix", ignoreCase = true) == true } ?: launch!!
                onMain { bannerFavorite.requestFocus() }
                settle()
                SystemClock.sleep(700)
                val headerArtwork = activity.findViewById<ImageView>(R.id.featured_artwork)
                val originalBanner = headerArtwork.drawable
                check(headerArtwork.background == null && headerArtwork.paddingLeft == 0) { "Header artwork must have no launcher frame" }
                repeat(3) {
                    key(KeyEvent.KEYCODE_DPAD_UP)
                    check(activity.currentFocus?.id == R.id.launcher_settings) { "Up must reach launcher settings" }
                    check(headerArtwork.drawable === originalBanner) { "Hovering settings must keep the banner" }
                    if (it == 0) capture("settings-button-focused")
                    key(KeyEvent.KEYCODE_DPAD_RIGHT)
                    check(activity.currentFocus?.id == R.id.tv_settings) { "TV settings must have a separate button" }
                    if (it == 0) capture("tv-settings-button-focused")
                    key(KeyEvent.KEYCODE_DPAD_LEFT)
                    key(KeyEvent.KEYCODE_DPAD_DOWN)
                    check(activity.currentFocus?.tag == bannerFavorite.tag) { "Down must return to the favorite" }
                    check(headerArtwork.drawable === originalBanner) { "Returning to the same favorite must not reset its banner" }
                }
                val activeInput = firstAction(activity.findViewById<View>(R.id.home_inputs))
                if (activeInput != null) {
                    checkHomeStaysStillOnDown(activeInput)
                    repeat(3) {
                        onMain { activeInput.requestFocus() }
                        check(headerArtwork.drawable === originalBanner) { "Hovering an input must keep the banner" }
                        onMain { bannerFavorite.requestFocus() }
                        check(headerArtwork.drawable === originalBanner) { "Input-to-favorite focus must not reload the banner" }
                    }
                }
                settle()
                capture("banner-stable")
                record("Borderless header keeps its artwork through repeated favorite, input and settings focus changes")
                record("Separate round launcher and TV settings controls work by remote")
                onMain { launch?.requestFocus() }
                settle()

                val favoriteRow = activity.findViewById<ViewGroup>(R.id.favorites_row)
                repeat(favoriteRow.childCount - 1) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
                check(activity.currentFocus?.contentDescription == "All apps")
                checkShelfFocusFits()
                capture("all-apps-focused")
                record("Terminal All apps keeps its full scaled outline after scrolling to the row end")

                key(KeyEvent.KEYCODE_DPAD_CENTER)
                check(findText("Your apps") != null) { "All apps must open the library by remote" }
                checkHeaderGap()
                capture("apps")
                repeat(2) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
                checkHeaderGap()
                capture("apps-scrolled-header")
                captureScrolledHeaderFocus("apps")
                key(KeyEvent.KEYCODE_MENU)
                capture("app-actions")
                key(KeyEvent.KEYCODE_BACK)
                key(KeyEvent.KEYCODE_BACK)
                check(activity.findViewById<View>(R.id.featured_region) != null) { "Back must return Home" }
                record("All apps opens the library, favorite actions open, and Back returns Home")

                val detectedSources = InputCatalog(targetContext).discover().sources()
                val inputManager = targetContext.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
                inputManager?.tvInputList.orEmpty().forEach { info ->
                    val customLabel = info.loadCustomLabel(targetContext)?.toString()?.trim().orEmpty()
                    if (customLabel.isNotEmpty() && detectedSources.any { it.inputId == info.id }) check(detectedSources.any { it.inputId == info.id && it.label == customLabel }) {
                        "Input names must honor the TV's custom labels"
                    }
                }
                if (detectedSources.isEmpty()) {
                    check(firstAction(activity.findViewById<View>(R.id.home_inputs)) == null) { "No detected inputs must leave no source action" }
                } else {
                    detectedSources.forEach { source ->
                        val label = checkNotNull(findText(source.label)) { "Every detected input must appear" }
                        val control = label.parent as View
                        check(control.isFocusable && control.isClickable == source.isActive) { "All inputs must be inspectable; only active inputs can switch" }
                        if (!source.isActive) check(label.currentTextColor == targetContext.getColor(R.color.color_muted_text)) { "Inactive input labels must be greyed" }
                    }
                }
                capture("inputs")
                val activeSource = detectedSources.firstOrNull { it.isActive }
                if (activeSource != null) {
                    val source = activeSource
                    val callback = activity.javaClass.getDeclaredField("inputCallback").apply { isAccessible = true }
                        .get(activity) as TvInputManager.TvInputCallback
                    onMain { callback.onInputStateChanged(source.inputId, TvInputManager.INPUT_STATE_CONNECTED_STANDBY) }
                    settle()
                    val standbyLabel = checkNotNull(findText(source.label)) { "A source entering standby must remain visible" }
                    val standbyControl = standbyLabel.parent as View
                    check(standbyControl.isFocusable && !standbyControl.isClickable) { "Standby inputs must retain focus access without switching" }
                    check(standbyLabel.currentTextColor == targetContext.getColor(R.color.color_muted_text)) { "A source entering standby must become grey" }
                    check(standbyControl.contentDescription.toString().contains("standby", ignoreCase = true)) { "Standby status must remain accessible" }
                    onMain { callback.onInputAdded(source.inputId) }
                    val deadline = SystemClock.uptimeMillis() + 5000
                    while ((findText(source.label)?.parent as? View)?.isClickable != true && SystemClock.uptimeMillis() < deadline) settle()
                    check((findText(source.label)?.parent as? View)?.isClickable == true) { "A new-input callback must restore the device-reported active source" }
                    record("Simulated input callbacks grey standby sources without hiding them and restore active TV discovery")
                }
                openSettings(launcher = true)
                check(findText("Appearance") != null) { "Settings must expose Appearance" }
                checkHeaderGap()
                capture("settings")
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                capture("background-choices")
                key(KeyEvent.KEYCODE_BACK)
                record("All detected inputs (${detectedSources.size} sources), TV custom labels and Settings choice dialog")

                onMain { activity.findViewById<View>(R.id.main_content_container).findViewWithTag<View>("settings_background").performClick() }
                settle()
                repeat(3) { key(KeyEvent.KEYCODE_DPAD_UP) }
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                settle()
                check(activity.window.decorView.hasWindowFocus()) { "Background choice must dismiss its dialog" }
                check(LauncherPreferences(targetContext).settings.selectedBackground == LauncherDefaults.BACKGROUND_MIDNIGHT)
                record("Background choice persists through native dialog")

                val about = activity.findViewById<View>(R.id.main_content_container).findViewWithTag<View>("settings_about")
                onMain { about.requestFocus() }
                settle()
                checkHeaderGap()
                check(findText("Made for the moments in between.") != null && findText("Ravhi Rizaldi") != null)
                val github = checkNotNull(findText("https://github.com/ravhirizaldi"))
                check(github.autoLinkMask == 0 && !github.linksClickable && github.movementMethod == null && !github.isClickable) {
                    "GitHub must remain plain text"
                }
                capture("creator-card")
                capture("settings-scrolled-header")
                captureScrolledHeaderFocus("settings")
                repeat(9) {
                    key(KeyEvent.KEYCODE_DPAD_CENTER)
                    check(activity.window.decorView.hasWindowFocus()) { "The first nine About clicks must not open a popup" }
                }
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                val photoDeadline = SystemClock.uptimeMillis() + 5000
                while (!creatorPhotoVisible() && SystemClock.uptimeMillis() < photoDeadline) settle()
                check(creatorPhotoVisible()) { "The tenth click must reveal the bundled photo" }
                capture("creator-photo")
                val imageBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(targetContext.resources, R.drawable.creator_easter_egg, imageBounds)
                check(imageBounds.outWidth == 539 && imageBounds.outHeight == 720) { "The photo must stay bounded" }
                key(KeyEvent.KEYCODE_BACK)
                check(activity.window.decorView.hasWindowFocus() && activity.currentFocus?.tag == "settings_about") {
                    "Back must dismiss the photo and restore creator-card focus"
                }
                key(KeyEvent.KEYCODE_DPAD_CENTER)
                check(activity.window.decorView.hasWindowFocus()) { "The ten-click counter must reset after revealing the photo" }
                record("Creator card keeps GitHub plain, opens its local photo on click ten and restores focus on Back")
                record("Scrolled Apps and Settings retain a 24dp header gap with a static shadow")

                openSettings(launcher = false)
                val settingsPackage = targetContext.packageManager.resolveActivity(Intent(android.provider.Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
                check(settingsPackage != null && awaitWindow(settingsPackage)) { "Android system settings must open" }
                key(KeyEvent.KEYCODE_BACK)
                check(awaitWindow(targetContext.packageName)) { "Back from Android settings must restore the launcher" }
                record("Native Android system settings opens and Back restores launcher focus")

                val realApps = AppCatalog(targetContext).discover()
                check(realApps.isNotEmpty()) { "Need an installed app for icon fixtures" }
                val artworkLoader = AppArtworkLoader(targetContext)
                try {
                    for (app in realApps.take(5)) {
                        val loaded = CountDownLatch(1)
                        var image: BitmapDrawable? = null
                        onMain { artworkLoader.load(app) { image = it as BitmapDrawable; loaded.countDown() } }
                        check(loaded.await(5, TimeUnit.SECONDS)) { "App artwork must load asynchronously" }
                        val bitmap = checkNotNull(image).bitmap
                        check(bitmap.width <= 512 && bitmap.height <= 288)
                        check(artworkLoader.cachedByteCount <= 1024 * 1024)
                        onMain {
                            var delivered = false
                            artworkLoader.load(app) { delivered = true }
                            check(delivered) { "Cached artwork must arrive in the same frame" }
                        }
                    }
                } finally { onMain { artworkLoader.close() } }
                record("Higher-resolution artwork loads off the UI thread within a 1 MiB cache")
                val icon = realApps.first().icon
                val fixtures = (1..25).map { index ->
                    AppCatalog.AppRecord("Test app ${index.toString().padStart(2, '0')}",
                        "test.fixture.app$index", Intent(), icon, true)
                }
                val noInputs = InputCatalog.Result.Success(emptyList(), null)
                home()
                for (count in listOf(0, 1, 3, 12)) {
                    onMain { mount(buildHomeScreen(activity, fixtures.take(count), emptyList(), callbacks, noInputs)) }
                    settle()
                    check(activity.findViewById<TextView>(R.id.featured_title).text.isNotBlank())
                    if (count > 0) {
                        check(activity.currentFocus?.tag == fixtures.first().packageName) { "Home must begin on a favorite" }
                        if (count > 1) {
                            key(KeyEvent.KEYCODE_DPAD_RIGHT)
                            capture("fixture-home-$count-after-right")
                            check(activity.currentFocus?.tag == fixtures[1].packageName) { "Right from favorite 1 with $count apps reached ${activity.currentFocus?.tag}, id ${activity.currentFocus?.id}" }
                            check(activity.findViewById<TextView>(R.id.featured_title).text == fixtures[1].label)
                        }
                        if (count == 12) {
                            repeat(9) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
                            check(activity.currentFocus?.tag == fixtures[10].packageName) { "Offscreen favorites must scroll into focus" }
                            checkShelfFocusFits()
                        }
                    } else check(findText("Browse apps") != null)
                    capture("fixture-home-$count")
                    if (count == 12) {
                        repeat(2) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
                        check(activity.currentFocus?.contentDescription == "All apps")
                        checkShelfFocusFits()
                        capture("fixture-terminal-all-apps")
                        repeat(12) { key(KeyEvent.KEYCODE_DPAD_LEFT) }
                        check(activity.currentFocus?.tag == fixtures.first().packageName)
                        checkShelfFocusFits()
                        check(activity.findViewById<TextView>(R.id.featured_title).text == fixtures.first().label) {
                            "Featured app must follow focus when scrolling back to the first favorite"
                        }
                        capture("fixture-home-twelve-return-left")
                    }
                    if (count == 1) {
                        onMain { (findText("All apps")!!.parent as View).requestFocus() }
                        settle()
                        capture("fixture-all-apps-focused")
                    }
                }
                record("Home fixtures: zero, one, three, twelve favorites; featured app follows focus")

                onMain { mount(buildAppsScreen(activity, fixtures, emptyList(), callbacks)) }
                settle()
                val headerBefore = headerPixels()
                val columns = appGridColumns(activity.resources.displayMetrics.widthPixels, activity.resources.configuration.screenWidthDp)
                repeat(fixtures.lastIndex / columns) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
                check(activity.currentFocus?.tag == fixtures[(fixtures.lastIndex / columns) * columns].packageName) {
                    "Grid must retain its column when scrolling to the final row"
                }
                capture("fixture-apps-25")
                check(headerBefore.contentEquals(headerPixels())) { "Scrolling apps must not draw over the clock header" }
                repeat(fixtures.lastIndex % columns) { key(KeyEvent.KEYCODE_DPAD_RIGHT) }
                check(activity.currentFocus?.tag == fixtures.last().packageName)
                record("Apps: all 25 fixture apps reachable, including the last partial row")

                val sources = listOf(
                    InputSource("fixture.hdmi1", "HDMI 1", TvInputInfo.TYPE_HDMI, ConnectionState.CONNECTED, true),
                    InputSource("fixture.hdmi2", "HDMI 2", TvInputInfo.TYPE_HDMI, ConnectionState.DISCONNECTED, true),
                    InputSource("fixture.av", "AV", TvInputInfo.TYPE_COMPOSITE, ConnectionState.UNKNOWN, false),
                    InputSource("fixture.hdmi3", "HDMI 3", TvInputInfo.TYPE_HDMI, ConnectionState.CONNECTED_STANDBY, true),
                    InputSource("fixture.channel", "Channel", TvInputInfo.TYPE_TUNER, ConnectionState.CONNECTED, false)
                )
                val activatedInputs = mutableListOf<String>()
                var inputPickerRequests = 0
                val inputCallbacks = object : LauncherUiCallbacks by callbacks {
                    override fun onActivateInput(source: InputSource) { activatedInputs += source.inputId }
                    override fun onOpenInputPicker() { inputPickerRequests++ }
                }
                onMain { mount(buildHomeScreen(activity, fixtures.take(1), sources, inputCallbacks, InputCatalog.Result.Success(sources, null))) }
                settle()
                capture("fixture-inputs")
                sources.forEach { source ->
                    val label = checkNotNull(findText(source.label)) { "Every fixture source must remain visible" }
                    val control = label.parent as View
                    check(control.isFocusable && control.isClickable == source.isActive)
                    check(label.currentTextColor == targetContext.getColor(if (source.isActive) R.color.color_warm_ivory_text else R.color.color_muted_text))
                    onMain { control.requestFocus() }
                    settle()
                    check(control.hasFocus()) { "Inactive inputs must remain reachable for inspection" }
                    val activationsBefore = activatedInputs.size
                    key(KeyEvent.KEYCODE_DPAD_CENTER)
                    check(activatedInputs.size == activationsBefore + if (source.isActive) 1 else 0) { "OK must activate only connected switchable inputs" }
                    if (source.isActive) check(activatedInputs.last() == source.inputId)
                    check(inputPickerRequests == 0) { "Inactive input OK must not open the system picker" }
                }
                record("Inputs: all source states stay visible and focusable; inactive fixtures are grey and cannot activate")

                val longTitle = fixtures.first().copy(label = "An exceptionally long favorite application title that must wrap without pushing inputs below the Home viewport")
                for (homeFavorites in listOf(listOf(longTitle), emptyList())) {
                    onMain { mount(buildHomeScreen(activity, homeFavorites, sources, callbacks, InputCatalog.Result.Success(sources, null))) }
                    settle()
                    if (homeFavorites.isNotEmpty()) {
                        check(activity.findViewById<TextView>(R.id.featured_title).lineCount == 2) { "Long app titles must wrap within the hero" }
                    } else check(findText("Browse apps")?.isShown == true) { "Empty Home must keep its browse action" }
                    checkHomeStaysStillOnDown(checkNotNull(firstAction(activity.findViewById<View>(R.id.home_inputs))))
                    capture(if (homeFavorites.isEmpty()) "fixture-empty-home-input-down" else "fixture-long-title-input-down")
                }
                record("Home stays fixed after repeated input Down presses, including long-title and empty-favorites fixtures")

                for (motion in MotionPreference.entries) {
                    onMain { mount(buildHomeScreen(activity, fixtures.take(1), emptyList(), callbacks, noInputs, motionPreference = motion)) }
                    settle()
                    val focused = activity.findViewById<ViewGroup>(R.id.favorites_row).getChildAt(0)
                    check(focused.hasFocus()) { "Favorite must receive initial focus" }
                    if (motion == MotionPreference.OFF) check(focused.scaleX == 1f && focused.scaleY == 1f)
                }
                record("All motion modes retain initial focus; Off uses scale 1.0")
                home()
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: ${passed.size} groups\n${passed.joinToString("\n")}\n") })
            } catch (error: Throwable) {
                if (::activity.isInitialized) runCatching { capture("failure") }
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${error.stackTraceToString()}\n") })
            }
        }.start()
    }

    private fun checkHeaderGap() {
        val header = Rect()
        val content = Rect()
        activity.findViewById<View>(R.id.top_information).getGlobalVisibleRect(header)
        activity.findViewById<View>(R.id.main_content_container).getGlobalVisibleRect(content)
        check(content.top - header.bottom >= activity.dimen(R.dimen.scroll_header_gap) - 1) {
            "Scrolling content must retain the full header separation"
        }
        check(activity.findViewById<View>(R.id.header_shadow).isShown) { "Scrollable screens need the static header shadow" }
    }
    private fun captureScrolledHeaderFocus(page: String) {
        val content = activity.findViewById<ViewGroup>(R.id.main_content_container)
        val scroll = content.getChildAt(0)
        val scrollY = scroll.scrollY
        val selected = activity.currentFocus
        for (id in listOf(R.id.launcher_settings, R.id.tv_settings)) {
            onMain { activity.findViewById<View>(id).requestFocus() }
            settle()
            check(scroll.scrollY == scrollY) { "Header focus must preserve the scrolled page" }
            val shadow = activity.findViewById<View>(R.id.header_shadow)
            val inset = (shadow.background as android.graphics.drawable.LayerDrawable).getLayerInsetTop(0)
            check(inset >= activity.dp(12f) - 1) { "The shadow needs 12dp clearance; measured $inset pixels" }
            val buttonBounds = Rect()
            val shadowBounds = Rect()
            activity.currentFocus!!.getGlobalVisibleRect(buttonBounds)
            shadow.getGlobalVisibleRect(shadowBounds)
            check(shadowBounds.top + inset - buttonBounds.bottom >= activity.dp(6f)) {
                "The focused header outline must not touch the shadow"
            }
            capture("$page-scrolled-${if (id == R.id.launcher_settings) "info" else "gear"}-focus")
        }
        onMain { selected?.requestFocus() }
        settle()
    }
    private fun creatorPhotoVisible(): Boolean {
        val root = uiAutomation.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText("Easter egg photo")
        val visible = nodes.any { it.isVisibleToUser }
        nodes.forEach { it.recycle() }
        root.recycle()
        return visible
    }
    private fun openSettings(launcher: Boolean) {
        onMain { activity.findViewById<View>(if (launcher) R.id.launcher_settings else R.id.tv_settings).requestFocus() }
        key(KeyEvent.KEYCODE_DPAD_CENTER)
    }
    private fun home() {
        key(KeyEvent.KEYCODE_BACK)
        key(KeyEvent.KEYCODE_BACK)
        check(activity.findViewById<View>(R.id.featured_title) != null)
    }
    private fun mount(view: View) {
        activity.findViewById<FrameLayout>(R.id.main_content_container).apply {
            removeAllViews()
            addView(view, FrameLayout.LayoutParams(-1, -1))
            firstAction(view)?.requestFocus()
        }
    }
    private fun firstAction(view: View): View? {
        if (view.isFocusable && view.isEnabled && view.isClickable) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            firstAction(view.getChildAt(index))?.let { return it }
        }
        return null
    }
    private fun onMain(block: () -> Unit) = runOnMainSync(block)
    private fun settle() { waitForIdleSync(); SystemClock.sleep(300) }
    private fun awaitWindow(packageName: String, timeoutMillis: Long = 5000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var choseHomeOnce = false
        while (SystemClock.uptimeMillis() < deadline) {
            val root = uiAutomation.rootInActiveWindow
            val current = root?.packageName?.toString()
            if (!choseHomeOnce && packageName == targetContext.packageName && current == "android") {
                val titles = root.findAccessibilityNodeInfosByViewId("android:id/title")
                val selected = titles.any { it.text?.contains("QuasarTV") == true }
                titles.forEach { it.recycle() }
                val buttons = root.findAccessibilityNodeInfosByViewId("android:id/button_once")
                if (selected && buttons.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    choseHomeOnce = true
                    sendStatus(0, Bundle().apply { putString("stream", "Back opened Android Home chooser; selected QuasarTV once, without changing the default.\n") })
                }
                buttons.forEach { it.recycle() }
            }
            root?.recycle()
            if (current == packageName || (packageName == targetContext.packageName && activity.window.decorView.hasWindowFocus())) {
                settle()
                return true
            }
            SystemClock.sleep(50)
        }
        return false
    }
    private fun key(code: Int) {
        val now = SystemClock.uptimeMillis()
        check(uiAutomation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0).apply { source = InputDevice.SOURCE_DPAD }, true))
        check(uiAutomation.injectInputEvent(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0).apply { source = InputDevice.SOURCE_DPAD }, true))
        settle()
    }
    private fun findText(text: String): TextView? {
        fun find(view: View): TextView? {
            if (view is TextView && view.text.toString() == text) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        return find(activity.window.decorView)
    }
    private fun capture(name: String) {
        val file = File(targetContext.getExternalFilesDir(null), "visual-smoke/$name-$variant.png")
        file.parentFile?.mkdirs()
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    private fun headerPixels(): IntArray {
        val header = activity.findViewById<View>(R.id.top_information)
        val position = IntArray(2)
        header.getLocationOnScreen(position)
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        val width = minOf(header.width / 2, activity.resources.displayMetrics.density.times(400).toInt())
        val pixels = IntArray(width * header.height)
        bitmap.getPixels(pixels, 0, width, position[0], position[1], width, header.height)
        bitmap.recycle()
        return pixels
    }
    private fun checkShelfFocusFits() {
        val focus = checkNotNull(activity.currentFocus)
        val shelf = activity.findViewById<View>(R.id.favorites_scroll)
        val focusPosition = IntArray(2)
        val shelfPosition = IntArray(2)
        focus.getLocationOnScreen(focusPosition)
        shelf.getLocationOnScreen(shelfPosition)
        val left = focusPosition[0].toFloat()
        val right = left + focus.width * focus.scaleX
        check(left >= shelfPosition[0] + shelf.paddingLeft - 1 &&
            right <= shelfPosition[0] + shelf.width - shelf.paddingRight + 1) {
            "Scaled favorite focus must fit inside the shelf viewport: $left..$right"
        }
    }
    private fun checkHomeStaysStillOnDown(input: View) {
        onMain { input.requestFocus() }
        settle()
        val container = activity.findViewById<ViewGroup>(R.id.main_content_container)
        val inputPosition = IntArray(2)
        val containerPosition = IntArray(2)
        input.getLocationOnScreen(inputPosition)
        container.getLocationOnScreen(containerPosition)
        check(inputPosition[1] >= containerPosition[1] &&
            inputPosition[1] + input.height * input.scaleY <= containerPosition[1] + container.height + 1) {
            "The complete focused input must fit below the header inside Home"
        }
        val views = listOf(container, container.getChildAt(0), activity.findViewById<View>(R.id.top_information),
            activity.findViewById<View>(R.id.featured_region), activity.findViewById<View>(R.id.favorites_scroll), activity.findViewById<View>(R.id.home_inputs))
        check(views.all { it.scrollY == 0 }) { "Reaching an input must not scroll Home vertically" }
        fun geometry() = views.flatMap { view ->
            val position = IntArray(2)
            view.getLocationOnScreen(position)
            listOf(position[0], position[1], view.width, view.height, view.scrollY)
        }
        val before = geometry()
        val headerBefore = headerPixels()
        repeat(6) {
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            check(activity.currentFocus === input) { "Down at the bottom input must retain focus" }
            check(geometry() == before) { "Down on an input must not move or scroll Home" }
        }
        check(headerBefore.contentEquals(headerPixels())) { "Input Down must not alter the fixed header" }
    }
    private fun record(message: String) { passed += message; sendStatus(0, Bundle().apply { putString("stream", "$message\n") }) }
}
