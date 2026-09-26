/**
 * Server-only, pure OMDb enrichment contract. No credential, upstream body,
 * network error or arbitrary OMDb field may enter the public catalog JSON.
 */
export function parseOmdbRating(data, expectedImdbId) {
  if (!data || typeof data !== "object" || Array.isArray(data) ||
      data.Response !== "True" || data.imdbID !== expectedImdbId ||
      typeof expectedImdbId !== "string" ||
      !/^tt\d{7,10}$/.test(expectedImdbId)) {
    return {settled:false,rating:null};
  }
  if (data.imdbRating === "N/A") return {settled:true,rating:null};
  // Strict decimal, never parse an HTML fragment, scientific notation,
  // locale-dependent commas or a null value as a rating.
  if (typeof data.imdbRating !== "string" ||
      !/^(?:[1-9](?:\.\d)?|10(?:\.0)?)$/.test(data.imdbRating)) {
    return {settled:false,rating:null};
  }
  const rating=Number(data.imdbRating);
  return {settled:Number.isFinite(rating) && rating > 0 && rating <= 10,
    rating:Number.isFinite(rating) && rating > 0 && rating <= 10 ? rating : null};
}

export function enrichmentCacheTtl(attempted, settled, normalTtl) {
  if (typeof attempted !== "boolean" || typeof settled !== "boolean" ||
      !Number.isSafeInteger(normalTtl) || normalTtl < 60 || normalTtl > 86_400) {
    throw new Error("invalid_enrichment_cache_policy");
  }
  return attempted && !settled ? 60 : normalTtl;
}
