package com.eafb

import android.content.Context
import android.content.SharedPreferences

internal class MemoryPreferences : SharedPreferences {
    val values = mutableMapOf<String, Any>()
    var writes = 0
    override fun contains(key: String) = key in values
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun getString(key: String, default: String?) = values[key] as? String ?: default
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val changes = mutableMapOf<String, Any?>()
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun putString(key: String, value: String) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun commit(): Boolean {
            writes++
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
        override fun apply() { commit() }
    }
}
internal class MemoryContext : Context() {
    val stores = mutableMapOf<String, MemoryPreferences>()
    override fun getSharedPreferences(name: String, mode: Int) = stores.getOrPut(name) { MemoryPreferences() }
}

fun main() {
    val empty = MemoryContext()
    EASettings.initialize(empty)
    check(!EASettings.sourceEnabled("dizibox") && !EASettings.sourceEnabled("diziyou"))
    val ctx = MemoryContext()
    val old = ctx.getSharedPreferences(CleanTestIdentity.LEGACY_STORE, 0)
    old.values["source_enabled_dizibox"] = true
    old.values["source_enabled_diziyou"] = false
    old.values["source_enabled_hdfilmcehennemi-land"] = true
    old.values["category_popular-tv"] = false
    val original = old.values.toMap()
    val target = ctx.getSharedPreferences(CleanTestIdentity.STORE, 0)
    // An explicit new-store choice must not be overridden by migration.
    target.values["source_enabled_dizibox"] = false
    EASettings.initialize(ctx)
    check(!EASettings.sourceEnabled("dizibox"))
    check(!EASettings.sourceEnabled("diziyou"))
    check(!target.contains("source_enabled_hdfilmcehennemi-land"))
    check(!EASettings.categoryEnabled("popular-tv"))
    check(old.values == original && old.writes == 0)
    EASettings.setSourceEnabled("diziyou", true)
    val writes = target.writes
    EASettings.initialize(ctx)
    check(EASettings.sourceEnabled("diziyou") && target.writes == writes)
    val migrated = MemoryContext()
    migrated.getSharedPreferences(CleanTestIdentity.LEGACY_STORE, 0).values["source_enabled_dizibox"] = true
    EASettings.initialize(migrated)
    check(EASettings.sourceEnabled("dizibox") && !EASettings.sourceEnabled("diziyou"))
    println("PASS: isolated opt-in settings and one-time, read-only legacy migration")
}
