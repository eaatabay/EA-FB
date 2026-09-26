import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const root = new URL("../../EA-FB/src/main/kotlin/com/eafb/", import.meta.url);
const bridge = readFileSync(new URL("WatchdogApprovedAdapterBridge.kt",root),"utf8");
const policy = readFileSync(new URL("ReviewedSourcePermitPolicy.kt",root),"utf8");

test("bridge gates signed offline snapshot through compiled rights before selection",()=>{
  assert.match(bridge,/ReviewedSourcePermitPolicy\.restrict\(\s*snapshot,\s*now,\s*ReviewedSourcePermits\.bundled\s*\)/);
  assert.match(bridge,/WatchdogAdapterSelection\.forNewSearch\(\s*reviewed,\s*kind,\s*now,\s*bundledAdapters\s*\)/);
  assert.doesNotMatch(bridge,/forNewSearch\(\s*snapshot,\s*kind/);
});
test("production permit list is empty and no runtime mutation API exists",()=>{
  assert.match(policy,/val bundled:\s*List<ReviewedSourcePermit>\s*=\s*emptyList\(\)/);
  assert.doesNotMatch(policy,/fun\s+(register|approve|addPermit)\s*\(/);
});

test("empty compiled rights fail before any offline cache read",()=>{
  const emptyGate = bridge.indexOf("ReviewedSourcePermits.bundled.isEmpty()) return emptyList()");
  const earlyGate = bridge.indexOf("if (kind == MediaKind.LIVE || now < 0L ||");
  assert.ok(earlyGate >= 0 && emptyGate > earlyGate,
    "LIVE, invalid clock and empty rights must share the early guard");
  const restore = bridge.indexOf("store.restoreVerifiedOffline(now)");
  assert.ok(emptyGate >= 0 && restore > emptyGate,
    "empty rights must return before restoring any cached source");
});

test("rights restriction must precede adapter selection in source order",()=>{
  const rights = bridge.indexOf("ReviewedSourcePermitPolicy.restrict(");
  const selection = bridge.indexOf("WatchdogAdapterSelection.forNewSearch(");
  assert.ok(rights >= 0 && selection > rights,
    "do not select an adapter before compiled rights are enforced");
});

test("LIVE and invalid timestamps cannot reach cached snapshot restoration",()=>{
  const earlyGate = bridge.indexOf("if (kind == MediaKind.LIVE || now < 0L ||");
  const restore = bridge.indexOf("store.restoreVerifiedOffline(now)");
  assert.ok(earlyGate >= 0 && restore > earlyGate,
    "unsupported media and invalid time must return before cache access");
});


test("future enabled bridge must propagate cancelled cache restoration",()=>{
 assert.match(bridge,/import kotlinx\.coroutines\.CancellationException/);
 assert.match(bridge,/catch \(cancelled: CancellationException\) \{ throw cancelled \}/);
});
