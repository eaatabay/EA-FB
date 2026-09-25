package com.eafb

fun main() {
    check(CatalogCardPolicy.hasPoster("/poster.jpg"))
    check(!CatalogCardPolicy.hasPoster(null))
    check(!CatalogCardPolicy.hasPoster(""))
    check(!CatalogCardPolicy.hasPoster("/"))
    check(!CatalogCardPolicy.hasPoster("https://untrusted.invalid/poster"))
    check(CatalogCardPolicy.hasNext(20, 1))
    check(CatalogCardPolicy.hasNext(20, 19))
    check(!CatalogCardPolicy.hasNext(19, 1))
    check(!CatalogCardPolicy.hasNext(0, 1))
    check(!CatalogCardPolicy.hasNext(20, 20))
    check(!CatalogCardPolicy.hasNext(20, 0))
    println("PASS: 11/11 catalog card and pagination assertions")
}
