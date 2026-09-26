import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";

const root=resolve(fileURLToPath(new URL("../../",import.meta.url)));
const provider=readFileSync(resolve(root,
 "EA-FB/src/main/kotlin/com/eafb/EAProvider.kt"),"utf8");

test("all three suspending catalog/live fetches preserve coroutine cancellation",()=>{
 assert.ok(provider.includes("import kotlinx.coroutines.CancellationException"));
 assert.equal((provider.match(/catch \(cancelled: CancellationException\) \{ throw cancelled \}/g)||[]).length,3);
 for(const url of ["channelsUrl","catalogConfigUrl","url"]){
  assert.ok(provider.includes("JSONObject(app.get("+url+").text)"),url);
 }
 assert.ok(!provider.includes("runCatching { JSONObject(app.get("));
});
