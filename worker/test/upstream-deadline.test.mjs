import test from "node:test";
import assert from "node:assert/strict";
import {withUpstreamDeadline} from "../src/upstream-deadline.mjs";
import {readBoundedText} from "../src/bounded-response.mjs";

test("one deadline covers fast headers and slow body", async () => {
  let aborted=false;
  const start=Date.now();
  await assert.rejects(withUpstreamDeadline(async signal => {
    signal.addEventListener("abort",()=>{aborted=true;},{once:true});
    await Promise.resolve({status:200});
    return new Promise(()=>{}); // headers delivered, body never finishes
  },100), err=>err.name==="TimeoutError" &&
    err.message==="upstream_deadline_exceeded");
  assert.equal(aborted,true);
  assert.ok(Date.now()-start<2000);
});

test("timeout beats a synchronous abort listener that returns fake success",async()=>{
  const result=withUpstreamDeadline(signal=>new Promise(resolve=>{
    signal.addEventListener("abort",()=>resolve("late-healthy"),{once:true});
  }),100);
  await assert.rejects(result,{name:"TimeoutError"});
});

test("fast task resolves and clears its timer",async()=>{
  assert.deepEqual(await withUpstreamDeadline(async signal=>({
    ready:true,aborted:signal.aborted
  }),100),{ready:true,aborted:false});
});

test("exceptions remain original, including byte-budget violations",async()=>{
  await assert.rejects(withUpstreamDeadline(async()=>{
    throw new Error("upstream_too_large");
  },100),/upstream_too_large/);
});

test("invalid timeouts and callbacks are rejected",async()=>{
  for(const timeout of [0,-1,99,60001,NaN,Infinity,"100"]){
    await assert.rejects(withUpstreamDeadline(()=>42,timeout),
      /invalid_upstream_deadline/);
  }
  await assert.rejects(withUpstreamDeadline(null,100),
    /invalid_upstream_deadline/);
});

test("byte-budget rejection aborts the still-open upstream fetch",async()=>{
  let aborted=false;
  await assert.rejects(withUpstreamDeadline(signal=>{
    signal.addEventListener("abort",()=>{aborted=true;},{once:true});
    throw new Error("upstream_too_large");
  },100),/upstream_too_large/);
  assert.equal(aborted,true);
});

test("real bounded JSON stream that never closes obeys upstream deadline",async()=>{
  let aborted=false;
  const response=new Response(new ReadableStream({
    start(controller){controller.enqueue(new TextEncoder().encode("{\"id\":"));}
  }),{headers:{"content-type":"application/json"}});
  await assert.rejects(withUpstreamDeadline(signal=>{
    signal.addEventListener("abort",()=>{aborted=true;},{once:true});
    return readBoundedText(response,2_000_000);
  },100),{name:"TimeoutError"});
  assert.equal(aborted,true);
});

test("actual oversized body preserves size error and aborts upstream",async()=>{
  let aborted=false;
  const response=new Response(new ReadableStream({
    start(controller){controller.enqueue(new Uint8Array(101));controller.close();}
  }),{headers:{"content-type":"application/json","content-length":"1"}});
  await assert.rejects(withUpstreamDeadline(signal=>{
    signal.addEventListener("abort",()=>{aborted=true;},{once:true});
    return readBoundedText(response,100);
  },100),/upstream_too_large/);
  assert.equal(aborted,true);
});
