import {buildCatalogDraft} from "./catalog-shelves.mjs";

const esc=x=>String(x??"").replace(/[&<>"']/g,c=>({
  "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
}[c]));

/** Read-only admin preview. It never grants streaming rights or writes D1. */
export function renderCatalogDraftPreview(input,revision) {
  const draft=buildCatalogDraft(input,revision);
  const rows=draft.shelves.map(s=>"<tr>"+[
    s.order,s.title,s.kind,s.providerId===undefined?"Tür: "+s.genres:
      "Platform: "+s.providerId+" ("+s.region+")",
    s.language??"—",
    [s.yearFrom??"…",s.yearTo??"…"].join("–"),
    s.enabled?"Açık":"Kapalı"
  ].map(x=>"<td>"+esc(x)+"</td>").join("")+"</tr>").join("");
  return "<!doctype html><html lang='tr'><head><meta charset='utf-8'>"+
    "<meta name='viewport' content='width=device-width,initial-scale=1'>"+
    "<title>EA-FB v6 Katalog Taslağı</title>"+
    "<style>body{font:16px system-ui;background:#07162d;color:#eff5ff;padding:24px}"+
    "h1{color:#ffd000}table{border-collapse:collapse;width:100%}"+
    "th,td{padding:10px;border:1px solid #354e71;text-align:left}"+
    "th{color:#ffd000}p{color:#acbfd8}</style></head><body>"+
    "<h1>EA-FB Katalog Rafları</h1><p>Yalnızca önizleme · Taslak revizyon "+
    esc(draft.revision)+" · Yayınlanmadı · Oynatma izni vermez</p>"+
    "<table><thead><tr><th>Sıra</th><th>Başlık</th><th>Tür</th>"+
    "<th>Filtre</th><th>Dil</th><th>Yıl</th><th>Durum</th></tr></thead><tbody>"+
    rows+"</tbody></table></body></html>";
}
