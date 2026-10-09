import {openInkStorage} from './handwriting-storage.js';
import {startInkSync} from './handwriting-sync.js';
import {mountInkOverlay} from './handwriting-overlay.js';

export function mountHandwriting({store,bridge,navigation}){
 if(new URLSearchParams(location.search).get('view')!=='main')return;
 const android=!!window.AndroidDayflow,overlays=new Map();let storage=null,context=null,sync=null,closed=false,busy=false;
 const options={enabled:false,finger:false,mode:'draw',color:'#34333a',width:3,eraseRadius:18};
 let toolbar=null,status=null,lastOverlay=null,details=null;
 const error=e=>{if(status)status.textContent=e.message||'필기를 저장하지 못했습니다. 원본을 유지합니다.';console.warn('[Dayflow ink] operation failed');};
 const refresh=()=>{for(const value of overlays.values())void value.overlay.refresh();};
 const saved=()=>{if(status)status.textContent='기기에 저장됨';void sync?.syncNow();};
 if(android){
  toolbar=document.createElement('div');toolbar.dataset.inkTools='true';toolbar.style.cssText='display:flex;flex-shrink:0;font:14px sans-serif';
  const button=(text,fn)=>{const b=document.createElement('button');b.textContent=text;b.style.cssText='min-height:36px;border:1px solid #dad7cf;border-radius:8px;background:#fffdf7;color:#34333a;font:14px sans-serif';b.onclick=fn;toolbar.append(b);return b;};
  const setEnabled=value=>{options.enabled=value;toggle.textContent='✎ 필기';toggle.setAttribute('aria-pressed',String(value));toggle.style.background=value?'#d9ede6':'#fffdf7';for(const item of overlays.values())item.overlay.updateMode();details.hidden=!value;document.documentElement.style.setProperty('--android-content-top',value?'92px':'48px');window.dispatchEvent(new Event('dayflow-ink-layout'));};
  const toggle=button('✎ 필기',()=>{setEnabled(!options.enabled);document.activeElement?.blur();});toggle.setAttribute('aria-pressed','false');toggle.style.whiteSpace='nowrap';
  details=document.createElement('div');details.hidden=true;details.dataset.inkSettings='true';details.style.cssText='position:fixed;left:0;top:48px;width:100%;height:44px;box-sizing:border-box;z-index:900;background:#fffdf7;padding:4px 8px;display:flex;align-items:center;gap:10px;white-space:nowrap;overflow-x:auto;overflow-y:hidden;transition:none;animation:none;transform-origin:top left';document.body.append(details);
  const color=document.createElement('input');color.type='color';color.title='필기 색상';color.setAttribute('aria-label','필기 색상');color.value=options.color;color.style.cssText='width:36px;height:32px;flex-shrink:0';color.oninput=()=>options.color=color.value;details.append(color);
  const width=document.createElement('input');width.type='range';width.min='1';width.max='12';width.value='3';width.setAttribute('aria-label','필기 굵기');width.style.cssText='width:90px;flex-shrink:0';width.oninput=()=>options.width=Number(width.value);details.append(width);
  const tool=document.createElement('select');tool.setAttribute('aria-label','필기 도구');tool.style.cssText='min-height:34px;flex-shrink:0;font:14px sans-serif';for(const [value,label]of [['draw','펜'],['partial','부분 지우개'],['lasso','올가미 선택'],['stroke','획 지우개']]){const option=document.createElement('option');option.value=value;option.textContent=label;tool.append(option);}tool.onchange=()=>options.mode=tool.value;details.append(tool);
  const eraseSize=document.createElement('input');eraseSize.type='range';eraseSize.min='6';eraseSize.max='60';eraseSize.value='18';eraseSize.setAttribute('aria-label','지우개 크기');eraseSize.title='지우개 크기';eraseSize.style.cssText='width:70px;flex-shrink:0';eraseSize.oninput=()=>options.eraseRadius=Number(eraseSize.value);details.append(eraseSize);
  const check=(label,key)=>{const line=document.createElement('label'),input=document.createElement('input');input.type='checkbox';input.onchange=()=>options[key]=input.checked;line.append(input,document.createTextNode(label));line.style.display='block';details.append(line);};check('손가락 필기','finger');
  const action=(label,fn)=>{const b=document.createElement('button');b.textContent=label;b.style.cssText='min-height:36px;flex-shrink:0';b.onclick=fn;details.append(b);};
  const current=()=>lastOverlay&&[...overlays.values()].some(v=>v.overlay===lastOverlay)?lastOverlay:[...overlays.values()].at(-1)?.overlay;
  action('선택 지우기',()=>current()?.deleteSelected());
  action('페이지 전체 지우기',()=>{const target=current();if(!target)return;const dialog=document.createElement('dialog');dialog.style.cssText='border:1px solid #ddd;border-radius:12px;padding:20px;max-width:320px;z-index:2147483500;background:#fffdf7';const text=document.createElement('p');text.textContent='선택한 페이지의 자유 필기만 모두 지울까요? 글자·일정은 유지되며 필기 취소로 되돌릴 수 있습니다.';const cancel=document.createElement('button'),confirm=document.createElement('button');cancel.textContent='취소';confirm.textContent='전체 지우기';for(const b of [cancel,confirm])b.style.cssText='min-height:44px;margin:4px;padding:8px 14px';cancel.onclick=()=>dialog.close();confirm.onclick=()=>{void target.clear();dialog.close();};dialog.append(text,cancel,confirm);dialog.onclose=()=>dialog.remove();document.body.append(dialog);dialog.showModal();});
  for(const [label,method]of [['필기 취소','undo'],['필기 복원','redo']]){const b=document.createElement('button');b.textContent=label;b.style.cssText='min-height:36px;flex-shrink:0';b.onclick=()=>current()?.[method]();details.append(b);}
  status=document.createElement('small');status.style.cssText='flex-shrink:0;white-space:nowrap';details.append(status);
  document.addEventListener('click',e=>{const button=e.target.closest?.('#android-toolbar button,#android-palette button');if(button&&!toolbar.contains(button)&&options.enabled)setEnabled(false);},true);
 }
 async function reconcile(){if(closed||busy)return;busy=true;try{
  if(store.getState().status!=='ready')return;
  let next;try{next=await bridge.sync.inkCall('context',{});}catch(e){error(e);return;}
  const owner=next?.gid??'local';
  if(!storage||context?.gid!==owner){sync?.();for(const value of overlays.values())value.overlay.dispose();overlays.clear();storage=openInkStorage('dayflow-handwriting-v1-'+owner);context={gid:owner,deviceId:next?.deviceId??'local'};
   // First connection binds previously unassigned ink once. Never copy a prior group's ink into another group.
   if(next?.gid&&!localStorage.getItem('dayflow-ink-first-owner')){const local=openInkStorage('dayflow-handwriting-v1-local');const records=await local.allRecords();if(records.length)await storage.applyStrokes(records,true);localStorage.setItem('dayflow-ink-first-owner',owner);}
   if(next?.gid)sync=startInkSync({bridge:{sync:{inkCall:(action,arg)=>bridge.sync.inkCall(action,{...arg,expectedGid:owner})}},storage,onChanged:refresh,onError:error});
  }
  const nav=navigation(),bookId=store.getState().library.activeID;
  const pages=new Set(document.querySelectorAll('[data-page-kind="daily"],[data-page-kind="weekly"]'));
  for(const [page,value]of overlays)if(!pages.has(page)){value.overlay.dispose();overlays.delete(page);}
  for(const page of pages){const kind=page.dataset.pageKind,date=page.dataset.tabletDay??(kind==='daily'?nav.day:nav.week);const key=`${bookId}|${kind}|${date}`;if(!bookId||!date)continue;
   if(overlays.get(page)?.key===key)continue;overlays.get(page)?.overlay.dispose();const overlay=mountInkOverlay({page,bookId,kind,date,editable:android,storage,deviceId:context.deviceId,options:()=>options,onInteraction:()=>lastOverlay=overlay,onSelection:n=>{lastOverlay=overlay;if(status)status.textContent=n?`${n}개 획 선택 · 선택 지우기를 누르세요`:'선택된 필기 없음';},onSaved:()=>{lastOverlay=overlay;saved();},onError:error});overlays.set(page,{key,overlay});
  }
  if(toolbar){const slot=document.querySelector('[data-ink-toolbar-slot]');if(slot&&toolbar.parentElement!==slot)slot.append(toolbar);toolbar.style.display=pages.size?'flex':'none';if(!pages.size&&options.enabled){options.enabled=false;details.hidden=true;document.documentElement.style.setProperty('--android-content-top','48px');window.dispatchEvent(new Event('dayflow-ink-layout'));}}
 }catch(e){error(e);}finally{busy=false;}}
 const timer=setInterval(()=>void reconcile(),1000);void reconcile();
 return()=>{closed=true;clearInterval(timer);sync?.();for(const value of overlays.values())value.overlay.dispose();toolbar?.remove();details?.remove();};
}
