package com.eafb

import android.content.Context
import android.content.SharedPreferences

/**
 * EA-FB-only choices: no API keys or login data are stored here.
 * Android SharedPreferences preserves category switches and sorting across restarts.
 */
object EASettings {
    // Keep the isolated LAND test independent from V6 STAGING and V64 preferences.
    private const val STORE = "ea_fb_v65_land_test_settings"
    private const val CATEGORY_PREFIX = "category_"
    private const val SORT_KEY = "catalog_sort"
    private const val SOURCE_PREFIX = "source_enabled_"

    @Volatile private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    }

    /** External playback sources are opt-in and remain disabled by default. */
    fun sourceEnabled(id: String): Boolean =
        preferences?.getBoolean(SOURCE_PREFIX + id, false) ?: false

    fun setSourceEnabled(id: String, enabled: Boolean) {
        preferences?.edit()?.putBoolean(SOURCE_PREFIX + id, enabled)?.apply()
    }

    fun categoryEnabled(id: String): Boolean =
        preferences?.getBoolean(CATEGORY_PREFIX + id, true) ?: true

    /**
     * Stable fingerprint of home-affecting preferences. CloudStream may cache a
     * MainPageRequest by its data key; changing this suffix forces a fresh home
     * request after switches or sorting change without changing visible titles.
     */
    fun homeRevision(): String {
        var hash = 17
        HomeCategories.all.filter { it.tmdbPath != null }.forEach { category ->
            hash = 31 * hash + category.id.hashCode()
            hash = 31 * hash + if (categoryEnabled(category.id)) 1 else 0
        }
        hash = 31 * hash + sortMode().key.hashCode()
        return hash.toUInt().toString(16)
    }

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
