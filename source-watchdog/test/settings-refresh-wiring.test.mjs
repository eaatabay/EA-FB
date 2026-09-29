import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const dialog=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/EASettingsDialog.kt",import.meta.url),"utf8");
const provider=readFileSync(new URL("../../EA-FB/src/main/kotlin/com/eafb/EAProvider.kt",import.meta.url),"utf8");
test("v6 settings changes force CloudStream home reload once",()=>{
  assert.match(dialog,/dialog\.setOnDismissListener\s*\{/);
  assert.match(dialog,/if \(dirty && !refreshRequested\) MainActivity\.reloadHomeEvent\(true\)/);
  assert.match(dialog,/refreshRequested = true\s*\n\s*dialog\.dismiss\(\)\s*\n\s*MainActivity\.reloadHomeEvent\(true\)/);
  assert.match(dialog,/categoryRow\(ctx, cat\) \{ dirty = true \}/);
  assert.match(dialog,/EASettings\.setSortMode\(mode\)\s*\n\s*dirty = true/);
  assert.doesNotMatch(dialog,/\.recreate\(\)/);
});
test("newest date filtering never blanks native trending or top-rated rails",()=>{
  assert.match(provider,/sortMode == CatalogSortMode\.NEWEST &&\s*category\.tmdbPath\?\.startsWith\("\/discover\/"\) == true/);
  assert.match(provider,/CatalogPagePolicy\.extraNewestPages/);
  assert.match(provider,/CatalogPagePolicy\.allowNextPage/);
  assert.match(provider,/scannedExtraPages/);
});
