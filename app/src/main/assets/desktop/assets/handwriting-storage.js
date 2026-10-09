import {mergeStrokes,validateStroke} from './handwriting-model.js';

// Single owner in the main planner window; settings reuse the parent's bridge.
export function createInkStore(backend) {
  let chain=Promise.resolve();
  const read=async()=>{
    const doc=(await backend.read())??{v:1,records:[],pending:{}};
    if (doc.v!==1 || !Array.isArray(doc.records) || !doc.pending || typeof doc.pending!=='object' || Array.isArray(doc.pending)) throw Error('필기 저장소 버전을 읽지 못했습니다. 원본은 보존했습니다.');
    for (const record of doc.records) validateStroke(record);
    const drafts=(await backend.readDrafts?.())??[];
    if(drafts.length){doc.records=mergeStrokes(doc.records,drafts);doc.pending={...doc.pending};for(const draft of drafts){const winner=doc.records.find(s=>s.id===draft.id);if(winner.stamp.time===draft.stamp.time&&winner.stamp.device===draft.stamp.device)doc.pending[draft.id]={...draft.stamp};}}
    return doc;
  };
  const run=fn=>{const result=chain.catch(()=>{}).then(async()=>fn(await read()));chain=result.catch(()=>{});return result;};
  const write=async doc=>{await backend.write(doc);await backend.clearDrafts?.(doc);};
  return {
    loadPage:(bookId,kind,date)=>run(doc=>doc.records.filter(s=>s.bookId===bookId&&s.kind===kind&&s.date===date)),
    pendingRecords:()=>run(doc=>doc.records.filter(s=>doc.pending[s.id])),
    allRecords:()=>run(doc=>doc.records),
    applyStrokes:(records,local=false)=>run(async doc=>{
      const merged=mergeStrokes(doc.records,records),pending={...doc.pending};
      if(local)for(const incoming of records){const winner=merged.find(s=>s.id===incoming.id);if(winner)pending[incoming.id]={...winner.stamp};}
      else for(const incoming of records){const winner=merged.find(s=>s.id===incoming.id);if(pending[incoming.id]&&winner&&winner.stamp.time===incoming.stamp.time&&winner.stamp.device===incoming.stamp.device)delete pending[incoming.id];}
      if(local){try{await backend.stageDrafts?.(records.map(validateStroke));}catch{/* The primary store can still succeed if the recovery journal is full. */}}
      await write({v:1,records:merged,pending});
      return merged;
    }),
    ackRecords:acks=>run(async doc=>{
      const pending={...doc.pending};
      for(const ack of acks){const current=pending[ack.id];if(current&&current.time===ack.stamp?.time&&current.device===ack.stamp?.device)delete pending[ack.id];}
      await write({...doc,pending});
    }),
  };
}

export function openInkStorage(name='dayflow-handwriting-v1') {
  const ready=new Promise((resolve,reject)=>{
    const request=indexedDB.open(name,1);
    request.onupgradeneeded=()=>request.result.createObjectStore('ink');
    request.onsuccess=()=>{request.result.onversionchange=()=>request.result.close();resolve(request.result);};
    request.onerror=()=>reject(request.error);
    request.onblocked=()=>reject(Error('다른 창이 필기 저장소를 사용 중입니다. 기존 기록은 보존합니다.'));
  });
  const operation=async(mode,value)=>{
    const db=await ready;
    return new Promise((resolve,reject)=>{
      const tx=db.transaction('ink',mode),store=tx.objectStore('ink');
      const request=mode==='readonly'?store.get('document'):store.put(value,'document');
      let result;
      request.onsuccess=()=>{result=request.result;};
      tx.oncomplete=()=>resolve(result);
      tx.onabort=()=>reject(tx.error??Error('필기를 저장하지 못했습니다.'));
      tx.onerror=()=>reject(tx.error??Error('필기 저장소 오류입니다.'));
    });
  };
  const prefix=name+':draft:';
  const entries=()=>Object.keys(localStorage).filter(key=>key.startsWith(prefix)).map(key=>({key,text:localStorage.getItem(key)}));
  return createInkStore({read:()=>operation('readonly'),write:doc=>operation('readwrite',doc),
    readDrafts:()=>entries().map(({text})=>validateStroke(JSON.parse(text))),
    stageDrafts:records=>{for(const record of records)localStorage.setItem(prefix+record.id,JSON.stringify(record));},
    clearDrafts:doc=>{for(const {key,text}of entries()){const draft=validateStroke(JSON.parse(text)),saved=doc.records.find(s=>s.id===draft.id);if(saved&&JSON.stringify(mergeStrokes([saved],[draft])[0])===JSON.stringify(validateStroke(saved))&&localStorage.getItem(key)===text)localStorage.removeItem(key);}},
  });
}
