// Debug-only local WebView inspection. Never prints credentials or planner contents.
(async()=>{
 const tabs=await fetch('http://127.0.0.1:9229/json').then(r=>r.json());
 const tab=tabs.find(t=>t.url.includes('view=main'));if(!tab)throw Error('Main WebView not found');
 const ws=new WebSocket(tab.webSocketDebuggerUrl);
 await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
 if(process.argv[2]==='tablet' || process.argv[2]==='phone-reset') {
  const tablet=process.argv[2]==='tablet';
  await new Promise(resolve=>{ws.onmessage=e=>{if(JSON.parse(e.data).id===3)resolve();};ws.send(JSON.stringify({id:3,method:tablet?'Emulation.setDeviceMetricsOverride':'Emulation.clearDeviceMetricsOverride',params:tablet?{width:1280,height:800,deviceScaleFactor:1,mobile:true}:{}}));});
 }
 if (process.argv[2]==='zoom' || process.argv[2]==='unzoom') {
  await new Promise(resolve=>{ws.onmessage=e=>{if(JSON.parse(e.data).id===2)resolve();};ws.send(JSON.stringify({id:2,method:'Emulation.setPageScaleFactor',params:{pageScaleFactor:process.argv[2]==='zoom'?2:1}}));});
  await new Promise(resolve=>setTimeout(resolve,150));
  process.argv[2]='palette';
 }
 const expression=process.argv[2]==='ink-popup-stability'
   ? "(async()=>{const button=document.querySelector('[data-ink-tools] button');if(!button)return {missing:true};const panel=document.querySelector('[data-ink-settings]');if(!panel)return {isolated:false};if(panel.hidden)button.click();const positions=[];for(let i=0;i<20;i++){await new Promise(r=>setTimeout(r,60));const rect=panel.getBoundingClientRect();positions.push({x:rect.x,y:rect.y,w:rect.width,h:rect.height});}return {isolated:panel.parentElement===document.body,stable:positions.every(p=>JSON.stringify(p)===JSON.stringify(positions[0])),samples:positions.length,rect:positions[0],ownRecognizer:typeof AndroidDayflow.recognizeInk}})()"
   : process.argv[2]==='recognizer-status'
   ? "new Promise(resolve=>{const old=window.__dayflowInkResult;const id='diagnostic-model-status';const timer=setTimeout(()=>{window.__dayflowInkResult=old;resolve({timeout:true})},10000);window.__dayflowInkResult=(key,result)=>{if(key!==id)return old?.(key,result);clearTimeout(timer);window.__dayflowInkResult=old;resolve(result)};AndroidDayflow.recognizeInk(id,'status','[]')})"
   : process.argv[2]==='ink-check'
   ? "(async()=>{const backup=await window.dayflow.storage.snapshotNow();const pages=Array.from(document.querySelectorAll('[data-page-kind]'));return {backupCreated:!!backup,pages:pages.map(e=>({kind:e.dataset.pageKind,ink:!!e.querySelector('[data-dayflow-ink]')})),tools:!!document.querySelector('[data-ink-tools]'),toolsInsidePalette:!!document.querySelector('#android-palette [data-ink-tools]'),recognizer:typeof AndroidDayflow.recognizeInk,viewport:[innerWidth,innerHeight]}})()"
   : process.argv[2]==='tablet-check'
   ? "({pages:Array.from(document.querySelectorAll('[data-tablet-day]')).map(e=>({date:e.dataset.tabletDay,rect:(()=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height}})()})),pens:document.querySelectorAll('#android-palette .pal-pen').length,blockingDialogs:document.querySelectorAll('[role=dialog],[role=alertdialog]').length,edge:document.documentElement.dataset.androidPaletteEdge,frames:document.querySelectorAll('iframe').length})"
   : process.argv[2]==='tablet-diagnose'
   ? "(async()=>{const lib=await window.dayflow.storage.readLibrary();document.querySelector('#android-palette .pal-book')?.click();await new Promise(r=>setTimeout(r,200));return {viewport:[innerWidth,innerHeight],libraryStatus:lib.status,bookCount:lib.status==='ok'?JSON.parse(lib.text).books?.length:null,dialog:!!document.querySelector('[role=dialog]'),buttons:Array.from(document.querySelectorAll('[role=dialog] button')).map(e=>e.textContent),frames:document.querySelectorAll('iframe').length}})()"
   : process.argv[2]==='tablet'
   ? "(async()=>{window.dayflow.window.setKind('daily');await new Promise(r=>setTimeout(r,250));return {pages:Array.from(document.querySelectorAll('[data-tablet-day]')).map(e=>({date:e.dataset.tabletDay,width:e.getBoundingClientRect().width})),paletteEdge:document.documentElement.dataset.androidPaletteEdge,spread:!!document.querySelector('.android-tablet-spread')}})()"
   : process.argv[2]==='book-menu'
   ? "(async()=>{document.querySelector('#android-palette .pal-book')?.click();await new Promise(r=>setTimeout(r,150));const dialog=document.querySelector('[role=dialog][aria-label=\"플래너 선택\"]');return {visible:!!dialog,choices:dialog?.querySelectorAll('button').length}})()"
   : process.argv[2]==='weekly-today'
   ? "(async()=>{const buttons=()=>Array.from(document.querySelectorAll('#android-toolbar button'));const weekly=buttons().find(e=>e.textContent==='주간');if(weekly)weekly.click();else window.dayflow.window.setKind('weekly');await new Promise(r=>setTimeout(r,300));const stage=document.querySelector('.dayflow-planner-stage');const initial=stage?.scrollLeft;stage?.scrollTo({left:0});buttons().find(e=>e.textContent==='오늘')?.click();await new Promise(r=>setTimeout(r,300));const page=stage?.querySelector('[data-page-kind=weekly]');const scale=Number(getComputedStyle(page).zoom);const expected=(42+((new Date().getDay()+6)%7)*276)*scale;return {initial,afterToday:stage?.scrollLeft,expected,aligned:Math.abs(stage.scrollLeft-expected)<2}})()"
   : process.argv[2]==='pens'
   ? "(()=>{const pen=document.querySelector('#android-palette .pal-pen');pen?.click();pen?.scrollIntoView({block:'nearest',inline:'center'});return {labels:Array.from(document.querySelectorAll('.android-pen-label')).map(e=>({text:e.textContent,width:e.clientWidth,height:e.clientHeight})),selected:document.querySelector('.android-selected-pen')?.textContent}})()"
   : process.argv[2]==='header'
   ? "({toolbar:(()=>{const e=document.getElementById('android-toolbar'),r=e?.getBoundingClientRect();return r?{top:r.top,bottom:r.bottom,text:e.textContent}:null})(),body:(()=>{const e=document.querySelector('.android-statistics,.dayflow-planner-stage'),r=e?.getBoundingClientRect();return r?{top:r.top,bottom:r.bottom}:null})()})"
   : process.argv[2]==='close-settings'
   ? "window.dayflow.shell.closeSettings(); 'closed'"
   : process.argv[2]==='home'
   ? "window.dayflow.window.setKind('home'); 'opened'"
   : process.argv[2]==='daily'
   ? "window.dayflow.window.setKind('daily'); 'opened'"
   : process.argv[2]==='readable'
   ? "({width:innerWidth,height:innerHeight,stage:Array.from(document.querySelectorAll('.dayflow-planner-stage')).map(e=>({readable:e.dataset.androidReadable,width:e.clientWidth,scrollWidth:e.scrollWidth,height:e.clientHeight,scrollHeight:e.scrollHeight})),statisticsRows:document.querySelectorAll('.android-statistics section').length})"
   : process.argv[2]==='palette-position'
   ? "(()=>{const style=document.createElement('style');style.textContent='#android-palette > .pal{justify-content:flex-start!important;align-items:flex-start!important}#android-palette .pal-card{margin:0!important}';document.head.append(style);return 'applied'})()"
   : process.argv[2]==='palette'
   ? "Array.from(document.querySelectorAll('#android-palette .pal, #android-palette .pal-card')).map(e=>{const r=e.getBoundingClientRect();return {class:e.className,x:r.x,y:r.y,width:r.width,height:r.height,viewport:innerHeight}})"
   : process.argv[2]==='network'
   ? "(async()=>{try{const r=await fetch('https://dayflow-sync.cybereunny.workers.dev/original/v1/pairings/claim',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({codeHash:'cors-verification-invalid',deviceName:'p.Android',nonce:'cors-verification',mode:'code'})});return {reachable:true,status:r.status}}catch(e){return {reachable:false,error:e.message}}})()"
   : process.argv[2]==='layout'
   ? "Array.from(document.querySelector('iframe')?.contentDocument.querySelectorAll('div')??[]).slice(0,12).map(e=>({class:e.className,width:e.clientWidth,display:getComputedStyle(e).display}))"
   : process.argv[2]==='settings'
   ? "window.dayflow.shell.openSettings('sync'); 'opened'"
   : "(async()=>{const s=await window.dayflow.sync.call('status');return {ready:s.ready,state:s.state,inGroup:s.inGroup,error:s.error,available:s.available,frames:document.querySelectorAll('iframe').length}})()";
 const result=await new Promise((resolve,reject)=>{ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id===1){if(m.result?.exceptionDetails)reject(Error(m.result.exceptionDetails.text));else resolve(m.result?.result?.value);}};ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression,awaitPromise:true,returnByValue:true}}));});
 console.log(JSON.stringify(result));ws.close();
})().catch(e=>{console.error(e.message);process.exitCode=1;});
