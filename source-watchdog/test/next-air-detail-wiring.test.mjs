import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {dirname,resolve} from "node:path";

const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const provider=readFileSync(resolve(root,
  "EA-FB/src/main/kotlin/com/eafb/EAProvider.kt"),"utf8");
const policy=readFileSync(resolve(root,
  "EA-FB/src/main/kotlin/com/eafb/EpisodeAirPolicy.kt"),"utf8");
const row=readFileSync(resolve(root,
  "EA-FB/src/main/kotlin/com/eafb/DetailMetaRow.kt"),"utf8");

test("next-air detail label is guarded by the same future-only date policy",()=>{
  assert.match(provider,/EpisodeAirPolicy\.nextAirDateLabel\(/);
  assert.match(policy,/parse\(date, nowMillis\) \?: return null/);
  assert.match(provider,/nextAiring = nextEpisode\(item, nextAir\)/);
});
test("recycled detail fragments clear a missing next airing instead of keeping stale text",()=>{
  assert.match(row,/if \(label == null\) \{/);
  assert.match(row,/findViewById<View>\(holderId\)\?\.visibility = View\.GONE/);
  assert.match(row,/findViewById<TextView>\(nextId\)\?\.apply \{/);
  assert.match(row,/text = ""/);
});
