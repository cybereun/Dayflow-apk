const json=(v,status=200)=>new Response(JSON.stringify(v),{status,headers:{'content-type':'application/json','cache-control':'no-store'}});
const error=(status,code)=>json({error:{code,message:code}},status);
async function routingBody(req){
 const reader=req.clone().body?.getReader();let size=0;const chunks=[];
 if(!reader)throw Error('invalid_body');
 for(;;){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>4096){await reader.cancel();throw Error('body_too_large');}chunks.push(value);}
 const bytes=new Uint8Array(size);let at=0;for(const chunk of chunks){bytes.set(chunk,at);at+=chunk.length;}
 return JSON.parse(new TextDecoder().decode(bytes));
}
export class OriginalDirectory {
 constructor(ctx){this.storage=ctx.storage;this.chain=Promise.resolve();}
 fetch(req){const run=this.chain.then(()=>this.handle(req));this.chain=run.catch(()=>{});return run;}
 async handle(req){
  const d=await req.json(),now=Date.now(),path=new URL(req.url).pathname;
  if(path==='/limit'){
   const key='limit:'+d.key;let v=await this.storage.get(key);if(!v||v.expires<=now)v={n:0,expires:now+600000};
   if(v.n>=d.max)return error(429,'rate_limited');v.n++;await this.storage.put(key,v);await this.storage.setAlarm(now+600000);return json({ok:true});
  }
  if(!/^(pair|recovery):[A-Za-z0-9_-]{43}$/.test(d.key??''))return error(400,'invalid_code');
  const old=await this.storage.get(d.key);
  if(path==='/lookup')return old&&old.expires>now?json(old):error(404,'pairing_not_found');
  if(path==='/reserve'){
   if(old&&old.expires>now&&old.gid!==d.gid)return error(409,'code_in_use');
   await this.storage.put(d.key,{gid:d.gid,expires:d.expires});await this.storage.setAlarm(now+600000);return json({ok:true});
  }
  return error(404,'not_found');
 }
 async alarm(){const now=Date.now();for(const [k,v]of await this.storage.list())if(v.expires<=now)await this.storage.delete(k);if((await this.storage.list()).size)await this.storage.setAlarm(now+600000);}
}
export const directory=(env,path,data)=>env.ORIGINAL_DIRECTORY.get(env.ORIGINAL_DIRECTORY.idFromName('original-v1')).fetch(new Request('https://internal'+path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(data)}));
export async function handleOriginalProtocol(req,env){
 const url=new URL(req.url);url.pathname=url.pathname.slice('/original'.length);
 const path=url.pathname,creating=path==='/v1/groups'&&req.method==='POST';
 const claim=path==='/v1/pairings/claim'&&req.method==='POST',recovery=/^\/v1\/recovery\/([A-Za-z0-9_-]{43})(?:\/join)?$/.exec(path);
 let gid=/^\/v1\/groups\/([0-9a-f-]{36})(?:\/|$)/i.exec(path)?.[1];
 if(creating||claim||recovery){
  const ip=req.headers.get('cf-connecting-ip')??'local';
  const digest=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(ip)))).map(x=>x.toString(16).padStart(2,'0')).join('');
  const limited=await directory(env,'/limit',{key:(creating?'create:':'join:')+digest,max:creating?10:40});if(!limited.ok)return limited;
 }
 if(creating)gid=crypto.randomUUID();
 if(recovery&&req.method==='PUT'){
  let data;try{data=await routingBody(req);}catch(e){return error(e.message==='body_too_large'?413:400,'invalid_body');}
  if(!/^[0-9a-f-]{36}$/i.test(data.gid??''))return error(400,'invalid_group');
  gid=data.gid;
 }else if(claim||recovery){
  let data;if(claim)try{data=await routingBody(req);}catch(e){return error(e.message==='body_too_large'?413:400,'invalid_body');}
  const key=recovery?'recovery:'+recovery[1]:'pair:'+data.codeHash;
  const found=await directory(env,'/lookup',{key});if(!found.ok)return found;gid=(await found.json()).gid;
 }
 if(!gid)return error(404,'not_found');
 const headers=new Headers(req.headers);headers.set('x-original-gid',gid);
 return env.ORIGINAL_GROUPS.get(env.ORIGINAL_GROUPS.idFromName(gid)).fetch(new Request(url,{method:req.method,headers,body:['GET','HEAD'].includes(req.method)?undefined:req.body,duplex:'half'}));
}
