package com.quasartv.launcher

import android.content.SharedPreferences
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import com.quasartv.launcher.data.ConnectionState
import com.quasartv.launcher.data.DensityPreference
import com.quasartv.launcher.data.FavoritesStore
import com.quasartv.launcher.data.InputSource
import com.quasartv.launcher.data.LauncherDefaults
import com.quasartv.launcher.data.MotionPreference
import com.quasartv.launcher.data.inputDisplayLabel
import com.quasartv.launcher.data.artworkSize
import com.quasartv.launcher.ui.appGridColumns
import com.quasartv.launcher.ui.inputIconResource
import com.quasartv.launcher.ui.focusMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherContractTest {
    @Test
    fun inputIconsUsePatchWallSourcesWithoutGuessingUnknownTypes() {
        assertEquals(R.drawable.patchwall_input_hdmi, inputIconResource(TvInputInfo.TYPE_HDMI))
        assertEquals(R.drawable.patchwall_input_tv, inputIconResource(TvInputInfo.TYPE_TUNER))
        for (type in listOf(TvInputInfo.TYPE_COMPOSITE, TvInputInfo.TYPE_COMPONENT, TvInputInfo.TYPE_SVIDEO)) {
            assertEquals(R.drawable.patchwall_input_av, inputIconResource(type))
        }
        assertEquals(R.drawable.ic_inputs, inputIconResource(TvInputInfo.TYPE_OTHER))
    }

    @Test
    fun onlyConnectedPhysicalSourcesAreActive() {
        assertTrue(InputSource("hdmi", "PlayStation", TvInputInfo.TYPE_HDMI, ConnectionState.CONNECTED, true).isActive)
        assertFalse(InputSource("hdmi", "HDMI2", TvInputInfo.TYPE_HDMI, ConnectionState.CONNECTED_STANDBY, true).isActive)
        assertFalse(InputSource("av", "AV", TvInputInfo.TYPE_COMPOSITE, ConnectionState.DISCONNECTED, true).isActive)
        assertFalse(InputSource("tv", "Channel", TvInputInfo.TYPE_TUNER, ConnectionState.CONNECTED, false).isActive)
    }

    @Test
    fun artworkFitsItsPixelBudgetWithoutChangingAspectRatio() {
        assertEquals(320 to 180, artworkSize(1920, 1080, 320, 180))
        assertEquals(176 to 176, artworkSize(512, 512, 320, 176))
        assertEquals(88 to 176, artworkSize(200, 400, 320, 176))
    }

    @Test
    fun inputNamesPreferCustomLabelsAndMatchXiaomiChannel() {
        assertEquals("PlayStation", inputDisplayLabel(" PlayStation ", "HDMI1", TvInputInfo.TYPE_HDMI, "id"))
        assertEquals("HDMI2", inputDisplayLabel(" ", "HDMI2", TvInputInfo.TYPE_HDMI, "id"))
        assertEquals("Channel", inputDisplayLabel(null, "DroidLogicTvInput", TvInputInfo.TYPE_TUNER, "id"))
        assertEquals("Antenna", inputDisplayLabel(null, "Antenna", TvInputInfo.TYPE_TUNER, "id"))
        assertEquals("My channels", inputDisplayLabel("My channels", "DroidLogicTvInput", TvInputInfo.TYPE_TUNER, "id"))
        assertEquals("DroidLogicTvInput", inputDisplayLabel(null, "DroidLogicTvInput", TvInputInfo.TYPE_OTHER, "id"))
        assertEquals("input-id", inputDisplayLabel(null, " ", TvInputInfo.TYPE_OTHER, "input-id"))
    }

    @Test
    fun gridKeepsReadableColumnsAtTvResolutionsAndHighDensity() {
        assertEquals(4, appGridColumns(1280, 960))
        assertEquals(5, appGridColumns(1920, 960))
        assertEquals(4, appGridColumns(1920, 720))
    }

    @Test
    fun reducedAndDisabledMotionKeepFocusBounded() {
        val full = focusMotion(MotionPreference.FULL)
        val reduced = focusMotion(MotionPreference.REDUCED)
        val off = focusMotion(MotionPreference.OFF)
        assertTrue(full.first in 1.03f..1.06f)
        assertTrue(reduced.first < full.first)
        assertTrue(reduced.second < full.second)
        assertEquals(1f to 0L, off)
    }

    @Test
    fun invalidStoredPreferencesUseSafeDefaults() {
        assertEquals(MotionPreference.FULL, MotionPreference.fromStorage(null))
        assertEquals(MotionPreference.FULL, MotionPreference.fromStorage("future-value"))
        assertEquals(MotionPreference.FULL, MotionPreference.fromStorage(""))
        assertEquals(DensityPreference.RELAXED, DensityPreference.fromStorage(null))
        assertEquals(DensityPreference.RELAXED, DensityPreference.fromStorage("future-value"))
        assertEquals(DensityPreference.RELAXED, DensityPreference.fromStorage(""))
    }

    @Test
    fun favoritesSupportAddRemoveReorderAndTrim() {
        val preferences = InMemorySharedPreferences()
        val favorites = FavoritesStore(preferences)

        assertTrue(favorites.addFavorite(" com.example.one "))
        assertFalse(favorites.addFavorite("com.example.one"))
        assertFalse(favorites.addFavorite("bad package"))
        assertTrue(favorites.addFavorite("com.example.two"))
        assertTrue(favorites.addFavorite("com.example.three"))
        assertEquals(
            listOf("com.example.one", "com.example.two", "com.example.three"),
            favorites.getFavorites()
        )

        assertTrue(favorites.reorderFavorites(fromIndex = 2, toIndex = 0))
        assertTrue(favorites.reorderFavorite(" com.example.one ", toIndex = 2))
        assertFalse(favorites.reorderFavorites(fromIndex = -1, toIndex = 0))
        assertEquals(
            listOf("com.example.three", "com.example.two", "com.example.one"),
            favorites.getFavorites()
        )

        assertTrue(favorites.removeFavorite(" com.example.two "))
        assertFalse(favorites.removeFavorite("com.example.missing"))
        assertTrue(favorites.addFavorite("com.example.stale"))
        assertEquals(
            listOf("com.example.one"),
            favorites.trimStale(listOf(" com.example.one ", "com.example.other"))
        )
        assertEquals(listOf("com.example.one"), FavoritesStore(preferences).getFavorites())
    }

    @Test
    fun inputConnectionStatesAndTypeLabelsMatchPlatformContracts() {
        assertEquals(
            ConnectionState.CONNECTED,
            ConnectionState.fromPlatformState(TvInputManager.INPUT_STATE_CONNECTED)
        )
        assertEquals(
            ConnectionState.CONNECTED_STANDBY,
            ConnectionState.fromPlatformState(TvInputManager.INPUT_STATE_CONNECTED_STANDBY)
        )
        assertEquals(
            ConnectionState.DISCONNECTED,
            ConnectionState.fromPlatformState(TvInputManager.INPUT_STATE_DISCONNECTED)
        )
        assertEquals(ConnectionState.UNKNOWN, ConnectionState.fromPlatformState(Int.MIN_VALUE))

        val labels = mapOf(
            TvInputInfo.TYPE_TUNER to "Tuner",
            TvInputInfo.TYPE_COMPOSITE to "Composite",
            TvInputInfo.TYPE_SVIDEO to "S-Video",
            TvInputInfo.TYPE_COMPONENT to "Component",
            TvInputInfo.TYPE_VGA to "VGA",
            TvInputInfo.TYPE_DVI to "DVI",
            TvInputInfo.TYPE_HDMI to "HDMI",
            TvInputInfo.TYPE_DISPLAY_PORT to "DisplayPort",
            TvInputInfo.TYPE_SCART to "SCART",
            TvInputInfo.TYPE_OTHER to "Other"
        )
        labels.forEach { (type, label) ->
            assertEquals(
                label,
                InputSource("input-$type", label, type, ConnectionState.UNKNOWN, false).typeName
            )
        }
        assertEquals(
            "Unknown",
            InputSource("input-unknown", "Unknown", Int.MIN_VALUE, ConnectionState.UNKNOWN, false).typeName
        )
    }

    @Test
    fun launcherDefaultsExposeSupportedDestinationsAndBackground() {
        assertEquals(
            setOf(
                LauncherDefaults.BACKGROUND_MIDNIGHT,
                LauncherDefaults.BACKGROUND_DEEP_PLUM,
                LauncherDefaults.BACKGROUND_EMBER_NIGHT
            ),
            LauncherDefaults.SUPPORTED_BACKGROUND_IDS
        )
        assertEquals(
            setOf(
                LauncherDefaults.DESTINATION_HOME,
                LauncherDefaults.DESTINATION_APPS,
                LauncherDefaults.DESTINATION_INPUTS,
                LauncherDefaults.DESTINATION_SETTINGS
            ),
            LauncherDefaults.SUPPORTED_DESTINATIONS
        )
        assertEquals(
            LauncherDefaults.DEFAULT_BACKGROUND,
            LauncherDefaults.DEFAULT_SETTINGS.selectedBackground
        )
        assertEquals(
            LauncherDefaults.DEFAULT_LAST_DESTINATION,
            LauncherDefaults.DEFAULT_SETTINGS.lastDestination
        )
        assertTrue(LauncherDefaults.DEFAULT_BACKGROUND in LauncherDefaults.SUPPORTED_BACKGROUND_IDS)
        assertTrue(LauncherDefaults.DEFAULT_LAST_DESTINATION in LauncherDefaults.SUPPORTED_DESTINATIONS)
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            set(key, value)

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = set(key, values?.toMutableSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = set(key, value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = set(key, value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = set(key, value)

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            set(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            key?.let {
                pending.remove(it)
                removals += it
            }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            pending.clear()
            removals.clear()
            return this
        }

        override fun commit(): Boolean {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            values.putAll(pending)
            return true
        }

        override fun apply() {
            commit()
        }

        private fun set(key: String?, value: Any?): SharedPreferences.Editor {
            key?.let {
                removals.remove(it)
                pending[it] = value
            }
            return this
        }
    }
}
