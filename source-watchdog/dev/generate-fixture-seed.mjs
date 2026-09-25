/**
 * Generates SQL to initialize a PRIVATE, LOCAL, fixture-only D1 test database.
 * NEVER run the output with Wrangler --remote or against any existing database.
 * This script prints SQL only: it does not contact Cloudflare or any website.\n * IMPORTANT: D1 imports reject explicit BEGIN/COMMIT in SQL files. Wrangler\n * executes the INSERT statements through its own transaction handling.
 */
import { initialState } from "../src/policy.mjs";

const ids = [
  ...Array.from({length: 27}, (_,i) => "fixture-healthy-" + String(i+1).padStart(2,"0")),
  "fixture-move-a", "fixture-move-b", "fixture-structural",
];
const sql = s => "'" + String(s).replaceAll("'", "''") + "'";
const rows = ids.map(id => {
  const config = {
    id,
    enabled: true,
    integrationApproved: true, // ONLY fictional, fixture-only approval
    approvalRef: "fixture-only",
    adapterVersion: 1,
    mediaKind: "both",
    currentUrl: "https://demo.example.org",
    lastKnownGoodUrl: "https://demo.example.org",
    verifiedDomains: ["demo.example.org", "moved.example.org"],
    requiredChecks: ["reachability", "search", "detail", "episode", "playback"],
  };
  const state = initialState(config, 0);
  return "INSERT OR IGNORE INTO source_registry " +
    "(id,config_json,state_json,revision,last_check_run_id,updated_at_ms,changed_by,change_reason) VALUES (" +
    [sql(id), sql(JSON.stringify(config)), sql(JSON.stringify(state)), 0, "NULL", 0,
      sql("admin:fixture"), sql("source_registered")].join(",") + ");";
});
process.stdout.write("-- FIXTURE-ONLY: local isolated D1 only; never use with --remote.\n" +
  "-- 27 healthy candidates, two approved-domain moves, one structural failure.\n" +
  rows.join("\n") + "\n");
