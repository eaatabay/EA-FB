package com.eafb

/** Pure pagination policy: bound scans, never claim page-2 after scanning it. */
object CatalogPagePolicy {
    fun extraNewestPages(
        page: Int,
        totalPages: Int,
        firstPageEmpty: Boolean,
        isDiscover: Boolean,
        isNewest: Boolean,
        validResultsArray: Boolean
    ): IntRange {
        if (page != 1 || !firstPageEmpty || !isDiscover || !isNewest ||
            !validResultsArray || totalPages < 2) return IntRange.EMPTY
        return 2..minOf(totalPages,3)
    }

    fun allowNextPage(
        usedFallback: Boolean,
        scannedExtraPages: Boolean,
        page: Int,
        rawLength: Int,
        totalPages: Int
    ): Boolean =
        !usedFallback && !scannedExtraPages &&
            page >= 1 && page < totalPages && rawLength > 0
}
