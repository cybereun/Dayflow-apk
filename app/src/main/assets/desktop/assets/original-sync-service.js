import {adaptPlannerStore} from './original-store-adapter.js';
let flushBeforeUpdate=async()=>{};
let historyItems=()=>[];
export const originalHistoryItems=(kind,date)=>historyItems(kind,date);
export async function mountOriginalTypingHint(root,key){
 const native=await import('../vendor/spiralday-sync/runtime/assets/index-BQNbWZEG.js');return native.mountNativeTypingHint(root,key);
}
export const saveOriginalSyncBeforeUpdate=()=>flushBeforeUpdate();
export async function requireMergeBackup(adapter,bridge){
 await adapter.getState().saveNow();const backup=await bridge.storage.snapshotNow();
 if(!backup)throw Error('백업을 만들지 못해 기록 합치기를 중단했어요.');return backup;
}
export function startOriginalSync({store,bridge,editing}){
 if(new URLSearchParams(location.search).get('view')!=='main'||!bridge?.sync?.onCommand)return;
 window.spiralday=bridge;
 let controller,adapter,native,disposed=false,cleanupListeners=()=>{};
 const ready=(async()=>{
  for(let i=0;store.getState().status!=='ready';i++){if(disposed)return;if(i>600)throw Error('플래너를 읽지 못해 동기화를 준비하지 못했어요.');await new Promise(r=>setTimeout(r,100));}
  native=await import('../vendor/spiralday-sync/runtime/assets/index-BQNbWZEG.js');
  adapter=adaptPlannerStore(store,bridge.storage);
  const backend={...bridge.storage,
   async writeLibrary(text){await bridge.storage.writeLibrary(text);controller?.saved({library:true});},
   async writeBook(id,text){await bridge.storage.writeBook(id,text);controller?.saved({bookId:id});},
   async deleteBook(id){await bridge.storage.deleteBook(id);controller?.saved({deletedBook:id,library:true});}
  };
  controller=native.createNativeSyncController({store:adapter,backend,bridge,editingKey:()=>editing.getState().key,onEditingChange:fn=>editing.subscribe(fn),makeStorage:m=>new m.IndexedDbSyncStorage('dayflow-original-sync-v1'),publish:value=>bridge.sync.publish(value),log:(level,message)=>{if(level==='error')console.error('[Dayflow sync]',message);}});
  historyItems=(kind,date)=>!controller.inGroup||!store.getState().library.activeID?[]:[{kind:'separator'},{label:kind==='week'?'이 주의 이전 버전…':'이 날의 이전 버전…',onSelect:()=>{controller.requestHistory({bookId:store.getState().library.activeID,kind,date});bridge.shell.openSettings('sync');}}];
  const offLibrary=store.subscribe((next,before)=>{if(next.library!==before.library)controller.saved({library:true});});
  const compose=e=>controller.composing(e.type==='compositionstart');document.addEventListener('compositionstart',compose,true);document.addEventListener('compositionend',compose,true);
  const online=()=>controller.networkChanged(true),focus=()=>controller.nudge();
  window.addEventListener('online',online);window.addEventListener('focus',focus);
  cleanupListeners=()=>{offLibrary();document.removeEventListener('compositionstart',compose,true);document.removeEventListener('compositionend',compose,true);window.removeEventListener('online',online);window.removeEventListener('focus',focus);};
  await controller.start();return{offLibrary};
 })();
 flushBeforeUpdate=async()=>{
  await ready;if(!adapter)return;
  await adapter.getState().saveNow();
  const sending=controller?.beforeClose();
  if(sending)await Promise.race([Promise.resolve(sending).catch(()=>{}),new Promise(r=>setTimeout(r,3000))]);
 };
 ready.catch(e=>bridge.sync.publish({ready:true,known:true,state:'error',available:false,inGroup:false,error:e.message,flow:null,warnings:[],localBooks:[]}));
 const off=bridge.sync.onCommand(async({id,action,arg})=>{
  try{
   await ready;if(!controller)throw Error('동기화를 준비하지 못했어요.');
   if(action==='sync.settingsClosed'){await controller.settingsClosed();bridge.sync.reply(id,{ok:true});return;}
   if(['sync.create','sync.join.start','sync.restore'].includes(action)&&!controller.inGroup){
    const root=document.getElementById('root'),previous=root?.inert,overlay=document.createElement('div');
    overlay.textContent='기존 기록을 안전하게 옮기는 중이에요…';overlay.style.cssText='position:fixed;inset:0;z-index:2147483647;background:#fffdf5e8;display:grid;place-items:center;font:18px sans-serif';
    document.activeElement?.blur();if(root)root.inert=true;document.body.append(overlay);
    try{await adapter.getState().saveNow();await bridge.sync.prepareMigration();await store.getState().load(bridge.storage);}
    finally{overlay.remove();if(root)root.inert=previous;}
   }
   if(['sync.join.accept','sync.restore'].includes(action)){
    await requireMergeBackup(adapter,bridge);
   }
   bridge.sync.reply(id,await controller.run(action,arg));
  }catch(e){bridge.sync.reply(id,{ok:false,message:e.message});}
 });
 const closing=event=>{if(controller)native.nativeSyncBeforeUnload(controller,event);};window.addEventListener('beforeunload',closing);
 return()=>{disposed=true;off();cleanupListeners();historyItems=()=>[];window.removeEventListener('beforeunload',closing);controller?.dispose();adapter?.dispose();};
}
