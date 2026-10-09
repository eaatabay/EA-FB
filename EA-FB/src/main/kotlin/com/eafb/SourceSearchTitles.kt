package com.eafb

object SourceSearchTitles {
    fun candidates(query: MediaQuery): List<String> = (listOf(query.title) + query.alternateTitles)
        .map { it.trim() }.filter { it.isNotBlank() }
        .distinctBy { Identity.normalize(it) }.take(3)
}
