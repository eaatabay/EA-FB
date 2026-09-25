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
