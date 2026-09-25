import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";

const workflow=readFileSync(new URL(
  "../../.github/workflows/v6-watchdog-local-check.yml",import.meta.url),
  "utf8");
const triggers=workflow.split("\npermissions:")[0];

test("v6 Watchdog CI is opt-in and never runs on branch pushes or a timer",()=>{
  assert.match(triggers,/^on:\s*$/m);
  assert.match(triggers,/^  pull_request:\s*$/m);
  assert.match(triggers,/^  workflow_dispatch:\s*$/m);
  assert.doesNotMatch(triggers,/^  (?:push|schedule|repository_dispatch):/m);
});

test("CI accepts only the reviewed same-repository v6 feature branch",()=>{
  assert.match(workflow,/github\.event\.pull_request\.head\.repo\.full_name == github\.repository/);
  assert.match(workflow,/github\.head_ref == 'feature\/detail-dual-ratings-v6'/);
  assert.match(workflow,/github\.ref_name == 'feature\/detail-dual-ratings-v6'/);
  assert.match(workflow,/persist-credentials: false/);
  assert.match(workflow,/^permissions:\s*\n  contents: read\s*$/m);
});

test("CI invokes only local D1 release gate; no Cloudflare credentials or deploy",()=>{
  assert.match(workflow,/npm run test:release-local/);
  assert.match(workflow,/WRANGLER_SEND_METRICS: "false"/);
  assert.doesNotMatch(workflow,/\$\{\{\s*secrets\./);
  const commands=workflow.split("\n        run: |").slice(1).join("\n");
  assert.doesNotMatch(commands,/(?:\bwrangler\s+(?:deploy|d1\s+create)\b|--remote\b)/);
  assert.match(workflow,/source-watchdog\/wrangler\.jsonc/);
});
