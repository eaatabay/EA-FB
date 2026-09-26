package com.eafb

fun main() {
    val now = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.parse("2026-09-26")!!.time + 12 * 60 * 60 * 1000
    check(CatalogReleasePolicy.released("2026-09-26", now))
    check(CatalogReleasePolicy.released("2026-09-25", now))
    check(!CatalogReleasePolicy.released("2026-09-27", now))
    check(!CatalogReleasePolicy.released("2031-12-19", now))
    check(!CatalogReleasePolicy.released("2028-01-01", now))
    check(!CatalogReleasePolicy.released("2026-02-31", now))
    check(!CatalogReleasePolicy.released("", now))
    check(!CatalogReleasePolicy.released(null, now))
    check(!CatalogReleasePolicy.released("garbage", now))
    println("PASS: 9/9 released-title assertions")
}
