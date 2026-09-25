/**
 * End-to-end LOCAL Wrangler+D1 smoke. NEVER uses Cloudflare credentials,
 * --remote, wrangler deploy, production DB IDs, or third-party probes.
 * Run from source-watchdog/: npm run test:wrangler-local
 */
import {execFileSync, spawn} from "node:child_process";
import {existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync} from "node:fs";
import {tmpdir} from "node:os";
import {join, resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {createServer} from "node:net";
import {verifyLocalConfig} from "./make-local-config.mjs";

const root = resolve(fileURLToPath(new URL("..", import.meta.url)));
const bin = resolve(root, "node_modules/.bin/wrangler");
const localConfig = resolve(root, "wrangler.local.jsonc");
const name = "ea-fb-watchdog-fixture-local";
const QUARTER = 15 * 60_000;
const persistDir = mkdtempSync(join(tmpdir(), "ea-fb-v6-d1-"));
const quietEnv = {
  ...process.env, CI:"true", NO_D1_WARNING:"true",
  WRANGLER_SEND_METRICS:"false",
};
// Even a future accidental CLI typo must not inherit account credentials.
for (const key of ["CLOUDFLARE_API_TOKEN","CLOUDFLARE_ACCOUNT_ID",
  "CF_API_TOKEN","CF_API_KEY","CF_EMAIL"]) delete quietEnv[key];
let worker;
let workerOutput = "";
let completed = false;
let steps = 0;

function assertSafeLocalConfig() {
  const tracked = JSON.parse(readFileSync(resolve(root,"wrangler.jsonc"),"utf8"));
  const local = JSON.parse(readFileSync(localConfig,"utf8"));
  if (tracked.d1_databases !== undefined ||
      tracked.workers_dev !== false ||
      verifyLocalConfig(tracked, local) !== true ||
      Object.values(tracked.vars ?? {}).some(v => v !== "false" && v !== "disabled") ||
      local.name !== "ea-fb-source-watchdog-local-fixtures" ||
      local.vars?.WATCHDOG_MODE !== "fixture" ||
      local.vars?.WATCHDOG_CRON_ENABLED !== "true" ||
      local.vars?.WATCHDOG_FIXTURE_ENABLED !== "true" ||
      local.vars?.WATCHDOG_ADMIN_ENABLED !== "false" ||
      local.vars?.WATCHDOG_ADMIN_WRITES_ENABLED !== "false" ||
      local.vars?.WATCHDOG_SNAPSHOT_ENABLED !== "false" ||
      local.workers_dev !== false ||
      local.d1_databases?.length !== 1 ||
      local.d1_databases[0].binding !== "SOURCES_DB" ||
      local.d1_databases[0].database_name !== name ||
      local.d1_databases[0].database_id !== "00000000-0000-0000-0000-000000000000") {
    throw new Error("unsafe_local_configuration");
  }
}
function wrangler(args, timeout=75_000) {
  if (args.includes("--remote") || args.includes("deploy") ||
      !args.includes("--local") || !args.includes("--config") ||
      !args.includes("wrangler.local.jsonc")) {
    throw new Error("remote_or_unscoped_wrangler_command_refused");
  }
  return execFileSync(bin, args, {
    cwd:root, env:quietEnv, timeout,
    encoding:"utf8",maxBuffer:1024*1024,
    stdio:["ignore","pipe","pipe"],
  });
}
function resultRows(raw) {
  // Wrangler --json returns a structured result; do not accept shell text
  // containing a coincidental success-looking word.
  const data = JSON.parse(raw);
  const sets = Array.isArray(data) ? data : [data];
  const first = sets.find(x => Array.isArray(x?.results));
  if (!first?.success || !first.results.length) {
    throw new Error("wrangler_json_result_missing");
  }
  return first.results;
}
function readStats() {
  const sql = [
    "SELECT COUNT(*) total,",
    "SUM(CASE WHEN json_extract(state_json,'$.status')='healthy' THEN 1 ELSE 0 END) healthy,",
    "SUM(CASE WHEN json_extract(state_json,'$.status')='admin_required' THEN 1 ELSE 0 END) admin_hold,",
    "SUM(CASE WHEN json_extract(config_json,'$.currentUrl')='https://moved.example.org' THEN 1 ELSE 0 END) moves",
    "FROM source_registry",
  ].join(" ");
  const rows = resultRows(wrangler(["d1","execute",name,"--local",
    "--config","wrangler.local.jsonc","--persist-to",persistDir,
    "--command",sql,"--json"]));
  const countRows = resultRows(wrangler(["d1","execute",name,"--local",
    "--config","wrangler.local.jsonc","--persist-to",persistDir,
    "--command","SELECT (SELECT revision FROM registry_meta WHERE singleton=1) revision, "+
      "(SELECT COUNT(*) FROM source_audit) audits, "+
      "(SELECT COUNT(*) FROM source_probe_runs) runs, "+
      "(SELECT COUNT(*) FROM source_probe_leases) leases","--json"]));
  return {...rows[0], ...countRows[0]};
}
async function unusedPort() {
  return new Promise((resolve,reject)=>{
    const s=createServer();
    s.once("error",reject);
    s.listen(0,"127.0.0.1",()=>{
      const port=s.address().port;
      s.close(()=>resolve(port));
    });
  });
}
async function waitUntilReady(base,timeoutMs=30_000) {
  const deadline=Date.now()+timeoutMs;
  while(Date.now()<deadline) {
    if(worker.exitCode!==null) throw new Error("local_worker_exited_early");
    try {
      const response=await fetch(base+"/health",{signal:AbortSignal.timeout(1800)});
      if(response.status===200) return;
    } catch { /* Wait for local-only workerd to start. */ }
    await new Promise(r=>setTimeout(r,250));
  }
  throw new Error("local_worker_startup_timeout");
}
async function runTick(base, time) {
  const url=base+"/cdn-cgi/local/scheduled?format=json&time="+time;
  const response=await fetch(url,{signal:AbortSignal.timeout(20000)});
  if(!response.ok) throw new Error("scheduled_local_http_"+response.status);
  const result=await response.json();
  if(result?.outcome!=="ok") throw new Error("scheduled_local_outcome_"+JSON.stringify(result));
  steps++;
}
async function shutdown() {
  if(!worker || worker.exitCode!==null) return;
  worker.kill("SIGTERM");
  await Promise.race([
    new Promise(resolve=>worker.once("exit",resolve)),
    new Promise(resolve=>setTimeout(()=>{worker.kill("SIGKILL");resolve();},4000)),
  ]);
}
try {
  if(!existsSync(bin)) throw new Error("wrangler_not_installed_run_npm_install");
  if(!existsSync(localConfig)) {
    execFileSync(process.execPath,["dev/make-local-config.mjs"],{
      cwd:root,env:quietEnv,timeout:10000,stdio:"pipe",
    });
  }
  assertSafeLocalConfig();
  wrangler(["d1","migrations","apply",name,"--local",
    "--config","wrangler.local.jsonc","--persist-to",persistDir]);
  mkdirSync(resolve(persistDir,"seed"),{recursive:true});
  const fixtureSql=execFileSync(process.execPath,["dev/generate-fixture-seed.mjs"],{
    cwd:root,encoding:"utf8",timeout:10000,
  });
  const seedPath=resolve(persistDir,"seed/fixtures.sql");
  writeFileSync(seedPath,fixtureSql,{mode:0o600});
  wrangler(["d1","execute",name,"--local","--config","wrangler.local.jsonc",
    "--persist-to",persistDir,"--file",seedPath]);
  const before=readStats();
  if(before.total!==30 || before.healthy!==0 || before.runs!==0 ||
     before.audits!==30 || before.revision!==30) {
    throw new Error("fixture_seed_disagrees_"+JSON.stringify(before));
  }
  steps++;
  const port=await unusedPort(),base="http://127.0.0.1:"+port;
  worker=spawn(bin,["dev","--local","--test-scheduled","--config",
    "wrangler.local.jsonc","--persist-to",persistDir,"--ip","127.0.0.1",
    "--port",String(port),"--log-level","error"],{
    cwd:root,env:quietEnv,stdio:["ignore","pipe","pipe"],
  });
  for(const stream of [worker.stdout,worker.stderr]) {
    stream.on("data",chunk=>{
      workerOutput=(workerOutput+chunk.toString()).slice(-5000);
    });
  }
  await waitUntilReady(base);
  for(let tick=0;tick<8;tick++) await runTick(base,tick*QUARTER);
  const after=readStats();
  if(after.total!==30 || after.healthy!==29 || after.admin_hold!==1 ||
     after.moves!==2 || after.runs!==59 || after.audits!==89 ||
     after.revision!==89 || after.leases!==0) {
    throw new Error("unexpected_fixture_reconciliation_"+JSON.stringify(after));
  }
  await runTick(base,7*QUARTER); // Duplicate scheduled-time replay
  const duplicate=readStats();
  if(duplicate.revision!==after.revision || duplicate.runs!==after.runs ||
     duplicate.audits!==after.audits || duplicate.leases!==0) {
    throw new Error("fixture_replay_changed_state_"+JSON.stringify(duplicate));
  }
  completed=true;
  console.log("PASS LOCAL Wrangler+D1: "+JSON.stringify({
    sources:after.total,healthy:after.healthy,adminHeld:after.admin_hold,
    approvedDomainMoves:after.moves,probes:after.runs,audits:after.audits,
    revision:after.revision,replayWrites:0,leases:after.leases,checks:steps,
  }));
} catch(error) {
  console.error("FAIL LOCAL Wrangler+D1:",error.message);
  if(workerOutput) console.error("Local Wrangler diagnostic tail:",workerOutput);
  process.exitCode=1;
} finally {
  await shutdown();
  rmSync(persistDir,{recursive:true,force:true});
  if(!completed) console.error("No remote D1, deployment, or third-party source was contacted.");
}
