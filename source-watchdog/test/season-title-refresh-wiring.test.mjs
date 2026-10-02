import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const src = readFileSync(path.resolve(here, "../../EA-FB/src/main/kotlin/com/eafb/EpisodeTitleStyle.kt"), "utf8");

assert.match(src, /observeSeasonSelection\(root, fragment, activity\)/);
assert.match(src, /addOnGlobalLayoutListener\s*\{/);
assert.match(src, /seasonSignatures\[root\] == signature/);
assert.match(src, /if \(changed\) scheduleSeasonRenders\(root, fragment\)/);
assert.match(src, /listOf\(0L, 180L, 450L, 950L\)/);
assert.match(src, /fragment\.view === root && root\.isAttachedToWindow/);
assert.match(src, /if \(!observedSeasonRoots\.add\(root\)\) return/);
assert.match(src, /if \(!pendingSeasonRenders\.add\(root\)\) return/);
console.log("season refresh wiring: 8/8 static checks passed (runtime behavior not verified)");
