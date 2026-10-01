/**
 * EA-FB navy/yellow read-only admin health page. HTML-escape ALL D1 fields.
 * No scripts, forms, editable source URLs or credential display.
 */
const escapeHtml=value=>String(value??"").replace(/[&<>"']/g,x=>({
  "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;",
}[x]));
const knownStates=["healthy","degraded","quarantined","admin_required","disabled","invalid_state"];
const knownErrors=new Set([
 "unreachable","unapproved_redirect","structural_change",
 "identity_unverified","functional_check_failed","unapproved_or_invalid_target",
 "adapter_error","probe_timeout",
]);
function utc(time) {
  if(!Number.isFinite(time)||time<0||time>8640000000000000) return "—";
  return new Date(time).toISOString().replace("T"," ").slice(0,16)+" UTC";
}

export function summarizeSources(records,now) {
  if(!Array.isArray(records)||!Number.isSafeInteger(now)||now<0) {
    throw Error("invalid_overview");
  }
  const counts=Object.fromEntries(knownStates.map(x=>[x,0]));
  const rows=[];
  for(const record of records) {
    const config=record?.config,state=record?.state;
    if(!config||!state||record.id!==config.id||record.id!==state.id) {
      throw Error("corrupt_overview_record");
    }
    // Corrupt/future states must not masquerade as an ordinary degraded source.
    const status=knownStates.includes(state.status)?state.status:"invalid_state";
    counts[status]++;
    rows.push({
      id:String(config.id).slice(0,64),
      status,
      revision:Number.isSafeInteger(record.revision) && record.revision >= 0
        ? record.revision : null,
      url:String(config.currentUrl??"").slice(0,2048),
      last:utc(state.lastCheckedAt),
      next:utc(state.nextCheckAt),
      error:knownErrors.has(state.lastFailure)?state.lastFailure:"—",
    });
  }
  rows.sort((a,b)=>a.id.localeCompare(b.id));
  return {counts,rows,generatedAt:utc(now)};
}

export function renderAdminDashboard(data, {editable = false, nonce = ""} = {}) {
  const stats=Object.entries(data.counts).map(([key,count])=>
    "<div class='metric'><strong>"+Number(count)+"</strong><span>"+
      escapeHtml(key)+"</span></div>").join("");
  const cells=data.rows.map(row=>"<tr>"+[
    row.id,row.status,row.url,row.last,row.next,row.error
  ].map(v=>"<td>"+escapeHtml(v)+"</td>").join("")+
    (editable ? "<td>"+(row.revision===null ? "—" :
      ["disable","enable","retest","rollback"].map(action=>
        "<button type='button' data-action='"+action+"' data-source='"+
        escapeHtml(row.id)+"' data-revision='"+row.revision+"'>"+
        ({disable:"Kapat",enable:"Aç",retest:"Yeniden test",rollback:"Geri al"}[action])+
        "</button>").join(" "))+"</td>" : "")+"</tr>").join("");
  const css=[
    ":root{color-scheme:dark;font:16px system-ui,Arial;color:#f0f4ff;background:#081b36}",
    "*{box-sizing:border-box}main{max-width:1280px;margin:auto;padding:28px 20px}",
    "h1{color:#f4cb36;margin:0 0 8px}.sub{color:#b5c4df;margin-bottom:24px}",
    ".metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px}",
    ".metric{border:1px solid #305780;border-radius:12px;background:#102a50;padding:16px}",
    ".metric strong{display:block;font-size:32px;color:#f4cb36}",
    ".metric span{color:#b5c4df}.wrap{overflow-x:auto;margin-top:24px}",
    "table{width:100%;border-collapse:collapse;background:#102a50;border-radius:10px}",
    "th,td{text-align:left;border-bottom:1px solid #305780;padding:12px}",
    "th{color:#f4cb36}td{overflow-wrap:anywhere}",
    "footer{color:#b5c4df;margin-top:24px;font-size:13px}",
    "button{background:#f4cb36;color:#081b36;border:0;border-radius:6px;padding:8px;margin:3px;cursor:pointer}",
    "button:disabled{opacity:.55;cursor:wait}.feedback{color:#f4cb36;min-height:24px}",
  ].join("");
  return "<!doctype html><html lang='tr'><head><meta charset='utf-8'>"+
    "<meta name='viewport' content='width=device-width,initial-scale=1'>"+
    "<title>EA-FB Kaynak Bekçisi</title><style>"+css+"</style></head>"+
    "<body><main><h1>EA-FB Kaynak Bekçisi</h1>"+
    "<p class='sub'>Salt okunur sağlık paneli · "+escapeHtml(data.generatedAt)+"</p>"+
    "<section class='metrics' aria-label='Kaynak durumları'>"+stats+"</section>"+
    "<div class='wrap'><table><thead><tr><th>Kaynak</th><th>Durum</th>"+
    "<th>Onaylı adres</th><th>Son kontrol</th><th>Sonraki kontrol</th>"+
    "<th>Hata</th>"+(editable?"<th>İşlem</th>":"")+
    "</tr></thead><tbody>"+cells+"</tbody></table></div>"+
    (editable ? "<p id='feedback' class='feedback' role='status' aria-live='polite'></p>" :
      "<footer>Bu panel salt okunurdur; kaynak ayarlarını değiştirmez.</footer>")+
    (editable ? "<script nonce='"+escapeHtml(nonce)+"'>"+ADMIN_SCRIPT+"</script>" : "")+
    "</main></body></html>";
}

/** Static code only; untrusted registry fields never enter JavaScript source. */
const ADMIN_SCRIPT = `document.addEventListener("click",async event=>{
  const button=event.target.closest("button[data-action]");
  if(!button||button.disabled)return;
  const id=button.dataset.source, action=button.dataset.action;
  const revision=Number(button.dataset.revision);
  const message=document.getElementById("feedback");
  if(!/^[a-z][a-z0-9-]{2,63}$/.test(id)||
     !["disable","enable","retest","rollback"].includes(action)||
     !Number.isSafeInteger(revision)||revision<0)return;
  const label={disable:"kapatmak",enable:"açmak",retest:"yeniden test etmek",
    rollback:"son sağlıklı adrese geri almak"}[action];
  if(!window.confirm(id+" kaynağını "+label+" istiyor musun?"))return;
  button.disabled=true;message.textContent="İşlem uygulanıyor…";
  try{
    const res=await fetch("/admin/api/sources/"+encodeURIComponent(id),{
      method:"POST",credentials:"same-origin",
      headers:{"content-type":"application/json","x-eafb-admin-action":"confirmed"},
      body:JSON.stringify({action,expectedRevision:revision})
    });
    const result=await res.json();
    if(!res.ok)throw Error(result.error||"admin_unavailable");
    message.textContent=id+": işlem kaydedildi; panel yenileniyor.";
    window.location.reload();
  }catch(error){
    message.textContent=id+": "+(error.message==="revision_conflict"?
      "Başka işlem yapıldı; sayfayı yenile.":"İşlem başarısız: "+error.message);
    button.disabled=false;
  }
});`;
