package com.eafb

/** Pure, testable DiziMom catalog matching. No network or playback permission. */
object DiziMomMatch {
    private val yearPattern = Regex("""Yapım Yılı\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE)
    private val suffix = Regex(
        """\s+(?:Türkçe Dublaj|Türkçe Altyazılı|Son Bölüm|izle|Final)\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun publishedYear(cardText: String): Int? =
        yearPattern.find(cardText)?.groupValues?.get(1)?.toIntOrNull()

    fun cleanTitle(raw: String): String {
        var title = raw.trim()
        repeat(3) { title = title.replace(suffix, "").trim() }
        return title
    }

    fun matches(title: String, expected: String, publishedYear: Int?, expectedYear: Int?): Boolean =
        (expectedYear == null || expectedYear == publishedYear) &&
            Identity.normalize(cleanTitle(title)) == Identity.normalize(expected)
}
