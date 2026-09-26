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
    println("PASS: 9/9 catalog shelf route assertions")
}
