import test from "node:test";
import assert from "node:assert/strict";
import {pruneExpiredPlaybackCandidates,pruneExpiredObserverReceipts} from "../src/playback-retention.mjs";

test("expiry maintenance deletes at most a bounded batch",async()=>{
  let sql,args;
  const db={prepare(query){sql=query;return {bind(...params){args=params;
    return {async run(){return {meta:{changes:2}};}};}};}};
  assert.deepEqual(await pruneExpiredPlaybackCandidates(db,5000,250),
    {deleted:2});
  assert.match(sql,/DELETE FROM playback_success/);
  assert.match(sql,/expires_at_ms <= \?/);
  assert.match(sql,/LIMIT \?/);
  assert.deepEqual(args,[5000,250]);
});

test("retention is private, requires D1 and bounded valid clock",async()=>{
  for(const args of [
    [null,1000,250],[{},1000,250],[{prepare(){}},-1,250],
    [{prepare(){}},1000,0],[{prepare(){}},1000,1001],
    [{prepare(){}},NaN,250],
  ]) await assert.rejects(pruneExpiredPlaybackCandidates(...args),
    /invalid_playback_retention/);
});

test("expired observer receipts have bounded cleanup and no identity fields",async()=>{
  let sql,args;
  const db={prepare(query){sql=query;return {bind(...params){args=params;
    return {async run(){return {meta:{changes:3}};}};}};}};
  assert.deepEqual(await pruneExpiredObserverReceipts(db,5000,10),
    {deleted:3});
  assert.match(sql,/DELETE FROM playback_observer_receipts/);
  assert.match(sql,/expires_at_ms < \?/);
  assert.deepEqual(args,[5000,10]);
  await assert.rejects(pruneExpiredObserverReceipts(db,5000,1001),
    /invalid_observer_retention/);
});
