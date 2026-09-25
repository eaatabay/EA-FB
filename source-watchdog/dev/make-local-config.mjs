/**
 * Offline-only Wrangler config generator. It NEVER provisions a remote D1
 * database, deploys a Worker, contacts Cloudflare or reads credentials.
 * Usage (from source-watchdog/): node dev/make-local-config.mjs
 */
import {readFileSync, writeFileSync} from "node:fs";
import {resolve} from "node:path";
import {fileURLToPath} from "node:url";

const DEV_DB_ID="00000000-0000-0000-0000-000000000000";
export function makeLocalConfig(tracked) {
  if (!tracked || tracked.name !== "ea-fb-source-watchdog-dev" ||
      tracked.workers_dev !== false || tracked.d1_databases !== undefined ||
      tracked.vars?.WATCHDOG_MODE !== "disabled" ||
      tracked.vars?.WATCHDOG_CRON_ENABLED !== "false" ||
      tracked.vars?.WATCHDOG_FIXTURE_ENABLED !== "false" ||
      tracked.vars?.WATCHDOG_SNAPSHOT_ENABLED !== "false" ||
      JSON.stringify(tracked.triggers?.crons) !== JSON.stringify(["*/15 * * * *"])) {
    throw new Error("tracked_worker_config_is_not_inert");
  }
  const config=structuredClone(tracked);
  config.name="ea-fb-source-watchdog-local-fixtures";
  config.d1_databases=[{
    binding:"SOURCES_DB",database_name:"ea-fb-watchdog-fixture-local",
    database_id:DEV_DB_ID,preview_database_id:"ea-fb-watchdog-fixture-local",
    migrations_dir:"migrations",
  }];
  config.vars={
    ...config.vars,WATCHDOG_MODE:"fixture",
    WATCHDOG_CRON_ENABLED:"true",
    WATCHDOG_FIXTURE_ENABLED:"true",
    WATCHDOG_SNAPSHOT_ENABLED:"false",
  };
  config.workers_dev=false;
  return config;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const tracked=JSON.parse(readFileSync(new URL("../wrangler.jsonc",import.meta.url),"utf8"));
  const local=makeLocalConfig(tracked);
  const target=new URL("../wrangler.local.jsonc",import.meta.url);
  writeFileSync(target,JSON.stringify(local,null,2)+"\n",{flag:"wx",mode:0o600});
  console.log("PASS: local fixture config generated; no remote D1 or Worker was created");
}
