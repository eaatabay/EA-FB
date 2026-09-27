package com.eafb

fun main() {
    val movie = "/discover/movie?with_watch_providers=8&watch_region=TR&with_watch_monetization_types=flatrate"
    val tv = "/discover/tv?with_genres=16"
    check(CatalogSortPolicy.route(movie, MediaKind.MOVIE, CatalogSortMode.POPULAR)
        == movie)
    check(CatalogSortPolicy.route(movie, MediaKind.MOVIE, CatalogSortMode.NEWEST)
        .endsWith("&sort_by=primary_release_date.desc"))
    check(CatalogSortPolicy.route(tv, MediaKind.SERIES, CatalogSortMode.NEWEST)
         .endsWith("&sort_by=first_air_date.desc"))
    check(CatalogSortPolicy.route(movie, MediaKind.MOVIE, CatalogSortMode.HIGHEST_RATED)
        .endsWith("&sort_by=vote_average.desc"))
    check(CatalogSortPolicy.route("/trending/movie/week", MediaKind.MOVIE, CatalogSortMode.NEWEST)
        == "/trending/movie/week")
    check(CatalogSortPolicy.route(movie + "&sort_by=popularity.desc", MediaKind.MOVIE, CatalogSortMode.NEWEST)
        .count { it == '?' } == 1)
    check(CatalogSortPolicy.route(movie + "&sort_by=popularity.desc", MediaKind.MOVIE, CatalogSortMode.NEWEST)
        .count { it == '&' } == 3)
    check(CatalogSortMode.fromKey("newest") == CatalogSortMode.NEWEST)
    check(CatalogSortMode.fromKey("bogus") == CatalogSortMode.POPULAR)
    check(!CatalogSortPolicy.route(movie, MediaKind.MOVIE, CatalogSortMode.NEWEST).contains("vote_count.gte"))
    check(!CatalogSortPolicy.route(movie, MediaKind.MOVIE, CatalogSortMode.NEWEST).contains("primary_release_date.lte="))
    check(CatalogSortPolicy.route("/discover/person?with_genres=18",
        MediaKind.MOVIE,CatalogSortMode.NEWEST) == "/discover/person?with_genres=18")
    check(CatalogSortPolicy.route("/discover/movie/unsafe?with_genres=18",
        MediaKind.MOVIE,CatalogSortMode.NEWEST) == "/discover/movie/unsafe?with_genres=18")
    check(CatalogSortPolicy.route("/discover/tv?with_genres=18",
        MediaKind.SERIES,CatalogSortMode.POPULAR) == "/discover/tv?with_genres=18")
    check(!CatalogSortPolicy.route(movie, MediaKind.MOVIE, CatalogSortMode.HIGHEST_RATED)
        .contains("vote_count.gte"))
    println("PASS: 15/15 category sorting assertions")
}
