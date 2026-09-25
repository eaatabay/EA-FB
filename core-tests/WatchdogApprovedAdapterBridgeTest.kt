package com.eafb

import android.content.Context
import android.content.SharedPreferences

/** An unconfigured production bridge must not even read cached private data. */
private class BridgeContext : Context() {
    var lookedUp = false
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        object : SharedPreferences {
            override fun getLong(key: String, default: Long): Long {
                lookedUp = true; error("unapproved snapshot read")
            }
            override fun getString(key: String, default: String?): String? {
                lookedUp = true; error("unapproved snapshot read")
            }
            override fun edit(): SharedPreferences.Editor = error("unexpected write")
        }
}
private class BridgeAdapter : VerifiedBaseUrlAdapter {
    override val id: String = "licensed-demo"
    override val adapterVersion: Int = 3
    var configured = 0
    override fun configuredForVerifiedBaseUrl(baseUrl: String): MediaSourceAdapter {
        configured++
        error("inactive bridge must never configure a source")
    }
    override suspend fun search(query: MediaQuery): List<MediaOffer> =
        error("inactive bridge must never search")
    override suspend fun resolve(offer: MediaOffer): List<SourceLink> =
        error("inactive bridge must never resolve")
}

fun main() {
    var count = 0
    fun ok(value: Boolean, label: String) { check(value) { label }; count++ }
    val context = BridgeContext()
    val store = WatchdogClientStore(context)
    val bundled = BridgeAdapter()
    val bridge = WatchdogApprovedAdapterBridge(store, listOf(bundled))
    val now = 1_800_000_000_000L
    ok(bridge.forNewSearch(MediaKind.MOVIE, now).isEmpty(),
        "production movie sources disabled")
    ok(bridge.forNewSearch(MediaKind.SERIES, now).isEmpty(),
        "production series sources disabled")
    ok(bridge.forNewSearch(MediaKind.LIVE, now).isEmpty(),
        "Live TV remains separate and untouched")
    ok(bundled.configured == 0 && !context.lookedUp,
        "no unapproved adapter initialization or cached-source read")
    ok(WatchdogTrustConfig.pinnedPublicKeys.isEmpty() &&
        WatchdogTrustConfig.installedAdapterVersions.isEmpty() &&
        !WatchdogDeliveryConfig.enabled,
        "checked-in v6 has no active signing pins or adapters")
    println("PASS: $count/$count production-off approved adapter bridge cases")
}
