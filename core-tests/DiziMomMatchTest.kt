package com.eafb

fun main() {
    check(DiziMomMatch.publishedYear("Yapım Yılı: 2024") == 2024)
    check(DiziMomMatch.publishedYear("Yapım Yılı : 2023") == 2023)
    check(DiziMomMatch.publishedYear("No year here") == null)
    check(DiziMomMatch.matches("War Türkçe Altyazılı", "War", 2024, 2024))
    check(!DiziMomMatch.matches("War Türkçe Altyazılı", "War", null, 2024))
    check(!DiziMomMatch.matches("War", "War", 2023, 2024))
    check(!DiziMomMatch.matches("War", "Wars", 2024, 2024))
    check(DiziMomMatch.cleanTitle("War Final") == "War")
    check(DiziMomMatch.cleanTitle("The Final") == "The")
    check(DiziMomMatch.cleanTitle("Final") == "Final")
    println("DiziMomMatch: 9 checks passed")
}
