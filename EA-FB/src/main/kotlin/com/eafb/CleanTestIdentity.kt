package com.eafb

/** Identity for this isolated test; never reuse a production preference store. */
object CleanTestIdentity {
    const val NAME = "EA-FB CODEX BRONZE NL LAND TEST"
    const val INTERNAL_NAME = "EA-FB-CODEX-BRONZE-NL-LAND-20261009"
    const val STORE = "ea_fb_clean_bronze_nl_land_20261009"
    const val LEGACY_STORE = "ea_fb_clean_codex_fix_20261009"
    val sourceIds = listOf("dizibox", "diziyou", "hdfilmcehennemi-nl", "hdfilmcehennemi-land")
    val legacySourceIds = listOf("dizibox", "diziyou")
}
