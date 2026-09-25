package com.eafb

import android.content.Context
import android.content.SharedPreferences

/** Bundled token for private builds; optional per-device token for public builds. */
object EASettings {
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences("ea_fb_settings", Context.MODE_PRIVATE)
    }

    /** Personal .cs3 builds need no device setup. Public builds retain the optional settings flow. */
    fun tmdbToken(): String =
        EAConfig.tmdbBearerToken.takeIf { it.isNotBlank() }
            ?: preferences?.getString("tmdb_read_token", "")?.trim().orEmpty()

    fun setTmdbToken(rawToken: String) {
        val cleaned = rawToken.trim().removePrefix("Bearer ").trim()
        preferences?.edit()?.putString("tmdb_read_token", cleaned)?.apply()
    }
}
