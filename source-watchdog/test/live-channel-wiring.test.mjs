import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const root = new URL("../../", import.meta.url);
const kotlin = new URL("EA-FB/src/main/kotlin/com/eafb/", root);
const provider = readFileSync(new URL("EAProvider.kt", kotlin), "utf8");
const policy = readFileSync(new URL("LiveSourcePolicy.kt", kotlin), "utf8");
const config = JSON.parse(readFileSync(new URL("config/channels.json", root), "utf8"));

test("checked-in live channel config and compiled gate are both empty", () => {
  assert.deepEqual(config.channels, []);
  assert.match(policy, /object LiveChannelDeliveryConfig\s*\{\s*const val enabled = false/);
});
test("compiled live rights gate executes before any remote playlist fetch", () => {
  const start = provider.indexOf("private suspend fun liveChannels()");
  const gate = provider.indexOf("if (!LiveChannelDeliveryConfig.enabled) return emptyList()", start);
  const fetch = provider.indexOf("app.get(channelsUrl)", start);
  assert.ok(start >= 0 && gate > start && fetch > gate);
});
test("punctuation-only search cannot match every local demo or live channel", () => {
  assert.match(provider, /val normalized = Identity\.normalize\(query\)/);
  assert.match(provider, /normalized\.isNotEmpty\(\) && Identity\.normalize\("Big Buck Bunny"\)/);
  assert.match(provider, /if \(normalized\.isEmpty\(\)\) emptyList\(\) else liveChannels\(\)/);
});
