package com.eafb

import java.text.Normalizer
import java.util.Locale

/** Source observations, never values copied from the catalogue to fill gaps. */
data class SourceIdentity(
    val title: String, val kind: MediaKind?, val year: Int?, val tmdbId: Int? = null,
    val season: Int? = null, val episode: Int? = null, val conflicting: Boolean = false, val observedNames: List<String> = emptyList()
)

enum class MatchLevel { EXACT, VERIFIED_ALIAS, CORROBORATED, AMBIGUOUS, REJECTED }
data class MatchDecision(val level: MatchLevel, val reason: String) {
    val accepted get() = level in setOf(MatchLevel.EXACT, MatchLevel.VERIFIED_ALIAS, MatchLevel.CORROBORATED)
}

/** Shared discovery and acceptance rules. No work-specific title/ID exception tables. */
object SourceTitleMatcher {
    const val MAX_CANDIDATES = 6
    const val MAX_PAGES = 8
    data class Candidate(val title: String, val discoveryOnly: Boolean = false)
    fun key(raw: String): String = Normalizer.normalize(raw.replace('ı', 'i').replace('İ', 'I')
        .lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
    private fun clean(raw: String): String = raw.trim().replace(
        Regex("(?:\\s+|\\s*[-|]\\s*)(?:izle|dizi izle|film izle|full hd izle)$", RegexOption.IGNORE_CASE), "")
    fun meaningfulSeason(raw: String?): String? = raw?.trim()?.takeIf {
        val k = key(it)
        k.isNotBlank() && !Regex("(?:season|sezon|series) ?\\d+|\\d+ ?(?:season|sezon)|(?:ozel bolumler|ozel bolum|specials|special|episodes|bolumler)").matches(k)
    }
    private fun direct(query: MediaQuery) = (listOf(query.title) + query.alternateTitles)
        .asSequence().map(String::trim).filter { it.isNotBlank() && it.length <= 200 }
        .distinctBy(::key).take(4).toList()
    private fun acceptedNames(query: MediaQuery): List<String> {
        val direct = direct(query)
        val season = if (query.kind == MediaKind.SERIES && query.season != null) meaningfulSeason(query.seasonTitle) else null
        return (direct + if (season == null) emptyList() else direct.take(2).filter {
            key(it) != key(season) && !key(it).endsWith(" " + key(season))
        }.map { "$it: $season" }).distinctBy(::key)
    }
    fun candidates(query: MediaQuery): List<Candidate> {
        val direct = direct(query)
        val names = acceptedNames(query)
        val combined = names.filter { it !in direct }
        val safe = direct.take(2) + combined + direct.drop(2)
        val normal = safe.map { key(it) }.filter { it.isNotBlank() }
        val broad = query.title.substringBefore(':').trim().takeIf { ':' in query.title && it.length >= 3 }
        return (safe.map { Candidate(it) } + normal.map { Candidate(it) } + listOfNotNull(broad?.let { Candidate(it, true) }))
            .distinctBy { it.title.lowercase(Locale.ROOT) }.take(MAX_CANDIDATES)
    }
    private fun equivalent(left: String, right: String, film: Boolean): Boolean {
        fun words(value: String) = (if (film) value.removePrefix("the ") else value).split(' ').filter { it.isNotBlank() }
        val a = words(left); val b = words(right)
        return a.isNotEmpty() && b.isNotEmpty() &&
            (a.joinToString("") == b.joinToString("") || (film && a.sorted() == b.sorted()))
    }
    fun titleLevel(query: MediaQuery, raw: String): MatchLevel? {
        val original = key(raw.trim())
        val direct = direct(query)
        if (original == key(query.title)) return MatchLevel.EXACT
        if (direct.drop(1).any { key(it) == original }) return MatchLevel.VERIFIED_ALIAS
        val actual = key(clean(raw)); if (actual.isBlank()) return null
        if (actual == key(query.title)) return MatchLevel.EXACT
        if (direct.drop(1).any { key(it) == actual }) return MatchLevel.VERIFIED_ALIAS
        val names = acceptedNames(query)
        if (names.any { key(it) == actual }) return MatchLevel.CORROBORATED
        val film = query.kind == MediaKind.MOVIE
        val parts = clean(raw).split(Regex("\\s+[-–—|]\\s+"))
        return if (parts.all { part -> names.any { equivalent(key(part), key(it), film) } }) MatchLevel.CORROBORATED else null
    }
    fun assess(query: MediaQuery, source: SourceIdentity): MatchDecision {
        fun reject(reason: String) = MatchDecision(MatchLevel.REJECTED, reason)
        if (source.conflicting) return reject("conflicting-source-evidence")
        if (source.kind != null && query.kind != source.kind) return reject("media-type")
        if (query.tmdbId != null && source.tmdbId != null && query.tmdbId != source.tmdbId) return reject("external-id")
        if (query.year != null && source.year != null && query.year != source.year) return reject("release-year")
        if (query.season != null && (query.season != source.season || query.episode != source.episode)) return reject("episode-identity")
        if (source.observedNames.any { titleLevel(query, it) == null }) return reject("conflicting-source-title")
        val relation = titleLevel(query, source.title) ?: return reject("title-unexplained")
        if (source.kind == null) return MatchDecision(MatchLevel.AMBIGUOUS, "missing-source-type")
        val equalId = query.tmdbId != null && source.tmdbId == query.tmdbId
        if (!equalId && (query.year == null || source.year == null)) return MatchDecision(MatchLevel.AMBIGUOUS, "missing-release-year")
        return MatchDecision(relation, if (equalId) "title-type-external-id" else "title-type-year-episode")
    }
    fun unique(offers: List<MediaOffer>): List<MediaOffer> = offers.distinctBy {
        Triple(it.pageUrl.trimEnd('/'), it.season to it.episode, it.playbackData)
    }.let { if (it.size == 1) it else emptyList() }
}
