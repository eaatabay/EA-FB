/**
 * v6 catalog-only shelf contract. Deliberately separate from playback-source
 * grants: creating a TMDb shelf cannot authorize a stream or modify watchdog.
 */
const ID=/^[a-z][a-z0-9-]{2,47}$/;
const REGION=/^[A-Z]{2}$/;
const LANGUAGE=/^[a-z]{2}$/;
const GENRES=/^\d{1,4}(,\d{1,4}){0,4}$/;
const PROVIDER=/^\d{1,6}$/;
const MAX=40;
const RESERVED=new Set(["continue","trending","now-playing","popular-movie","popular-tv","top-movie","top-tv","netflix-movie","netflix-tv","disney-movie","disney-tv","amazon-movie","amazon-tv","apple-movie","apple-tv","max-movie","max-tv","paramount-movie","paramount-tv","mubi-movie","action","sci-fi","horror","comedy","animation-movie","animation-tv","documentary-top","documentary-trend","community"]);
export function validateCatalogShelves(input) {
  if (!Array.isArray(input) || input.length>MAX) throw Error("invalid_shelf_count");
  const seen=new Set();
  return input.map((item,index)=>{
    if (!item || typeof item!=="object" || Array.isArray(item) ||
      Object.keys(item).some(k=>!["id","title","kind","providerId","region","genres","enabled","order","language","yearFrom","yearTo"].includes(k)) ||
      !ID.test(item.id) || seen.has(item.id) || RESERVED.has(item.id) ||
      typeof item.title!=="string" || item.title.trim().length<2 ||
      item.title.length>64 || /[<>]/.test(item.title) ||
      !["movie","tv"].includes(item.kind) ||
      typeof item.enabled!=="boolean" ||
      !Number.isSafeInteger(item.order) || item.order<0 || item.order>=MAX) {
      throw Error("invalid_catalog_shelf");
    }
    if (item.language!==undefined && (typeof item.language!=="string" || !LANGUAGE.test(item.language))) {
      throw Error("invalid_catalog_language");
    }
    const yearKeys=["yearFrom","yearTo"];
    for (const key of yearKeys) {
      if (item[key]!==undefined && (!Number.isSafeInteger(item[key]) ||
          item[key]<1888 || item[key]>2100)) throw Error("invalid_catalog_year");
    }
    if (item.yearFrom!==undefined && item.yearTo!==undefined &&
        item.yearFrom>item.yearTo) throw Error("invalid_catalog_year_range");
    const provider=item.providerId;
    const genres=item.genres;
    if ((provider===undefined)===(genres===undefined) ||
      (provider!==undefined && (!PROVIDER.test(String(provider)) || !REGION.test(item.region))) ||
      (genres!==undefined && (!GENRES.test(genres) || item.region!==undefined))) {
      throw Error("invalid_catalog_filter");
    }
    seen.add(item.id);
    return Object.freeze({
      id:item.id,title:item.title.trim(),kind:item.kind,
      enabled:item.enabled,order:item.order,
      ...(provider!==undefined?{providerId:String(provider),region:item.region}:{genres}),
      ...(item.language!==undefined?{language:item.language}:{}),
      ...(item.yearFrom!==undefined?{yearFrom:item.yearFrom}:{}),
      ...(item.yearTo!==undefined?{yearTo:item.yearTo}:{}),
    });
  }).sort((a,b)=>a.order-b.order||a.id.localeCompare(b.id));
}
export function publicCatalogShelves(input) {
  return {version:1,shelves:validateCatalogShelves(input).filter(x=>x.enabled)};
}
