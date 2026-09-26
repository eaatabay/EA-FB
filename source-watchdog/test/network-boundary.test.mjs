import test from 'node:test';
import assert from 'node:assert/strict';
import {publicIPv4, preflightTarget, inspectRedirectPath} from '../src/network-boundary.mjs';
const source = {verifiedDomains:['licensed.example.com','backup.example.com']};
const resolve = async () => ['8.8.8.8','1.1.1.1'];

test('reject all private, reserved, mapped and ambiguous addresses', () => {
  const blocked = ['127.0.0.1','10.0.0.4','192.168.1.1','172.17.0.4',
    '169.254.169.254','100.100.100.100','0.0.0.0','224.0.0.1',
    '198.51.100.4','203.0.113.3','192.0.2.4','192.0.0.1',
    '198.18.0.2','192.88.99.1','255.255.255.255','::1','::ffff:127.0.0.1',
    '127.000.0.1','256.0.0.1','1.2.3.999','1.2.3'];
  blocked.forEach(ip => assert.equal(publicIPv4(ip),false,ip));
  ['8.8.8.8','1.1.1.1','9.9.9.9'].forEach(ip => assert.equal(publicIPv4(ip),true,ip));
});

test('one private or IPv6 DNS answer fails entire destination', async () => {
  for (const addr of ['127.0.0.1','169.254.169.254','::1','2001:4860:4860::8888']) {
    const x=await preflightTarget(source,'https://licensed.example.com',
      async()=>['8.8.8.8',addr]);
    assert.equal(x.reason,'untrusted_dns_answer');
  }
  assert.equal((await preflightTarget(source,'https://licensed.example.com',
    async()=>[])).allowed,false);
  assert.equal((await preflightTarget(source,'https://licensed.example.com',
    async()=>{throw Error('dns')})).reason,'dns_failure');
});

test('only reviewed HTTPS hosts, never arbitrary redirect or userinfo', async () => {
  for (const target of ['http://licensed.example.com','https://127.0.0.1',
      'https://user:pass@licensed.example.com','https://licensed.example.com:8443',
      'https://[::1]','https://localhost','https://licensed.example.com/#x',
      'https://licensed.example.com/?token=secret',
      'https://licensed.example.com/?',
      'https://licensed.example.com:443',
      'https://LICENSED.example.com',
      'https://licensed%2eexample.com',
      'https://licensed.example.com/#',
      'https://-bad.example.com','https://bad-.example.com']) {
    assert.equal((await preflightTarget(source,target,resolve)).allowed,false,target);
  }
  const blocked=await preflightTarget(source,'https://evil.example.org',resolve);
  assert.equal(blocked.reason,'admin_domain_approval_required');
  assert.equal((await preflightTarget(source,'https://licensed.example.com',resolve)).allowed,true);
  assert.equal((await preflightTarget(source,'https://licensed.example.com',null)).reason,'resolver_required');
  const oversized = 'a'.repeat(64) + '.example.com';
  assert.equal((await preflightTarget({verifiedDomains:[oversized]},
    'https://' + oversized,resolve)).allowed,false);
});

test('each approved redirect hop is revalidated and unknown domains request admin', async () => {
  const visited=[];
  const dns=async host=>{visited.push(host);return ['8.8.8.8']};
  assert.deepEqual(await inspectRedirectPath(source,[
    'https://licensed.example.com/a','https://backup.example.com/b'],dns),
    {allowed:true,hops:1});
  assert.deepEqual(visited,['licensed.example.com','backup.example.com']);
  const next=await inspectRedirectPath(source,[
    'https://licensed.example.com','https://evil.example.org'],dns);
  assert.equal(next.reason,'admin_domain_approval_required');
  assert.equal(next.hop,1);
  assert.equal((await inspectRedirectPath(source,[
    'https://licensed.example.com','https://licensed.example.com'],dns)).reason,'redirect_loop');
});

test('redirect depth and mixed-domain DNS rebinding fail closed', async () => {
  const many=Array.from({length:5},(_,i)=>'https://licensed.example.com/'+i);
  assert.equal((await inspectRedirectPath(source,many,resolve)).reason,'redirect_limit');
  let checks=0;
  const dns=async()=>{checks++;return checks===2?['8.8.8.8','10.0.0.1']:['8.8.8.8']};
  const result=await inspectRedirectPath(source,[
    'https://licensed.example.com','https://backup.example.com'],dns);
  assert.equal(result.reason,'untrusted_dns_answer');
  assert.equal(result.hop,1);
});
