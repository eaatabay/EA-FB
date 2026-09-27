import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const prod=JSON.parse(readFileSync(new URL("../../worker/wrangler.jsonc",import.meta.url)));
const stage=JSON.parse(readFileSync(new URL("../../worker/wrangler.staging.jsonc",import.meta.url)));
const smoke=readFileSync(new URL("../../scripts/smoke-v6-staging.sh",import.meta.url),"utf8");
test("V6 staging Worker cannot overwrite the production Worker",()=>{
  assert.equal(prod.name,"ea-fb-catalog");
  assert.equal(stage.name,"ea-fb-catalog-v6-staging");
  assert.notEqual(stage.name,prod.name);
  assert.equal(stage.workers_dev,true);
  assert.equal(stage.main,prod.main);
  assert.deepEqual(stage.ratelimits.map(x=>x.name).sort(),prod.ratelimits.map(x=>x.name).sort());
  const prodIds=new Set(prod.ratelimits.map(x=>x.namespace_id));
  assert.ok(stage.ratelimits.every(x=>!prodIds.has(x.namespace_id)),
    "staging rate limits must have isolated namespaces");
  assert.ok(!Object.keys(stage).some(x=>/d1_databases|routes|zone_id|vars/.test(x)),
    "no production D1, routes or inline secrets in staging config");
});
test("staging smoke script blocks production and only performs read-only GETs",()=>{
  assert.match(smoke,/BLOCKED: production Worker must never be used as staging/);
  assert.match(smoke,/https:\/\/ea-fb-catalog-v6-staging\.\*\.workers\.dev/);
  assert.match(smoke,/curl --silent --show-error/);
  assert.doesNotMatch(smoke,/wrangler deploy|--data-raw|--request POST/);
});
