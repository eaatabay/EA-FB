import test from "node:test";
import assert from "node:assert/strict";
import { validAdapterProbe,sanitizeAdapterProbe } from "../src/adapter-contract.mjs";

const config={requiredChecks:["reachability","search","detail","episode","playback"]};
const good=()=>({reached:true,finalUrl:"https://demo.example.org",
  identityVerified:true,checks:{search:true,detail:true,episode:true,playback:true}});

test("complete healthy, failed and structural fixture probes pass schema",()=>{
  assert.equal(validAdapterProbe(good(),config),true);
  assert.equal(validAdapterProbe({...good(),reached:false,identityVerified:false,
    checks:{search:false,detail:false,episode:false,playback:false}},config),true);
  assert.equal(validAdapterProbe({...good(),structuralChange:true},config),true);
  assert.equal(validAdapterProbe({...good(),checks:{...good().checks,episode:false}},config),true);
});

test("parser schema drift fails closed for missing, truthy and unexpected fields",()=>{
  const cases=[
    null,[],{}, {reached:true}, {...good(),checks:null},
    {...good(),reached:"true"}, {...good(),identityVerified:1},
    {...good(),finalUrl:null}, {...good(),structuralChange:"false"},
    {...good(),checks:{...good().checks,episode:undefined}},
    {...good(),checks:{...good().checks,search:"true"}},
    {...good(),checks:{...good().checks,unexpected:true}},
    {...good(),runnerFailure:"probe_timeout"},
    {...good(),cookie:"do-not-persist"},
    {...good(),checks:[]},
    Object.assign(Object.create({runnerFailure:"probe_timeout"}),good()),
  ];
  for(const probe of cases)assert.equal(validAdapterProbe(probe,config),false,
    JSON.stringify(probe));
});

test("plain or null-prototype fixtures pass, class/proxy-like prototypes fail",()=>{
  assert.equal(validAdapterProbe({...good(),checks:Object.assign(
    Object.create(null),good().checks)},config),true);
  assert.equal(validAdapterProbe(Object.assign(Object.create(null),good()),config),true);
  const inherited=Object.assign(Object.create({reached:true}),good());
  assert.equal(validAdapterProbe(inherited,config),false);
  class Probe {constructor(){Object.assign(this,good());}}
  assert.equal(validAdapterProbe(new Probe(),config),false);
});

test("movie-only adapters need not return episode/playback but still require search/detail",()=>{
  const movie={requiredChecks:["reachability","search","detail"]};
  const probe={...good(),checks:{search:true,detail:true}};
  assert.equal(validAdapterProbe(probe,movie),true);
  assert.equal(validAdapterProbe(probe,config),false);
  assert.equal(validAdapterProbe(probe,{requiredChecks:"search"}),false);
});

test("sanitized probe snapshot is independent of adapter mutations",()=>{
  const raw=good();
  const safe=sanitizeAdapterProbe(raw,config);
  assert.ok(safe);
  assert.notEqual(safe,raw);
  assert.notEqual(safe.checks,raw.checks);
  raw.reached=false;raw.finalUrl="https://evil.example.org";
  raw.checks.search=false;
  assert.equal(safe.reached,true);
  assert.equal(safe.finalUrl,"https://demo.example.org");
  assert.equal(safe.checks.search,true);
});

test("getters, setters and symbol properties cannot cross the parser boundary",()=>{
  const getter={...good()};
  Object.defineProperty(getter,"reached",{enumerable:true,get(){
    throw Error("secret getter side effect");
  }});
  assert.equal(sanitizeAdapterProbe(getter,config),null);
  const nested={...good()};
  Object.defineProperty(nested.checks,"search",{enumerable:true,get(){return true}});
  assert.equal(sanitizeAdapterProbe(nested,config),null);
  const symbol={...good(),[Symbol("hidden")]:"secret"};
  assert.equal(sanitizeAdapterProbe(symbol,config),null);
  const clean=sanitizeAdapterProbe(good(),config);
  assert.deepEqual(clean,good());
});
