package com.eafb

/** Identity for this isolated test; never reuse a production preference store. */
object CleanTestIdentity {
    const val NAME = "EA-FB SERIES CODEX 8502 TEST"
    const val INTERNAL_NAME = "EA-FB-SERIES-CODEX-8502-TEST"
    const val STORE = "ea_fb_series_codex_8502_test"
    const val LEGACY_STORE = "ea_fb_series_codex_8502_seed"
    val sourceIds = listOf("dizibox", "diziyou", "hdfilmcehennemi-nl", "hdfilmcehennemi-land")
    val legacySourceIds = sourceIds
}

/** Fail closed unless secondary-user isolation or explicit, OS-verified separate-app isolation holds. */
internal object SeriesTestIsolationPolicy {
    data class AppIdentity(val packageName: String, val uid: Int, val dataDir: String,
                           val processName: String, val uidPackages: List<String>)
    fun separateApps(uid: Int, host: AppIdentity, other: AppIdentity, actualProcess: String): Boolean {
        val known = setOf("com.lagradost.cloudstream3", "com.lagradost.cloudstream3.prerelease",
            "com.lagradost.cloudstream3.debug", "com.lagradost.cloudstream3.prerelease.debug")
        fun valid(app: AppIdentity): Boolean = app.packageName in known && app.uid in 10000..99999 &&
            app.uidPackages == listOf(app.packageName) && app.processName == app.packageName &&
            app.dataDir in listOf("/data/user/0/${app.packageName}", "/data/data/${app.packageName}")
        return uid == host.uid && valid(host) && valid(other) && host.packageName != other.packageName &&
            host.uid != other.uid && host.dataDir != other.dataDir &&
            host.processName != other.processName && actualProcess == host.processName
    }
    fun inventoryName(name: String, entryPoint: String): String =
        if (name == CleanTestIdentity.INTERNAL_NAME && entryPoint == "com.eafb.EAPlugin") name
        else if (name == CleanTestIdentity.INTERNAL_NAME || entryPoint.startsWith("com.eafb.")) "EA-FB-CONFLICT"
        else name
    fun canLoad(uid: Int, installedNames: List<String>, separateApp: Boolean = false): Boolean {
        if (uid < 0 || uid % 100000 !in 10000..99999) return false
        if (uid < 100000 && !separateApp) return false
        if (installedNames.count { it == CleanTestIdentity.INTERNAL_NAME } != 1) return false
        return installedNames.filter { it != CleanTestIdentity.INTERNAL_NAME }.none {
            val name = it.uppercase(java.util.Locale.ROOT).replace('_', '-').replace(' ', '-')
            name.startsWith("EA-FB") || name.startsWith("EAFB")
        }
    }
}
