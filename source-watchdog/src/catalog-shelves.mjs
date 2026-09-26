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
      typeof item.id!=="string" || !ID.test(item.id) || seen.has(item.id) || RESERVED.has(item.id) ||
      typeof item.title!=="string" || item.title.trim().length<2 ||
      item.title.length>64 || /[<>]/.test(item.title) ||
      [...item.title].some(ch=>ch.charCodeAt(0)<32 || ch.charCodeAt(0)===127) ||
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
/** Compile validated metadata shelves to a strictly allowlisted TMDb query.
 * Draft language/year filters are omitted until the metadata relay is released.
 */
export function compileCatalogShelf(shelf) {
  const [item]=validateCatalogShelves([shelf]);
  if (item.language!==undefined || item.yearFrom!==undefined ||
      item.yearTo!==undefined) throw Error("unsupported_catalog_filter");
  const kind=item.kind==="tv"?"tv":"movie";
  const q=new URLSearchParams();
  if (item.providerId!==undefined) {
    q.set("with_watch_providers",item.providerId);
    q.set("watch_region",item.region);
    q.set("with_watch_monetization_types","flatrate");
  } else q.set("with_genres",item.genres);
  return {id:item.id,title:item.title,kind:item.kind,enabled:item.enabled,
    order:item.order,path:"/discover/"+kind+"?"+q.toString()};
}
/** A deterministic draft artifact for an authenticated admin workflow.
 * Caller must explicitly publish through a separately reviewed metadata path.
 */
export function buildCatalogDraft(input,revision) {
  if (!Number.isSafeInteger(revision) || revision<0) throw Error("invalid_catalog_revision");
  const shelves=validateCatalogShelves(input);
  return Object.freeze({version:1,revision,status:"draft-v6-not-published",
    shelves:Object.freeze(shelves.map(x=>Object.freeze({...x})))});
}
/** Pure CAS editor for an authenticated, unpublished catalog draft.
 * No HTTP handler, D1 writes, source grants, or automatic publication.
 */
export function editCatalogDraft(draft,expectedRevision,operation) {
  if (!draft || draft.status!=="draft-v6-not-published" ||
      !Number.isSafeInteger(expectedRevision) || draft.revision!==expectedRevision)
    throw Error("catalog_revision_conflict");
  if (!operation || typeof operation!=="object" || Array.isArray(operation) ||
      !["add","replace","remove","move","toggle","rename"].includes(operation.action) ||
      typeof operation.id!=="string") throw Error("invalid_catalog_edit");
  const rows=validateCatalogShelves(draft.shelves).map(x=>({...x}));
  const index=rows.findIndex(x=>x.id===operation.id);
  const exact=(keys)=>Object.keys(operation).sort().join(",")===keys.sort().join(",");
  switch(operation.action) {
    case "add":
      if (!exact(["action","id","shelf"]) || index>=0 ||
          operation.shelf?.id!==operation.id) throw Error("invalid_catalog_edit");
      rows.push({...operation.shelf,order:rows.length});
      break;
    case "replace":
      if (!exact(["action","id","shelf"]) || index<0 ||
          operation.shelf?.id!==operation.id) throw Error("invalid_catalog_edit");
      rows[index]={...operation.shelf,order:rows[index].order};
      break;
    case "remove":
      if (!exact(["action","id"]) || index<0) throw Error("invalid_catalog_edit");
      rows.splice(index,1);
      break;
    case "move":
      if (!exact(["action","id","order"]) || index<0 ||
          !Number.isSafeInteger(operation.order) || operation.order<0 ||
          operation.order>=rows.length) throw Error("invalid_catalog_edit");
      {
        const [moving]=rows.splice(index,1);
        rows.splice(operation.order,0,moving);
        rows.forEach((x,i)=>x.order=i);
      }
      break;
    case "toggle":
      if (!exact(["action","id","enabled"]) || index<0 ||
          typeof operation.enabled!=="boolean") throw Error("invalid_catalog_edit");
      rows[index].enabled=operation.enabled;
      break;
    case "rename":
      if (!exact(["action","id","title"]) || index<0)
        throw Error("invalid_catalog_edit");
      rows[index].title=operation.title;
      break;
  }
  // Keep every edit's order canonical: no duplicate/sparse positions after
  // add, remove or replace. The client may send an order, but never use it to
  // silently reorder unrelated shelves outside an explicit move.
  const canonical=validateCatalogShelves(rows).map((item,index)=>({
    ...item,order:index
  }));
  return buildCatalogDraft(canonical,expectedRevision+1);
}
export function publicCatalogShelves(input) {
  return {version:1,shelves:validateCatalogShelves(input).filter(x=>x.enabled)};
}

/** Build a local, non-published preview of only relay-compatible enabled rails.
 * Never silently discard a constraint on an enabled rail.
 */
export function compileCatalogDraftPreview(draft) {
  if (!draft || draft.version!==1 || draft.status!=="draft-v6-not-published" ||
      Object.keys(draft).sort().join(",")!=="revision,shelves,status,version")
    throw Error("invalid_draft_envelope");
  const validated=buildCatalogDraft(draft.shelves,draft.revision);
  return Object.freeze({
    version:1,revision:validated.revision,status:"preview-v6-not-published",
    shelves:Object.freeze(validated.shelves.filter(x=>x.enabled)
      .map(x=>Object.freeze(compileCatalogShelf(x))))
  });
}
