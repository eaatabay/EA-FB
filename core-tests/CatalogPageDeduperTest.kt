package com.eafb

fun main() {
    val deduper = CatalogPageDeduper()
    check(deduper.filter("weekly-tv", 1, listOf("a", "b", "b")) { it } == listOf("a", "b"))
    check(deduper.filter("weekly-tv", 2, listOf("b", "c", "d")) { it } == listOf("c", "d"))
    check(deduper.filter("weekly-tv", 3, listOf("d", "e")) { it } == listOf("e"))
    check(deduper.filter("weekly-movie", 1, listOf("b", "x")) { it } == listOf("b", "x"))
    check(deduper.filter("weekly-tv", 1, listOf("a", "c")) { it } == listOf("a", "c"))
    deduper.reset("weekly-tv")
    check(deduper.filter("weekly-tv", 2, listOf("a")) { it } == listOf("a"))
    println("PASS: 6/6 cross-page catalog dedupe assertions")
}
