// Read-only diagnostics: no keys, tokens, group IDs or handwriting content are printed.
(async()=>{
 const port=Number(process.argv[2]??9229);if(![9228,9229].includes(port))throw Error('Unsupported local diagnostic port');
 const tabs=await(await fetch('http://127.0.0.1:'+port+'/json')).json(),tab=tabs.find(t=>t.url.includes('view=main'));
 if(!tab)throw Error('Main WebView not found');
 const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j});
 let seq=0;const calls=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(calls.has(m.id)){calls.get(m.id)(m);calls.delete(m.id);}};
 const evaluate=async expression=>{const id=++seq;const m=await new Promise(resolve=>{calls.set(id,resolve);ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{returnByValue:true,expression}}));});if(m.error||m.result?.exceptionDetails)throw Error('Diagnostic evaluation failed');return m.result.result.value;};
 await evaluate(`window.__inkDiagnostic=null;(async()=>{
 const bounded=(promise,stage)=>Promise.race([promise,new Promise((_,reject)=>setTimeout(()=>reject(Error(stage+' timed out')),12000))]);
 const context=await bounded(window.dayflow.sync.inkCall('context',{}),'native context');
 const {openInkStorage}=await import('./assets/handwriting-storage.js');
 const storage=openInkStorage('dayflow-handwriting-v1-'+(context.gid??'local'));
 const records=await bounded(storage.allRecords(),'local ink storage'),pending=await bounded(storage.pendingRecords(),'pending ink');
 let serverCount=0,cursor=0,more=!!context.gid;
 while(more){const batch=await bounded(window.dayflow.sync.inkCall('pull',{since:cursor,expectedGid:context.gid}),'server pull');serverCount+=batch.records.length;more=batch.more;if(more&&batch.cursor<=cursor)throw Error('Non-advancing server cursor');cursor=batch.cursor;}
 return {connected:!!context.gid,localRecords:records.length,liveRecords:records.filter(r=>!r.deleted).length,pendingRecords:pending.length,serverRecords:serverCount,renderedStrokes:document.querySelectorAll('[data-dayflow-ink] path').length,status:document.querySelector('[data-ink-settings] small')?.textContent??null};
 })().then(value=>window.__inkDiagnostic={value},e=>window.__inkDiagnostic={error:e.message});'started'`);
 let result;for(let i=0;i<160;i++){result=await evaluate('window.__inkDiagnostic');if(result)break;await new Promise(r=>setTimeout(r,250));}ws.close();if(!result)throw Error('Ink diagnostic timed out');if(result.error)throw Error(result.error);console.log(JSON.stringify(result.value));
})().catch(e=>{console.error(e.message);process.exit(1)});
