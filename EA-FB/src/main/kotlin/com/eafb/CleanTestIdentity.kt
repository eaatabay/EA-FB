package com.eafb

/** Identity for this isolated test; never reuse a production preference store. */
object CleanTestIdentity {
    const val NAME = "EA-FB CODEX CLEAN TEST"
    const val INTERNAL_NAME = "EA-FB-CODEX-CLEAN-20261009"
    const val STORE = "ea_fb_clean_codex_fix_20261009"
    const val LEGACY_STORE = "ea_fb_catalog_settings_v6_staging"
    val sourceIds = listOf("dizibox", "diziyou")
}
