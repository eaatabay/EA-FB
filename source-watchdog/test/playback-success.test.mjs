import test from 'node:test';
import assert from 'node:assert/strict';
import {playbackKey,recordPlaybackSuccess,findPlaybackCandidates,
  expirePlaybackCandidate,prioritizeApprovedOffers}
  from '../src/playback-success.mjs';

test('movie and episode keys are separate and validated', () => {
  assert.deepEqual(playbackKey({kind:'movie',tmdbId:42}),
    {kind:'movie',tmdbId:42,season:-1,episode:-1});
  assert.deepEqual(playbackKey({kind:'series',tmdbId:42,season:3,episode:2}),
    {kind:'series',tmdbId:42,season:3,episode:2});
  for (const invalid of [
    {kind:'movie',tmdbId:0}, {kind:'movie',tmdbId:1,season:1},
    {kind:'series',tmdbId:1,season:1},
    {kind:'series',tmdbId:1,season:-1,episode:2},
    {kind:'series',tmdbId:1,season:1,episode:0},
    {kind:'live',tmdbId:1},
  ]) assert.throws(() => playbackKey(invalid), /invalid_playback_key/);
});

test('success write only accepts source identity, never a URL or session', async () => {
  let sql, args;
  const db={prepare(query){sql=query;return {bind(...values){args=values;return {
    async run(){return {meta:{changes:1}};}};}};}};
  await recordPlaybackSuccess(db,{kind:'series',tmdbId:123,season:3,episode:2},
    {sourceId:'source-a',variantId:'tr-1080',audioLanguage:'tr',quality:1080},
    1_000_000);
  assert.match(sql,/ON CONFLICT/);
  assert.deepEqual(args,[ 'series',123,3,2,'source-a','tr-1080','tr',1080,
    1_000_000,2_800_000 ]);
  assert.ok(!sql.includes('playback_url'));
  for (const invalid of [
    {sourceId:'https://example.com',variantId:'v1'},
    {sourceId:'source-a',variantId:'../token'},
    {sourceId:'source-a',variantId:'v1',audioLanguage:'cookie=abc'},
  ]) await assert.rejects(
    recordPlaybackSuccess(db,{kind:'movie',tmdbId:1},invalid,1_000),
    /invalid_source_identity/);
  await assert.rejects(recordPlaybackSuccess(db,{kind:'movie',tmdbId:1},
    {sourceId:'source-a',variantId:'v1'},1_000,7*60*60_000),
    /invalid_success_lifetime/);
});

test('read requires unexpired key-specific candidates and returns no URLs', async () => {
  let sql,args;
  const db={prepare(query){sql=query;return {bind(...values){args=values;return {
    async all(){return {results:[{source_id:'source-a',variant_id:'tr-1080',
      audio_language:'tr',quality:1080,confirmed_count:2,
      last_confirmed_at_ms:900}]};}};}};}};
  const results=await findPlaybackCandidates(db,
    {kind:'series',tmdbId:12,season:3,episode:2},1_000);
  assert.deepEqual(args,['series',12,3,2,1_000,5]);
  assert.match(sql,/expires_at_ms > \?/);
  assert.deepEqual(results,[{sourceId:'source-a',variantId:'tr-1080',
    audioLanguage:'tr',quality:1080,confirmedCount:2,lastConfirmedAtMs:900}]);
  assert.ok(!JSON.stringify(results).includes('url'));
  await assert.rejects(findPlaybackCandidates(db,{kind:'movie',tmdbId:1},
    1_000,21),/invalid_candidate_query/);
});

test('failed candidate expires only the matching source and episode', async () => {
  let sql, args;
  const db={prepare(query){sql=query;return {bind(...values){args=values;return {
    async run(){return {meta:{changes:1}};}};}};}};
  const result=await expirePlaybackCandidate(db,
    {kind:'series',tmdbId:44,season:3,episode:2},
    {sourceId:'source-a',variantId:'tr-1080'},2000);
  assert.deepEqual(result,{expired:true});
  assert.match(sql,/UPDATE playback_success/);
  assert.match(sql,/source_id = \?/);
  assert.deepEqual(args,[2000,'series',44,3,2,'source-a','tr-1080',2000,2000]);
});

test('prioritization respects registry approval, health and exact variant', () => {
  const offers=[
    {sourceId:'source-a',variantId:'original',approved:true,healthy:true},
    {sourceId:'source-b',variantId:'tr',approved:false,healthy:true},
    {sourceId:'source-c',variantId:'tr',approved:true,healthy:false},
    {sourceId:'source-a',variantId:'tr',approved:true,healthy:true},
    {sourceId:'source-d',variantId:'tr',approved:true,healthy:true},
  ];
  const history=[
    {sourceId:'source-b',variantId:'tr'},
    {sourceId:'source-a',variantId:'tr'},
    {sourceId:'source-c',variantId:'tr'},
  ];
  assert.deepEqual(prioritizeApprovedOffers(offers,history),[
    offers[3],offers[0],offers[4],
  ]);
  assert.deepEqual(prioritizeApprovedOffers(offers,[]),[
    offers[0],offers[3],offers[4],
  ]);
  assert.throws(()=>prioritizeApprovedOffers(null,[]),/invalid_candidate_lists/);
});

test('ignored stale confirmation must not report stored', async () => {
  const db={prepare(){return {bind(){return {async run(){
    return {meta:{changes:0}};
  }};}};}};
  assert.deepEqual(await recordPlaybackSuccess(db,
    {kind:'movie',tmdbId:12},
    {sourceId:'source-a',variantId:'tr'},1000),{stored:false});
});
