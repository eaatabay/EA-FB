package com.eafb

import android.content.Context
import android.content.SharedPreferences

/** Per-device personal credential, never compiled into the public extension. */
object EASettings {
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences("ea_fb_settings", Context.MODE_PRIVATE)
    }

    fun tmdbToken(): String = preferences?.getString("tmdb_read_token", "")?.trim().orEmpty()

    fun setTmdbToken(rawToken: String) {
        val cleaned = rawToken.trim().removePrefix("Bearer ").trim()
        preferences?.edit()?.putString("tmdb_read_token", cleaned)?.apply()
    }
}
