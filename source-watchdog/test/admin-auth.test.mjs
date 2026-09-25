import test from "node:test";
import assert from "node:assert/strict";
import {adminAccessConfigured,verifyAdminAccess} from "../src/admin-auth.mjs";

const NOW=1_800_000_000;
const TEAM="https://ea-fixture.cloudflareaccess.com";
const AUD="fixture-audience-0123456789012345";
const ENV={
 WATCHDOG_ADMIN_ENABLED:"true",WATCHDOG_MODE:"production",
 WATCHDOG_FIXTURE_ENABLED:"false",WATCHDOG_ACCESS_TEAM_DOMAIN:TEAM,
 WATCHDOG_ACCESS_AUD:AUD,WATCHDOG_ADMIN_EMAILS:"admin@example.com",
};
const header={alg:"RS256",typ:"JWT",kid:"fixture-key-001"};
function b64(x){return Buffer.from(JSON.stringify(x)).toString("base64url");}
function request(jwt){return new Request("https://watchdog.example.org/admin",{
 headers:jwt?{"cf-access-jwt-assertion":jwt}:{},
});}
async function fixture() {
 const pair=await crypto.subtle.generateKey({name:"RSASSA-PKCS1-v1_5",
   modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:"SHA-256"},
   true,["sign","verify"]);
 const jwk=await crypto.subtle.exportKey("jwk",pair.publicKey);
 const fetchJwks=async()=>({keys:[{kid:header.kid,kty:"RSA",
   n:jwk.n,e:jwk.e,alg:"RS256",use:"sig"}]});
 async function sign(payload,customHeader=header) {
   const plain=b64(customHeader)+"."+b64(payload);
   const sig=await crypto.subtle.sign("RSASSA-PKCS1-v1_5",pair.privateKey,
     new TextEncoder().encode(plain));
   return plain+"."+Buffer.from(sig).toString("base64url");
 }
 const claims={iss:TEAM,aud:AUD,email:"admin@example.com",
   iat:NOW-100,nbf:NOW-10,exp:NOW+3600};
 const verify=(jwt,env=ENV,jwks=fetchJwks)=>verifyAdminAccess(request(jwt),env,
   {nowSeconds:()=>NOW,fetchJwks:jwks});
 return {sign,verify,claims,fetchJwks};
}
test("admin Access configuration must be explicit and never enabled by fixture mode",()=>{
 assert.equal(adminAccessConfigured(ENV),true);
 for(const env of [
  {...ENV,WATCHDOG_ADMIN_ENABLED:"false"},
  {...ENV,WATCHDOG_MODE:"fixture"},
  {...ENV,WATCHDOG_FIXTURE_ENABLED:"true"},
  {...ENV,WATCHDOG_ACCESS_TEAM_DOMAIN:"https://169.254.169.254"},
  {...ENV,WATCHDOG_ACCESS_TEAM_DOMAIN:"https://team.cloudflareaccess.com.attacker.org"},
  {...ENV,WATCHDOG_ADMIN_EMAILS:"*"},
  {...ENV,WATCHDOG_ADMIN_EMAILS:"Admin@Example.com"},
 ]) assert.equal(adminAccessConfigured(env),false);
});
test("signed Access JWT with audience, issuer and approved email permits read-only admin",async()=>{
 const {sign,verify,claims}=await fixture();
 assert.equal(await verify(await sign(claims)),"admin@example.com");
 assert.equal(await verify(await sign({...claims,aud:["other",AUD]})),"admin@example.com");
});
test("unsigned/spoofed email header and tampered JWT never authorize",async()=>{
 const {sign,verify,claims}=await fixture();
 assert.equal(await verify(null),null);
 const original=await sign(claims);
 const tampered=original.split(".");
 tampered[1]=b64({...claims,email:"other@example.com"});
 assert.equal(await verify(tampered.join(".")),null);
 assert.equal(await verify(original+"extra"),null);
});
test("expired token, future nbf, wrong issuer, audience and email are rejected",async()=>{
 const {sign,verify,claims}=await fixture();
 for(const changed of [
  {exp:NOW-1},{nbf:NOW+100},{iat:NOW+100},
  {iss:"https://fake.cloudflareaccess.com"},{aud:"wrong-audience"},
  {email:"other@example.com"},{email:"admin+evil@example.com"},
 ]) assert.equal(await verify(await sign({...claims,...changed})),null,
   JSON.stringify(changed));
});
test("wrong signing key, forged JWT alg, unknown kid and no JWKS all fail closed",async()=>{
 const {sign,verify,claims}=await fixture();
 assert.equal(await verify(await sign(claims,{...header,alg:"none"})),null);
 assert.equal(await verify(await sign(claims,{...header,kid:"unknown-kid"})),null);
 assert.equal(await verify(await sign(claims),ENV,async()=>({keys:[]})),null);
 assert.equal(await verify(await sign(claims),ENV,async()=>{throw Error("network");}),null);
 const second=await fixture();
 assert.equal(await verify(await second.sign(claims)),null);
});
