package com.eafb

fun main() {
    val basic = CatalogShelfDefinition("new-turkish-tv", "Yeni Diziler", MediaKind.SERIES, genres="18")
    val route = CatalogShelfPolicy.category(basic)
    check(route?.tmdbPath == "/discover/tv?with_genres=18")
    val platform = CatalogShelfDefinition("regional-apple-movies", "Apple Filmleri",
        MediaKind.MOVIE, providerId=350, region="TR")
    check(CatalogShelfPolicy.category(platform)?.tmdbPath ==
        "/discover/movie?with_watch_providers=350&watch_region=TR&with_watch_monetization_types=flatrate")
    check(CatalogShelfPolicy.category(basic.copy(id="netflix-tv")) == null)
    check(CatalogShelfPolicy.category(basic.copy(enabled=false)) == null)
    check(CatalogShelfPolicy.category(basic.copy(language="tr")) == null)
    check(CatalogShelfPolicy.category(basic.copy(yearFrom=2025)) == null)
    check(CatalogShelfPolicy.category(basic.copy(genres="18;evil")) == null)
    check(CatalogShelfPolicy.category(platform.copy(region="US", yearFrom=2025, yearTo=2024)) == null)
    check(CatalogShelfPolicy.category(platform.copy(region="tr")) == null)
    val ordered = CatalogShelfPolicy.categories(listOf(
        platform.copy(order=2),basic.copy(order=0),
        basic.copy(id="disabled-archive",enabled=false,order=1)
    ))
    check(ordered?.map { it.id } == listOf("new-turkish-tv","regional-apple-movies"))
    check(CatalogShelfPolicy.categories(listOf(basic,basic)) == null)
    check(CatalogShelfPolicy.categories(listOf(basic,basic.copy(
        id="bad-filter",genres="18;evil"))) == null)
    check(CatalogShelfPolicy.categories(List(41) { index ->
        basic.copy(id="custom-rail-${index + 100}",order=index.coerceAtMost(39))
    }) == null)
    check(CatalogShelfPolicy.categories(listOf(basic.copy(order=40))) == null)
    check(CatalogShelfPolicy.categories(emptyList())?.isEmpty() == true)
    check(CatalogShelfPolicy.category(basic.copy(title="İki\nSatır")) == null)
    check(CatalogShelfPolicy.category(platform.copy(providerId=0)) == null)
    println("PASS: 17/17 catalog shelf route and batch assertions")
}
