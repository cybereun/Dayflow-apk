const assert=require('node:assert/strict');
(async()=>{
 const tabs=await(await fetch('http://127.0.0.1:9229/json')).json(),tab=tabs.find(t=>t.url.includes('view=main'));
 const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j});
 const result=await new Promise((resolve,reject)=>{ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id!==1)return;if(m.result.exceptionDetails)reject(Error(m.result.exceptionDetails.text));else resolve(m.result.result.value);};ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{awaitPromise:true,returnByValue:true,expression:`(async()=>{
 const {mountInkOverlay}=await import('./assets/handwriting-overlay.js'),{createInkStore}=await import('./assets/handwriting-storage.js');let doc=null;
 const storage=createInkStore({read:async()=>structuredClone(doc),write:async v=>doc=structuredClone(v)});
 const record={v:1,id:'flicker-existing',bookId:'flicker-test',kind:'daily',date:'2026-10-09',points:[{x:100,y:100,p:.5,t:0},{x:110,y:110,p:.5,t:1}],color:'#123456',width:3,stamp:{time:1,device:'test'},deleted:false};await storage.applyStrokes([record],true);
 const page=document.createElement('div');page.style.cssText='position:absolute;left:0;top:0;width:1277px;height:2000px;opacity:0;pointer-events:none';document.body.append(page);
 const overlay=mountInkOverlay({page,bookId:'flicker-test',kind:'daily',date:'2026-10-09',editable:true,storage,deviceId:'test',options:()=>({enabled:true,mode:'draw',finger:false,color:'#000000',width:3})});
 try{await overlay.refresh();const svg=page.querySelector('svg'),existing=svg.querySelector('path');svg.setPointerCapture=()=>{};const r=svg.getBoundingClientRect();svg.dispatchEvent(new PointerEvent('pointerdown',{pointerId:1,pointerType:'pen',clientX:r.left+20,clientY:r.top+20,pressure:.5}));const active=svg.lastElementChild;let removed=0;
 const observer=new MutationObserver(entries=>{for(const entry of entries)for(const node of entry.removedNodes)if(node===active||node===existing)removed++;});observer.observe(svg,{childList:true});
 await overlay.refresh();await overlay.refresh();await new Promise(r=>setTimeout(r,20));observer.disconnect();return {removed,existingRetained:svg.contains(existing),activeRetained:svg.contains(active)};
 }finally{overlay.dispose();page.remove();}
 })()`}}));});ws.close();assert.deepEqual(result,{removed:0,existingRetained:true,activeRetained:true});console.log('Ink refresh retains active and unchanged paths: PASS');
})().catch(e=>{console.error(e);process.exit(1)});
