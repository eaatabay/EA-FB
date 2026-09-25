package com.eafb

/** Keep the TV home rows navigable when TMDb returns titles without artwork. */
object CatalogCardPolicy {
    fun hasPoster(path: String?): Boolean =
        path != null && path.length > 1 && path.startsWith('/')

    /** TMDb catalog pages contain at most 20 raw results. */
    fun hasNext(rawResultCount: Int, page: Int): Boolean =
        rawResultCount >= 20 && page in 1..19
}
