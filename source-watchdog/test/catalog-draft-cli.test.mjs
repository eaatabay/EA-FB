import test from "node:test";
import assert from "node:assert/strict";
import {mkdtemp,readFile,writeFile,rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {applyLocalCatalogEdit,previewLocalCatalogDraft} from "../dev/catalog-draft-cli.mjs";
test("local draft editor atomically applies CAS and renders safe preview",async()=>{
  const dir=await mkdtemp(join(tmpdir(),"eafb-catalog-"));
  try {
    const file=join(dir,"test.catalog-draft.json");
    const draft={version:1,status:"draft-v6-not-published",revision:0,
      shelves:[{id:"custom-tv",title:"Yeni Diziler",kind:"tv",genres:"18",
        enabled:true,order:0}]};
    await writeFile(file,JSON.stringify(draft));
    const updated=await applyLocalCatalogEdit(file,{expectedRevision:0,
      operation:{action:"rename",id:"custom-tv",title:"Yeni Türk Dizileri"}});
    assert.equal(updated.revision,1);
    assert.match(await previewLocalCatalogDraft(file),/Yeni Türk Dizileri/);
    await assert.rejects(applyLocalCatalogEdit(file,{expectedRevision:0,
      operation:{action:"remove",id:"custom-tv"}}),/catalog_revision_conflict/);
    assert.equal(JSON.parse(await readFile(file,"utf8")).shelves.length,1);
    await writeFile(file+".lock","held");
    await assert.rejects(applyLocalCatalogEdit(file,{expectedRevision:1,
      operation:{action:"remove",id:"custom-tv"}}),{code:"EEXIST"});
    await rm(file+".lock");
    await assert.rejects(applyLocalCatalogEdit(join(dir,"wrong.json"),{}),/draft_filename_required/);
  } finally {await rm(dir,{recursive:true,force:true});}
});
