package com.eafb

fun main() {
    fun pages(page:Int,total:Int,empty:Boolean=true,discover:Boolean=true,
              newest:Boolean=true,valid:Boolean=true) =
        CatalogPagePolicy.extraNewestPages(page,total,empty,discover,newest,valid).toList()
    check(pages(1,0).isEmpty())
    check(pages(1,1).isEmpty())
    check(pages(1,2)==listOf(2))
    check(pages(1,3)==listOf(2,3))
    check(pages(1,100)==listOf(2,3))
    check(pages(2,5).isEmpty())
    check(pages(1,5,empty=false).isEmpty())
    check(pages(1,5,discover=false).isEmpty())
    check(pages(1,5,newest=false).isEmpty())
    check(pages(1,5,valid=false).isEmpty())
    check(!CatalogPagePolicy.allowNextPage(true,false,1,20,5))
    check(!CatalogPagePolicy.allowNextPage(false,true,1,20,5))
    check(CatalogPagePolicy.allowNextPage(false,false,1,20,5))
    check(!CatalogPagePolicy.allowNextPage(false,false,5,20,5))
    check(!CatalogPagePolicy.allowNextPage(false,false,1,0,5))
    check(CatalogPagePolicy.allowNextPage(false,false,1,20,0))
    check(!CatalogPagePolicy.allowNextPage(false,false,1,19,0))
    check(!CatalogPagePolicy.allowNextPage(false,false,21,20,99))
    fun sparse(page:Int,total:Int,empty:Boolean=true,discover:Boolean=true,
               valid:Boolean=true) =
        CatalogPagePolicy.extraSparseDiscoverPages(page,total,empty,discover,valid).toList()
    check(sparse(1,0).isEmpty())
    check(sparse(1,1).isEmpty())
    check(sparse(1,2)==listOf(2))
    check(sparse(1,3)==listOf(2,3))
    check(sparse(1,50)==listOf(2,3))
    check(sparse(2,5).isEmpty())
    check(sparse(1,5,empty=false).isEmpty())
    check(sparse(1,5,discover=false).isEmpty())
    check(sparse(1,5,valid=false).isEmpty())
    println("PASS: 27/27 bounded newest, sparse discover and pagination assertions")
}
