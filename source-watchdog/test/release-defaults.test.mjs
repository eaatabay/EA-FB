import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname,join} from 'node:path';

test('checked-in Watchdog Wrangler defaults are inert and have no remote D1 binding',()=>{
  const root=join(dirname(fileURLToPath(import.meta.url)),'..');
  const config=JSON.parse(readFileSync(join(root,'wrangler.jsonc'),'utf8'));
  assert.equal(config.workers_dev,false);
  assert.equal(config.vars?.WATCHDOG_MODE,'disabled');
  for(const key of ['WATCHDOG_CRON_ENABLED','WATCHDOG_FIXTURE_ENABLED',
    'WATCHDOG_SNAPSHOT_ENABLED','WATCHDOG_ADMIN_ENABLED',
    'WATCHDOG_ADMIN_WRITES_ENABLED']){
    assert.equal(config.vars?.[key],'false',key);
  }
  assert.equal(config.d1_databases,undefined);
  assert.equal(config.routes,undefined);
});
