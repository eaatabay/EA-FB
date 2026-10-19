package com.eafb

fun main() {
    val own = CleanTestIdentity.INTERNAL_NAME
    val v83 = "EA-FB-V83-TEST"
    var count = 0
    fun verify(value: Boolean) { check(value); count++ }
    for (uid in listOf(-1, 0, 1000, 10001, 99999, 100000, 101000)) {
        verify(!SeriesTestIsolationPolicy.canLoad(uid, listOf(own)))
    }
    for (uid in listOf(110001, 1012345, 1112345)) {
        verify(SeriesTestIsolationPolicy.canLoad(uid, listOf(own)))
        verify(SeriesTestIsolationPolicy.canLoad(uid, listOf("DiziBox", own)))
        verify(!SeriesTestIsolationPolicy.canLoad(uid, listOf(v83, own)))
        verify(!SeriesTestIsolationPolicy.canLoad(uid, listOf(own, v83)))
        verify(!SeriesTestIsolationPolicy.canLoad(uid, listOf("EA-FB", own)))
        verify(!SeriesTestIsolationPolicy.canLoad(uid, listOf("ea_fb_v81_test", own)))
        verify(!SeriesTestIsolationPolicy.canLoad(uid, emptyList()))
        verify(!SeriesTestIsolationPolicy.canLoad(uid, listOf(own, own)))
    }
    verify(CleanTestIdentity.STORE !in listOf("ea_fb_v83_test", "ea_fb_v82_test"))
    verify(CleanTestIdentity.LEGACY_STORE !in listOf("ea_fb_v83_test", "ea_fb_v82_test"))
    println("PASS: $count profile/inventory isolation assertions; primary profile, V83 in either order, unknown/duplicate inventory blocked")
}
