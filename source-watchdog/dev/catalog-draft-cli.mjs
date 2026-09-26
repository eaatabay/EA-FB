import {readFile,writeFile,rename,unlink,open} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {resolve,dirname,basename,join} from "node:path";
import {buildCatalogDraft,editCatalogDraft} from "../src/catalog-shelves.mjs";
import {renderCatalogDraftPreview} from "../src/catalog-admin-preview.mjs";

/** Local-only editor; never touches production Worker, D1 or source grants. */
export async function applyLocalCatalogEdit(file,command) {
  const absolute=resolve(file);
  if (!absolute.endsWith(".catalog-draft.json")) throw Error("draft_filename_required");
  const lock=absolute+".lock";
  // Exclusive local lock closes the read/CAS/write race between two editors.
  const handle=await open(lock,"wx",0o600);
  try {
    const draft=JSON.parse(await readFile(absolute,"utf8"));
    if (Object.keys(draft).sort().join(",")!=="revision,shelves,status,version" ||
        draft.status!=="draft-v6-not-published" || draft.version!==1)
      throw Error("invalid_draft_envelope");
    const current=buildCatalogDraft(draft.shelves,draft.revision);
    const updated=editCatalogDraft(current,command.expectedRevision,command.operation);
    const target=join(dirname(absolute),"."+basename(absolute)+".tmp-"+process.pid);
    try {
      await writeFile(target,JSON.stringify(updated,null,2)+"\n",{flag:"wx",mode:0o600});
      await rename(target,absolute);
    } catch(error) {
      await unlink(target).catch(()=>{});
      throw error;
    }
    return updated;
  } finally {
    await handle.close();
    await unlink(lock).catch(()=>{});
  }
}

/** Create a new local unpublished draft; refuse overwriting any existing file. */
export async function initLocalCatalogDraft(file,shelves=[]) {
  const absolute=resolve(file);
  if (!absolute.endsWith(".catalog-draft.json")) throw Error("draft_filename_required");
  const draft=buildCatalogDraft(shelves,0);
  await writeFile(absolute,JSON.stringify(draft,null,2)+"\n",{flag:"wx",mode:0o600});
  return draft;
}

export async function previewLocalCatalogDraft(file) {
  const draft=JSON.parse(await readFile(resolve(file),"utf8"));
  if (Object.keys(draft).sort().join(",")!=="revision,shelves,status,version" ||
      draft.version!==1 || draft.status!=="draft-v6-not-published")
    throw Error("not_an_unpublished_draft");
  return renderCatalogDraftPreview(draft.shelves,draft.revision);
}

if (process.argv[1] && resolve(process.argv[1])===resolve(fileURLToPath(import.meta.url))) {
  const [action,file,arg]=process.argv.slice(2);
  try {
    if (action==="init" && file && !arg) {
      const draft=await initLocalCatalogDraft(file);
      process.stdout.write("Created unpublished catalog draft revision "+draft.revision+"\n");
    } else if (action==="preview" && file && !arg) {
      process.stdout.write(await previewLocalCatalogDraft(file));
    } else if (action==="edit" && file && arg) {
      const result=await applyLocalCatalogEdit(file,JSON.parse(await readFile(resolve(arg),"utf8")));
      process.stdout.write("Updated local unpublished catalog draft to revision "+result.revision+"\n");
    } else throw Error("usage: node dev/catalog-draft-cli.mjs init FILE | preview FILE | edit FILE COMMAND.json");
  } catch(error) {
    process.stderr.write(String(error.message)+"\n");
    process.exitCode=1;
  }
}
