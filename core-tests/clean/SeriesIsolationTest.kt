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
    verify(SeriesTestIsolationPolicy.inventoryName("renamed-plugin", "com.eafb.EAPlugin") == "EA-FB-CONFLICT")
    verify(SeriesTestIsolationPolicy.inventoryName(own, "org.other.Plugin") == "EA-FB-CONFLICT")
    verify(SeriesTestIsolationPolicy.inventoryName(own, "com.eafb.EAPlugin") == own)
    verify(!SeriesTestIsolationPolicy.canLoad(110001, listOf(own,
        SeriesTestIsolationPolicy.inventoryName("renamed-plugin", "com.eafb.EAPlugin"))))
    verify(CleanTestIdentity.STORE !in listOf("ea_fb_v83_test", "ea_fb_v82_test"))
    verify(CleanTestIdentity.LEGACY_STORE !in listOf("ea_fb_v83_test", "ea_fb_v82_test"))
    val host = SeriesTestIsolationPolicy.AppIdentity("com.lagradost.cloudstream3", 10123,
        "/data/user/0/com.lagradost.cloudstream3", "com.lagradost.cloudstream3", listOf("com.lagradost.cloudstream3"))
    val other = SeriesTestIsolationPolicy.AppIdentity("com.lagradost.cloudstream3.prerelease", 10456,
        "/data/user/0/com.lagradost.cloudstream3.prerelease", "com.lagradost.cloudstream3.prerelease", listOf("com.lagradost.cloudstream3.prerelease"))
    fun isolated(a: SeriesTestIsolationPolicy.AppIdentity = host, b: SeriesTestIsolationPolicy.AppIdentity = other,
                 uid: Int = host.uid, process: String = host.processName) = SeriesTestIsolationPolicy.separateApps(uid, a, b, process)
    verify(isolated())
    verify(SeriesTestIsolationPolicy.canLoad(host.uid, listOf(own), isolated()))
    verify(!SeriesTestIsolationPolicy.canLoad(host.uid, listOf(own, v83), isolated()))
    verify(!SeriesTestIsolationPolicy.canLoad(host.uid, listOf(own, "EA-FB"), isolated()))
    verify(!isolated(uid = 10124))
    verify(!isolated(process = other.processName))
    verify(!isolated(b = host))
    for (bad in listOf(host.copy(uid = other.uid), host.copy(dataDir = other.dataDir),
        host.copy(processName = other.processName), host.copy(uidPackages = listOf(host.packageName, "shared")),
        host.copy(uidPackages = emptyList()), host.copy(packageName = "unknown"), host.copy(uid = 1000),
        host.copy(dataDir = "/sdcard/host"))) verify(!isolated(a = bad))
    verify(!isolated(b = other.copy(uidPackages = listOf(other.packageName, "shared"))))
    println("PASS: $count profile/inventory isolation assertions; unverified primary profile, shared UID/data/process, V83 in either order and duplicate inventory blocked")
}
