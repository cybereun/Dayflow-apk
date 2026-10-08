// Original Spiralday HttpTransport contract. Separate storage from legacy Dayflow.
import {directory} from './original-router.mjs';
const encode = value => btoa(String.fromCharCode(...value)).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');
const random = () => encode(crypto.getRandomValues(new Uint8Array(32)));
const hash = async value => encode(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))));
const hashAuth = async value => {
  if(!/^[A-Za-z0-9_-]{43}$/.test(value??''))return null;
  const bytes=Uint8Array.from(atob(value.replaceAll('-','+').replaceAll('_','/')+'='),c=>c.charCodeAt(0));
  return encode(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)));
};
const response = (value,status=200) => new Response(JSON.stringify(value),{status,headers:{'content-type':'application/json','cache-control':'no-store'}});
const failure = (status,code) => {throw Object.assign(new Error(code),{status,code});};
async function readBody(request) {
  if(!request.body)return {};
  const reader=request.body.getReader(),chunks=[];let size=0;
  for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>6*1024*1024){await reader.cancel();failure(413,'body_too_large');}chunks.push(value);}
  const all=new Uint8Array(size);let offset=0;for(const c of chunks){all.set(c,offset);offset+=c.length;}
  try{return JSON.parse(new TextDecoder().decode(all));}catch{failure(400,'invalid_json');}
}

export class OriginalSyncGroup {
  constructor(ctx,env){this.ctx=ctx;this.storage=ctx.storage;this.env=env;this.chain=Promise.resolve();this.buckets=new WeakMap();}
  fetch(request){
    const run=this.chain.then(async()=>{
      if(request.headers.get('upgrade')?.toLowerCase()==='websocket')return this.openSocket(request);
      // deleteAll is atomic on SQLite storage, but is not exposed by the txn facade.
      const deletingGroup=request.method==='DELETE'&&/^\/v1\/groups\/[^/]+\/?$/.test(new URL(request.url).pathname);
      const result=await(this.storage.transaction&&!deletingGroup?this.storage.transaction(tx=>this.withStorage(tx,()=>this.handle(request))):this.handle(request));
      if(result.ok&&request.method!=='GET')await this.notify();return result;
    });
    this.chain=run.catch(()=>{});
    return run.catch(error=>response({error:{code:error.status?error.code:'internal_error',message:error.status?error.message:'서버 요청을 처리하지 못했어요.'}},error.status??500));
  }
  sockets(){return this.ctx.getWebSockets?.()??[];}
  async openSocket(request){
    const match=/^\/v1\/groups\/([^/]+)\/ws$/.exec(new URL(request.url).pathname),group=await this.storage.get('group');
    const protocols=(request.headers.get('sec-websocket-protocol')??'').split(',').map(s=>s.trim()),auth=protocols.find(p=>p.startsWith('spiralday.auth.'))?.slice(15);
    if(!match||!group||group.gid!==match[1])failure(404,'group_not_found');
    const tokenHash=await hash(auth??''),device=group.devices.find(d=>d.tokenHash===tokenHash&&(!d.provisionalExpires||d.provisionalExpires>Date.now()));
    if(!device||!protocols.includes('spiralday.v1'))failure(401,'device_removed');
    delete device.provisionalExpires;group.lastActive=Date.now();await this.storage.put('group',group);
    const pair=new WebSocketPair(),client=pair[0],server=pair[1];
    for(const ws of this.sockets())if(ws.deserializeAttachment()?.deviceId===device.id)ws.close(4000,'replaced');
    server.serializeAttachment({deviceId:device.id,live:protocols.includes('spiralday.live.1')});this.ctx.acceptWebSocket(server);
    server.send(JSON.stringify({head:group.head}));await this.notify();
    return new Response(null,{status:101,webSocket:client,headers:{'sec-websocket-protocol':'spiralday.v1'}});
  }
  async notify(){
    const group=await this.storage.get('group'),sockets=this.sockets();
    for(const ws of sockets){try{
      const own=ws.deserializeAttachment();
      if(!group){ws.send(JSON.stringify({deleted:true}));ws.close(4404,'deleted');continue;}
      if(!group.devices.some(d=>d.id===own.deviceId)){ws.send(JSON.stringify({removed:true}));ws.close(4401,'removed');continue;}
      const peers=sockets.filter(s=>s!==ws&&group.devices.some(d=>d.id===s.deserializeAttachment()?.deviceId));
      ws.send(JSON.stringify({head:group.head}));ws.send(JSON.stringify({devices:true}));ws.send(JSON.stringify({peers:peers.length,live:peers.filter(s=>s.deserializeAttachment()?.live).length}));
    }catch{}}
  }
  async webSocketMessage(ws,message){
    const group=await this.storage.get('group'),own=ws.deserializeAttachment();
    if(!group||!group.devices.some(d=>d.id===own?.deviceId)){ws.close(4401,'removed');return;}
    if(message==='ping'){ws.send('pong');return;}
    if(!own.live||typeof message!=='string'||message.length>65536)return;
    const now=Date.now();let bucket=this.buckets.get(ws)??{at:now,n:20};bucket.n=Math.min(20,bucket.n+(now-bucket.at)*.02);bucket.at=now;if(bucket.n<1)return;bucket.n--;this.buckets.set(ws,bucket);
    let data;try{data=JSON.parse(message);}catch{return;}
    if(typeof data.draft!=='string'||!/^[A-Za-z0-9_-]+$/.test(data.draft)||data.draft.length>64000||typeof data.q!=='string'||data.q.length>100)return;
    const value=JSON.stringify({draft:data.draft,q:data.q,from:own.deviceId});
    for(const peer of this.sockets()){const other=peer.deserializeAttachment();if(peer!==ws&&other.live&&group.devices.some(d=>d.id===other.deviceId))try{peer.send(value);}catch{}}
  }
  async webSocketClose(){await this.notify();}
  async webSocketError(){await this.notify();}
  alarm(){const run=this.chain.then(async()=>{
    const group=await this.storage.get('group');if(!group)return;
    const now=Date.now();
    if(now-(group.lastActive??group.created)>396*86400000){await this.storage.deleteAll();await this.notify();return;}
    for(const entry of group.records){const record=await this.storage.get('record:'+entry.rid);if(!record)continue;
      const keep=[];for(const version of record.history??[]){if(version.at>now-30*86400000)keep.push(version);else{for(let i=0;i<(version.chunks??0);i++)await this.storage.delete(`chunk:${entry.rid}:${version.seq}:${i}`);group.bytes=Math.max(0,(group.bytes??0)-(version.size??version.ct?.length??0));}}
      record.history=keep;await this.storage.put('record:'+entry.rid,record);
    }
    group.devices=group.devices.filter(d=>!d.provisionalExpires||d.provisionalExpires>now);
    group.pairings=group.pairings.filter(p=>p.expiresAt>now);await this.storage.put('group',group);await this.storage.setAlarm(now+86400000);
  });this.chain=run.catch(()=>{});return run;}
  async withStorage(storage,fn){const previous=this.storage;this.storage=storage;try{return await fn();}finally{this.storage=previous;}}
  async readVersion(rid,version){
    if(version.ct!==undefined)return version.ct;
    const chunks=[];for(let i=0;i<version.chunks;i++){const value=await this.storage.get(`chunk:${rid}:${version.seq}:${i}`);if(typeof value!=='string')failure(500,'missing_ciphertext');chunks.push(value);}return chunks.join('');
  }
  async handle(request){
    const url=new URL(request.url),method=request.method;
    const recoveryMatch=/^\/v1\/recovery\/([A-Za-z0-9_-]{43})(\/join)?$/.exec(url.pathname);
    const claiming=url.pathname==='/v1/pairings/claim'&&method==='POST';
    const match=recoveryMatch?[null,null,'/recovery']:claiming?[null,null,'/claim']:/^\/v1\/groups(?:\/([^/]+))?(.*)$/.exec(url.pathname);
    if(!match)failure(404,'not_found');
    const gid=match[1],route=match[2]||'/';
    let group=await this.storage.get('group');
    if(!gid&&!claiming&&!recoveryMatch&&method==='POST'){
      if(group)failure(409,'group_exists');
      const data=await readBody(request);
      if(typeof data.deviceName!=='string'||!/^p\.(Mac|Windows|iPhone|iPad|Android|Web)$/.test(data.deviceName))failure(400,'invalid_device');
      const id=encode(crypto.getRandomValues(new Uint8Array(16))),token=random(),now=Date.now();
      group={gid:request.headers.get('x-original-gid')??crypto.randomUUID(),head:0,created:now,devices:[{id,name:data.deviceName,tokenHash:await hash(token),created:now,lastSeen:now}],records:[],pairings:[]};
      await this.storage.put('group',group);await this.storage.setAlarm(now+86400000);
      return response({gid:group.gid,deviceId:id,token});
    }
    if(!group||!claiming&&!recoveryMatch&&group.gid!==gid)failure(404,'group_not_found');
    group.devices=group.devices.filter(d=>!d.provisionalExpires||d.provisionalExpires>Date.now());
    if(recoveryMatch){
      const id=recoveryMatch[1];
      if(method==='GET'&&!recoveryMatch[2]){
        if(group.recovery?.id!==id)failure(404,'recovery_not_found');
        return response({gid:group.gid,wrappedKey:group.recovery.wrappedKey});
      }
      if(method==='POST'&&recoveryMatch[2]){
        const data=await readBody(request);
        if(group.recovery?.id!==id||await hashAuth(data.auth)!==group.recovery.authHash)failure(403,'recovery_not_found');
        if(!/^p\.(Mac|Windows|iPhone|iPad|Android|Web)$/.test(data.deviceName??''))failure(400,'invalid_device');
        if(group.devices.length>=10)failure(409,'device_limit');
        const deviceId=encode(crypto.getRandomValues(new Uint8Array(16))),token=random(),now=Date.now();
        group.devices.push({id:deviceId,name:data.deviceName,tokenHash:await hash(token),created:now,lastSeen:now});
        await this.storage.put('group',group);return response({gid:group.gid,deviceId,token});
      }
      if(method==='PUT'&&!recoveryMatch[2]){
        const token=request.headers.get('authorization')?.replace(/^Bearer /,'')??'';
        const tokenHash=await hash(token);
        if(!group.devices.some(d=>d.tokenHash===tokenHash))failure(401,'device_removed');
        const data=await readBody(request);
        if(data.gid!==group.gid||typeof data.wrappedKey!=='string'||data.wrappedKey.length>512||!/^[A-Za-z0-9_-]{43}$/.test(data.authHash??''))failure(400,'invalid_recovery');
        if(this.env.ORIGINAL_DIRECTORY){const reserved=await directory(this.env,'/reserve',{key:'recovery:'+id,gid:group.gid,expires:Date.now()+396*86400000});if(!reserved.ok)return reserved;}
        group.recovery={id,wrappedKey:data.wrappedKey,authHash:data.authHash};
        await this.storage.put('group',group);return response({ok:true});
      }
      failure(405,'method_not_allowed');
    }
    if(claiming){
      const data=await readBody(request),offer=group.pairings.find(p=>p.codeHash===data.codeHash&&p.status==='pending'&&p.expiresAt>Date.now());
      if(!offer)failure(404,'pairing_not_found');
      if(!/^p\.(Mac|Windows|iPhone|iPad|Android|Web)$/.test(data.deviceName??'')||!/^[-_A-Za-z0-9]{22}$/.test(data.nonce??'')||data.mode!==offer.mode)failure(400,'invalid_join');
      if(group.devices.length>=10)failure(409,'device_limit');
      const token=random(),id=encode(crypto.getRandomValues(new Uint8Array(16))),now=Date.now();
      offer.status='claimed';offer.device={id,name:data.deviceName,tokenHash:await hash(token),created:now,lastSeen:now};offer.nonce=data.nonce;
      await this.storage.put('group',group);
      return response({gid:group.gid,pairingId:offer.id,deviceId:id,token,expiresAt:offer.expiresAt});
    }
    const token=request.headers.get('authorization')?.replace(/^Bearer /,'')??'';
    const tokenHash=await hash(token),device=group.devices.find(d=>d.tokenHash===tokenHash);
    const joinMatch=/^\/pairings\/([^/]+)\/join$/.exec(route);
    if(joinMatch){
      const offer=group.pairings.find(p=>p.id===joinMatch[1]);
      if(!offer||offer.device?.tokenHash!==tokenHash)failure(404,'pairing_not_found');
      if(method==='DELETE'){
        group.devices=group.devices.filter(d=>d.id!==offer.device.id);offer.status='denied';delete offer.wrappedKey;
        await this.storage.put('group',group);return response({ok:true});
      }
      if(method!=='POST')failure(405,'method_not_allowed');
      if(offer.expiresAt<=Date.now())return response({status:'expired'});
      if(offer.status==='approved')return response({status:'approved',wrappedKey:offer.wrappedKey});
      return response({status:offer.status==='denied'?'denied':'waiting',expiresAt:offer.expiresAt});
    }
    if(!device)failure(401,'device_removed');
    // waitForApproval reads /devices before accept. Only sync traffic finalizes.
    if(['/changes','/records','/records/batch'].includes(route))delete device.provisionalExpires;
    device.lastSeen=Date.now();group.lastActive=device.lastSeen;await this.storage.put('group',group);
    if(route==='/pairings'&&method==='POST'){
      const data=await readBody(request),now=Date.now();
      if(!/^[A-Za-z0-9_-]{43}$/.test(data.codeHash??'')||typeof data.wrappedKey!=='string'||data.wrappedKey.length>512||!['code','qr'].includes(data.mode))failure(400,'invalid_pairing');
      group.pairings=group.pairings.filter(p=>p.expiresAt>now);
      if(group.pairings.some(p=>p.codeHash===data.codeHash))failure(409,'code_in_use');
      if(group.pairings.length>=10)failure(429,'too_many_pairings');
      const offer={id:crypto.randomUUID(),codeHash:data.codeHash,wrappedKey:data.wrappedKey,mode:data.mode,status:'pending',owner:device.id,expiresAt:now+600000};
      if(this.env.ORIGINAL_DIRECTORY){const reserved=await directory(this.env,'/reserve',{key:'pair:'+data.codeHash,gid:group.gid,expires:offer.expiresAt});if(!reserved.ok)return reserved;}
      group.pairings.push(offer);await this.storage.put('group',group);
      return response({pairingId:offer.id,expiresAt:offer.expiresAt});
    }
    const pm=/^\/pairings\/([^/]+)(\/approve)?$/.exec(route);
    if(pm){
      const offer=group.pairings.find(p=>p.id===pm[1]&&p.expiresAt>Date.now());
      if(!offer)failure(404,'pairing_not_found');
      if(offer.owner!==device.id)failure(403,'forbidden');
      if(pm[2]&&method==='POST'){
        const data=await readBody(request);
        if(offer.status!=='claimed'||offer.device?.id!==data.deviceId)failure(409,'pairing_not_claimed');
        if(group.devices.length>=10)failure(409,'device_limit');
        offer.status='approved';group.devices.push({...offer.device,provisionalExpires:offer.expiresAt});
      }else if(method==='DELETE'){
        if(offer.device)group.devices=group.devices.filter(d=>d.id!==offer.device.id||!d.provisionalExpires);
        offer.status='denied';delete offer.wrappedKey;
      }else if(method==='GET')return response({status:offer.status,expiresAt:offer.expiresAt,...offer.device?{device:{id:offer.device.id,name:offer.device.name},nonce:offer.nonce}:{}});
      else failure(405,'method_not_allowed');
      await this.storage.put('group',group);return response({ok:true});
    }
    if(route==='/'&&method==='GET')return response({gid:group.gid,devices:group.devices.length,maxDevices:10,head:group.head});
    if(route==='/'&&method==='DELETE'){await this.storage.deleteAll();return response({ok:true});}
    if(route==='/devices'&&method==='GET')return response({maxDevices:10,devices:group.devices.map(({tokenHash,provisionalExpires,...d})=>d)});
    const dm=/^\/devices\/([^/]+)$/.exec(route);
    if(dm){
      const id=dm[1]==='me'?device.id:decodeURIComponent(dm[1]),target=group.devices.find(d=>d.id===id);
      if(!target)failure(404,'device_not_found');
      if(method==='PATCH'){
        if(id!==device.id)failure(403,'forbidden');
        const data=await readBody(request);
        if(typeof data.name!=='string'||!/^e1\.[A-Za-z0-9_-]+$/.test(data.name)||data.name.length>512)failure(400,'invalid_name');
        target.name=data.name;
      }else if(method==='DELETE')group.devices=group.devices.filter(d=>d.id!==id);
      else failure(405,'method_not_allowed');
      await this.storage.put('group',group);return response({ok:true});
    }
    if(route==='/records'&&method==='POST'){
      const write=await readBody(request),result=await this.writeRecord(group,write);
      await this.storage.put('group',group);
      return result.conflict?response({seq:result.seq,ct:result.ct,error:{code:'conflict',message:'다른 기기의 변경이 있어요.'}},409):response({seq:result.seq});
    }
    if(route==='/records/batch'&&method==='POST'){
      const data=await readBody(request);
      if(!Array.isArray(data.writes)||data.writes.length>100)failure(400,'invalid_writes');
      const results=[];for(const write of data.writes)results.push(await this.writeRecord(group,write));
      await this.storage.put('group',group);return response({results});
    }
    if(route==='/changes'&&method==='GET'){
      const since=Number(url.searchParams.get('since')??0),limit=Number(url.searchParams.get('limit')??500);
      if(!Number.isSafeInteger(since)||since<0||!Number.isInteger(limit)||limit<1||limit>500)failure(400,'invalid_cursor');
      const entries=group.records.filter(r=>r.seq>since).sort((a,b)=>a.seq-b.seq),changes=[];
      for(const entry of entries.slice(0,limit)){const item=await this.storage.get('record:'+entry.rid);if(item)changes.push({rid:entry.rid,seq:item.seq,ct:await this.readVersion(entry.rid,item)});}
      return response({head:group.head,changes,more:entries.length>limit});
    }
    const hm=/^\/records\/([A-Za-z0-9_-]+)\/history$/.exec(route);
    if(hm&&method==='GET'){
      const item=await this.storage.get('record:'+hm[1]);
      const metadata=item?[...(item.history??[]).filter(v=>v.at>Date.now()-30*86400000),{seq:item.seq,chunks:item.chunks,ct:item.ct,at:item.at,current:true}]:[],versions=[];
      for(const v of metadata)versions.push({seq:v.seq,ct:await this.readVersion(hm[1],v),at:v.at,current:!!v.current});
      return response({versions});
    }
    failure(404,'not_found');
  }
  async writeRecord(group,write){
    if(typeof write.rid!=='string'||!/^[A-Za-z0-9_-]{43}$/.test(write.rid)||typeof write.ct!=='string'||!/^[A-Za-z0-9_-]+$/.test(write.ct)||write.ct.length>1400000||!Number.isSafeInteger(write.baseSeq)||write.baseSeq<0)failure(400,'invalid_record');
    const key='record:'+write.rid,previous=await this.storage.get(key);
    if(write.baseSeq!==(previous?.seq??0))return {rid:write.rid,conflict:true,seq:previous?.seq??0,ct:previous?await this.readVersion(write.rid,previous):null};
    if(!previous&&group.records.length>=800)failure(413,'quota_exceeded');
    let usage=(group.bytes??group.records.reduce((sum,r)=>sum+r.size,0))+write.ct.length;
    const at=Date.now(),seq=++group.head,history=[];
    for(const v of [...(previous?.history??[]),...(previous?[{seq:previous.seq,chunks:previous.chunks,ct:previous.ct,at:previous.at,size:previous.size??previous.ct?.length??0,current:false}]:[])]){if(v.at>at-30*86400000)history.push(v);else{for(let i=0;i<(v.chunks??0);i++)await this.storage.delete(`chunk:${write.rid}:${v.seq}:${i}`);usage=Math.max(0,usage-(v.size??v.ct?.length??0));}}
    if(usage>50*1024*1024)failure(413,'quota_exceeded');
    if(history.length>=500)failure(413,'history_quota');
    const chunks=Math.ceil(write.ct.length/96000);
    for(let i=0;i<chunks;i++)await this.storage.put(`chunk:${write.rid}:${seq}:${i}`,write.ct.slice(i*96000,(i+1)*96000));
    await this.storage.put(key,{seq,chunks,size:write.ct.length,at,history});group.bytes=usage;
    group.records=group.records.filter(r=>r.rid!==write.rid);group.records.push({rid:write.rid,seq,size:write.ct.length});
    return {rid:write.rid,seq};
  }
}
