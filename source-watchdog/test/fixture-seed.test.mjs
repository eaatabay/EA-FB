import test from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

let DatabaseSync;
try { ({DatabaseSync} = await import("node:sqlite")); } catch {}
const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const generate = () => execFileSync(process.execPath,
  [join(root, "dev/generate-fixture-seed.mjs")], {encoding:"utf8"});

test("fixture generator never contains live domains or dangerous remote commands",()=>{
  const sql = generate();
  assert.equal((sql.match(/INSERT OR IGNORE INTO source_registry/g)||[]).length,30);
  assert.match(sql,/FIXTURE-ONLY/);
  assert.ok(!/^\s*(?:wrangler|npx)\b/m.test(sql));
  assert.ok(!sql.includes("DROP TABLE"));
  assert.ok(!sql.includes("DELETE FROM"));
  assert.ok(!sql.includes("UPDATE source_registry"));
  assert.equal((sql.match(/\.example\.org/g)||[]).length>30,true);
});

(DatabaseSync ? test : test.skip)("seeded isolated SQLite fixture registry starts at revision 30 with 30 audit events",()=>{
  const db=new DatabaseSync(":memory:");
  try {
    db.exec(readFileSync(join(root,"migrations/0001_registry.sql"),"utf8"));
    db.exec(generate());
    const count=table=>Number(db.prepare("SELECT COUNT(*) AS n FROM " + table).get().n);
    assert.equal(count("source_registry"),30);
    assert.equal(count("source_audit"),30);
    assert.equal(count("source_probe_runs"),0);
    assert.equal(db.prepare("SELECT revision FROM registry_meta WHERE singleton=1").get().revision,30);
    assert.equal(db.prepare("SELECT config_json FROM source_registry WHERE id='fixture-move-a'").get().config_json.includes("moved.example.org"),true);
    assert.equal(db.prepare("SELECT state_json FROM source_registry WHERE id='fixture-structural'").get().state_json.includes("degraded"),true);
    // Re-seeding is idempotent; global revision and audit stay unchanged.
    db.exec(generate());
    assert.equal(count("source_registry"),30);
    assert.equal(count("source_audit"),30);
    assert.equal(db.prepare("SELECT revision FROM registry_meta WHERE singleton=1").get().revision,30);
  } finally { db.close(); }
});
