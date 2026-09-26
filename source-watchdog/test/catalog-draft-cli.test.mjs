import test from "node:test";
import assert from "node:assert/strict";
import {mkdtemp,readFile,writeFile,rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {applyLocalCatalogEdit,previewLocalCatalogDraft,initLocalCatalogDraft,compileLocalCatalogDraftPreview,diffLocalCatalogDrafts} from "../dev/catalog-draft-cli.mjs";
test("local draft editor atomically applies CAS and renders safe preview",async()=>{
  const dir=await mkdtemp(join(tmpdir(),"eafb-catalog-"));
  try {
    const file=join(dir,"test.catalog-draft.json");
    const draft={version:1,status:"draft-v6-not-published",revision:0,
      shelves:[{id:"custom-tv",title:"Yeni Diziler",kind:"tv",genres:"18",
        enabled:true,order:0}]};
    await writeFile(file,JSON.stringify(draft));
    const beforeFile=join(dir,"before.catalog-draft.json");
    await writeFile(beforeFile,JSON.stringify(draft));
    const updated=await applyLocalCatalogEdit(file,{expectedRevision:0,
      operation:{action:"rename",id:"custom-tv",title:"Yeni Türk Dizileri"}});
    assert.equal(updated.revision,1);
    const diff=await diffLocalCatalogDrafts(beforeFile,file);
    assert.equal(diff.status,"review-only");
    assert.equal(diff.changes[0].field,"title");
    assert.match(await previewLocalCatalogDraft(file),/Yeni Türk Dizileri/);
    const compiled=await compileLocalCatalogDraftPreview(file);
    assert.equal(compiled.status,"preview-v6-not-published");
    assert.equal(compiled.shelves[0].path,"/discover/tv?with_genres=18");
    await assert.rejects(applyLocalCatalogEdit(file,{expectedRevision:0,
      operation:{action:"remove",id:"custom-tv"}}),/catalog_revision_conflict/);
    assert.equal(JSON.parse(await readFile(file,"utf8")).shelves.length,1);
    await writeFile(file+".lock","held");
    await assert.rejects(applyLocalCatalogEdit(file,{expectedRevision:1,
      operation:{action:"remove",id:"custom-tv"}}),{code:"EEXIST"});
    await rm(file+".lock");
    const original=JSON.parse(await readFile(file,"utf8"));
    await writeFile(file,JSON.stringify({...original,playbackGrant:true}));
    await assert.rejects(previewLocalCatalogDraft(file),/not_an_unpublished_draft/);
    await assert.rejects(compileLocalCatalogDraftPreview(file),/invalid_draft_envelope/);
    await assert.rejects(applyLocalCatalogEdit(file,{expectedRevision:1,
      operation:{action:"remove",id:"custom-tv"}}),/invalid_draft_envelope/);
    await writeFile(file,JSON.stringify(original));
    await assert.rejects(applyLocalCatalogEdit(join(dir,"wrong.json"),{}),/draft_filename_required/);
  } finally {await rm(dir,{recursive:true,force:true});}
});

test("local draft initialization is create-only and private",async()=>{
  const dir=await mkdtemp(join(tmpdir(),"eafb-catalog-init-"));
  try {
    const file=join(dir,"new.catalog-draft.json");
    const draft=await initLocalCatalogDraft(file);
    assert.equal(draft.revision,0);
    assert.equal(draft.shelves.length,0);
    assert.equal(JSON.parse(await readFile(file,"utf8")).status,"draft-v6-not-published");
    await assert.rejects(initLocalCatalogDraft(file),{code:"EEXIST"});
    await assert.rejects(initLocalCatalogDraft(join(dir,"other.json")),/draft_filename_required/);
  } finally {await rm(dir,{recursive:true,force:true});}
});
