import test from 'node:test';
import assert from 'node:assert/strict';
import {playbackKey,recordPlaybackSuccess,findPlaybackCandidates}
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
