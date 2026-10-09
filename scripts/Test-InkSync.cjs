const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const dir=path.resolve(__dirname,'../app/src/main/assets/desktop/assets');
const url=source=>'data:text/javascript;base64,'+Buffer.from(source).toString('base64');
const model=url(fs.readFileSync(path.join(dir,'handwriting-model.js'),'utf8'));
const load=name=>import(url(fs.readFileSync(path.join(dir,name),'utf8').replace("'./handwriting-model.js'",JSON.stringify(model))));
global.window=new EventTarget();
const stroke=(id='a')=>({v:1,id,bookId:'book',kind:'daily',date:'2026-10-09',points:[{x:1,y:2,p:1,t:0}],color:'#000000',width:2,stamp:{time:1,device:'tab'},deleted:false});
test('offline pending strokes survive and upload after reconnection',async()=>{
 const {createInkStore}=await load('handwriting-storage.js'),{startInkSync}=await load('handwriting-sync.js');
 let doc=null,online=false,pushes=0,errors=0;
 const storage=createInkStore({read:async()=>structuredClone(doc),write:async v=>{doc=structuredClone(v)}});
 await storage.applyStrokes([stroke()],true);
 const bridge={sync:{inkCall:async(action,arg)=>{if(!online)throw Error('offline');if(action==='pull')return {records:[],cursor:0,more:false};pushes++;assert.equal(arg.baseSeq,0);return {ok:true,seq:1};}}};
 const sync=startInkSync({bridge,storage,onError:()=>errors++,interval:999999});
 try{await new Promise(r=>setImmediate(r));assert.equal(errors,1);assert.equal((await storage.pendingRecords()).length,1);online=true;await sync.syncNow();assert.equal(pushes,1);assert.equal((await storage.pendingRecords()).length,0);}finally{sync();}
});
test('malformed pagination cannot trap a sync loop',async()=>{
 const {startInkSync}=await load('handwriting-sync.js');let calls=0,errors=0;
 const sync=startInkSync({bridge:{sync:{inkCall:async()=>{if(++calls>3)throw Error('loop');return {records:[],cursor:0,more:true};}}},storage:{applyStrokes:async()=>{},pendingRecords:async()=>[]},onError:()=>errors++,interval:999999});
 try{await new Promise(r=>setImmediate(r));assert.equal(calls,1);assert.equal(errors,1);}finally{sync();}
});
