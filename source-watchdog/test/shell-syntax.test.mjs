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
  assert.equal(count("com.eafb.SourceSnapshotOfflinePolicyTestKt"),1);
  assert.equal(count("com.eafb.WatchdogClientStoreJvmTestKt"),1);
  assert.equal(count("com.eafb.WatchdogSnapshotRefreshTestKt"),1);
  assert.equal(count("com.eafb.WatchdogHttpsTransportTestKt"),1);
  assert.equal(count("com.eafb.WatchdogAdapterSelectionTestKt"),1);
  assert.equal(count("com.eafb.WatchdogApprovedAdapterBridgeTestKt"),1);
  assert.equal(count("com.eafb.ReviewedSourcePermitPolicyTestKt"),1);
  assert.equal(count("org/bouncycastle/crypto/signers/Ed25519Signer.class"),1);
});

test("v6 candidate build is blocked until actual local workerd+D1 smoke runs",()=>{
  const script=readFileSync(resolve(root,"scripts/verify-v6.sh"),"utf8");
  const local=script.indexOf("npm run test:wrangler-local");
  const build=script.indexOf("bash scripts/build-codespace.sh");
  assert.ok(local>=0 && build>local,"Local D1 gate must precede v6 build");
  assert.match(script,/if \[ ! -x source-watchdog\/node_modules\/\.bin\/wrangler \]/);
});
