import test from "node:test";
import assert from "node:assert/strict";
import {createWatchdogWorker} from "../src/worker.mjs";

test("central playback success is not a public read/write API",async()=>{
  const worker=createWatchdogWorker({logger:{warn(){},info(){}}});
  for(const path of ["/v1/playback","/v1/playback/candidates",
    "/v1/playback/success","/v1/playback/failure",
    "/playback/success","/playback/candidates"]) {
    const get=await worker.fetch(new Request("https://watchdog.example.org"+path));
    assert.equal(get.status,404,path+" GET");
    const post=await worker.fetch(new Request("https://watchdog.example.org"+path,{
      method:"POST",headers:{"content-type":"application/json"},
      body:JSON.stringify({outcome:"success"}),
    }));
    assert.equal(post.status,405,path+" POST");
  }
});

test("playback service does not embed public endpoints or streaming credentials",async()=>{
  const {readFileSync}=await import("node:fs");
  const {fileURLToPath}=await import("node:url");
  const {dirname,join}=await import("node:path");
  const root=join(dirname(fileURLToPath(import.meta.url)),"../src");
  for(const name of ["playback-success.mjs",
    "playback-candidate-service.mjs","trusted-playback-recorder.mjs"]) {
    const text=readFileSync(join(root,name),"utf8");
    assert.ok(!text.includes("Authorization: Bearer "));
    assert.ok(!text.includes("api.themoviedb.org/3/movie/"));
    assert.ok(!text.includes("http://"));
  }
});
