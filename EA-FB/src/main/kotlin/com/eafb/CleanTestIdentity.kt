package com.eafb

/** Identity for this isolated test; never reuse a production preference store. */
object CleanTestIdentity {
    const val NAME = "EA-FB UNIVERSAL 8601 TEST"
    const val INTERNAL_NAME = "EA-FB-UNIVERSAL-8601-TEST"
    const val STORE = "ea_fb_universal_8601_test"
    const val LEGACY_STORE = "ea_fb_universal_8601_seed"
    val sourceIds = listOf("dizibox", "diziyou", "hdfilmcehennemi-nl", "hdfilmcehennemi-land")
    val legacySourceIds = sourceIds
}
