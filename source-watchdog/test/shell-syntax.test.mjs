import test from "node:test";
import assert from "node:assert/strict";
import {execFileSync} from "node:child_process";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";

const root=resolve(fileURLToPath(new URL("../../",import.meta.url)));
const scripts=["scripts/test-core.sh","scripts/verify-v6.sh",
  "source-watchdog/dev/run-release-validation.sh",
  "source-watchdog/dev/run-local-fixtures.sh"];

test("every local release/core test shell script passes bash -n",()=>{
  for(const path of scripts){
    const abs=resolve(root,path);
    execFileSync("bash",["-n",abs],{encoding:"utf8",timeout:5000});
  }
});

test("signed snapshot core tests and gate run exactly once",()=>{
  const body=readFileSync(resolve(root,"scripts/test-core.sh"),"utf8");
  const count=text=>body.split(text).length-1;
  assert.equal(count("com.eafb.SourceSnapshotTrustTestKt"),1);
  assert.equal(count("com.eafb.SourceSnapshotGateTestKt"),1);
  assert.equal(count("org/bouncycastle/crypto/signers/Ed25519Signer.class"),1);
});
