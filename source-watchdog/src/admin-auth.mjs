/**
 * Require a signed Cloudflare Access JWT before any admin read. Access's
 * unverified email header by itself grants NO privileges.
 * The team-domain JWKS URL is fixed by an admin-only env setting.
 */
const TEAM=/^https:\/\/[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.cloudflareaccess\.com$/;
const AUD=/^[A-Za-z0-9_-]{16,128}$/;
const EMAIL=/^[^@\s,]+@[^@\s,]+\.[^@\s,]+$/;
const MAX_JWT=8192;

export function adminAccessConfigured(env) {
  return env?.WATCHDOG_ADMIN_ENABLED==="true" &&
    env?.WATCHDOG_MODE==="production" &&
    env?.WATCHDOG_FIXTURE_ENABLED!=="true" &&
    typeof env?.WATCHDOG_ACCESS_TEAM_DOMAIN==="string" &&
    TEAM.test(env.WATCHDOG_ACCESS_TEAM_DOMAIN) &&
    typeof env?.WATCHDOG_ACCESS_AUD==="string" &&
    AUD.test(env.WATCHDOG_ACCESS_AUD) &&
    typeof env?.WATCHDOG_ADMIN_EMAILS==="string" &&
    env.WATCHDOG_ADMIN_EMAILS.split(",").length<=10 &&
    env.WATCHDOG_ADMIN_EMAILS.split(",").every(x=>
      EMAIL.test(x) && x===x.toLowerCase());
}

function decodeBase64Url(part) {
  if(typeof part!=="string" || !/^[A-Za-z0-9_-]+$/.test(part)) {
    throw Error("invalid_token");
  }
  const padded=part.replace(/-/g,"+").replace(/_/g,"/")+
    "=".repeat((4-part.length%4)%4);
  return Uint8Array.from(atob(padded),x=>x.charCodeAt(0));
}
function decodeJSON(part) {
  const bytes=decodeBase64Url(part);
  if(bytes.length>8192) throw Error("token_too_large");
  return JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));
}
async function loadJwks(team) {
  const signal=AbortSignal.timeout(4000);
  const res=await fetch(team+"/cdn-cgi/access/certs",{
    method:"GET",redirect:"error",signal,headers:{accept:"application/json"},
  });
  if(!res.ok || Number(res.headers.get("content-length")||0)>65536) {
    throw Error("invalid_access_certs");
  }
  const body=await res.text();
  if(body.length>65536) throw Error("oversized_access_certs");
  return JSON.parse(body);
}

/**
 * Returns only a verified allowlisted admin email or null.
 * fetchJwks/clock are injectable by INTERNAL tests only.
 */
export async function verifyAdminAccess(request,env,{
  fetchJwks=loadJwks,nowSeconds=()=>Math.floor(Date.now()/1000),
}={}) {
  if(!adminAccessConfigured(env)) return null;
  const jwt=request?.headers?.get("cf-access-jwt-assertion");
  if(typeof jwt!=="string" || jwt.length>MAX_JWT) return null;
  const parts=jwt.split(".");
  if(parts.length!==3) return null;
  try {
    const [h,p,sig]=parts,header=decodeJSON(h),claims=decodeJSON(p);
    if(header?.alg!=="RS256" || !/^[A-Za-z0-9_-]{1,128}$/.test(header.kid) ||
       header.crit!==undefined || header.jku!==undefined ||
       claims?.iss!==env.WATCHDOG_ACCESS_TEAM_DOMAIN ||
       !(claims.aud===env.WATCHDOG_ACCESS_AUD ||
         (Array.isArray(claims.aud)&&claims.aud.includes(env.WATCHDOG_ACCESS_AUD))) ||
       typeof claims.email!=="string" || !EMAIL.test(claims.email) ||
       !Number.isSafeInteger(claims.exp) || !Number.isSafeInteger(claims.iat)) return null;
    const now=nowSeconds();
    if(!Number.isSafeInteger(now) || claims.exp<=now ||
       claims.exp<=claims.iat || claims.exp-claims.iat>24*3600 ||
       claims.iat>now+60 || now-claims.iat>24*3600 ||
       (claims.nbf!==undefined &&
        (!Number.isSafeInteger(claims.nbf)||claims.nbf>now))) return null;
    const email=claims.email.toLowerCase();
    if(!env.WATCHDOG_ADMIN_EMAILS.split(",").includes(email)) return null;
    const jwks=await fetchJwks(env.WATCHDOG_ACCESS_TEAM_DOMAIN);
    if(!Array.isArray(jwks?.keys)||jwks.keys.length>24) return null;
    const key=jwks.keys.find(x=>x?.kid===header.kid&&x.kty==="RSA"&&
      (!x.alg||x.alg==="RS256")&&(!x.use||x.use==="sig"));
    if(!key) return null;
    const verifier=await crypto.subtle.importKey("jwk",{
      kty:"RSA",n:key.n,e:key.e,ext:true,
    },{name:"RSASSA-PKCS1-v1_5",hash:"SHA-256"},false,["verify"]);
    const signature=decodeBase64Url(sig);
    if(signature.length>1024) return null;
    return await crypto.subtle.verify("RSASSA-PKCS1-v1_5",
      verifier,signature,new TextEncoder().encode(h+"."+p))?email:null;
  }catch{return null;}
}
