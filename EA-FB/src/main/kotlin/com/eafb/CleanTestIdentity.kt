package com.eafb

/** Identity for this isolated test; never reuse a production preference store. */
object CleanTestIdentity {
    const val NAME = "EA-FB SERIES CODEX 8501 TEST"
    const val INTERNAL_NAME = "EA-FB-SERIES-CODEX-8501-TEST"
    const val STORE = "ea_fb_series_codex_8501_test"
    const val LEGACY_STORE = "ea_fb_series_codex_8501_seed"
    val sourceIds = listOf("dizibox", "diziyou", "hdfilmcehennemi-nl", "hdfilmcehennemi-land")
    val legacySourceIds = sourceIds
}

/** Android's per-user UID range provides process/data isolation from the primary profile. */
internal object SeriesTestIsolationPolicy {
    fun canLoad(uid: Int, installedNames: List<String>): Boolean {
        if (uid < 100000 || uid % 100000 !in 10000..99999) return false
        if (installedNames.count { it == CleanTestIdentity.INTERNAL_NAME } != 1) return false
        return installedNames.filter { it != CleanTestIdentity.INTERNAL_NAME }.none {
            val name = it.uppercase(java.util.Locale.ROOT).replace('_', '-').replace(' ', '-')
            name.startsWith("EA-FB") || name.startsWith("EAFB")
        }
    }
}
