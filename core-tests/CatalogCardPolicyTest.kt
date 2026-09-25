package com.eafb
fun main() {
    check(CatalogCardPolicy.hasPoster("/poster.jpg"))
    check(!CatalogCardPolicy.hasPoster(null))
    check(!CatalogCardPolicy.hasPoster(""))
    check(!CatalogCardPolicy.hasPoster("/"))
    check(!CatalogCardPolicy.hasPoster("https://bad.invalid/poster"))
    check(CatalogCardPolicy.hasNext(1,19,4))
    check(CatalogCardPolicy.hasNext(3,1,4))
    check(!CatalogCardPolicy.hasNext(4,20,4))
    check(!CatalogCardPolicy.hasNext(1,0,4))
    check(!CatalogCardPolicy.hasNext(20,20,30))
    check(!CatalogCardPolicy.hasNext(1,19,0))
    check(CatalogCardPolicy.hasNext(1,20,0))
    check(!CatalogCardPolicy.hasNext(0,20,2))
    println("PASS: 13/13 catalog card assertions")
}
