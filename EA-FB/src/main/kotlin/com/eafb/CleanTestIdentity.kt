package com.eafb

/** Identity for this isolated test; never reuse a production preference store. */
object CleanTestIdentity {
    const val NAME = "EA-FB V82 TEST"
    const val INTERNAL_NAME = "EA-FB-V82-TEST"
    const val STORE = "ea_fb_v82_test"
    const val LEGACY_STORE = "ea_fb_clean_bronze_nl_land_20261009"
    val sourceIds = listOf("dizibox", "diziyou", "hdfilmcehennemi-nl", "hdfilmcehennemi-land")
    val legacySourceIds = sourceIds
}
