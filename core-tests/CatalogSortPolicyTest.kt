package com.eafb

fun main() {
    val movie = "/discover/movie?with_watch_providers=8&watch_region=TR&with_watch_monetization_types=flatrate"
    val tv = "/discover/tv?with_genres=16"
    check(CatalogSortPolicy.route(movie, MediaKind.MOVIE, CatalogSortMode.POPULAR)
        .endsWith("&sort_by=popularity.desc"))
    check(CatalogSortPolicy.route(movie, MediaKind.MOVIE, CatalogSortMode.NEWEST)
        .endsWith("&sort_by=primary_release_date.desc"))
    check(CatalogSortPolicy.route(tv, MediaKind.SERIES, CatalogSortMode.NEWEST)
        == tv + "&sort_by=first_air_date.desc")
    check(CatalogSortPolicy.route(movie, MediaKind.MOVIE, CatalogSortMode.HIGHEST_RATED)
        .endsWith("&sort_by=vote_average.desc&vote_count.gte=100"))
    check(CatalogSortPolicy.route("/trending/movie/week", MediaKind.MOVIE, CatalogSortMode.NEWEST)
        == "/trending/movie/week")
    check(CatalogSortPolicy.route(movie + "&sort_by=popularity.desc", MediaKind.MOVIE, CatalogSortMode.NEWEST)
        .count { it == '?' } == 1)
    check(CatalogSortPolicy.route(movie + "&sort_by=popularity.desc", MediaKind.MOVIE, CatalogSortMode.NEWEST)
        .count { it == '&' } == 3)
    check(CatalogSortMode.fromKey("newest") == CatalogSortMode.NEWEST)
    check(CatalogSortMode.fromKey("bogus") == CatalogSortMode.POPULAR)
    println("PASS: 9/9 category sorting assertions")
}
