// Real-device regression: no planner text or credentials are inspected.
const assert=require('node:assert/strict');
(async()=>{
 const tabs=await fetch('http://127.0.0.1:9229/json').then(r=>r.json()),tab=tabs.find(t=>t.url.includes('view=main'));
 assert.ok(tab,'main WebView missing');const ws=new WebSocket(tab.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j});
 if(process.argv[2]==='phone')await new Promise(resolve=>{ws.onmessage=e=>{if(JSON.parse(e.data).id===2)resolve()};ws.send(JSON.stringify({id:2,method:'Emulation.setDeviceMetricsOverride',params:{width:360,height:800,screenWidth:360,screenHeight:800,deviceScaleFactor:1,mobile:true}}));});
 const result=await new Promise((resolve,reject)=>{ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id===1){if(m.result.exceptionDetails)reject(Error(m.result.exceptionDetails.text));else resolve(m.result.result.value);}};ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{awaitPromise:true,returnByValue:true,expression:`(async()=>{
 const tools=document.querySelector('[data-ink-tools]'),panel=document.querySelector('[data-ink-settings]');
 if(!tools||!panel)return {missing:true};
 const inHeader=!!tools.closest('#android-toolbar');const button=tools.querySelector('button');
 if(panel.hidden)button.click();await new Promise(r=>setTimeout(r,200));
 const p=panel.getBoundingClientRect(),header=document.querySelector('#android-toolbar').getBoundingClientRect(),stage=document.querySelector('.android-tablet-spread,.dayflow-planner-stage');const content=stage?.getBoundingClientRect();
 const initial=[p.x,p.y,p.width,p.height];let stable=true;for(let i=0;i<15;i++){await new Promise(r=>setTimeout(r,80));const r=panel.getBoundingClientRect();if(JSON.stringify([r.x,r.y,r.width,r.height])!==JSON.stringify(initial))stable=false;}
 const controlsFit=Array.from(document.querySelectorAll('#android-toolbar button')).filter(e=>getComputedStyle(e).display!=='none').every(e=>{const r=e.getBoundingClientRect();return r.left>=header.left-1&&r.right<=header.right+1});
 const result={inHeader,panelBelowHeader:p.top>=header.bottom-1,contentBelowPanel:!!content&&content.top>=p.bottom-1,oneRow:p.height<=48,stable,controlsFit};button.click();await new Promise(r=>setTimeout(r,100));result.closes=panel.hidden;return result;
 })()`}}));});
 if(process.argv[2]==='phone')await new Promise(resolve=>{ws.onmessage=e=>{if(JSON.parse(e.data).id===3)resolve()};ws.send(JSON.stringify({id:3,method:'Emulation.clearDeviceMetricsOverride',params:{}}));});
 ws.close();assert.deepEqual(result,{inHeader:true,panelBelowHeader:true,contentBelowPanel:true,oneRow:true,stable:true,controlsFit:true,closes:true});console.log('Ink header and settings row: PASS');
})().catch(e=>{console.error(e);process.exitCode=1;});
