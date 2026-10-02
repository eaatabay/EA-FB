import test from "node:test";
import assert from "node:assert/strict";
import { orderPlaybackOffers } from "../src/playback-candidate-service.mjs";

const db = { prepare() {} };
const media = { kind:"series", tmdbId:42, season:3, episode:2 };
const record = (id, opts={}) => ({
  id,
  config: {
    id, enabled:true, integrationApproved:true, mediaKind:"both",
    ...opts.config,
  },
  state:{ id, status:"healthy", ...opts.state },
});
const offer = (id, variant="tr") => ({
  sourceId:id, variantId:variant, approved:true, healthy:true,
});
const history = [
  {sourceId:"source-b",variantId:"tr"},
  {sourceId:"source-a",variantId:"tr"},
];

test("no release-approved grants means no registry/history read", async () => {
  let reads=0;
  assert.deepEqual(await orderPlaybackOffers({
    db,media,offers:[offer("source-a")],nowMs:1000,
    readRegistry:async()=>{reads++;return [];},
    readHistory:async()=>{reads++;return history;},
  }),[]);
  assert.equal(reads,0);
});

test("history prioritizes only healthy, rights-reviewed registry sources", async () => {
  const offers=[
    offer("source-a","original"),
    offer("source-b"),
    offer("source-a"),
    offer("source-disabled"),
    offer("source-unapproved"),
    offer("source-sick"),
    offer("fixture-demo"),
    offer("source-movie"),
  ];
  let readCount=0;
  const result=await orderPlaybackOffers({
    db,media,offers,nowMs:1000,
    approvedSourceIds:[...new Set(offers.map(x=>x.sourceId))],
    readRegistry:async()=>[
      record("source-a"),record("source-b"),
      record("source-disabled",{config:{enabled:false}}),
      record("source-unapproved",{config:{integrationApproved:false}}),
      record("source-sick",{state:{status:"degraded"}}),
      record("fixture-demo"),
      record("source-movie",{config:{mediaKind:"movie"}}),
    ],
    readHistory:async()=>{readCount++;return history;},
  });
  assert.deepEqual(result.map(x=>x.sourceId+":"+x.variantId),[
    "source-b:tr","source-a:tr","source-a:original",
  ]);
  assert.equal(readCount,1);
});

test("all unhealthy sources fail closed before history lookup", async () => {
  let reads=0;
  assert.deepEqual(await orderPlaybackOffers({
    db,media,offers:[offer("source-a")],nowMs:1000,
    approvedSourceIds:["source-a"],
    readRegistry:async()=>[record("source-a",{state:{status:"quarantined"}})],
    readHistory:async()=>{reads++;return history;},
  }),[]);
  assert.equal(reads,0);
});

test("invalid arguments and duplicate rights IDs are rejected", async () => {
  await assert.rejects(orderPlaybackOffers({
    db,media,offers:[],nowMs:1000,approvedSourceIds:["source-a","source-a"],
  }),/invalid_playback_lookup/);
  await assert.rejects(orderPlaybackOffers({
    db,media,offers:[],nowMs:-1,approvedSourceIds:["source-a"],
  }),/invalid_playback_lookup/);
});
