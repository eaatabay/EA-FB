package com.eafb

fun main() {
    var count = 0
    fun checkCase(ok: Boolean) { check(ok); count++ }
    val movies = listOf(
        FilmCollectionPart(3,"Spider-Man 3","2007-05-03"),
        FilmCollectionPart(1,"Spider-Man","2002-05-03"),
        FilmCollectionPart(2,"Spider-Man 2","2004-06-30")
    )
    checkCase(FilmCollectionPolicy.chronological(movies,2).map { it.id } == listOf(1,2,3))
    checkCase(FilmCollectionPolicy.chronological(movies.reversed(),1).map { it.id } == listOf(1,2,3))
    checkCase(FilmCollectionPolicy.chronological(movies + movies[0],2).size == 3)
    checkCase(FilmCollectionPolicy.chronological(movies,999).isEmpty())
    checkCase(FilmCollectionPolicy.chronological(listOf(movies[0]),movies[0].id).isEmpty())
    checkCase(FilmCollectionPolicy.chronological(movies,0).isEmpty())
    val missing = FilmCollectionPart(4,"Unreleased",null)
    checkCase(FilmCollectionPolicy.chronological(movies + missing,2).last().id == 4)
    val invalid = FilmCollectionPart(5,"Unknown Date","2026-99-99")
    checkCase(FilmCollectionPolicy.chronological(movies + invalid,2).last().id == 5)
    val sameDay = FilmCollectionPart(6,"Same date","2004-06-30")
    checkCase(FilmCollectionPolicy.chronological(movies + sameDay,2).map { it.id } ==
        listOf(1,2,6,3))
    checkCase(FilmCollectionPolicy.chronological(movies +
        FilmCollectionPart(-1,"Bad","2000-01-01") +
        FilmCollectionPart(10,"  ","1990-01-01"),2).size == 3)
    checkCase(FilmCollectionPolicy.chronological(movies,2).first().title == "Spider-Man")
    checkCase(FilmCollectionPolicy.chronological(movies +
        FilmCollectionPart(7,"  Bonus  ","2001-01-01"),2).first().title == "Bonus")
    val impossible = FilmCollectionPart(8,"Impossible February","2026-02-31")
    val badLeap = FilmCollectionPart(9,"Not Leap Year","2025-02-29")
    val leap = FilmCollectionPart(11,"Leap Year","2024-02-29")
    checkCase(FilmCollectionPolicy.chronological(movies + impossible,2).last().id == 8)
    checkCase(FilmCollectionPolicy.chronological(movies + badLeap,2).last().id == 9)
    checkCase(FilmCollectionPolicy.chronological(movies + leap,2).map { it.id } ==
        listOf(1,2,3,11))
    println("PASS: $count/15 film collection chronology and identity cases")
}
