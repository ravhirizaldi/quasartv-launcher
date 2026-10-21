package com.quasartv.launcher.data

import android.content.SharedPreferences

class FavoritesStore(private val preferences: SharedPreferences) {
    fun getFavorites(): List<String> {
        val stored = readStored() ?: return emptyList()
        return stored
            .split(SEPARATOR)
            .mapNotNull(::normalizePackageName)
            .distinct()
    }

    fun addFavorite(packageName: String): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        val favorites = getFavorites()
        if (normalized in favorites) return false
        writeFavorites(favorites + normalized)
        return true
    }

    fun removeFavorite(packageName: String): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        val favorites = getFavorites()
        if (normalized !in favorites) return false
        writeFavorites(favorites.filterNot { it == normalized })
        return true
    }

    fun reorderFavorites(fromIndex: Int, toIndex: Int): Boolean {
        val favorites = getFavorites()
        if (fromIndex !in favorites.indices || toIndex !in favorites.indices || fromIndex == toIndex) {
            return false
        }
        val reordered = favorites.toMutableList()
        val packageName = reordered.removeAt(fromIndex)
        reordered.add(toIndex, packageName)
        writeFavorites(reordered)
        return true
    }

    fun reorderFavorite(packageName: String, toIndex: Int): Boolean {
        val normalized = normalizePackageName(packageName) ?: return false
        val favorites = getFavorites()
        val fromIndex = favorites.indexOf(normalized)
        if (fromIndex < 0) return false
        return reorderFavorites(fromIndex, toIndex)
    }

    fun trimStale(installedPackageNames: Collection<String>): List<String> {
        val installed = installedPackageNames
            .asSequence()
            .mapNotNull(::normalizePackageName)
            .toSet()
        val favorites = getFavorites()
        val trimmed = favorites.filter { it in installed }
        if (trimmed != favorites) writeFavorites(trimmed)
        return trimmed
    }

    private fun readStored(): String? = try {
        preferences.getString(KEY_FAVORITES, null)
    } catch (_: ClassCastException) {
        null
    }

    private fun writeFavorites(favorites: List<String>) {
        val editor = preferences.edit()
        if (favorites.isEmpty()) {
            editor.remove(KEY_FAVORITES)
        } else {
            editor.putString(KEY_FAVORITES, favorites.joinToString(SEPARATOR))
        }
        editor.commit()
    }

    private fun normalizePackageName(packageName: String): String? {
        val normalized = packageName.trim()
        if (normalized.isEmpty() || normalized.any { it.isWhitespace() } || SEPARATOR in normalized) {
            return null
        }
        return normalized
    }

    private companion object {
        const val KEY_FAVORITES = "favorite_packages"
        const val SEPARATOR = "\u001f"
    }
}
