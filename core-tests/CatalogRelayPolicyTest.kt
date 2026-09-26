package com.eafb

private const val HOUR = 3_600_000L
private const val NOW = 1_800_000_000_000L

fun main() {
    var passed = 0
    fun checked(condition: Boolean, label: String) {
        check(condition) { label }
        passed++
    }
    val pinned = CatalogRelayPolicy.approvedOrigin
    checked(CatalogRelayPolicy.approved(pinned, "ready") == pinned,
        "exact approved metadata Worker is accepted")
    for (origin in listOf(
        "http://ea-fb-catalog.eaatabay.workers.dev",
        "https://ea-fb-catalog.eaatabay.workers.dev.evil.example",
        "https://user@ea-fb-catalog.eaatabay.workers.dev",
        "https://ea-fb-catalog.eaatabay.workers.dev:443",
        "https://ea-fb-catalog.eaatabay.workers.dev/",
        "https://127.0.0.1",
        "https://evil.example.org",
        "https://ea-fb-catalog.eaatabay.workers.dev?x=1",
        "https://ea-fb-catalog.eaatabay.workers.dev#x"
    )) checked(CatalogRelayPolicy.approved(origin, "ready") == null,
        "unreviewed origin is rejected: $origin")
    checked(CatalogRelayPolicy.approved(pinned, "disabled") == null,
        "explicitly disabled backend cannot be used")
    checked(CatalogRelayPolicy.approved(pinned, "") == null,
        "missing backend status fails closed")
    checked(CatalogRelayPolicy.usableCached(pinned, NOW, NOW + HOUR) == pinned,
        "pinned origin may survive short config outage")
    checked(CatalogRelayPolicy.usableCached(pinned, NOW, NOW + 24 * HOUR) == pinned,
        "cached origin valid exactly at 24 hour boundary")
    checked(CatalogRelayPolicy.usableCached(pinned, NOW, NOW + 24 * HOUR + 1) == null,
        "stale config cannot be used beyond 24 hours")
    checked(CatalogRelayPolicy.usableCached("https://evil.example.org", NOW, NOW) == null,
        "unreviewed cached origin is never trusted")
    checked(CatalogRelayPolicy.usableCached(pinned, NOW + 1, NOW) == null,
        "clock rollback invalidates cache")
    checked(CatalogRelayPolicy.usableCached(pinned, 0, NOW) == null,
        "uninitialized cache timestamp is not trusted")
    println("PASS: $passed/$passed pinned metadata relay checks")
}
