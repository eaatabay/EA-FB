package com.eafb

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    check(PlaybackLinkBridge.isCatalogIdentity("ea-fb:movie:42"))
    check(PlaybackLinkBridge.isCatalogIdentity("ea-fb:episode:42:3:2"))
    check(!PlaybackLinkBridge.isCatalogIdentity("ea-fb:episode:42:3:0"))
    check(!PlaybackLinkBridge.isCatalogIdentity("ea-fb:live:abc"))
    check(!PlaybackLinkBridge.isCatalogIdentity("ea-fb:open:big-buck-bunny"))
    check(PlaybackLinkBridge.alternatives(
        "ea-fb:movie:42", "", null, 1000).isEmpty())
    check(PlaybackLinkBridge.alternatives(
        "ea-fb:episode:42:3:2", "İsyan", 2026, 1000).isEmpty())
    check(PlaybackLinkBridge.alternatives(
        "ea-fb:live:abc", "Canlı", null, 1000).isEmpty())
    println("PASS: V49 catalog bridge fails closed without live grants")
}
