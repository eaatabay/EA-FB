import test from "node:test";
import assert from "node:assert/strict";
import {renderCatalogDraftPreview} from "../src/catalog-admin-preview.mjs";
test("catalog preview escapes names and stays unpublished",()=>{
  const html=renderCatalogDraftPreview([{
    id:"custom-archive",title:"A & B \"Archive\"",kind:"movie",
    genres:"18",enabled:false,order:0
  }],7);
  assert.match(html,/A &amp; B &quot;Archive&quot;/);
  assert.match(html,/Taslak revizyon 7/);
  assert.match(html,/Yayınlanmadı/);
  assert.match(html,/Oynatma izni vermez/);
  assert.doesNotMatch(html,/<script|<form|<input/i);
});
test("catalog preview rejects markup and invalid draft",()=>{
  assert.throws(()=>renderCatalogDraftPreview([{
    id:"custom-archive",title:"<img onerror=alert(1)>",
    kind:"movie",genres:"18",enabled:true,order:0
  }],1));
});
