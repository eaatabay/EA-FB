import test from "node:test";
import assert from "node:assert/strict";
import {readBoundedText} from "../src/bounded-response.mjs";

test("Content-Length budget rejects before reading any bytes",async()=>{
  const response=new Response(new ReadableStream({
    pull(controller){controller.enqueue(new Uint8Array(1));controller.close();},
  }),{headers:{"content-length":"2000001"}});
  await assert.rejects(readBoundedText(response,2_000_000),/upstream_too_large/);
  // A ReadableStream can prefetch on construction; rejection must not depend on reading it.
  assert.equal(response.body.locked,false);
});

test("lying or missing length cannot bypass the actual byte budget",async()=>{
  for(const headers of [{},{ "content-length":"1"}]){
    const response=new Response(new ReadableStream({
      start(c){c.enqueue(new Uint8Array(100));c.enqueue(new Uint8Array(1));c.close();},
    }),{headers});
    await assert.rejects(readBoundedText(response,100),/upstream_too_large/);
  }
});

test("exact byte limit accepts valid multibyte UTF-8",async()=>{
  const bytes=new TextEncoder().encode('{"title":"İstanbul"}');
  assert.equal(await readBoundedText(new Response(bytes),bytes.length),
    '{"title":"İstanbul"}');
  await assert.rejects(readBoundedText(new Response(bytes),bytes.length-1),
    /upstream_too_large/);
});

test("malformed UTF-8 and empty bodies are rejected",async()=>{
  await assert.rejects(readBoundedText(new Response(Uint8Array.of(0xff)),50));
  await assert.rejects(readBoundedText(new Response(null),50),/upstream_empty/);
  await assert.rejects(readBoundedText(new Response("{}"),0),
    /invalid_upstream_budget/);
});

test("throwing stream cancellation never masks the size-limit error",async()=>{
  const response=new Response(new ReadableStream({
    start(c){c.enqueue(new Uint8Array(101));},
    cancel(){throw Error("UNTRUSTED_CANCEL_ERROR");},
  }));
  await assert.rejects(readBoundedText(response,100),/upstream_too_large/);
});

test("never-settling cancel cannot stall an oversized upstream response",async()=>{
  let cancelCalled=false;
  const response=new Response(new ReadableStream({
    start(c){c.enqueue(new Uint8Array(101));},
    cancel(){cancelCalled=true;return new Promise(()=>{});},
  }));
  const outcome=await Promise.race([
    readBoundedText(response,100).then(()=> "unexpected_success",
      e=>e.message),
    new Promise(resolve=>setTimeout(()=>resolve("hung_cancel"),150)),
  ]);
  assert.equal(outcome,"upstream_too_large");
  assert.equal(cancelCalled,true);
});
