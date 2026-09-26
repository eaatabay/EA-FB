import test from "node:test";
import assert from "node:assert/strict";
import {parseOmdbRating,enrichmentCacheTtl} from "../src/ratings-enrichment.mjs";

const id="tt14688458";
const good=(rating="8.1")=>({Response:"True",imdbID:id,imdbRating:rating});
test("real OMDb score and genuine N/A have different safe outcomes",()=>{
  assert.deepEqual(parseOmdbRating(good(),id),{settled:true,rating:8.1});
  assert.deepEqual(parseOmdbRating(good("10.0"),id),{settled:true,rating:10});
  assert.deepEqual(parseOmdbRating(good("1"),id),{settled:true,rating:1});
  assert.deepEqual(parseOmdbRating(good("N/A"),id),{settled:true,rating:null});
});
test("malformed or wrong-identity OMDb responses are not trusted",()=>{
  const cases=[null,[],{},good(""),good("0"),good("11"),good("-1"),
    good("8,1"),good("8.1<script>"),good("8e0"),good("Infinity"),
    good("NaN"),good(8.1),good(null),{...good(),Response:"False"},
    {...good(),imdbID:"tt00000001"},{...good(),imdbID:null}];
  for(const value of cases)assert.deepEqual(parseOmdbRating(value,id),
    {settled:false,rating:null},JSON.stringify(value));
  assert.deepEqual(parseOmdbRating(good(),"invalid-id"),
    {settled:false,rating:null});
});
test("transient failure gets short cache, valid score and N/A keep normal TTL",()=>{
  assert.equal(enrichmentCacheTtl(true,false,3600),60);
  assert.equal(enrichmentCacheTtl(true,true,3600),3600);
  assert.equal(enrichmentCacheTtl(false,false,21600),21600);
  assert.equal(enrichmentCacheTtl(false,true,1800),1800);
  for(const ttl of [-1,0,59,86_401,NaN,"3600"]) {
    assert.throws(()=>enrichmentCacheTtl(true,false,ttl),
      /invalid_enrichment_cache_policy/);
  }
  assert.throws(()=>enrichmentCacheTtl("true",false,3600));
});
