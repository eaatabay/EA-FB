package com.eafb

/** Identity for this isolated test; never reuse a production preference store. */
object CleanTestIdentity {
    const val NAME = "EA-FB SERIES CODEX 8503 TEST"
    const val INTERNAL_NAME = "EA-FB-SERIES-CODEX-8503-TEST"
    const val STORE = "ea_fb_series_codex_8503_test"
    const val LEGACY_STORE = "ea_fb_series_codex_8503_seed"
    val sourceIds = listOf("dizibox", "diziyou", "hdfilmcehennemi-nl", "hdfilmcehennemi-land")
    val legacySourceIds = sourceIds
}
