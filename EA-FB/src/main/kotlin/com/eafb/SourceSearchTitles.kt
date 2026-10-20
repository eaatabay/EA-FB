package com.eafb

object SourceSearchTitles {
    fun candidates(query: MediaQuery): List<String> = SourceTitleMatcher.candidates(query).map { it.title }
}
