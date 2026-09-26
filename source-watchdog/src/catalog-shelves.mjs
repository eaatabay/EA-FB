/**
 * v6 catalog-only shelf contract. Deliberately separate from playback-source
 * grants: creating a TMDb shelf cannot authorize a stream or modify watchdog.
 */
const ID=/^[a-z][a-z0-9-]{2,47}$/;
const REGION=/^[A-Z]{2}$/;
const GENRES=/^\d{1,4}(,\d{1,4}){0,4}$/;
const PROVIDER=/^\d{1,6}$/;
const MAX=40;
export function validateCatalogShelves(input) {
  if (!Array.isArray(input) || input.length>MAX) throw Error("invalid_shelf_count");
  const seen=new Set();
  return input.map((item,index)=>{
    if (!item || typeof item!=="object" || Array.isArray(item) ||
      Object.keys(item).some(k=>!["id","title","kind","providerId","region","genres","enabled","order"].includes(k)) ||
      !ID.test(item.id) || seen.has(item.id) ||
      typeof item.title!=="string" || item.title.trim().length<2 ||
      item.title.length>64 || /[<>]/.test(item.title) ||
      !["movie","tv"].includes(item.kind) ||
      typeof item.enabled!=="boolean" ||
      !Number.isSafeInteger(item.order) || item.order<0 || item.order>=MAX) {
      throw Error("invalid_catalog_shelf");
    }
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
    });
  }).sort((a,b)=>a.order-b.order||a.id.localeCompare(b.id));
}
export function publicCatalogShelves(input) {
  return {version:1,shelves:validateCatalogShelves(input).filter(x=>x.enabled)};
}
