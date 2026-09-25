package com.eafb

import android.content.Context
import android.content.SharedPreferences

/**
 * EA-FB-only choices: no API keys or login data are stored here.
 * Android SharedPreferences preserves category switches and sorting across restarts.
 */
object EASettings {
    private const val STORE = "ea_fb_catalog_settings_v1"
    private const val CATEGORY_PREFIX = "category_"
    private const val SORT_KEY = "catalog_sort"

    @Volatile private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    }

    fun categoryEnabled(id: String): Boolean =
        preferences?.getBoolean(CATEGORY_PREFIX + id, true) ?: true

    fun setCategoryEnabled(id: String, enabled: Boolean) {
        preferences?.edit()?.putBoolean(CATEGORY_PREFIX + id, enabled)?.apply()
    }

    fun sortMode(): CatalogSortMode =
        CatalogSortMode.fromKey(preferences?.getString(SORT_KEY, null))

    fun setSortMode(mode: CatalogSortMode) {
        preferences?.edit()?.putString(SORT_KEY, mode.key)?.apply()
    }

    fun setAllCategories(enabled: Boolean) {
        val editor = preferences?.edit() ?: return
        HomeCategories.all.filter { it.tmdbPath != null }
            .forEach { editor.putBoolean(CATEGORY_PREFIX + it.id, enabled) }
        editor.apply()
    }

    fun restoreDefaults() {
        val editor = preferences?.edit() ?: return
        HomeCategories.all.forEach { editor.remove(CATEGORY_PREFIX + it.id) }
        editor.remove(SORT_KEY).apply()
    }
}
