// Independent Dayflow-only protocol; secrets/plaintext never enter Worker logs.
import {handleOriginalProtocol} from './original-router.mjs';
export {OriginalDirectory} from './original-router.mjs';
export {OriginalSyncGroup} from './original-protocol.mjs';
const UUID = /^[0-9a-f-]{36}$/i, HEX = /^[0-9a-f]{64}$/;
const MAX_BODY = 6 * 1024 * 1024, MAX_GROUP = 50 * 1024 * 1024;
const json = (body, status=200) => new Response(JSON.stringify(body), {status,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'}});
const fail = (status, message) => { throw Object.assign(new Error(message), {status}); };
const random = () => { const b=crypto.getRandomValues(new Uint8Array(32)); return [...b].map(v=>v.toString(16).padStart(2,'0')).join(''); };
async function hash(v) { return [...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(v)))].map(v=>v.toString(16).padStart(2,'0')).join(''); }
async function body(req) {
  if (Number(req.headers.get('content-length')) > MAX_BODY) fail(413,'body_too_large');
  if (!req.body) return {};
  const reader=req.body.getReader(); let chunks=[], length=0;
  for (;;) { const {done,value}=await reader.read(); if(done) break; length+=value.length; if(length>MAX_BODY) { await reader.cancel(); fail(413,'body_too_large'); } chunks.push(value); }
  const all=new Uint8Array(length);let at=0;for(const c of chunks){all.set(c,at);at+=c.length;}
  try { return JSON.parse(new TextDecoder().decode(all)); } catch { fail(400,'invalid_json'); }
}
const tokenOf = req => req.headers.get('authorization')?.replace(/^Bearer /,'') ?? '';
const internal = (env, binding, name, path, data, token, method = data ? 'POST':'GET') => env[binding].get(env[binding].idFromName(name)).fetch(new Request('https://internal'+path,{method,headers:{...(token?{authorization:'Bearer '+token}:{}),'content-type':'application/json'},...(data?{body:JSON.stringify(data)}:{})}));
async function guarded(fn) { try { return await fn(); } catch(e) { return json({error:e.status?e.message:'internal_error'},e.status??500); } }
export default {
  fetch(req, env) { return guarded(async()=>{
    const url=new URL(req.url),path=url.pathname;
    if(path.startsWith('/original/'))return handleOriginalProtocol(req,env);
    if(path==='/health' && req.method==='GET') return json({service:'dayflow-sync',protocol:1});
    const creating=path==='/v1/groups' && req.method==='POST', joining=path==='/v1/join' && req.method==='POST', recovering=path==='/v1/recover'&&req.method==='POST';
    if(creating||joining||recovering) {
      const key=await hash(req.headers.get('cf-connecting-ip') ?? 'local');
      const limit=await internal(env,'DIRECTORY','directory','/limit',{key,action:creating?'create':'join'});
      if(!limit.ok) return limit;
      const data=await body(req);
      if(recovering){if(!UUID.test(data.gid??''))fail(400,'invalid_recovery');return internal(env,'GROUPS',data.gid,'/recover',data);}
      if(creating) {
        const gid=crypto.randomUUID();
        return internal(env,'GROUPS',gid,'/init',{gid,name:data.name});
      }
      if(!HEX.test(data.codeHash??'')) fail(400,'invalid_code');
      const found=await internal(env,'DIRECTORY','directory','/lookup',{codeHash:data.codeHash});
      if(!found.ok) return found;
      const entry=await found.json();
      return internal(env,'GROUPS',entry.gid,'/claim',{...data,inviteId:entry.id});
    }
    const match=/^\/v1\/groups\/([0-9a-f-]{36})(\/.*)?$/i.exec(path);
    if(!match || !UUID.test(match[1])) fail(404,'not_found');
    const rest=match[2]??'/';
    if(!/^\/(?:migration|changes|records|recovery|devices(?:\/[0-9a-f-]{36})?|invites(?:\/[0-9a-f-]{36}(?:\/approve)?)?|joins\/[0-9a-f-]{36})$/.test(rest)&&!(rest==='/'&&req.method==='DELETE')) fail(404,'not_found');
    const data=['POST','PUT','PATCH'].includes(req.method)?await body(req):null;
    return internal(env,'GROUPS',match[1],rest+url.search,data,tokenOf(req),req.method);
  }); }
};
export class PairDirectory {
  constructor(ctx) { this.storage=ctx.storage; }
  fetch(req) { return guarded(()=>this.storage.transaction(async tx=>{
    const p=new URL(req.url).pathname,d=await body(req),now=Date.now();
    if(p==='/limit') {
      const k=`limit:${d.action}:${d.key}`,window=d.action==='create'?3600000:600000, max=d.action==='create'?5:20;
      let v=await tx.get(k); if(!v||v.expires<now) v={n:0,expires:now+window};
      if(v.n>=max) fail(429,'rate_limited'); v.n++;await tx.put(k,v);await tx.setAlarm(now+3600000);return json({ok:true});
    }
    if(!HEX.test(d.codeHash??'')) fail(400,'invalid_code');
    const k='code:'+d.codeHash;
    if(p==='/reserve') {
      const old=await tx.get(k);if(old&&old.expires>now) fail(409,'code_in_use');
      await tx.put(k,{gid:d.gid,id:d.id,expires:d.expires});await tx.setAlarm(now+3600000);return json({ok:true});
    }
    if(p==='/lookup') { const v=await tx.get(k);if(!v||v.expires<now)fail(404,'invalid_or_expired_code');return json(v); }
    fail(404,'not_found');
  })); }
  async alarm() { for(const [k,v] of await this.storage.list()) if(v.expires<Date.now()) await this.storage.delete(k); if((await this.storage.list()).size) await this.storage.setAlarm(Date.now()+3600000); }
}
export class SyncGroup {
  constructor(ctx,env) {this.storage=ctx.storage;this.env=env;}
  fetch(req) {return guarded(()=>this.storage.transaction(async tx=>{
    const url=new URL(req.url),p=url.pathname,method=req.method,now=Date.now(),d=await body(req);
    let meta=await tx.get('meta');
    if(p==='/init') {
      if(meta)fail(409,'exists');
      const deviceId=crypto.randomUUID(), token=random();
      meta={gid:d.gid,head:0,bytes:0}; await tx.put('meta',meta);
      await tx.put('device:'+deviceId,{id:deviceId,tokenHash:await hash(token),name:String(d.name??'내 기기').slice(0,80),created:now});
      return json({gid:d.gid,deviceId,token});
    }
    if(!meta)fail(404,'group_not_found');
    if(meta.migrated&&method!=='GET'&&p!=='/migration')fail(410,'update_required');
    const token=tokenOf(req),tokenHash=await hash(token);
    const devices=await tx.list({prefix:'device:'});
    const device=[...devices.values()].find(x=>x.tokenHash===tokenHash);
    if(p==='/recover'){
      const recovery=await tx.get('recovery');
      if(!HEX.test(d.auth??'')||!recovery||await hash(d.auth)!==recovery.authHash)fail(403,'invalid_recovery');
      if(devices.size>=10)fail(409,'device_limit');
      const deviceId=crypto.randomUUID(),token=random();
      await tx.put('device:'+deviceId,{id:deviceId,tokenHash:await hash(token),name:String(d.name??'복구 기기').slice(0,80),created:now});
      return json({gid:meta.gid,deviceId,token,wrappedKey:recovery.wrappedKey});
    }
    const publicKeyOk=v=>typeof v==='string'&&/^[A-Za-z0-9_-]{50,100}$/.test(v);
    if(p==='/claim') {
      const invite=await tx.get('invite:'+d.inviteId);
      if(!invite||invite.expires<now||invite.claimed)fail(404,'invalid_or_expired_code');
      if(!publicKeyOk(d.publicKey))fail(400,'invalid_public_key');
      if(devices.size>=10)fail(409,'device_limit');
      const joinId=crypto.randomUUID(),deviceId=crypto.randomUUID(),token=random();
      const join={id:joinId,deviceId,tokenHash:await hash(token),publicKey:d.publicKey,name:String(d.name??'새 기기').slice(0,80),state:'pending',inviteId:invite.id,expires:invite.expires};
      invite.claimed=joinId;await tx.put('invite:'+invite.id,invite);await tx.put('join:'+joinId,join);
      return json({gid:meta.gid,joinId,deviceId,token,publicKey:invite.publicKey,expires:join.expires});
    }
    const joinMatch=/^\/joins\/([0-9a-f-]{36})$/.exec(p);
    if(joinMatch) {
      const join=await tx.get('join:'+joinMatch[1]);
      if(!join||join.tokenHash!==tokenHash)fail(401,'unauthorized');
      if(join.expires<now)fail(410,'pairing_expired');
      if(method==='DELETE'){await tx.delete('join:'+join.id);await tx.delete('device:'+join.deviceId);return json({ok:true});}
      if(method!=='GET')fail(405,'method_not_allowed');
      return json({state:join.state,wrappedKey:join.wrappedKey??null});
    }
    if(!device)fail(401,'unauthorized');
    if(p==='/migration'&&method==='POST'){if(d.mode!=='original-v1')fail(400,'invalid_migration');meta.migrated=true;await tx.put('meta',meta);return json({ok:true});}
    if(p==='/recovery'&&method==='POST'){
      if(!HEX.test(d.authHash??'')||typeof d.wrappedKey!=='string'||! /^[A-Za-z0-9_-]{80,1000}$/.test(d.wrappedKey))fail(400,'invalid_recovery');
      await tx.put('recovery',{authHash:d.authHash,wrappedKey:d.wrappedKey});return json({ok:true});
    }
    if(p==='/'&&method==='DELETE'){await tx.deleteAll();return json({ok:true});}
    if(p==='/devices'&&method==='GET')return json({devices:[...devices.values()].map(({tokenHash,...v})=>v)});
    const dm=/^\/devices\/([0-9a-f-]{36})$/.exec(p);
    if(dm&&method==='DELETE'){await tx.delete('device:'+dm[1]);for(const [k,j] of await tx.list({prefix:'join:'}))if(j.deviceId===dm[1])await tx.delete(k);return json({ok:true});}
    if(p==='/invites'&&method==='POST') {
      if(!HEX.test(d.codeHash??'')||!publicKeyOk(d.publicKey))fail(400,'invalid_invite');
      for(const [k,i] of await tx.list({prefix:'invite:'})){await tx.delete(k);if(i.claimed)await tx.delete('join:'+i.claimed);}
      const invite={id:crypto.randomUUID(),owner:device.id,publicKey:d.publicKey,expires:now+600000};
      const reserved=await internal(this.env,'DIRECTORY','directory','/reserve',{codeHash:d.codeHash,gid:meta.gid,id:invite.id,expires:invite.expires});
      if(!reserved.ok)return reserved;
      await tx.put('invite:'+invite.id,invite);await tx.setAlarm(now+600000);return json({id:invite.id,expires:invite.expires});
    }
    const im=/^\/invites\/([0-9a-f-]{36})(\/approve)?$/.exec(p);
    if(im) {
      const invite=await tx.get('invite:'+im[1]);
      if(!invite||invite.expires<now||invite.owner!==device.id)fail(404,'invite_not_found');
      if(method==='DELETE'){await tx.delete('invite:'+invite.id);if(invite.claimed)await tx.delete('join:'+invite.claimed);return json({ok:true});}
      const join=invite.claimed?await tx.get('join:'+invite.claimed):null;
      if(!im[2]&&method==='GET')return json({expires:invite.expires,request:join?{joinId:join.id,deviceId:join.deviceId,publicKey:join.publicKey,name:join.name}:null});
      if(im[2]&&method==='POST') {
        if(!join||join.id!==d.joinId||join.state!=='pending')fail(409,'invalid_join');
        if(typeof d.wrappedKey!=='string'||! /^[A-Za-z0-9_-]{80,1000}$/.test(d.wrappedKey))fail(400,'invalid_wrapped_key');
        join.state='approved';join.wrappedKey=d.wrappedKey;
        await tx.put('join:'+join.id,join);await tx.put('device:'+join.deviceId,{id:join.deviceId,tokenHash:join.tokenHash,name:join.name,created:now});
        return json({ok:true});
      }
    }
    if(p==='/changes'&&method==='GET') {
      const since=Number(url.searchParams.get('since')??0);if(!Number.isSafeInteger(since)||since<0)fail(400,'invalid_since');
      const all=[...(await tx.list({prefix:'record:'})).values()].filter(r=>r.seq>since).sort((a,b)=>a.seq-b.seq),records=all.slice(0,100);
      return json({head:meta.head,more:all.length>100,records,migrated:meta.migrated===true});
    }
    if(p==='/records'&&method==='POST') {
      if(!HEX.test(d.rid??'')||!Number.isSafeInteger(d.base)||d.base<0||typeof d.ct!=='string'||! /^[A-Za-z0-9_-]+$/.test(d.ct)||d.ct.length<40||d.ct.length>5*1024*1024)fail(400,'invalid_record');
      const key='record:'+d.rid,old=await tx.get(key);
      if((old?.seq??0)!==d.base)return json({seq:old?.seq??0,ct:old?.ct??null},409);
      const bytes=meta.bytes-(old?.ct.length??0)+d.ct.length;
      if(bytes>MAX_GROUP||!old&&(await tx.list({prefix:'record:'})).size>=2000)fail(413,'group_quota');
      const record={rid:d.rid,ct:d.ct,seq:++meta.head};meta.bytes=bytes;
      await tx.put(key,record);await tx.put('meta',meta);return json({seq:record.seq});
    }
    fail(405,'method_not_allowed');
  }));}
  async alarm(){for(const prefix of ['invite:','join:'])for(const [k,v]of await this.storage.list({prefix}))if(v.expires<Date.now())await this.storage.delete(k);}
}
