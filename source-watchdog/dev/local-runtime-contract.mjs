/**
 * Pure, locally testable safety boundary for Wrangler smoke execution.
 * No network, filesystem writes, credentials, or Cloudflare API calls.
 */
import {resolve, sep} from "node:path";

const DATABASE="ea-fb-watchdog-fixture-local";
const CONFIG="wrangler.local.jsonc";
const FLAGS=new Set(["--local","--json"]);
const VALUES=new Set(["--config","--persist-to","--command","--file"]);

// Restrict local D1 reads to the TWO immutable aggregate queries below.
// An accidental future dynamic string cannot inspect other local tables.
export const STATS_QUERY = [
  "SELECT COUNT(*) total,",
  "SUM(CASE WHEN json_extract(state_json,'$.status')='healthy' THEN 1 ELSE 0 END) healthy,",
  "SUM(CASE WHEN json_extract(state_json,'$.status')='admin_required' THEN 1 ELSE 0 END) admin_hold,",
  "SUM(CASE WHEN json_extract(config_json,'$.currentUrl')='https://moved.example.org' THEN 1 ELSE 0 END) moves",
  "FROM source_registry",
].join(" ");
export const COUNTERS_QUERY = "SELECT " +
  "(SELECT revision FROM registry_meta WHERE singleton=1) revision, " +
  "(SELECT COUNT(*) FROM source_audit) audits, " +
  "(SELECT COUNT(*) FROM source_probe_runs) runs, " +
  "(SELECT COUNT(*) FROM source_probe_leases) leases";

export function validateLocalWranglerArgs(args, persistDir) {
  if (!Array.isArray(args) || typeof persistDir !== "string" ||
      persistDir.length < 4 || !persistDir.startsWith("/") ||
      args[0] !== "d1") throw new Error("remote_or_unscoped_wrangler_command_refused");
  const migration=args[1]==="migrations" && args[2]==="apply";
  const execute=args[1]==="execute";
  if (!migration && !execute) {
    throw new Error("remote_or_unscoped_wrangler_command_refused");
  }
  const start=migration?3:2;
  if (args[start]!==DATABASE) throw new Error("unknown_local_database");
  const opts=new Map();
  for(let i=start+1;i<args.length;i++){
    const arg=args[i];
    if(FLAGS.has(arg)){
      if(opts.has(arg)) throw new Error("duplicate_wrangler_argument");
      opts.set(arg,true);
    }else if(VALUES.has(arg)){
      if(opts.has(arg)||typeof args[i+1]!=="string" ||
          args[i+1].startsWith("--")) {
        throw new Error("unsafe_wrangler_argument");
      }
      opts.set(arg,args[++i]);
    }else{
      // In particular, reject --remote, --env, --profile, deploy,
      // experimental provisioning and any future unreviewed Wrangler option.
      throw new Error("remote_or_unscoped_wrangler_command_refused");
    }
  }
  if(opts.get("--local")!==true || opts.get("--config")!==CONFIG ||
     opts.get("--persist-to")!==persistDir) {
    throw new Error("remote_or_unscoped_wrangler_command_refused");
  }
  if(migration){
    if(opts.has("--file")||opts.has("--command")||opts.has("--json")){
      throw new Error("unsafe_migration_argument");
    }
  }else{
    const command=opts.get("--command"),file=opts.get("--file");
    if((typeof command==="string") === (typeof file==="string")){
      throw new Error("missing_or_ambiguous_sql");
    }
    if(command!==undefined &&
        command!==STATS_QUERY && command!==COUNTERS_QUERY) {
      throw new Error("only_approved_aggregate_sql_allowed");
    }
    if(file!==undefined &&
        (resolve(file)!==resolve(persistDir,"seed","fixtures.sql") ||
         !resolve(file).startsWith(resolve(persistDir)+sep))) {
      throw new Error("unapproved_fixture_sql_path");
    }
  }
  return true;
}

/** Require structured, successful CLI output; console text cannot spoof PASS. */
export function parseLocalD1Rows(raw){
  let data;
  try {data=JSON.parse(raw);}catch{throw new Error("invalid_wrangler_json");}
  const sets=Array.isArray(data)?data:[data];
  // A single approved SELECT must produce exactly one successful statement.
  // Never skip over a failed statement and cherry-pick a later success.
  if(sets.length!==1 || sets[0]?.success!==true ||
      !Array.isArray(sets[0].results) || sets[0].results.length!==1 ||
      !sets[0].results[0] || Array.isArray(sets[0].results[0]) ||
      typeof sets[0].results[0]!=="object"){
    throw new Error("wrangler_json_result_missing");
  }
  return sets[0].results;
}

export function validateFixtureCounters(phase,current,previous=null) {
  const expected={
    initial:{total:30,healthy:0,admin_hold:0,moves:0,
      runs:0,audits:30,revision:30,leases:0},
    final:{total:30,healthy:29,admin_hold:1,moves:2,
      runs:59,audits:89,revision:89,leases:0},
  };
  if(phase==="replay"){
    if(!previous) throw new Error("missing_replay_baseline");
    const keys=["total","healthy","admin_hold","moves","runs","audits",
      "revision","leases"];
    if(keys.some(key=>!Number.isSafeInteger(current?.[key]) ||
        current[key]!==previous[key])) throw new Error("cron_replay_modified_state");
    return true;
  }
  if(!Object.hasOwn(expected,phase) ||
      Object.entries(expected[phase]).some(([key,value])=>
        !Number.isSafeInteger(current?.[key]) || current[key]!==value)){
    throw new Error("fixture_counters_disagree");
  }
  return true;
}
