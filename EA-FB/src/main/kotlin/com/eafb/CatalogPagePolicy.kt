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

    /** Sparse regional platform/genre feeds can have posterless first pages.
     * Scan at most two additional pages only when the first page has no
     * displayable cards; never replace a genuine empty TMDb result with
     * unrelated films or another streaming provider.
     */
    fun extraSparseDiscoverPages(
        page: Int,
        totalPages: Int,
        firstPageEmpty: Boolean,
        isDiscover: Boolean,
        validResultsArray: Boolean
    ): IntRange {
        if (page != 1 || !firstPageEmpty || !isDiscover ||
            !validResultsArray || totalPages < 2) return IntRange.EMPTY
        return 2..minOf(totalPages, 3)
    }

    fun allowNextPage(
        usedFallback: Boolean,
        scannedExtraPages: Boolean,
        page: Int,
        rawLength: Int,
        totalPages: Int
    ): Boolean =
        !usedFallback && !scannedExtraPages &&
            CatalogCardPolicy.hasNext(page,rawLength,totalPages)
}
